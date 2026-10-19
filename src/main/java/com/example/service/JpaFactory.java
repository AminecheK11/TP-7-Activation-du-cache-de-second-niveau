package com.example.service;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;

public final class JpaFactory {
    private JpaFactory() { }
    public static EntityManagerFactory creer(boolean cache, String nomBase) {
        return creer(cache, nomBase, true);
    }
    public static EntityManagerFactory creer(boolean cache, String nomBase, boolean afficherSql) {
        if (!nomBase.matches("[a-zA-Z0-9_]+")) throw new IllegalArgumentException("Nom H2 invalide");
        Map<String, Object> p = new HashMap<>();
        p.put("javax.persistence.jdbc.url", "jdbc:h2:mem:" + nomBase + ";DB_CLOSE_DELAY=-1");
        p.put("hibernate.cache.use_second_level_cache", cache);
        p.put("hibernate.cache.use_query_cache", cache);
        p.put("hibernate.show_sql", afficherSql);
        if (!cache) p.put("hibernate.cache.region.factory_class",
                "org.hibernate.cache.internal.NoCachingRegionFactory");
        return Persistence.createEntityManagerFactory("hibernate-cache-pu", p);
    }
}
