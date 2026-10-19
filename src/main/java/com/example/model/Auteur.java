package com.example.model;

import javax.persistence.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "auteurs")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@NamedEntityGraph(name = "Auteur.livres", attributeNodes = @NamedAttributeNode("livres"))
public class Auteur {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nom;
    @OneToMany(mappedBy = "auteur", cascade = CascadeType.ALL, orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Livre> livres = new ArrayList<>();

    public Auteur() { }
    public Auteur(String nom) { this.nom = nom; }
    public Long getId() { return id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public List<Livre> getLivres() { return livres; }
    public void ajouterLivre(Livre livre) {
        Objects.requireNonNull(livre);
        if (livre.getAuteur() != null && livre.getAuteur() != this)
            throw new IllegalArgumentException("Le livre appartient deja a un autre auteur.");
        if (!livres.contains(livre)) livres.add(livre);
        livre.setAuteur(this);
    }
    public void retirerLivre(Livre livre) {
        if (livres.remove(livre)) livre.setAuteur(null);
    }
}
