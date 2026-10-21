# Replay TV : consignes pour Claude

## Installer chaque release sur la box

Quand une release vient d'être publiée sur GitHub (tag `vX.Y.Z`, APK présents dans la release) et que
le débogage de la box Google TV est ouvert (`adb devices` la montre en `device`), installer
directement la nouvelle version sur la box, sans demander :

```bash
gh release download vX.Y.Z --repo idkw/captvty-android-tv --pattern '*-universal.apk' --dir <scratchpad>
adb -s <box> install -r <scratchpad>/replaytv-vX.Y.Z-universal.apk
adb -s <box> shell dumpsys package dev.valentin.replaytv | grep versionName   # vérifier la version
```

- Toujours l'APK `universal` : c'est celui que la box accepte.
- `install -r` garde les données (comptes, favoris, positions de reprise) : les releases sont signées
  avec la même clé que la version installée.
- Si la box n'apparaît pas ou reste `unauthorized`, ne pas insister : dire que l'installation n'a
  pas été faite et que la mise à jour sera proposée dans l'application.
