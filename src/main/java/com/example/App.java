package com.example;

import com.example.service.*;
import javax.persistence.EntityManagerFactory;
import java.util.List;

public class App {
    public static void main(String[] args) {
        System.out.println("=== Hibernate cache lab : 10 auteurs, 50 livres, Java " + Runtime.version().feature() + " ===");
        EntityManagerFactory sansCache = JpaFactory.creer(false, "cache_lab_sans");
        try {
            new DataInitService(sansCache).initData();
            PerformanceTestService p = new PerformanceTestService(sansCache);
            Mesure n1 = p.testPerformanceSansCache();
            Mesure join = p.testPerformanceAvecJoinFetch();
            Mesure graph = p.testPerformanceAvecEntityGraph();
            System.out.println("\n=== Cache L2 et cache de requete DESACTIVES ===");
            n1.afficher(); join.afficher(); graph.afficher();
            verifier(n1, 11, 10, 50); verifier(join, 1, 10, 50); verifier(graph, 1, 10, 50);
            if (!n1.ids().equals(join.ids()) || !join.ids().equals(graph.ids()))
                throw new IllegalStateException("Les optimisations ont change les resultats.");
        } finally { sansCache.close(); }

        EntityManagerFactory avecCache = JpaFactory.creer(true, "cache_lab_avec");
        try {
            new DataInitService(avecCache).initData();
            PerformanceTestService p = new PerformanceTestService(avecCache);
            System.out.println("\n=== L2 entite + collection, SANS cache de requete, EntityManager distincts ===");
            p.viderCaches();
            Mesure idFroid = p.lectureParId(1L, "find auteur 1 : froid");
            Mesure idChaud = p.lectureParId(1L, "find auteur 1 : chaud");
            idFroid.afficher(); idChaud.afficher();
            verifier(idFroid, 2, 1, 5); verifier(idChaud, 0, 1, 5);
            if (idChaud.hitsL2() == 0) throw new IllegalStateException("Aucun hit L2 reel.");

            System.out.println("\n=== Un JOIN FETCH non cacheable execute toujours sa requete SQL ===");
            Mesure nonCacheable = p.testPerformanceAvecJoinFetch();
            nonCacheable.afficher(); verifier(nonCacheable, 1, 10, 50);

            System.out.println("\n=== L2 + collection + requete explicitement cacheable ===");
            List<Mesure> cache = p.testPerformanceAvecCache();
            cache.forEach(Mesure::afficher);
            verifier(cache.get(0), 11, 10, 50); verifier(cache.get(1), 0, 10, 50);
            if (cache.get(1).hitsL2() == 0 || cache.get(1).hitsQuery() != 1)
                throw new IllegalStateException("Les statistiques ne confirment pas les caches.");
            System.out.println("\nVALIDATION OK : N+1=11 SQL ; JOIN FETCH=1 ; Entity Graph=1 ; lecture chaude=0.");
            System.out.println("Les durees sont indicatives (JIT, XML, console SQL) ; les assertions portent sur les requetes et les donnees.");
        } finally { avecCache.close(); }
    }
    private static void verifier(Mesure m, long sql, int auteurs, int livres) {
        if (m.sql() != sql || m.auteurs() != auteurs || m.livres() != livres)
            throw new IllegalStateException("Resultat inattendu : " + m);
    }
}
