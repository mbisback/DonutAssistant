# Donut Assistant (stage 1)

Client-only Fabric project for Minecraft Java 26.2, Java 25, official Mojang mappings.

## Current foundation
- Public game-chat transaction parser with whisper/DM exclusions.
- Tracking toggle, transaction totals, bounded recent log (100), persistent JSON.
- Compact summary HUD and a clean custom tracker window.
- Multiline text cards with configurable foreground/background colours and a gradient-ready flag.
- Fabric keybinds: Right Shift opens the GUI. Tracking/HUD toggles can be bound in Controls.

This is a staged foundation, not a claim that every planned screen/feature is complete. It does not include gambling-host/rakeback/bet-streak features. Transaction parsing depends on the server's public chat wording and must be checked against actual server messages.

## Build
Requires JDK 25 and internet access for Gradle dependencies.
- Windows: `gradlew.bat build`
- Linux/macOS: `./gradlew build`

The workflow `.github/workflows/build.yml` also builds with JDK 25 and uploads the JAR as an Actions artifact.
