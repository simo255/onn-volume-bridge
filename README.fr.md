# onn Volume Bridge

Un projet indÃ©pendant pour utiliser les boutons **+/âˆ’ et Muet de la tÃ©lÃ©commande
onn avec le volume du tÃ©lÃ©viseur**, lorsque la configuration infrarouge de la box
Ã©choue. Il fonctionne sur le rÃ©seau local et ne remplace pas le launcher.

[Documentation principale](README.md) Â· [Installation](docs/setup.md) Â· [CompatibilitÃ©](docs/compatibility.md)

## Ce qui est prÃªt

- Moteur de lecture des boutons onn sÃ©parÃ© des protocoles des tÃ©lÃ©viseurs.
- Samsung Tizen : WebSocket chiffrÃ© et appairage autorisÃ© sur la tÃ©lÃ©.
- Roku TV : commandes ECP, avec vÃ©rification quâ€™il sâ€™agit dâ€™une tÃ©lÃ© Roku.
- Sony BRAVIA : API audio JSON-RPC et clÃ© PSK si nÃ©cessaire.
- UPnP RenderingControl : mode expÃ©rimental pour des points de contrÃ´le compatibles.
- Configuration Ã  la tÃ©lÃ© en franÃ§ais et en anglais, pause et reconnexion.
- ClÃ©s privÃ©es et identifiants chiffrÃ©s sur lâ€™appareil ; aucune donnÃ©e personnelle publiÃ©e.

**Les commandes de volume de l’application indépendante ont été confirmées avec
une vraie télécommande onn et une Samsung avec TizenOS : appuis courts, Muet,
maintien et arrêt au relâchement. La validation complète reste à faire pour le
redémarrage, la lecture multimédia et les écouteurs. Roku, Sony et UPnP restent
expérimentaux : leurs tests de protocole ne remplacent pas un essai matériel.**

Le projet ne rÃ©pare pas lâ€™attestation du service Google et ne programme pas les
codes infrarouges. Il contourne ce chemin avec les commandes rÃ©seau du tÃ©lÃ©viseur.
Une ancienne tÃ©lÃ© uniquement infrarouge nâ€™est donc pas automatiquement compatible.
LG webOS et dâ€™autres protocoles pourront Ãªtre ajoutÃ©s ; ils ne sont pas annoncÃ©s
comme disponibles dans cette premiÃ¨re version.

## Installation

Construisez le projet avec JDK 17 et Android SDK 34 :

```powershell
.\gradlew.bat --no-daemon testReleaseUnitTest lintRelease assembleRelease
adb -s <box endpoint> install app/build/outputs/apk/release/app-release.apk
```

Ouvrez lâ€™app, choisissez le protocole de votre tÃ©lÃ© et son adresse locale. Enregistrez,
autorisez lâ€™appairage si nÃ©cessaire, puis testez les trois boutons de lâ€™app.
Activez ensuite le relais et son service dâ€™accessibilitÃ©, et autorisez le dÃ©bogage
USB local en cochant **Toujours autoriser**. Gardez le contrÃ´le du volume de la
box sur **Automatique (CEC)**.

La box reste Ã  niveau fixe ; seules les commandes destinÃ©es au volume de la tÃ©lÃ©
sont relayÃ©es. Si le CEC rÃ¨gle dÃ©jÃ  correctement le volume, ce relais est inutile
et pourrait doubler les commandes : laissez-le dÃ©sactivÃ©.

AprÃ¨s la configuration, aucun PC nâ€™est nÃ©cessaire pendant lâ€™utilisation. La tÃ©lÃ©
et la box doivent rester sur le mÃªme rÃ©seau et le dÃ©bogage doit rester activÃ©.
Selon le firmware, le dÃ©bogage peut exposer ADB au rÃ©seau local : utilisez un
rÃ©seau de confiance. La connexion de lâ€™app reste limitÃ©e Ã  la boucle locale.

Pour revenir au fonctionnement initial, mettez le relais en pause et dÃ©sactivez
son service dâ€™accessibilitÃ©. Le projet ne supprime ni ne dÃ©sactive aucune app systÃ¨me.

Licence MIT. Aucun APK officiel nâ€™est publiÃ© pour lâ€™instant ; chacun peut construire
le code source. Les retours de compatibilitÃ© matÃ©rielle aideront Ã  Ã©largir la prise
en charge sans promettre une compatibilitÃ© universelle non vÃ©rifiÃ©e.
