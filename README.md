# AnvilOrder für Minecraft 26.3

## Installation
- Minecraft Java Edition 26.3
- Fabric Loader 0.19.5 oder neuer
- Fabric API 0.160.5+26.3 (passend zu Minecraft 26.3)
- Java 25 oder neuer

Die Datei `anvilorder-1.0.1+26.3.jar` zusammen mit Fabric API in den `mods`-Ordner legen. Die alte AnvilOrder-JAR ersetzen. Der Mod läuft auf dem Client.

## Änderungen
- Minecraft-Zielversion auf 26.3 aktualisiert, einschließlich Test-Mod.
- Fabric Loader auf 0.19.5 und Fabric API auf 0.160.5+26.3 aktualisiert.
- Linke Maustaste der Ergebnis-Scrollleisten über `InputConstants.MOUSE_BUTTON_LEFT` abgefragt. Das berücksichtigt die mit 26.3 eingeführte SDL-Eingabe.
- Clienttest um den tatsächlichen Ambossbildschirm und einen Regressionstest für linke/rechte Maustasten an der Scrollleiste erweitert.
- Loom 1.17.19, Gradle 9.6.1 und Java 25 beibehalten; Build erfolgreich.

## Prüfung
`bash gradlew build runClientGameTest` mit Java 25 erfolgreich.
8 Unit-Tests erfolgreich. Clienttest: Amboss-Mixin, Verzauberungsauswahl, Sweeping-Edge-Levelregler und Scrollleisten-Eingabe erfolgreich.

Die 43 Vanilla-Verzauberungen wurden hinsichtlich Ambosskosten, maximalem Level, unterstützten Items und Ausschlussgruppen mit 26.2 verglichen: keine Änderungen dieser Felder. Kein umfassender Test mit anderen Mods oder auf Multiplayer-Servern.

## Quellen
- https://fabricmc.net/2026/09/15/263.html
- https://meta.fabricmc.net/v2/versions/loader/26.3
- https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml
- https://piston-meta.mojang.com/mc/game/version_manifest_v2.json

Der Quellcode basiert auf DUzzL/AnvilOrder. Änderungen wurden lokal vorgenommen und nicht auf GitHub veröffentlicht.
