# NeptunOptimizer 1.0.0

Eigenständiges Paper-Plugin für Minecraft 1.21.3 / Java 21. Es verändert weder ACI noch NeptunCore.

## Build

Voraussetzung: JDK 21 und Gradle 8.x.

```bash
gradle build
```

Die JAR liegt danach unter `build/libs/NeptunOptimizer-1.0.0.jar`.

## Installation

1. JAR nach `plugins/` kopieren.
2. Server vollständig neu starten.
3. Einstellungen in `plugins/NeptunOptimizer/config.yml` anpassen.

## Befehle

- `/nopt help`
- `/nopt trades` – auf einen Villager schauen (max. 6 Blöcke) und den Befehl ausführen. Setzt die vorhandenen Angebote auf 0 Nutzungen und praktisch unbegrenzte Nutzbarkeit.
- `/nopt merge` – führt die Zusammenführungen sofort aus.

## Funktionen und Grenzen

- Gleiche fallengelassene ItemStacks werden bis zur normalen Maximalgröße zusammengeführt. Die Anzahl wird als Name angezeigt (z. B. `bow x15`). Überschüssige Items bleiben als weitere Stacks liegen.
- XP-Kugeln in der Nähe werden zu einer Kugel mit addierter XP-Menge zusammengeführt.
- Konfigurierte Mob-Typen werden zu einer Mob-Entity mit `xN`-Namensanzeige zusammengeführt. Bei tödlichem Schaden wird bei einem Stack mit mehr als einem Mob ein Mob aus dem Zähler entfernt und das verbleibende Mob geheilt.
- Mob-Stacking ist eine vereinfachte Variante: Loot-/XP-Auszahlung für jedes einzelne Mitglied eines Mob-Stacks ist nicht vollständig simuliert. Für faire Farmen sollte Mob-Stacking zunächst deaktiviert werden (`mobs.enabled: false`), bis Loot-Regeln passend ergänzt wurden.
- `mobs.disable-ai: true` stoppt die KI vollständig. Die Mobs laufen dann nicht und greifen auch nicht an. Standard ist daher `false`.
- Die öffentliche Paper-API bietet keine zuverlässige Methode, Villager-Angebote neu auszulosen. Der Befehl setzt daher Nutzungen zurück und macht die vorhandenen Trades unbegrenzt nutzbar; er würfelt nicht neue Angebote.
- Chunk-Pregeneration, Sichtweite und clientseitiges Entity-Culling sind keine Funktionen dieses Plugins. Dafür sind separate Werkzeuge/Client-Mods nötig.
