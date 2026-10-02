Prototype d'application Android TV pour parcourir, lire et télécharger les replays de France TV et d'Arte directement sur la box (testé pour Google TV Streamer).

**Fichiers**
- `replaytv-*-universal.apk` : s'installe sur n'importe quelle box Android TV (à prendre en cas de doute).
- `replaytv-*-arm64-v8a.apk` : box 64 bits (Google TV Streamer, Shield…), plus léger.
- `replaytv-*-armeabi-v7a.apk` : box 32 bits (Chromecast with Google TV…).
- `replaytv-*-x86_64.apk` : émulateur Android TV.

Si l'installateur affiche « application non compatible avec votre téléviseur », c'est que l'APK ne
contient pas l'architecture de la box : prendre l'APK universel.

**Installation**
```bash
adb connect <ip-de-la-box>:5555
adb install -r replaytv-<version>-universal.apk
```

**Fonctionnalités**
- Accueil par chaîne (TF1, France 2, 3, 4, 5, M6, Arte, puis TMC, TFX, TF1 Séries Films, LCI, W9, 6ter, franceinfo) : les replays de la chaîne classés par rubrique, et une recherche qui filtre programmes et collections (correspondance « contient », accents ignorés). Le bouton « Par thème » garde la navigation par genre.
- Catalogue France TV (documentaires, séries, films, info, …) et Arte (à la une, documentaires, séries, films, …).
- Lecture en streaming HLS jusqu'en 1080p (ExoPlayer).
- Téléchargement en MP4 sur la box (yt-dlp + ffmpeg embarqués), notification de progression, lecture hors ligne.
- Lecteur pensé pour la télécommande : OK = lecture/pause, ◀ ▶ = −10 s / +10 s (pas qui grandit si la touche est maintenue), Retour masque la surcouche puis quitte, reprise automatique à la dernière position.
- Sous-titres : désactivés par défaut, menu ▲ pour choisir la piste, la taille du texte et la version audio ; affichés en bas au centre quel que soit le positionnement du flux. Le choix est mémorisé.

**Limites**
- TF1+ et M6+ : flux protégés par DRM, pas de lecture ni de téléchargement possibles.
- APK signé avec une clé de débogage générée par la CI : pour passer d'une version à l'autre il peut être nécessaire de désinstaller l'ancienne (`adb uninstall dev.valentin.replaytv`).
- Pas encore de recherche, de sous-titres ni de reprise de téléchargement interrompu.
