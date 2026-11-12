# Cadastre web et exploration personnelle

## Utilisation

- Site : **`/cadastre`**, également accessible depuis **Carte → Cadastre & propriétés**.
- Administration : **`/admin/cadastre`**, accessible depuis les raccourcis du tableau d’administration ; le bouton **Mode gestion / Vue joueur** permet de changer de lecture.
- Minecraft : **`/mc land`** ; **`/mc land sync`** demande une synchronisation anticipée.
- Centre : **Shaleton**, blocs **X −716 / Z −1300**, chunk **−45 / −82**. La hauteur Y 79 ne change pas le cadastre, qui porte sur les colonnes de chunks.

Une visite révèle un carré de **17 × 17 chunks**, soit un rayon de huit chunks sur les axes X et Z. Les limites sont inclusives. La progression est personnelle, séparée par dimension et par monde. Elle est conservée après déconnexion et redémarrage. Les explorations antérieures à l’installation ne peuvent pas être reconstituées : il faut revisiter les lieux.

La découverte révèle aussi la propriété des chunks concernés. Lors d’une actualisation ultérieure, le propriétaire affiché est celui de la dernière copie reçue du serveur ; il ne s’agit pas d’un historique figé au moment du premier voyage. Seules les portions découvertes d’une parcelle sont renvoyées aux joueurs, même lorsqu’une parcelle s’étend beaucoup plus loin. Les administrateurs peuvent consulter toutes les parcelles en mode gestion, sans découvrir ces terres pour leur compte joueur.

Le fond est un **plan cadastral**, sans relief ni constructions. La carte illustrée existante n’est pas géoréférencée. Un futur fond terrain devra être précisément calé sur les coordonnées Minecraft avant de pouvoir y superposer les claims.

## Actualisation et charge

La découverte est calculée localement lorsqu’un joueur change de chunk, après un contrôle de position toutes les dix ticks. Aucun chargement/génération de chunk ni appel HTTP ne sert à découvrir une zone. Les surfaces sont stockées sous forme d’intervalles fusionnés, et non d’un objet par bloc ou par chunk.

Par défaut, le serveur effectue **un échange groupé toutes les 120 secondes** avec le backend. Un seul échange est en cours à la fois. Il envoie au maximum 128 lignes de découvertes par lot ; les très grandes lignes sont paginées. Les lignes déjà confirmées ne sont pas renvoyées, sauf si elles s’agrandissent. Le catalogue complet des parcelles n’est renvoyé que lorsque son empreinte change ou après une erreur nécessitant une reprise.

Le backend fusionne les découvertes par écritures Mongo groupées et révisions, afin de conserver aussi les extensions concurrentes. Un lot ordinaire nécessite une lecture groupée, une écriture groupée et une vérification groupée, plutôt qu’une succession d’allers-retours par chunk.

Sur le site, une lecture intervient à l’ouverture et au changement de mode ; ensuite seul **Actualiser** charge les données. Déplacement, zoom et recherche restent locaux. Une zone déplacée est signalée comme nécessitant une actualisation. Une nouvelle taille de fenêtre peut également demander cette actualisation. Les requêtes sont limitées à une fenêtre de 96 × 96 chunks et ne sont pas stockées dans un cache public ou dans le service worker.

`/mc land sync` permet de demander un échange plus tôt, avec un délai partagé de 30 secondes entre demandes manuelles. Les confirmations de modifications et les lots restant à envoyer peuvent déclencher une reprise après cinq secondes. Si le backend est indisponible, le délai augmente progressivement jusqu’à dix minutes et les découvertes restent sur disque. Aucune requête réseau ne bloque le thread du serveur Minecraft.

## Gestion depuis le site

Le mode gestion permet de sélectionner un chunk ou un rectangle de **256 chunks maximum**, puis :

1. Créer une parcelle, éventuellement avec les chunks sélectionnés.
2. Modifier son nom, propriétaire, nature, ville, couleur et description.
3. Attribuer une sélection à une parcelle existante.
4. Retirer une attribution en conservant la parcelle et ses autres chunks.

Chaque demande exige un motif et une confirmation. Elle apparaît **En attente du serveur**, puis **Appliqué**, **Conflit** ou **Refusé** après actualisation. Si le serveur est arrêté, la demande reste en attente. Le navigateur ne décide jamais qu’une écriture a réussi avant confirmation par Minecraft.

Minecraft compare l’empreinte du cadastre à celle qui était affichée lors de la confirmation. Un changement intervenu entre-temps bloque la demande : actualiser puis la préparer de nouveau. Une parcelle ne peut pas écraser l’attribution d’une autre. L’écriture locale et l’identifiant de la dernière demande web sont enregistrés ensemble via remplacement atomique, avec `lands.json.bak`. Une réponse réseau perdue ne rejoue pas l’écriture.

