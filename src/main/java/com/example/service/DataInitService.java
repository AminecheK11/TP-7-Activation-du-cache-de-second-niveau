package com.example.service;

import com.example.model.*;
import javax.persistence.*;

public class DataInitService {
    private final EntityManagerFactory emf;
    public DataInitService(EntityManagerFactory emf) { this.emf = emf; }
    public void initData() {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            long auteurs = em.createQuery("select count(a) from Auteur a", Long.class).getSingleResult();
            long livres = em.createQuery("select count(l) from Livre l", Long.class).getSingleResult();
            if (auteurs != 0 || livres != 0) throw new IllegalStateException("Initialiser uniquement une base vide.");
            for (int i = 1; i <= 10; i++) {
                Auteur auteur = new Auteur("Auteur " + i);
                for (int j = 1; j <= 5; j++) auteur.ajouterLivre(new Livre("Livre " + j + " de l'auteur " + i));
                em.persist(auteur);
            }
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally { em.close(); }
    }
}
