Prototype d'application Android TV pour parcourir, lire et télécharger les replays de France TV et d'Arte directement sur la box (testé pour Google TV Streamer).

**Fichiers**
- `replaytv-*-arm64-v8a.apk` : pour la box Android TV (Google TV Streamer, Chromecast with Google TV, Shield…).
- `replaytv-*-x86_64.apk` : pour l'émulateur Android TV.

**Installation**
```bash
adb connect <ip-de-la-box>:5555
adb install -r replaytv-<version>-arm64-v8a.apk
```

**Fonctionnalités**
- Catalogue France TV (documentaires, séries, films, info, …) et Arte (à la une, documentaires, séries, films, …).
- Lecture en streaming HLS jusqu'en 1080p (ExoPlayer).
- Téléchargement en MP4 sur la box (yt-dlp + ffmpeg embarqués), notification de progression, lecture hors ligne.

**Limites**
- TF1+ et M6+ : flux protégés par DRM, pas de lecture ni de téléchargement possibles.
- APK signé avec une clé de débogage générée par la CI : pour passer d'une version à l'autre il peut être nécessaire de désinstaller l'ancienne (`adb uninstall dev.valentin.replaytv`).
- Pas encore de recherche, de sous-titres ni de reprise de téléchargement interrompu.
