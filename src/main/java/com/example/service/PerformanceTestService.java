package com.example.service;

import com.example.model.Auteur;
import javax.persistence.*;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import java.util.*;
import java.util.function.Function;

public class PerformanceTestService {
    private final EntityManagerFactory emf;
    private final SessionFactory factory;
    public PerformanceTestService(EntityManagerFactory emf) {
        this.emf = emf; this.factory = emf.unwrap(SessionFactory.class);
    }
    public void viderCaches() { factory.getCache().evictAllRegions(); }

    /** Statistiques globales de la factory : mesures a executer sequentiellement. */
    private Mesure mesurer(String nom, Function<EntityManager, List<Auteur>> lecture) {
        Statistics stats = factory.getStatistics();
        stats.clear(); // Ne vide pas le cache : separe compteurs et donnees.
        long start = System.nanoTime();
        EntityManager em = emf.createEntityManager(); // Nouveau L1 pour CHAQUE mesure.
        EntityTransaction tx = em.getTransaction();
        int livres;
        List<Long> ids;
        try {
            tx.begin();
            List<Auteur> auteurs = lecture.apply(em);
            livres = auteurs.stream().mapToInt(a -> a.getLivres().size()).sum();
            ids = auteurs.stream().map(Auteur::getId).toList();
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally { em.close(); }
        return new Mesure(nom, System.nanoTime() - start, stats.getPrepareStatementCount(),
                stats.getSecondLevelCacheHitCount(), stats.getSecondLevelCacheMissCount(),
                stats.getSecondLevelCachePutCount(), stats.getQueryCacheHitCount(), stats.getQueryCacheMissCount(),
                ids.size(), livres, List.copyOf(ids));
    }

    public Mesure testPerformanceSansCache() {
        if (factory.getSessionFactoryOptions().isSecondLevelCacheEnabled())
            throw new IllegalStateException("La mesure sans cache exige une factory sans L2.");
        return lectureLazy(false, "Sans cache : N+1");
    }
    public Mesure testPerformanceAvecJoinFetch() {
        return mesurer("JOIN FETCH", em -> em.createQuery(
                "select distinct a from Auteur a left join fetch a.livres order by a.id", Auteur.class).getResultList());
    }
    public Mesure testPerformanceAvecEntityGraph() {
        return mesurer("Entity Graph", em -> {
            EntityGraph<?> graph = em.getEntityGraph("Auteur.livres");
            return em.createQuery("select distinct a from Auteur a order by a.id", Auteur.class)
                    .setHint("javax.persistence.fetchgraph", graph).getResultList();
        });
    }
    public Mesure lectureLazy(boolean cacheRequete, String nom) {
        return mesurer(nom, em -> em.createQuery("select a from Auteur a order by a.id", Auteur.class)
                .setHint("org.hibernate.cacheable", cacheRequete).getResultList());
    }
    public Mesure lectureParId(Long id, String nom) {
        return mesurer(nom, em -> {
            Auteur auteur = em.find(Auteur.class, id);
            return auteur == null ? List.of() : List.of(auteur);
        });
    }
    public List<Mesure> testPerformanceAvecCache() {
        if (!factory.getSessionFactoryOptions().isSecondLevelCacheEnabled())
            throw new IllegalStateException("Cache de second niveau desactive.");
        viderCaches();
        Mesure froide = lectureLazy(true, "L2 + cache de requete : froid");
        Mesure chaude = lectureLazy(true, "L2 + cache de requete : chaud (nouvel EntityManager)");
        return List.of(froide, chaude);
    }
}
