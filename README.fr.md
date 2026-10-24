# onn Volume Bridge

Un projet indépendant pour utiliser les boutons **+/− et Muet de la télécommande
onn avec le volume du téléviseur**, lorsque la configuration infrarouge de la box
échoue. Il fonctionne sur le réseau local et ne remplace pas le launcher.

[Documentation principale](README.md) · [Installation](docs/setup.md) · [Compatibilité](docs/compatibility.md)

## Ce qui est prêt

- Moteur de lecture des boutons onn séparé des protocoles des téléviseurs.
- Samsung Tizen : WebSocket chiffré et appairage autorisé sur la télé.
- Roku TV : commandes ECP et maintien natif (keydown/keyup), avec vérification qu’il s’agit d’une télé Roku.
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

**[Télécharger l’APK — aperçu v0.2.0](https://github.com/simo255/onn-volume-bridge/releases/download/v0.2.0/onn-volume-bridge-v0.2.0.apk)** · [Notes et sommes de contrôle](https://github.com/simo255/onn-volume-bridge/releases/tag/v0.2.0)

Installez l’APK sur la onn, choisissez votre télé dans **Find TVs on my network**,
puis **Set up my remote automatically**. Autorisez les demandes Android et testez
la vraie télécommande. L’app active son service de volume après votre autorisation
de débogage local et vérifie le volume HDMI. Un réglage manuel reste nécessaire
uniquement si le système ne permet pas de le vérifier ou de l’appliquer.

L’app s’ouvre en anglais par défaut, même sur une box en français. Le français
reste disponible dans **Advanced options → App language**.
Les étapes de débogage et d’appairage imposées par Android et la télé ne peuvent
pas être acceptées silencieusement par l’app. Aucun PC n’est nécessaire à l’usage.
Voir le [guide de configuration](docs/setup.md) pour les détails et limites.
