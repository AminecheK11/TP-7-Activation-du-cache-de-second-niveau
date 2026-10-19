# TP — Optimisation des performances JPA/Hibernate et Cache de Second Niveau

## Réalisé par : Amine Chekkouri

---

## 1. Objectif du TP

L’objectif de ce TP est d’étudier et d’optimiser les performances d’une application utilisant **JPA et Hibernate**.

Le travail porte principalement sur le problème **N+1**, les différentes techniques permettant d’optimiser le chargement des relations et l’utilisation du **cache de second niveau Hibernate avec EhCache**.

Le TP permet notamment d’aborder :

- la configuration d’un projet Maven avec JPA et Hibernate ;
- la création d’entités JPA avec des relations ;
- le chargement LAZY des associations ;
- l’identification du problème N+1 ;
- l’optimisation avec `JOIN FETCH` ;
- l’utilisation des Entity Graphs ;
- l’activation du cache de second niveau ;
- l’utilisation du cache de requête ;
- la comparaison des différentes stratégies d’accès aux données ;
- la validation du fonctionnement avec des tests.

---

## 2. Technologies utilisées

Les principales technologies utilisées sont :

- Java 17
- Maven
- JPA 2.2
- Hibernate ORM 5.4.32.Final
- Hibernate JCache
- EhCache 3.8.1
- H2 Database 1.4.200
- JUnit
- IntelliJ IDEA

---

## 3. Structure du projet

Le projet Maven est organisé en plusieurs packages permettant de séparer les différentes responsabilités de l’application.

Le package `model` contient les entités JPA `Auteur` et `Livre`.

Le package `service` contient les services nécessaires à l’initialisation des données et aux différents tests de performance.

La classe principale `App` permet d’exécuter les différents scénarios du TP.

Les tests sont regroupés dans la classe `CacheLabTest`.

Les fichiers de configuration nécessaires à la persistance et au cache sont placés dans `src/main/resources`.

### Capture — Structure du projet

<img width="547" height="601" alt="cap1_lab9" src="https://github.com/user-attachments/assets/971f4a6a-e33a-405f-b38a-9115851d649b" />


---

## 4. Modèle de données

Le projet utilise deux entités principales :

- `Auteur`
- `Livre`

Un auteur peut posséder plusieurs livres, tandis qu’un livre appartient à un auteur.

Cette relation est représentée par une association bidirectionnelle **OneToMany / ManyToOne**.

Le chargement des relations permet ensuite d’étudier le comportement de Hibernate et notamment le problème N+1.

---

## 5. Initialisation des données

Un service d’initialisation est utilisé pour préparer les données nécessaires aux différents tests.

La base H2 est alimentée avec plusieurs auteurs possédant chacun plusieurs livres.

Ces données permettent d’effectuer les mêmes opérations avec différentes stratégies de chargement afin de comparer leur comportement.

---

## 6. Problème N+1

Le premier scénario consiste à récupérer les auteurs puis à accéder à leurs livres.

Lorsque les associations sont chargées de manière différée, Hibernate peut exécuter une première requête pour récupérer les auteurs puis des requêtes supplémentaires pour récupérer leurs collections.

Ce comportement correspond au **problème N+1**.

Il peut entraîner un nombre important d’accès à la base et réduire les performances d’une application lorsque le volume de données augmente.

---

## 7. Optimisation avec JOIN FETCH

Une première solution au problème N+1 consiste à utiliser `JOIN FETCH`.

Cette technique permet de demander à Hibernate de charger les entités principales et leurs associations dans la même requête.

Elle permet donc de réduire fortement le nombre de requêtes nécessaires.

L’utilisation de `DISTINCT` permet également de gérer les éventuels doublons pouvant apparaître lors du chargement d’une collection avec une jointure.

---

## 8. Optimisation avec Entity Graph

Une deuxième stratégie d’optimisation est basée sur les **Entity Graphs** de JPA.

Un Entity Graph permet de préciser les associations qui doivent être chargées avec une entité sans intégrer directement le chargement dans la requête JPQL.

Cette approche offre davantage de flexibilité et permet de réutiliser une même requête avec différentes stratégies de chargement.

Les performances du chargement avec Entity Graph peuvent ensuite être comparées au scénario N+1 et à `JOIN FETCH`.

### Capture — Comparaison N+1, JOIN FETCH et Entity Graph

<img width="927" height="93" alt="cap2_lab9" src="https://github.com/user-attachments/assets/f2029dc5-fedb-4157-9c3c-12f719b10afc" />


---

## 9. Cache de second niveau Hibernate

Hibernate possède un cache de premier niveau associé à chaque contexte de persistance.

Le **cache de second niveau**, quant à lui, est partagé entre plusieurs `EntityManager` appartenant à la même `EntityManagerFactory`.

Il permet de conserver certaines données déjà chargées afin d’éviter de consulter systématiquement la base de données lors de nouvelles lectures.