Les comptes du site sont authentifiés par Firebase et leur UUID Minecraft associé est utilisé pour filtrer les découvertes. Un joueur ne peut pas choisir l’UUID d’un autre joueur dans une requête. Le mode gestion et son historique sont réservés au rang **Admin** côté backend. Les clés serveur existantes restent uniquement dans la configuration serveur.

Les règles de protection de construction et les règles économiques ne sont pas modifiées par ce cadastre.

## Configuration et fichiers

`config/medievalcoins/land_config.json` conserve ses options précédentes et reçoit :

```json
{
  "webSyncEnabled": true,
  "webSyncInSingleplayer": false,
  "webSyncIntervalSeconds": 120,
  "webWorldKey": "renblood"
}
```

La synchronisation solo est désactivée par défaut pour éviter qu’un monde de test envoie ses parcelles au cadastre du serveur. La découverte locale et le cadastre en jeu fonctionnent quand même en solo. Pour tester la synchronisation web, utiliser un serveur dédié local relié à une base de recette isolée. Ne pas réutiliser une même `webWorldKey` pour deux mondes différents : le backend refuse le remplacement d’un monde déjà associé.

L’URL et la clé API restent celles de `config/medieval_coins/server_config.json`. Le nouveau backend doit être disponible avant d’activer l’intégration sur un serveur distant. Aucune clé n’est inscrite dans le frontend.

Le cadastre reste dans `config/medievalcoins/lands.json`. Les découvertes et l’identifiant du monde se trouvent dans **`<monde>/data/renblood_land_discovery.dat`**. Sauvegarder les deux avec le monde. Les nouvelles collections Mongo sont `land_worlds`, `land_discovery` et `land_commands` ; aucune migration des joueurs ou des soldes n’est nécessaire.

## Versions et livraison

- Medieval Coins **1.0.73** ; protocole réseau existant **7**.
- NPC Shopkeeper **1.0.58**, inchangé et inclus pour fournir le trio cohérent.
- Medieval Structures **0.1.49**, recompilé avec Coins 1.0.73.

Remplacer les anciennes versions après arrêt propre et installer le même trio sur serveur et clients. Les destinations locales de recette reçoivent une sauvegarde des JAR précédents. Aucun monde personnel n’est lancé ; les fichiers de claims existants sont vérifiés inchangés à l’installation. Le profil client de production est conservé. **Aucun déploiement du frontend ou du backend et aucune mise à jour du serveur distant de production ne sont effectués.**

## Vérification

Tests automatisés : 18 tests backend en mémoire, cinq tests de calcul du cadastre côté frontend, 31 tests wiki existants lors du build, 17 tests Forge et une recette web sans compte réel sur PC/tablette/téléphone. Le test dédié des trois JAR vérifie aussi le parcours HTTP complet : demande web en attente → validation Minecraft → fichier de claim → reçu confirmé → projection à jour. Cette recette utilise un monde jetable et une API sur l’adresse loopback, sans base distante. Les résultats sont consignés dans les fichiers `*-results.json`, `backend-tests.json` et les logs de build.

Recette avec de vrais joueurs, après mise à disposition des composants :

1. **Solo** : visiter un lieu, ouvrir `/mc land`, naviguer au-delà de huit chunks et vérifier le masquage. Quitter complètement puis rouvrir ; les lieux connus restent connus. Aucun envoi web par défaut.
2. **Multi, deux joueurs associés** : A visite un territoire ; B reste loin. Après synchronisation et Actualiser, A voit les chunks visités, B ne voit ni leur géométrie ni leurs propriétaires. Vérifier une parcelle s’étendant au-delà de la zone connue.
3. **Limites** : à Shaleton, vérifier que les chunks −53/−90 à −37/−74 sont connus après une visite du chunk −45/−82. Le chunk −54/−90 reste masqué tant qu’aucun autre trajet ne le couvre.
4. **Charge** : déplacer/zoomer/rechercher sur le site et vérifier l’absence de nouvelles requêtes. Actualiser doit produire une seule lecture de zone. Vérifier la date de copie du serveur.
5. **Administration** : sélectionner deux chunks, créer/attribuer avec motif, annuler une première confirmation puis confirmer. Vérifier l’état en attente, puis la confirmation en jeu et sur le site. Retirer seulement un des deux chunks et redémarrer pour contrôler la persistance.
6. **Concurrence** : préparer une demande web, modifier le cadastre en jeu avant sa validation ; le site doit afficher un conflit et conserver la modification du jeu.
7. **Indisponibilité** : couper le backend, voyager, puis le remettre en service ; les découvertes doivent être reprises sans perte. Arrêter Minecraft puis envoyer une modification web : elle reste en attente jusqu’à son retour.
8. **Interface** : tester clic/toucher, déplacement tactile volontaire, sélection rectangulaire admin, recherche, clavier et différentes échelles d’interface. Le rendu Minecraft et le multijoueur réel restent à valider par cette recette.
