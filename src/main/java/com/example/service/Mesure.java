package com.example.service;

import java.util.List;
import java.util.Locale;

public record Mesure(String scenario, long nanosecondes, long sql, long hitsL2,
                     long missesL2, long putsL2, long hitsQuery, long missesQuery,
                     int auteurs, int livres, List<Long> ids) {
    public void afficher() {
        System.out.printf(Locale.FRANCE,
                "%s | SQL=%d | L2 hits/misses/puts=%d/%d/%d | Query hits/misses=%d/%d | %d auteurs, %d livres | %.3f ms%n",
                scenario, sql, hitsL2, missesL2, putsL2, hitsQuery, missesQuery, auteurs, livres, nanosecondes / 1_000_000.0);
    }
}
