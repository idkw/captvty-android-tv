# Nouveautés

Chaque release GitHub reprend la section de sa version. Ajouter la section de la prochaine version
avant de pousser son tag : sans elle, le workflow de release échoue.

## v0.7.6

- Épisodes suivants en fin de lecture : la collection est relue auprès de la chaîne pendant les 2 dernières minutes de l'épisode (ou à la fin si on y a sauté), pour proposer un épisode publié pendant la lecture.
- Retour aux listes : sans lecture, la liste revient telle quelle ; après une lecture, elle se recharge pour montrer les nouveaux épisodes, et le curseur retrouve l'épisode quitté même s'il a changé de place.

## v0.7.5

- Retour d'une fiche avec la touche Retour : le curseur revient sur la vignette quittée, au même défilement, sans recharger la liste. Valable dans les collections, les pages de chaîne (la recherche en cours est gardée), les rubriques par thème, les Favoris et « En cours ».

## v0.7.4

- Recherche de mise à jour à chaque retour dans Replay TV, même quand Google TV l'avait gardée en mémoire (au plus une fois toutes les 5 minutes). Avant, elle n'avait lieu qu'au démarrage de l'application.
- Les fenêtres (mise à jour, « En cours », yt-dlp refusé) gardent le focus de la télécommande : les flèches ne passent plus aux chaînes derrière, et OK active bien le bouton sélectionné.

## v0.7.3

- Choisir l'épisode suivant à la fin d'une vidéo TF1+ ou M6+ ne ferme plus l'application : le nouveau lecteur démarrait avant que l'ancien ait libéré sa session média.

## v0.7.2

- M6+ : quand un programme garde plusieurs saisons sous les mêmes titres, chaque épisode affiche son année entre parenthèses, par exemple « Épisode 10 - Partie 2 (2026) ». Les épisodes suivants proposés en fin de lecture tiennent compte de cette année : l'épisode 11 de l'an dernier ne passe plus pour la suite de l'épisode 10 de cette année.

## v0.7.1

- Épisode choisi à la fin d'une vidéo TF1+ ou M6+ : la fiche lance bien cet épisode, et la lecture démarre seule (elle relançait l'épisode qui venait de se terminer).
- M6 garde plusieurs saisons d'un programme sans numéro de saison : les épisodes suivants proposés sont ceux de la même saison, sans doublon (« Épisode 10 - Partie 2 » apparaissait trois fois, une par année).

## v0.7.0

- Coche verte sur la vignette des épisodes vus jusqu'au bout. Elle est gardée en mémoire et disparaît quand on relance l'épisode depuis le début.
- À la fin d'un épisode, l'écran propose « Revoir » et les 3 épisodes suivants, dans l'ordre : épisode 10 partie 1, puis partie 2, puis épisode 11. L'épisode choisi démarre seul. Les suggestions viennent de la collection d'où l'épisode a été ouvert, y compris en reprise depuis « En cours ».

## v0.6.1

- Avance et retour rapides avec ◀ ▶ : la vidéo affiche l'image de la position visée pendant que l'on déplace le curseur, comme sur Netflix ; la lecture reprend au relâchement de la touche.

## v0.6.0

- Revenir au début d'un épisode commencé :
  - sur la fiche de l'épisode, bouton « Recommencer depuis le début » à côté de « Reprendre à … » ;
  - dans l'onglet « En cours », choix « Recommencer » dans le popup ;
  - pendant la lecture, menu ▲ › « Revenir au début de l'épisode », avec une confirmation où « Annuler » est sélectionné par défaut.
- Le menu ▲ du lecteur s'ouvre maintenant sur toutes les vidéos ; quand il n'y a pas de sous-titres, il l'indique au lieu de rester fermé.
- Le menu ▲ défile quand il est long (vidéos Arte avec beaucoup de pistes) : la ligne sélectionnée reste visible.

## v0.5.3

- Onglets « Favoris » et « En cours », résultats de recherche : la première ligne de vignettes n'est plus rognée en haut quand une vignette est sélectionnée.

## v0.5.2

- La mise à jour automatique de yt-dlp vérifie désormais chaque release avant de l'installer : signature de l'équipe yt-dlp sur le fichier d'empreintes et empreinte SHA-256 du fichier téléchargé.
- En cas de non-conformité, rien n'est installé et une popup « Mise à jour de yt-dlp refusée » en donne la raison.
- La version de yt-dlp déjà en place est contrôlée de la même façon.

## v0.5.1

- yt-dlp se met à jour tout seul, au plus une fois par jour : les collections Arte s'affichent de nouveau.
- Le texte « yt-dlp prêt » n'apparaît plus en haut de l'accueil.

## v0.5.0

- Onglet « Favoris » : les collections marquées d'une étoile (« ☆ Ajouter aux favoris » en haut d'une collection).
- Onglet « En cours » : les vidéos commencées et pas terminées, de la plus récente à la plus ancienne ; un clic propose de reprendre la lecture ou de retirer la vidéo de la liste.

## v0.4.2

- L'écran de veille de Google TV ne coupe plus la lecture au bout de 10 minutes.
- Revenir dans l'application depuis l'accueil Google TV ramène au lecteur.

## v0.4.0

- Mise à jour depuis l'application : une nouvelle version est proposée au lancement, téléchargée, vérifiée (empreinte et signature) puis installée sans désinstaller la précédente.

## v0.3.3

- TF1 : le catalogue d'une chaîne se charge en entier.

## v0.3.2

- Recherche plus fluide : la saisie ne se bloque plus pendant le filtrage.

## v0.3.1

- Catalogue TF1+ plus robuste aux changements du site tf1.fr.

## v0.3.0

- Accueil par chaîne (TF1, France 2 à 5, M6, Arte, puis les chaînes secondaires), avec une recherche dans les programmes de chaque chaîne.
- Catalogues TF1+ et M6+, lisibles en streaming avec un compte gratuit.

## v0.2.1

- Menu des sous-titres (piste, taille, version audio) ; sous-titres affichés en bas au centre.

## v0.2.0

- Lecteur adapté à la télécommande : Retour masque la surcouche, ◀ ▶ avancent de 10 s, reprise à la dernière position.

## v0.1.2

- Barre de chargement pendant l'analyse d'une vidéo.

## v0.1.1

- APK universel et APK 32 bits pour les box qui refusaient l'APK 64 bits.

## v0.1.0

- Première version : catalogues France TV et Arte, lecture en streaming, téléchargement en MP4.
