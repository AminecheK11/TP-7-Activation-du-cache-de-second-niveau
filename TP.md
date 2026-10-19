# Hibernate Cache Lab — réalisation des étapes 1 à 13

Projet indépendant : com.example:hibernate-cache-lab:1.0-SNAPSHOT.
Java 17, JPA javax.persistence 2.2, Hibernate 5.4.32.Final, Ehcache 3.8.1, H2 1.4.200.
Aucun Spring, serveur MySQL ou autre projet du LAB n'est utilisé.

## Correspondance avec l'énoncé

| Étape | Fichiers et réalisation |
|---|---|
| 1 | Projet Maven hibernate-cache-lab ; sources et ressources standard. |
| 2 | pom.xml : dépendances imposées ; intégration hibernate-jcache corrigée pour Ehcache 3. |
| 3 | com.example.model.Auteur et Livre ; relation bidirectionnelle, cascade et orphanRemoval. |
| 4 | META-INF/persistence.xml : hibernate-cache-pu, RESOURCE_LOCAL, H2, JCache, statistiques. |
| 5 | ehcache.xml : régions Auteur, Livre, Auteur.livres, résultats et timestamps. |
| 6 | DataInitService : exactement 10 auteurs, chacun avec 5 livres, dans une transaction. |
| 7 | PerformanceTestService.testPerformanceSansCache : factory sans L2 ni query cache, 11 SQL. |
| 8 | testPerformanceAvecJoinFetch : DISTINCT LEFT JOIN FETCH, 1 SQL. |
| 9 | Auteur.livres et javax.persistence.fetchgraph : 1 SQL. |
| 10 | Cache READ_WRITE des entités ET de la collection ; lectures froides/chaudes séparées. |
| 11 | com.example.App orchestre les scénarios et vérifie leurs résultats. |
| 12 | Console : durées, requêtes préparées, hits/misses/puts L2, hits/misses query cache. |
| 13 | Explications ci-dessous, distinction L1/L2/query cache et tests d'invalidation. |

## Exécution

Dans IntelliJ : ouvrir le dossier du projet, recharger Maven et sélectionner le JDK **ms-17**
pour le projet et pour Maven Runner. La configuration Run fournie lance **com.example.App**.

Commandes standard :

```powershell
mvn clean install
mvn exec:java
mvn clean test
```

Sur cette machine, Maven est intégré à IntelliJ et n'est pas dans PATH.
Le script sélectionne temporairement Java 17, puis restaure JAVA_HOME :

```powershell
.\mvn17.ps1 clean install
.\mvn17.ps1 exec:java
.\mvn17.ps1 clean test
```

Les bases H2 en mémoire sont propres à cette JVM. App utilise une base pour la configuration
sans cache et une autre pour celle avec cache, chacune contenant exactement les mêmes données.
Les tables auteurs et livres sont créées puis supprimées à la fermeture (create-drop).

## Corrections nécessaires par rapport à l'énoncé

### Intégration du cache

