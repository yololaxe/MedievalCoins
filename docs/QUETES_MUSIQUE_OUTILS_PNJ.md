# Quêtes, musique et outils PNJ — 12 novembre 2026

Installer ensemble les trois JAR du paquet sur le serveur Minecraft et les clients Forge 1.20.1, après sauvegarde et arrêt propre. Le protocole Medieval Coins passe à **6** : les anciennes versions client/serveur ne doivent pas être mélangées. Les fichiers de configuration et les mondes existants sont à conserver.

## Quêtes

Le mod accepte maintenant les objets stockés par l’éditeur dans `target.items`, ainsi que les anciens formats. Le journal et les interactions PNJ affichent les quantités et les noms d’objets. Les textes mal encodés sont corrigés. Deux lignes pour le même objet demandent leur quantité cumulée.

Le bouton « Vérifier et remettre » demande au serveur de contrôler l’inventaire actuel ; une ancienne prévisualisation ne bloque plus le clic. Seuls les objets demandés sont retirés après validation de leur présence. Les réservations et les reçus de récompense existants sont conservés. Les règles d’XP n’ont pas changé : ce champ est la récompense de quête et peut rester à zéro.

Essai : créer un objectif de 5 pains dans l’éditeur. Avec 4 pains, la remise doit être refusée. Avec deux piles totalisant 6 pains, elle doit réussir et laisser exactement 1 pain. Vérifier qu’un deuxième clic ne verse pas une deuxième récompense.

## Musique personnelle

- `/mc music` ou `/mc music list` : liste cliquable des quinze pistes.
- `/mc music 1` à `/mc music 15` : choisir une piste.
- `/mc music random` : enchaînement aléatoire, sans répéter immédiatement la même piste.
- `/mc music stop` : arrêter la lecture Renblood.
- `/mc music volume 60` : régler le volume personnel entre 0 et 100, conservé sur ce client.
- `/mc music credits` : consulter l’auteur et la source.

La catégorie **Musique** des options Minecraft s’applique aussi. Les pistes sont lues en streaming depuis les ressources du mod, sans HTTP pendant le jeu ni traitement audio côté serveur. Le lecteur s’arrête à la déconnexion. Les quinze compositions de Kevin MacLeod sont distribuées sous **CC BY 4.0** avec les crédits complets dans `MUSIC_CREDITS.md` et dans le JAR. Elles ne sont pas dans le domaine public.

## PNJ de route

`/ns npc info` ou `/ns npc info nearest` affiche le PNJ chargé le plus proche, dans un rayon de 12 blocs. `/ns npc info <uuid>` cible une identité précise. Les informations comprennent l’UUID, le monde, la position, la catégorie, les routes liées, la ville et le skin. Les UUID sont proposés par Tab.

`/ns npc remove <uuid>` retire un ancien PNJ local, son modèle persistant et ses références de route. Une identité supprimée est aussi mémorisée pour empêcher sa restauration depuis un ancien chunk. Les fichiers concernés sont sauvegardés sous `npcshopkeeper/admin-backups` dans le monde. Aucun PNJ réel n’a été supprimé pendant le développement.

Le bouton cliquable dans les informations prépare la commande sans l’exécuter. Vérifier le nom, la position et l’UUID avant validation. Les PNJ API, clients d’auberge, capitaines et boutiques permanentes sont exclus de cette suppression ; ils conservent leurs outils de gestion propres. Une route encore active peut créer un **nouveau** PNJ : supprimer une identité n’arrête pas une route active.

Essai : inspecter un ancien PNJ de route, retirer son UUID, puis redémarrer le monde et recharger son chunk. Le même UUID ne doit pas revenir. Vérifier qu’une boutique et un PNJ API ne peuvent pas être retirés par cette commande.

## Références et traversées

Les vingt références affichées par `/ns reference missing` sont cliquables : elles préparent `/ns reference price <item> ` dans le chat. Compléter le minimum, le maximum et le booléen d’envoi au backend avant de valider.

Les joueurs possédant un billet réservé voient un cercle de particules près de leur quai de départ. Se tenir à l’intérieur donne un repère pour l’embarquement. Le bouton de réduction d’animation a été retiré de la scène de traversée.

Le signalement de départ bloqué a été retiré du périmètre à la demande de l’utilisateur : annuler puis réserver à nouveau a résolu le problème. Aucun diagnostic supplémentaire de voyage n’est livré, et les horaires n’ont pas changé.

## Configuration et production

Aucun déploiement du front ou du backend n’a été effectué. Les corrections de code concernent uniquement les mods. La clé du profil local actif MAJ-RP-2024-1.20.1, qui différait de la clé de l’API de production, a été alignée avec sauvegarde privée. La requête du joueur réellement actif a ensuite répondu HTTP 200.

En multijoueur, les requêtes de quête utilisent toutefois la configuration du **serveur Minecraft**, pas celle du client. Si le 401 persiste en jeu, vérifier `config/medieval_coins/server_config.json` côté serveur : `apiUrl` doit cibler le backend voulu et `apiKey` correspondre à sa variable `API_KEY_RENBLOOD`, puis recharger ou redémarrer le serveur. Ne pas publier cette clé dans le chat ou dans le paquet client. Le paquet livré ne contient aucun accès privé.

La production Minecraft est gérée par l’utilisateur : les JAR ne sont pas installés à distance. Les essais en vrai multijoueur, le rendu des particules et l’écoute dans le client restent à exécuter après mise à jour simultanée.
