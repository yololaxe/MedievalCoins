# Cadastre et attribution des chunks

## Ouvrir et consulter

`/mc land` ouvre directement le cadastre. `/mc land map` reste disponible, ainsi que les anciennes commandes `/land`.

La carte affiche une case par chunk, avec les couleurs exactes des parcelles et des contours continus entre voisins d’une même parcelle. Le carré blanc repère votre position et le contour doré votre sélection. Cliquer une case affiche le nom de la parcelle, son propriétaire, son type, la ville, les coordonnées et un aperçu de sa description. Le survol affiche aussi le nom et le propriétaire.

Les boutons Nord, Sud, Est et Ouest déplacent la vue sans charger les chunks du monde. « Ici » revient sur votre position ; « Actualiser » relit le registre local. La consultation reste limitée aux environs du joueur (64 chunks de déplacement). Les rayons configurés dans `land_config.json` sont conservés.

La liste d’administration est bornée pour tenir dans le paquet réseau d’ouverture. Si « Liste limitée » apparaît, les commandes existantes restent disponibles pour les parcelles qui ne figurent pas dans cette liste.

« Limites de mon chunk · 20 s » ferme la carte et affiche un contour dans le monde autour du chunk où vous vous trouvez. L’entrée dans une parcelle affiche son nom et son propriétaire ; la sortie indique « Territoire non attribué ». `/mc land info` reste disponible, avec un lien vers la carte sur les chunks non attribués.

## Attribuer et retirer — administration

Les règles de permission existantes sont conservées : consultation publique, attribution réservée au niveau d’administration défini par `adminPermissionLevel`. Le serveur vérifie à nouveau les droits lors de chaque action.

1. Créer une parcelle avec « Créer… » : le chat prépare `/mc land create ` ; compléter son identifiant et son nom, puis valider. Rouvrir la carte pour actualiser la liste des parcelles.
2. Chercher la parcelle par nom ou identifiant dans la fiche de droite. Le bouton de sélection permet de parcourir les résultats.
3. Cliquer un chunk non attribué, choisir « Attribuer », puis « Confirmer ».
4. Pour retirer un chunk, le sélectionner, choisir « Retirer », puis « Confirmer ». « Annuler » quitte cette confirmation sans changer le cadastre.
5. « Propriétaire… » prépare la commande de modification avec la parcelle et son type actuels. Compléter ou corriger le nom avant validation.

Une attribution existante n’est pas remplacée silencieusement : il faut d’abord la retirer. Retirer un chunk conserve sa parcelle, son propriétaire et ses autres chunks. Si un autre administrateur a changé l’attribution depuis la première confirmation, le serveur refuse l’ancienne demande ; actualiser la carte.

Les identifiants de parcelle et les commandes historiques sont conservés. Le cadastre garde sa fonction d’attribution et d’affichage ; les règles économiques et les protections de construction ne sont pas modifiées par cette refonte.

## Données et performance

Les fichiers de parcelles existants ne sont pas migrés. Ils restent dans `config/medievalcoins/lands.json` côté serveur. Les sauvegardes sont écrites via un fichier temporaire puis remplacement, avec une copie précédente dans `lands.json.bak`. Un fichier illisible ou des attributions contradictoires bloquent l’édition au lieu d’être écrasés.

La carte utilise le registre local indexé ; aucun HTTP, génération ou chargement de chunk n’est nécessaire pour sa navigation. Le suivi de position s’effectue toutes les dix ticks et ne relit plus le fichier de configuration à chaque tick. La recherche dans la liste des parcelles est recalculée uniquement quand le texte change.

## Installation et recette

Le protocole Medieval Coins passe à **7**. Installer le même paquet de trois JAR sur le serveur et les clients, après sauvegarde et arrêt propre. Les mondes et configurations sont à conserver. Aucun déploiement en production n’est effectué.

Les tests Forge contrôlent les coordonnées négatives, les limites de navigation, les conflits d’attribution, le retrait d’un seul chunk, les dimensions, les métadonnées de propriétaire, le transport réseau et les petits/grands écrans. Le test de fichier corrompu s’exécute uniquement dans la configuration de test jetable.

Essais manuels après installation :

1. Ouvrir `/mc land`, sélectionner plusieurs cases et vérifier noms, propriétaires, couleurs, coordonnées et repère de position. Tester plusieurs tailles de fenêtre et échelles d’interface.
2. Naviguer puis revenir avec « Ici » ; afficher les limites du chunk et traverser une frontière de propriété.
3. Administrateur : créer une parcelle de recette, attribuer deux chunks voisins, puis retirer un seul chunk. Vérifier que le propriétaire et le second chunk sont conservés.
4. Cliquer « Retirer », puis « Annuler » ; rien ne doit changer. Tester le refus de remplacement d’une autre parcelle.
5. Avec deux administrateurs, préparer la même attribution puis en modifier une : l’autre confirmation doit être refusée.
6. Avec un joueur sans droits, vérifier l’absence des boutons d’édition et le refus serveur des actions administratives.
7. Redémarrer le serveur et vérifier la persistance. Contrôler que `lands.json.bak` contient la version précédente.

Le rendu réel dans Minecraft et les essais multijoueurs restent à vérifier par cette recette ; aucun monde personnel ni parcelle de production n’est modifié pendant les tests automatiques.