hibernate-ehcache est destiné à Ehcache 2, alors que le fournisseur et le XML fournis sont ceux
d'Ehcache 3. Il est remplacé par **hibernate-jcache 5.4.32.Final**, avec javax.cache-api 1.1.1.
Les versions de hibernate-core, Ehcache et H2 sont conservées.
Voir la [documentation Hibernate 5.4 sur JCache](https://docs.hibernate.org/orm/5.4/userguide/html_single/#caching-provider-jcache).

La plage de versions de jaxb-runtime transitive d'Ehcache est fixée à **2.3.1**, déjà utilisée
par cette génération d'Hibernate : résolution Maven reproductible, sans les anciennes
dépendances JAXB provenant de dépôts HTTP. JAXB utilise ici les API javax, compatibles Java 17.

Le XML place expiry avant heap, conformément au schéma Ehcache 3.
La propriété hibernate.javax.cache.uri charge explicitement ehcache.xml.
La stratégie missing_cache_strategy=fail empêche la création silencieuse de régions mal nommées.
Voir la [configuration XML Ehcache](https://www.ehcache.org/documentation/3.4/xml.html).

Les régions utilisées par Hibernate 5.4 sont :
- com.example.model.Auteur ;
- com.example.model.Livre ;
- com.example.model.Auteur.livres ;
- default-update-timestamps-region ;
- default-query-results-region.

Les entités et collections ont un TTL de 60 minutes ; les timestamps n'expirent pas.
La capacité des timestamps est largement suffisante pour les deux tables du TP :
leur éviction avant les résultats compromettrait la validation de fraîcheur.

### Services exécutables hors conteneur JEE

Dans un main Java, @PersistenceContext ne réalise pas automatiquement l'injection.
L'accès dataInitService.em de l'énoncé est également impossible car em est privé.
Les services reçoivent donc une EntityManagerFactory par constructeur.

DataInitService gère une seule transaction locale et App n'en ouvre pas une seconde autour.
Les EntityManager sont fermés systématiquement ; rollback en cas d'erreur.
Les deux factories sont fermées dans des blocs finally.

### Mesures fiables et distinction des caches

La méthode sans cache utilise réellement une factory dont L2 ET query cache sont désactivés.
Chaque mesure crée un nouvel EntityManager : une seconde lecture ne profite pas du premier niveau.
Les caches sont vidés avant les lectures froides, après l'initialisation.
Statistics.clear remet seulement les compteurs à zéro, sans effacer les données du cache.

Les annotations READ_WRITE sont ajoutées sur Auteur, Livre et la collection Auteur.livres.
Un JOIN FETCH n'est pas automatiquement mis en cache : sans org.hibernate.cacheable=true,
il continue à exécuter son SELECT, même si les entités sont dans le L2.
La démonstration du query cache utilise une requête explicitement cacheable avec chargement
lazy des collections, afin de distinguer les régions et leurs statistiques.

DISTINCT supprime les doublons d'auteurs issus de la jointure.
LEFT JOIN FETCH conserve aussi les auteurs sans livre, ce qu'un JOIN FETCH interne exclurait.
Le graphe est appliqué à la même requête d'auteurs, sans clause FETCH JPQL.

## Mesures observées et vérifiées

| Scénario | SQL | Auteurs / livres |
|---|---:|---:|
| Sans cache, accès lazy à chaque collection | 11 | 10 / 50 |
| JOIN FETCH | 1 | 10 / 50 |
| Entity Graph | 1 | 10 / 50 |
| find d'un auteur + livres, cache froid | 2 | 1 / 5 |
| Même find, nouvel EntityManager, cache chaud | 0 | 1 / 5 |
| JPQL non cacheable avec L2 déjà chaud | 1 | 10 / 50 |
| Requête cacheable + L2 froid | 11 | 10 / 50 |
| Même requête cacheable, nouvel EntityManager, cache chaud | 0 | 10 / 50 |

La lecture chaude par identifiant produit 7 hits L2 : 1 auteur, 1 collection, 5 livres.
La lecture chaude globale produit 70 hits L2 et 1 hit du cache de requête :
10 auteurs, 10 collections et 50 livres.

Les durées sont affichées en millisecondes via System.nanoTime.
Elles incluent l'ouverture du contexte, la transaction et les lectures.
Avec 10 auteurs, JIT, échauffement, logs SQL et charge système influencent beaucoup les temps.
Aucun test ne prétend garantir un facteur d'accélération : les assertions portent sur
les requêtes SQL et sur l'exactitude des données.

## Étape 13 — Comprendre les résultats

Le premier niveau appartient à l'EntityManager : deux find dans le même contexte retournent
la même instance. Il ne démontre pas un cache partagé.

Le second niveau appartient à la SessionFactory et stocke les données des entités par clé.
Le cache d'une collection conserve les identifiants de ses éléments. Les données de Livre
doivent elles aussi être cacheables pour éviter leur relecture individuelle.

Le cache de requête conserve notamment les identifiants correspondant au résultat JPQL.
Il complète le L2, mais ne le remplace pas. Sa validité dépend des tables interrogées,
via les timestamps de modification.

Le N+1 est visible ici dans le sens Auteur -> livres : une requête pour les 10 auteurs,
puis une requête pour chacune des 10 collections. Le ManyToOne LAZY de Livre reste conforme
au modèle, mais n'est pas la cause de cette boucle particulière.

JOIN FETCH et Entity Graph évitent le N+1 dès la première lecture, sans avoir besoin du cache.
Le cache aide les lectures répétées, mais ne dispense pas d'une bonne stratégie de chargement.

### Invalidation vérifiée

- Modifier un nom d'auteur rend la nouvelle valeur visible et invalide le résultat JPQL d'auteurs.
- Ajouter ou retirer un livre invalide la collection de son auteur.
- La liste cacheable des auteurs peut rester valide lors d'une modification limitée aux livres :
  elle dépend de la table auteurs. La collection invalidée est malgré tout relue correctement.
- orphanRemoval supprime réellement la ligne Livre.
- Un rollback ne publie pas une valeur annulée dans le cache.
- Une éviction du cache rétablit les requêtes SQL attendues.

Les mesures utilisent les statistiques globales de leur factory et s'exécutent séquentiellement.
Elles ne constituent pas un benchmark concurrent ni un test de charge.

## Validation

17 tests JUnit vérifient :
versions, H2 en mémoire, initialisation, absence de doublons, N+1, JOIN FETCH, Entity Graph,
auteurs sans livres, L1 distinct du L2, lecture chaude sans SQL, requêtes non cacheables,
éviction, mises à jour, invalidation, cascade, orphanRemoval et rollback.

Preuves :
- work/validation-finale.log : clean install, tests, exécution d'App ;
- target/surefire-reports/com.example.CacheLabTest.txt : bilan JUnit ;
- target/hibernate-cache-lab-1.0-SNAPSHOT.jar : archive Maven.

Captures utiles : tableau des mesures sans cache, lecture par identifiant froide/chaude,
cache de requête froid/chaud, configuration des régions, annotations Cache/EntityGraph
et résultat Maven BUILD SUCCESS avec 17 tests.
