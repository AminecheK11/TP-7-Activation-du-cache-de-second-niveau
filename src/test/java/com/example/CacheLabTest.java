package com.example;

import com.example.model.*;
import com.example.service.*;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.*;
import javax.persistence.*;
import java.util.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class CacheLabTest {
    private EntityManagerFactory emf;
    private PerformanceTestService performance;

    @BeforeEach void creerBaseEtCache() {
        emf = JpaFactory.creer(true, "test_" + UUID.randomUUID().toString().replace("-", ""), false);
        new DataInitService(emf).initData();
        performance = new PerformanceTestService(emf);
        performance.viderCaches();
    }
    @AfterEach void fermer() { if (emf != null && emf.isOpen()) emf.close(); }
    private <T> T transaction(Function<EntityManager, T> action) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            T result = action.apply(em);
            em.getTransaction().commit();
            return result;
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally { em.close(); }
    }
    private Statistics stats() { return emf.unwrap(SessionFactory.class).getStatistics(); }
    private void verifier(Mesure m, long sql, int auteurs, int livres) {
        assertEquals(sql, m.sql(), m.scenario());
        assertEquals(auteurs, m.auteurs());
        assertEquals(livres, m.livres());
        assertEquals(auteurs, new HashSet<>(m.ids()).size(), "Pas de doublons dans les auteurs");
    }

    @Test void versionsEtBaseImposees() {
        assertEquals(17, Runtime.version().feature());
        assertEquals("5.4.32.Final", org.hibernate.Version.getVersionString());
        transaction(em -> {
            em.unwrap(org.hibernate.Session.class).doWork(c -> {
                assertEquals("H2", c.getMetaData().getDatabaseProductName());
                assertTrue(c.getMetaData().getDatabaseProductVersion().startsWith("1.4.200"));
                assertTrue(c.getMetaData().getURL().startsWith("jdbc:h2:mem:"));
            });
            return null;
        });
    }

    @Test void dixAuteursCinquanteLivresEtRelations() {
        transaction(em -> {
            assertEquals(10L, em.createQuery("select count(a) from Auteur a", Long.class).getSingleResult());
            assertEquals(50L, em.createQuery("select count(l) from Livre l", Long.class).getSingleResult());
            Auteur a = em.find(Auteur.class, 1L);
            assertEquals(5, a.getLivres().size());
            assertTrue(a.getLivres().stream().allMatch(l -> l.getAuteur() == a));
            return null;
        });
    }

    @Test void initialisationNeDupliquePasLesDonnees() {
        assertThrows(IllegalStateException.class, () -> new DataInitService(emf).initData());
        verifier(performance.testPerformanceAvecJoinFetch(), 1, 10, 50);
    }

    @Test void cacheReellementDesactiveEtNPlusUnVisible() {
        EntityManagerFactory sans = JpaFactory.creer(false, "sans_" + UUID.randomUUID().toString().replace("-", ""), false);
        try {
            new DataInitService(sans).initData();
            PerformanceTestService p = new PerformanceTestService(sans);
            assertFalse(sans.unwrap(SessionFactory.class).getSessionFactoryOptions().isSecondLevelCacheEnabled());
            Mesure premiere = p.testPerformanceSansCache();
            Mesure seconde = p.testPerformanceSansCache();
            verifier(premiere, 11, 10, 50); verifier(seconde, 11, 10, 50);
            assertEquals(0, seconde.hitsL2());
            verifier(p.testPerformanceAvecJoinFetch(), 1, 10, 50);
            verifier(p.testPerformanceAvecEntityGraph(), 1, 10, 50);
        } finally { sans.close(); }
    }

    @Test void joinFetchUneSeuleRequeteSansDoublons() {
        verifier(performance.testPerformanceAvecJoinFetch(), 1, 10, 50);
    }

    @Test void entityGraphUneRequeteEtCollectionChargee() {
        verifier(performance.testPerformanceAvecEntityGraph(), 1, 10, 50);
        List<Auteur> detaches = transaction(em -> em.createQuery("select distinct a from Auteur a", Auteur.class)
                .setHint("javax.persistence.fetchgraph", em.getEntityGraph("Auteur.livres")).getResultList());
        assertEquals(10, detaches.size());
        for (Auteur a : detaches) {
            assertTrue(emf.getPersistenceUnitUtil().isLoaded(a, "livres"));
            assertEquals(5, a.getLivres().size()); // Fonctionne apres fermeture de l'EntityManager.
        }
    }

    @Test void optimisationsConserventAuteurSansLivre() {
        Long id = transaction(em -> { Auteur a = new Auteur("Sans livre"); em.persist(a); return a.getId(); });
        performance.viderCaches();
        Mesure join = performance.testPerformanceAvecJoinFetch();
        Mesure graph = performance.testPerformanceAvecEntityGraph();
        verifier(join, 1, 11, 50); verifier(graph, 1, 11, 50);
        assertTrue(join.ids().contains(id));
        assertEquals(join.ids(), graph.ids());
    }

    @Test void cacheEntiteEtCollectionEntreEntityManagers() {
        Mesure froide = performance.lectureParId(1L, "froide");
        Mesure chaude = performance.lectureParId(1L, "chaude");
        verifier(froide, 2, 1, 5); verifier(chaude, 0, 1, 5);
        assertEquals(7, chaude.hitsL2()); // Auteur + collection + 5 livres.
        assertEquals(0, chaude.hitsQuery());
        assertTrue(froide.putsL2() > 0);
    }

    @Test void queryCacheFroidPuisChaudSansSql() {
        List<Mesure> mesures = performance.testPerformanceAvecCache();
        verifier(mesures.get(0), 11, 10, 50); verifier(mesures.get(1), 0, 10, 50);
        assertEquals(1, mesures.get(0).missesQuery());
        assertEquals(1, mesures.get(1).hitsQuery());
        assertEquals(70, mesures.get(1).hitsL2()); // 10 auteurs + 10 collections + 50 livres.
    }

    @Test void jpqlNonCacheableContinueAExecuterDuSql() {
        verifier(performance.lectureLazy(false, "froid"), 11, 10, 50);
        Mesure chaud = performance.lectureLazy(false, "chaud");
        verifier(chaud, 1, 10, 50);
        assertEquals(0, chaud.hitsQuery());
        assertTrue(chaud.hitsL2() > 0);
        verifier(performance.testPerformanceAvecJoinFetch(), 1, 10, 50);
        verifier(performance.testPerformanceAvecJoinFetch(), 1, 10, 50);
    }

    @Test void premierNiveauEtSecondNiveauSontDistincts() {
        stats().clear();
        Auteur premier = transaction(em -> {
            Auteur a = em.find(Auteur.class, 1L);
            Auteur b = em.find(Auteur.class, 1L);
            assertSame(a, b);
            assertEquals(1, stats().getPrepareStatementCount());
            assertEquals(0, stats().getSecondLevelCacheHitCount());
            return a;
        });
        stats().clear();
        Auteur suivant = transaction(em -> em.find(Auteur.class, 1L));
        assertNotSame(premier, suivant);
        assertEquals(premier.getId(), suivant.getId());
        assertEquals(0, stats().getPrepareStatementCount());
        assertEquals(1, stats().getSecondLevelCacheHitCount());
    }

    @Test void evictionRetourAuxRequetesSql() {
        performance.lectureParId(1L, "chauffage");
        verifier(performance.lectureParId(1L, "chaud"), 0, 1, 5);
        performance.viderCaches();
        verifier(performance.lectureParId(1L, "apres eviction"), 2, 1, 5);
    }

    @Test void miseAJourEntiteVisibleDansNouveauContexte() {
        performance.lectureParId(1L, "chauffage");
        transaction(em -> { em.find(Auteur.class, 1L).setNom("Nom modifie"); return null; });
        String nom = transaction(em -> em.find(Auteur.class, 1L).getNom());
        assertEquals("Nom modifie", nom);
    }

    @Test void ajoutCascadeInvalideCollectionMaisPasListeAuteurs() {
        performance.testPerformanceAvecCache();
        transaction(em -> {
            em.find(Auteur.class, 1L).ajouterLivre(new Livre("Nouveau livre"));
            return null;
        });
        Mesure actualisee = performance.lectureLazy(true, "apres ajout");
        assertEquals(10, actualisee.auteurs());
        assertEquals(51, actualisee.livres());
        assertEquals(0, actualisee.missesQuery());
        assertEquals(1, actualisee.hitsQuery()); // La requete porte uniquement sur la table auteurs.
        assertTrue(actualisee.sql() > 0);
        verifier(performance.lectureLazy(true, "rechauffe"), 0, 10, 51);
    }

    @Test void orphanRemovalSupprimeEtInvalideLesCaches() {
        performance.testPerformanceAvecCache();
        Long retire = transaction(em -> {
            Auteur a = em.find(Auteur.class, 1L);
            Livre l = a.getLivres().get(0);
            Long id = l.getId();
            a.retirerLivre(l);
            assertNull(l.getAuteur());
            return id;
        });
        Mesure m = performance.lectureLazy(true, "apres suppression");
        assertEquals(49, m.livres());
        assertEquals(0, m.missesQuery());
        assertEquals(1, m.hitsQuery());
        assertTrue(m.sql() > 0); // La collection invalidee est relue.
        assertNull(transaction(em -> em.find(Livre.class, retire)));
        verifier(performance.lectureLazy(true, "rechauffe"), 0, 10, 49);
    }

    @Test void modificationAuteurInvalideLaRequeteCachee() {
        performance.testPerformanceAvecCache();
        transaction(em -> { em.find(Auteur.class, 1L).setNom("Nom actualise"); return null; });
        Mesure m = performance.lectureLazy(true, "apres modification auteur");
        assertEquals(1, m.missesQuery());
        assertEquals(0, m.hitsQuery());
        assertEquals(10, m.auteurs());
        assertEquals(50, m.livres());
        assertTrue(m.sql() > 0);
        assertEquals("Nom actualise", transaction(em -> em.find(Auteur.class, 1L).getNom()));
        verifier(performance.lectureLazy(true, "rechauffe"), 0, 10, 50);
    }
    @Test void rollbackNePubliePasUneModificationDeCache() {
        performance.lectureParId(1L, "chauffage");
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.find(Auteur.class, 1L).setNom("A annuler");
            em.flush();
            em.getTransaction().rollback();
        } finally {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
        assertEquals("Auteur 1", transaction(e -> e.find(Auteur.class, 1L).getNom()));
    }
}