Dans ce TP, le cache de second niveau est mis en place avec **EhCache via JCache**.

Les entités et collections nécessaires sont configurées afin de pouvoir être utilisées par ce mécanisme de cache.

---

## 10. Lecture froide et lecture chaude

Le fonctionnement du cache de second niveau est étudié en comparant deux types de lectures.

Une **lecture froide** correspond à une situation dans laquelle les données nécessaires ne sont pas encore disponibles dans le cache.

Hibernate doit alors accéder à la base de données avant d’alimenter le cache.

Une **lecture chaude** correspond à une nouvelle consultation de données déjà présentes dans le cache.

L’utilisation d’`EntityManager` distincts permet de vérifier qu’il s’agit bien du cache de second niveau et non uniquement du cache de premier niveau associé à un seul contexte de persistance.

### Capture — Impact du cache de second niveau

<img width="1123" height="917" alt="cap_2 1_lab9" src="https://github.com/user-attachments/assets/b74eddec-826a-4089-8475-d8123d602c0f" />


---

## 11. Cache de requête

En complément du cache de second niveau, Hibernate permet d’utiliser un **cache de requête**.

Le cache de second niveau conserve principalement les données des entités, tandis que le cache de requête permet de conserver les résultats nécessaires à l’identification des données retournées par une requête.

Une requête doit être explicitement rendue cacheable pour bénéficier de ce mécanisme.

L’association du cache de requête et du cache L2 permet ainsi de réduire davantage les accès à la base lors de lectures répétées.

---

## 12. Validation du cache

Plusieurs lectures successives sont effectuées afin d’observer le comportement du cache.

Les statistiques Hibernate permettent notamment d’étudier :

- les accès SQL ;
- les cache hits ;
- les cache misses ;
- les mises en cache ;
- les accès au cache de requête.

La comparaison entre une première lecture et une lecture suivante permet de vérifier que les données déjà disponibles peuvent être récupérées depuis le cache.

### Capture — Cache L2, cache de requête et validation finale

<img width="1403" height="449" alt="cap2 2_lab9" src="https://github.com/user-attachments/assets/4f8fa1fe-0a5d-4825-85a3-ff46a389aa16" />


---

## 13. Comparaison des stratégies

Les différents scénarios du TP permettent de comparer plusieurs stratégies d’accès aux données.

Le chargement classique avec des associations LAZY permet de mettre en évidence le problème N+1.

`JOIN FETCH` permet de récupérer directement les associations nécessaires dans la requête.

Les Entity Graphs fournissent une autre solution permettant de contrôler dynamiquement les associations à charger.

Enfin, le cache de second niveau et le cache de requête permettent de limiter les nouveaux accès à la base lorsque les mêmes données sont consultées plusieurs fois.

Le choix de la stratégie dépend donc du type de requête, du volume de données et de la fréquence des accès.

---

## 14. Tests et validation du projet

Des tests automatisés ont été réalisés avec la classe :

```text
CacheLabTest
```

Ils permettent de vérifier le fonctionnement des principales fonctionnalités étudiées dans le TP :

- la persistance des auteurs et des livres ;
- les relations entre les entités ;
- le comportement du chargement LAZY ;
- le problème N+1 ;
- l’optimisation avec `JOIN FETCH` ;
- l’utilisation des Entity Graphs ;
- le fonctionnement du cache L2 ;
- le comportement du cache de requête ;
- la cohérence des données après les différentes opérations.

L’exécution Maven permet finalement de vérifier le bon fonctionnement global du projet.

### Capture — Tests Maven

<img width="913" height="429" alt="cap3_lab9" src="https://github.com/user-attachments/assets/39280ff6-6da6-4409-8d40-3ed84f851c09" />


---

## 15. Bonnes pratiques

L’optimisation des performances avec Hibernate nécessite de choisir les stratégies de chargement en fonction des besoins de l’application.

Il est notamment recommandé de :

- éviter les chargements inutiles ;
- surveiller le problème N+1 ;
- utiliser `JOIN FETCH` lorsque les associations doivent être récupérées immédiatement ;
- utiliser les Entity Graphs lorsqu’une stratégie de chargement plus flexible est nécessaire ;
- utiliser le cache L2 pour les données fréquemment consultées ;
- rendre les requêtes cacheables uniquement lorsque cela est pertinent ;
- analyser les statistiques Hibernate pour vérifier l’efficacité des optimisations ;
- tester les performances avec plusieurs contextes de persistance.

---

## Conclusion

Ce TP présente différentes techniques d’optimisation des performances avec **JPA et Hibernate**.

L’étude du problème N+1, de `JOIN FETCH` et des Entity Graphs montre l’importance de la stratégie de chargement des relations. L’utilisation du cache de second niveau avec EhCache et du cache de requête permet également de limiter les accès répétés à la base de données.

Les différents scénarios et tests réalisés permettent de valider le fonctionnement de ces mécanismes d’optimisation.

---

**Réalisé par : Amine Chekkouri**
