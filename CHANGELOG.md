# Nouveautés

Chaque release GitHub reprend la section de sa version. Ajouter la section de la prochaine version
avant de pousser son tag : sans elle, le workflow de release échoue.

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
