# Replay TV — prototype Android TV

Prototype d'application Android TV (testée pour une box Google TV Streamer) qui permet de
**parcourir, lire et télécharger les replays** des chaînes gratuites françaises, directement
depuis la télévision, sans ordinateur. TF1+ et M6+, protégés par DRM, sont lisibles en streaming
seulement.

> Captvty lui-même est un logiciel propriétaire (.NET/Windows, « gratuit mais pas libre », sa
> licence interdit la réutilisation). Il ne peut pas être empaqueté dans un APK. Ce projet
> reproduit la fonctionnalité avec des composants libres : le catalogue est lu sur les sites
> des chaînes, et l'extraction/le téléchargement sont confiés à **yt-dlp**, embarqué dans l'APK.

## Ce que fait le prototype

| Fonction | État |
|---|---|
| Accueil par chaîne (TF1, France 2 à 5, M6, Arte, puis TMC, TFX, TF1 Séries Films, LCI, W9, 6ter, franceinfo) : les replays de la chaîne par rubrique, avec recherche « contient » sur les programmes et collections | OK |
| Catalogue France TV (documentaires, séries, films, info, …, programmes et épisodes) | OK, par lecture des pages HTML de france.tv |
| Catalogue Arte (à la une, documentaires, séries, films, …, collections) | OK, via l'API publique EMAC d'Arte |
| Lecture en streaming (HLS, jusqu'en 1080p) | OK, Media3/ExoPlayer |
| Téléchargement en MP4 dans la box, avec progression et notification | OK, yt-dlp + ffmpeg embarqués, service au premier plan |
| Lecture des fichiers téléchargés | OK |
| Lecteur télécommande : Retour masque la surcouche avant de quitter, ◀ ▶ = ±10 s (accéléré si maintenu), reprise à la dernière position | OK |
| Catalogue TF1+ (TF1, TMC, TFX, TF1 Séries Films, LCI) et M6+ | OK, via l'API GraphQL de tf1.fr et l'API « middleware » de M6, sans compte. Seuls les replays gratuits sont listés. |
| Lecture TF1+ / M6+ | OK en **streaming uniquement** : flux DASH chiffré en Widevine, licence obtenue avec un compte gratuit de la chaîne (écran « Comptes »). Jusqu'en 720p avec un compte gratuit. |
| Téléchargement TF1+ / M6+ | **Non** : le déchiffrement se fait dans le module DRM de la box, le fichier n'est jamais accessible en clair. Captvty bute sur la même limite. |
| Navigation à la télécommande (D-pad, Retour) | OK, Compose for TV |
| Sous-titres (menu ▲ : piste, taille, version audio), désactivés par défaut | OK |
| Recherche, reprise de téléchargement interrompu | Pas encore |

Testé en amont (poste de travail, yt-dlp 2026.08.19) : France TV et Arte fournissent des
flux HLS 1080p **sans DRM** ; TF1 renvoie `This video is DRM protected`.

### TF1+ et M6+

Les comptes gratuits se saisissent dans **Comptes** (accueil, en haut à droite). L'app vérifie
les identifiants auprès de la chaîne avant de les enregistrer, chiffrés par une clé du Keystore
Android. Au moment de lire :

- TF1+ : connexion Gigya (`compte.tf1.fr`) → jeton TF1 → `mediainfo.tf1.fr` renvoie le manifeste
  DASH et l'URL de licence Widevine ;
- M6+ : connexion Gigya (`login-gigya.m6.fr`) → JWT 6cloud → jeton « upfront » par vidéo, présenté
  à la licence DRMtoday ; le manifeste DASH vient de la fiche publique de la vidéo.

Ces API ne sont pas documentées : les clés Gigya viennent du code des sites web et peuvent
changer sans préavis. Leur usage par une application tierce n'est probablement pas autorisé par
les conditions d'utilisation des deux plateformes.

**Requêtes GraphQL de TF1+.** Le serveur `tf1.fr/graphql/web` n'accepte que des requêtes
persistées désignées par un identifiant. Le catalogue (`Tf1Catalog`) ne dépend plus d'identifiants
figés :

1. `Tf1QueryResolver` retrouve la table nom → empreinte (`"ProgramCatalogDocument",0,{__meta__:
   {hash:"sha256:…"}}`) publiée dans les scripts du site (page `/programmes-tv` → chemins
   `static/chunks/*.js` → scripts, un niveau de plus si besoin), la met en cache sur disque
   sept jours, et la redécouvre quand le serveur répond `no queries to execute` à un identifiant
   (au plus une découverte à la fois, pas de nouvel essai pendant une heure après un échec) ;
2. les requêtes nommées utilisées sont `ProgramCatalogDocument` (programmes, filtre par chaîne ou
   catégorie) et `VideoContainer_RelatedVideosListDocument` (replays d'un programme) ;
3. en dernier recours, les identifiants de l'ancienne version du site (`483ce0f`, `a6f9cf0e`),
   encore acceptés, avec leur propre forme de réponse.

Si toute la chaîne échoue, l'écran affiche « Le catalogue TF1+ a changé, une mise à jour de
l'application est nécessaire ». L'analyse des réponses (`app/src/test`) est couverte par des
tests unitaires sur des extraits réels (`app/src/test/resources/fixtures/tf1`). Le flux de lecture
(mediainfo, Gigya, licence) ne dépend pas de ces identifiants.

## Aperçu

Captures prises sur l'émulateur Android TV (API 36, x86_64) lors du test de bout en bout :
accueil → catalogue France TV → fiche vidéo → lecture HLS → téléchargement → lecture du MP4 local.

| Accueil | Catalogue France TV |
|---|---|
| ![Accueil](docs/screenshots/home.png) | ![Catalogue](docs/screenshots/catalogue-francetv.png) |

| Fiche vidéo | Téléchargements |
|---|---|
| ![Fiche](docs/screenshots/detail.png) | ![Téléchargements](docs/screenshots/downloads.png) |

### Tester sans box : émulateur Android TV dans Docker

`scripts/emulator-smoke.sh` installe l'image système Android TV, démarre un émulateur headless
(KVM requis), installe l'APK x86_64 et déroule le scénario ci-dessus en prenant des captures
dans `.cache/smoke/`. Le conteneur `replaytv-emu` reste disponible ensuite pour `adb`.

## Architecture

```
app/src/main/java/dev/valentin/replaytv/
├── ReplayTvApp.kt            Application : OkHttp, yt-dlp, catalogue, gestionnaire de téléchargements
├── MainActivity.kt           Activité Leanback (Compose for TV)
├── model/Catalog.kt          Source, Section, CatalogItem (Video | Collection), CatalogRow
├── catalog/
│   ├── FranceTvCatalog.kt    Lecture des cartes <a data-card-link> des pages france.tv
│   ├── ArteCatalog.kt        API EMAC (pages), API player (métadonnées), yt-dlp (collections)
│   ├── Tf1Catalog.kt         API GraphQL tf1.fr : requêtes nommées puis anciens identifiants
│   ├── Tf1Queries.kt         Découverte et cache des identifiants de requêtes persistées
│   ├── M6Catalog.kt          API middleware M6 : dossiers, programmes, épisodes gratuits, fiche vidéo
│   └── CatalogRepository.kt  Dispatch par source
├── ytdlp/YtDlp.kt            Façade youtubedl-android : init, `-J` (analyse), `--flat-playlist`, téléchargement
├── download/
│   ├── DownloadManager.kt    File d'attente séquentielle, état observable, métadonnées JSON à côté des MP4
│   └── DownloadService.kt    Service au premier plan + notification de progression
├── drm/
│   ├── Accounts.kt           Comptes TF1+ / M6+ chiffrés (AES-GCM, clé du Keystore)
│   ├── Tf1Playback.kt        Connexion TF1+, manifeste DASH et URL de licence
│   ├── M6Playback.kt         Connexion M6+, jeton DRMtoday, manifeste DASH
│   └── DrmPlayback.kt        Dispatch par source, vérification et enregistrement des comptes
├── player/PlayerActivity.kt  ExoPlayer plein écran (HLS distant, DASH Widevine ou fichier local)
└── ui/                       Home, Browse (rangées de cartes), Detail, DrmDetail, Accounts, Downloads, thème
```

Flux d'une vidéo : carte du catalogue → `yt-dlp -J <url>` sur la box → manifeste HLS
(lecture directe) ou `yt-dlp -o … -f "bestvideo[height<=1080]+bestaudio/best"` (téléchargement,
fusion MP4 par ffmpeg) → fichier dans
`Android/data/dev.valentin.replaytv/files/Movies/replays/`.

Composants principaux :

- [youtubedl-android](https://github.com/yausername/youtubedl-android) 0.18.1 : Python + yt-dlp + ffmpeg
  pour Android (arm64-v8a, x86_64). yt-dlp est mis à jour indépendamment de l'APK
  (`YoutubeDL.updateYoutubeDL`, pas encore branché dans l'UI).
- Jetpack Compose + `androidx.tv:tv-material` pour l'interface télécommande.
- Media3 ExoPlayer 1.11 (HLS, DASH, Widevine).
- OkHttp, kotlinx-serialization, Coil.

## Télécharger l'APK

Les APK sont publiés dans les [releases GitHub](https://github.com/idkw/captvty-android-tv/releases)
(`replaytv-<version>-universal.apk` s'installe sur toute box ; les variantes `arm64-v8a`, `armeabi-v7a` et `x86_64` sont plus légères). Pousser un tag `vX.Y.Z` déclenche la
construction et la publication par GitHub Actions (`.github/workflows/release.yml`).

## Mises à jour depuis l'application

Au lancement, l'application interroge `api.github.com/repos/idkw/captvty-android-tv/releases/latest`.
Si une version plus récente existe, une boîte de dialogue propose de la télécharger et de
l'installer, de la reporter, ou de l'ignorer. Le bouton `vX.Y.Z` de l'accueil relance la
vérification à la demande. L'APK choisi suit l'architecture de la box (`arm64-v8a`,
`armeabi-v7a`), sinon l'universel.

Avant d'ouvrir l'installateur Android, deux contrôles :

1. le SHA-256 du fichier téléchargé doit être celui que l'API GitHub publie pour cet APK ;
2. l'APK doit être signé par le même certificat que l'application installée. Le système refuserait
   de toute façon une signature différente ; le message explique alors qu'une installation
   manuelle (désinstaller puis installer) est nécessaire une fois.

La première installation d'un APK depuis l'application demande d'autoriser Replay TV à installer
des applications (Paramètres Android, « sources inconnues ») ; la boîte de dialogue y mène.

### Clé de signature des releases

Pour que les mises à jour s'installent par-dessus la version précédente, tous les APK doivent être
signés avec la même clé. La CI la lit dans les secrets du dépôt GitHub (le keystore lui-même n'est
pas versionné) :

| Secret | Contenu |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | le fichier `.jks` encodé en base64 (`base64 -w0 keystore/replaytv-release.jks`) |
| `RELEASE_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `RELEASE_KEY_ALIAS` | alias de la clé (`replaytv`) |
| `RELEASE_KEY_PASSWORD` | mot de passe de la clé |

Sans ces secrets, la CI signe avec une clé de debug générée à chaque build, et chaque mise à jour
exige une désinstallation manuelle. En local, `scripts/build-docker.sh` lit les mêmes variables
dans `.env` (`RELEASE_KEYSTORE_PATH=keystore/replaytv-release.jks`, …).

Chaque release porte aussi une attestation de provenance GitHub (`actions/attest-build-provenance`) :
`gh attestation verify replaytv-vX.Y.Z-universal.apk --owner idkw` prouve que l'APK sort bien du
workflow de ce dépôt.

## Compiler

### Sans Android Studio (Docker)

```bash
scripts/build-docker.sh                 # assembleDebug
ls app/build/outputs/apk/debug/         # app-universal-debug.apk, app-arm64-v8a-debug.apk, …
```

Premier lancement long (téléchargement de Gradle, du SDK Android et des dépendances) ;
les caches sont conservés dans `.cache/`.

### Avec Android Studio

Ouvrir le dossier, laisser le SDK 36 s'installer, `Run` sur un appareil Android TV.

## Installer sur la box

1. Sur la box : Paramètres → Système → À propos → appuyer 7 fois sur « Build » pour activer
   les options développeur, puis activer **Débogage USB** / **Débogage réseau (ADB)**.
2. Depuis le poste (box et PC sur le même réseau) :

```bash
adb connect <ip-de-la-box>:5555
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
```

L'application apparaît dans le lanceur Google TV sous « Replay TV ».

## Limites connues et pistes

- **DRM** : TF1+ et M6+ se lisent en streaming mais ne se téléchargent pas. Les contenus payants
  (TF1+ `MAX`, packs M6+) sont masqués. La lecture a été validée sur l'émulateur (Widevine L3) ;
  la box dispose d'un Widevine matériel (L1).
- Le catalogue France TV dépend du HTML du site : un changement de balisage casse la lecture
  des cartes (`FranceTvCatalog.parseCards`). L'API EMAC d'Arte est plus stable.
- Pas de permission de stockage demandée : les fichiers sont dans le dossier privé de l'app
  (supprimés à la désinstallation). Pour les voir depuis un autre lecteur, il faudra passer par
  `MediaStore` ou un dossier public.
- Pas encore : mise à jour de yt-dlp depuis l'UI, reprise après coupure,
  limite d'espace disque.
- Les téléchargements tournent dans un service au premier plan ; l'application peut rester en
  arrière-plan pendant ce temps.
