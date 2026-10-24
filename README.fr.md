# onn Volume Bridge

Un projet indépendant pour utiliser les boutons **+/− et Muet de la télécommande
onn avec le volume du téléviseur**, lorsque la configuration infrarouge de la box
échoue. Il fonctionne sur le réseau local et ne remplace pas le launcher.

[Documentation principale](README.md) · [Installation](docs/setup.md) · [Compatibilité](docs/compatibility.md)

## Ce qui est prêt

- Moteur de lecture des boutons onn séparé des protocoles des téléviseurs.
- Samsung Tizen : WebSocket chiffré et appairage autorisé sur la télé.
- Roku TV : commandes ECP, avec vérification qu’il s’agit d’une télé Roku.
- Sony BRAVIA : API audio JSON-RPC et clé PSK si nécessaire.
- UPnP RenderingControl : mode expérimental pour des points de contrôle compatibles.
- Configuration à la télé en français et en anglais, pause et reconnexion.
- Clés privées et identifiants chiffrés sur l’appareil ; aucune donnée personnelle publiée.

**Les commandes de volume de l’application indépendante ont été confirmées avec
une vraie télécommande onn et une Samsung avec TizenOS : appuis courts, Muet,
maintien et arrêt au relâchement. La validation complète reste à faire pour le
redémarrage, la lecture multimédia et les écouteurs. Roku, Sony et UPnP restent
expérimentaux : leurs tests de protocole ne remplacent pas un essai matériel.**

Le projet ne répare pas l’attestation du service Google et ne programme pas les
codes infrarouges. Il contourne ce chemin avec les commandes réseau du téléviseur.
Une ancienne télé uniquement infrarouge n’est donc pas automatiquement compatible.
LG webOS et d’autres protocoles pourront être ajoutés ; ils ne sont pas annoncés
comme disponibles dans cette première version.

## Installation

Construisez le projet avec JDK 17 et Android SDK 34 :

```powershell
.\gradlew.bat --no-daemon testReleaseUnitTest lintRelease assembleRelease
adb -s <box endpoint> install app/build/outputs/apk/release/app-release.apk
```

Ouvrez l’app, choisissez le protocole de votre télé et son adresse locale. Enregistrez,
autorisez l’appairage si nécessaire, puis testez les trois boutons de l’app.
Activez ensuite le relais et son service d’accessibilité, et autorisez le débogage
USB local en cochant **Toujours autoriser**. Gardez le contrôle du volume de la
box sur **Automatique (CEC)**.

La box reste à niveau fixe ; seules les commandes destinées au volume de la télé
sont relayées. Si le CEC règle déjà correctement le volume, ce relais est inutile
et pourrait doubler les commandes : laissez-le désactivé.

Après la configuration, aucun PC n’est nécessaire pendant l’utilisation. La télé
et la box doivent rester sur le même réseau et le débogage doit rester activé.
Selon le firmware, le débogage peut exposer ADB au réseau local : utilisez un
réseau de confiance. La connexion de l’app reste limitée à la boucle locale.

Pour revenir au fonctionnement initial, mettez le relais en pause et désactivez
son service d’accessibilité. Le projet ne supprime ni ne désactive aucune app système.

Licence MIT. Aucun APK officiel n’est publié pour l’instant ; chacun peut construire
le code source. Les retours de compatibilité matérielle aideront à élargir la prise
en charge sans promettre une compatibilité universelle non vérifiée.
