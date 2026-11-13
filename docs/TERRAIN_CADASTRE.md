# Fond Minecraft des cadastres — 13 novembre 2026

## Affichage

La carte Minecraft fournie est intégrée au cadastre web **joueur et administrateur** ainsi qu’au cadastre Minecraft `/mc land`.

Le fond s’active par défaut. Le bouton **Fond Minecraft** sur le site, ou **Fond : Minecraft / grille** en jeu, permet de retrouver le plan simple. Les couleurs des parcelles sont translucides pour laisser voir les chemins et bâtiments. La sélection, les informations de propriété et les boutons administrateur restent disponibles.

Les zones inconnues restent masquées pour les joueurs. Les réponses de l’API restent filtrées comme précédemment ; ajouter le fond ne révèle pas de propriétaires ou de géométrie de claims supplémentaires. Le mode gestion web donne toujours la vue d’ensemble aux administrateurs. Les images de terrain sont des ressources statiques, comme l’atlas public : leur masquage visuel ne constitue pas une protection du relief lui-même.

Le fond représente l’export fourni : il ne se redessine pas en direct après une construction en jeu. Il est affiché dans `minecraft:overworld` ; les autres dimensions gardent la grille du cadastre.

## Calage confirmé

- Image originale : **7296 × 10112 pixels**. Le redimensionnement de l’aperçu de la conversation n’est pas utilisé.
- Échelle confirmée : **1 pixel = 1 bloc Minecraft**.
- Pixel **(2868, 3691)** : Shaleton, **X −716 / Z −1300**.
- Origine de l’image dans le monde : **X −3584 / Z −4991**.
- X croît vers la droite ; Z croît vers le bas.

Le coin nord-ouest du chunk −45/−82 correspond ainsi au pixel (2864, 3679), et un chunk conserve exactement 16 × 16 pixels source. La calibration est identique dans les manifestes du site et du mod. Les coordonnées ne sont jamais recalculées à partir des versions compressées.

## Optimisation

L’original de **125 535 370 octets** est conservé intact dans le dépôt front, à l’emplacement local `artwork/maps/big_map_2026.png`. Il est exclu de Git et du dossier public ; les tuiles optimisées sont versionnées à sa place. Son empreinte SHA-256 est `0cfa9e433c69ba0849d88217977b5a8c08dc40e7a6346fed12484c84b0ef895f`.

L’image est découpée en **300 tuiles de 512 × 512 pixels** par format :

- **Site** : WebP, **25 140 724 octets** pour tout le fond. Seules les tuiles visibles qui croisent une zone connue sont demandées. Dans la recette de Shaleton, deux tuiles suffisent pour la zone personnelle initiale ; une vue de 96 × 96 chunks est plafonnée à seize tuiles. Les fichiers ont des URL versionnées et bénéficient du cache d’images existant. Les tuiles entièrement sous le brouillard ne sont pas chargées.
- **Minecraft** : PNG indexé, **25 367 102 octets** au total dans les ressources du mod. Décodage et préparation en arrière-plan via le chargement de textures de Minecraft ; le rendu n’attend pas les futures de chargement. Cache partagé limité à **16 textures** de 512 px, soit environ **16 Mio de surfaces RGBA sur le GPU**, hors allocations transitoires. La vue actuelle du jeu nécessite au plus quatre tuiles.

Les dernières tuiles sont complétées par transparence, sans étirer l’image. Aucune image complète de 7296 × 10112 n’est décodée ou envoyée au GPU en jeu. Le chargement du fond n’ajoute aucun appel au backend ni chargement de chunk Minecraft. Sur le site, se déplacer peut demander de nouvelles **images statiques**, mais aucune requête supplémentaire de données du cadastre ; son bouton **Actualiser** conserve le fonctionnement précédent.

Les propriétaires, la découverte, les sauvegardes et les confirmations administrateur ne changent pas. Aucun changement du backend n’est nécessaire pour ce fond.

## Versions

- Medieval Coins **1.0.74**.
- NPC Shopkeeper **1.0.58**, inchangé.
- Medieval Structures **0.1.50**, recompilé avec Coins 1.0.74.

Le trio est à remplacer après arrêt propre sur le serveur et les clients. Les configurations, les claims et les découvertes existants sont conservés. L’utilisateur installe lui-même les JAR sur le serveur Minecraft de production ; le profil client de production local n’est pas modifié automatiquement.

## Validation et recette

Les vérifications automatisées contrôlent le point de Shaleton, l’échelle bloc/pixel, les coordonnées négatives, les coutures de tuiles, les vues hors export, les plafonds de chargement et le masquage des tuiles inconnues. Les **600 fichiers d’image** sont vérifiés, y compris la transparence des bords et l’égalité des calibrations. La recette web teste le terrain, la bascule grille/fond, les requêtes, le brouillard, les formats PC/téléphone et le toucher avec des données fictives.

Après installation :

1. Site joueur : ouvrir `/cadastre`, localiser X −716 / Z −1300 et vérifier le calage des rues et des cases. Une zone visitée en jeu doit afficher le fond ; une zone inconnue doit rester masquée.
2. Site administrateur : activer Mode gestion, déplacer et zoomer, sélectionner un terrain, puis vérifier que sa fiche et son attribution restent cohérentes avec la grille. Comparer Fond Minecraft activé/désactivé.
3. En jeu : ouvrir `/mc land` à Shaleton ; vérifier le fond, les coins des chunks et la position du joueur, puis tester plusieurs échelles d’interface et la navigation. Le premier chargement peut afficher brièvement la grille en attendant les textures.
4. Vérifier la lisibilité des limites de propriété et de la sélection sur l’eau, la forêt et les bâtiments. Tester un chunk traversant une couture de tuile.
5. Naviguer hors de l’export et dans une autre dimension : la grille reste utilisable. Fermer/rouvrir plusieurs fois et vérifier que les textures reviennent sans chargement de monde ni perte de découverte.

Le rendu natif dans le client Minecraft reste une recette manuelle ; aucun client personnel ni monde de production n’est lancé par les tests automatiques.
