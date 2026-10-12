# AGENTS.md - Librarian Filter Project Context & Developer Guide

## 1. Project Overview

**Librarian Filter** (`librarian_filter`) is a Minecraft quality-of-life mod designed to remove the tedious grinding from villager trading halls. 

### Core Gameplay Mechanic:
1. The player places an oak/wall sign attached to or directly adjacent to a **Lectern** (librarian workstation).
2. The player writes an enchantment name or short keyword on the sign (e.g., `eff`, `mending`, `unbreaking 3`, `fire_protection 4`).
3. The player right-clicks the lectern with an empty hand / main hand.
4. The mod searches for the assigned villager, matches the enchantment using smart fuzzy logic, verifies that the villager has not been traded with at their current level, updates the villager's trade to the requested enchanted book, and locks it to the **absolute lowest possible vanilla emerald cost** (e.g., Efficiency 5 for 17 emeralds, Mending for 10 emeralds).
5. Green happy particles and chime sounds trigger on success; angry villager particles and chat explanations trigger on failure.

---

## 2. Environment & Toolchain

| Component | Specification |
| :--- | :--- |
| **Minecraft Target** | `26.2` (Tracked via official Mojang mappings) |
| **Java Toolchain** | Java 25 (`JavaLanguageVersion.of(25)`, `release = 25`) |
| **Build System** | Gradle (`gradlew`, Gradle Wrapper) |
| **Forge Loader** | MinecraftForge `65.1.3`, ForgeGradle `7.0.40` |
| **Fabric / Loom** | Fabric Loom `1.17-SNAPSHOT`, Fabric API `0.154.2+26.2`, Loader `0.19.3` |
| **NeoForge Loader** | NeoForge `26.2.0.8-beta`, ModDev `2.0.141` |
| **Mod ID** | `librarian_filter` |
| **Maven Group** | `com.gbdhapa` |

---

## 3. Git Branching Strategy & Repository Architecture

This repository uses feature- and loader-specialized branches. When working with code or introducing features, always be aware of the active branch:

- **`26.2-forge-only` (Current Active Branch)**:
  - Focused strictly on **MinecraftForge**.
  - Root `settings.gradle` includes `:common` and `:forge`.
  - `:forge` pulls in sources from `:common` directly during compilation (`compileJava.source(project(":common").sourceSets.main.allSource)`).
- **`26.2` (Multi-Loader Root)**:
  - Multi-platform Architectury-style setup targeting Fabric, NeoForge, and Quilt.
  - Contains client mixins (`AbstractSignEditScreenMixin`, `CameraMixin`), cross-platform network payload syncing (`TradeConfigSyncPayload`, `TradeConfigUpdatePayload`), and in-game config GUIs (`FabricTradeConfigScreen`, `NeoForgeTradeConfigScreen`).
- **`26.2-ai-debug`**:
  - Adds an in-game local LLM helper (`/reroll ai`, `/reroll debug`) powered by Ollama (`qwen2.5-coder:7b` / `llama3.2`).
  - Reads in-game reroll failures and recent logs to help players diagnose why a reroll did not trigger.
- **Backport Branches (`1.20.1-*`, `1.20.6-*`, `1.21.11-*`, `26.1-*`)**:
  - Historical Minecraft version ports. Check [`BACKPORT_TRACKER.md`](file:///c:/Users/Gulab/Downloads/Projects/Librarian-filter/BACKPORT_TRACKER.md) for version-specific changes (e.g., `EntityType.VILLAGER` vs `EntityTypes.VILLAGER`, registry lookups, network payload APIs).

---

## 4. Codebase Architecture & Key Classes

### A. Common Module (`common/src/main/java/com/gbdhapa/`)

- [`Constants.java`](file:///c:/Users/Gulab/Downloads/Projects/Librarian-filter/common/src/main/java/com/gbdhapa/Constants.java):
  - Defines `MOD_ID = "librarian_filter"`, `MOD_NAME = "Librarian Filter"`, and SLF4J logger `LOG`.

- [`CommonClass.java`](file:///c:/Users/Gulab/Downloads/Projects/Librarian-filter/common/src/main/java/com/gbdhapa/CommonClass.java):
  - Common initialization routine called by loader entrypoints.

- [`LibrarianCore.java`](file:///c:/Users/Gulab/Downloads/Projects/Librarian-filter/common/src/main/java/com/gbdhapa/LibrarianCore.java):
  - **`handleRightClick(Player, Level, BlockPos, InteractionHand)`**:
    - Triggered when a player interacts with a block.
    - Validates main-hand, lectern block, attached sign, assigned villager (`findAssignedVillager` checking `MemoryModuleType.JOB_SITE` within 48 blocks), and trading status (`hasTradedForCurrentLevel`).
  - **`findAttachedSign(Level, BlockPos, BlockState)`**:
    - Scans facing directions, opposite facing, all horizontal neighbors, above, and below the lectern for a `SignBlockEntity` containing text.
  - **`findMatchingEnchantment(ServerLevel, List<String>)`**:
    - Parses sign text, handles regex level detection (`^(.*?)[\\s\\-_:]+(\\d+)$`), and matches against registry enchantments.
  - **`findEnchantmentHolder(List<Holder.Reference<Enchantment>>, String)`**:
    - 4-tier fuzzy matching priority:
      1. Exact match (registry path, spaced, clean alphanumeric, description, or resource location).
      2. Direct prefix match (`eff` -> `efficiency`).
      3. Clean alphanumeric prefix match.
      4. Substring fallback.
  - **Lowest Price Guarantee (`LOWEST_PRICES_FOR_MAX_LEVEL` & `getLowestPrice`)**:
    - Hardcoded optimal lowest emerald costs for max-level enchantments based on vanilla formula `2 + 3 * level` (doubled for treasure/curse tags: `EnchantmentTags.DOUBLE_TRADE_PRICE`).
  - **`applyEnchantedBookTrade(Villager, Holder.Reference<Enchantment>, int)`**:
    - Generates enchanted book `ItemStack`, replaces or adds the trade to the villager's `MerchantOffers`, and sets the cost.

- [`LibrarianCommands.java`](file:///c:/Users/Gulab/Downloads/Projects/Librarian-filter/common/src/main/java/com/gbdhapa/LibrarianCommands.java):
  - Registers the `/reroll` command tree with Brigadier:
    - `/reroll`: Explains command usage.
    - `/reroll list [page]`: Interactive clickable chat list of all available enchantments, recommended sign keywords, and minimum emerald costs.
    - `/reroll find <enchantment>`: Finds any librarian within 96 blocks selling the requested book and highlights them with the `GLOWING` effect for 30 seconds.
    - `/reroll clear`: Removes glowing effects from previously highlighted villagers, scoped per-player (`GLOWING_VILLAGERS_BY_PLAYER`).

### B. Forge Loader (`forge/src/main/java/com/gbdhapa/forge/`)

- [`LibrarianfilterForge.java`](file:///c:/Users/Gulab/Downloads/Projects/Librarian-filter/forge/src/main/java/com/gbdhapa/forge/LibrarianfilterForge.java):
  - `@Mod("librarian_filter")` entrypoint.
  - Registers `RegisterCommandsEvent` -> `LibrarianCommands.register`.
  - Registers `PlayerInteractEvent.RightClickBlock` -> `LibrarianCore.handleRightClick`.

---

## 5. Development & Build Commands

Run the following commands from the repository root in PowerShell:

### Build Forge Jar:
```powershell
.\gradlew.bat :forge:build
```

### Run Client in Development:
```powershell
.\gradlew.bat :forge:runClient
```

### Clean Build Cache:
```powershell
.\gradlew.bat clean
```

---

## 6. Coding & Modification Guidelines for Agents

1. **Maintain Clean Common / Loader Separation**:
   - Keep shared logic in `common/` (`LibrarianCore`, `LibrarianCommands`). Do not import loader-specific classes (`net.minecraftforge.*`, `net.fabricmc.*`) into `common/`.
2. **Handle Null Safety in Registry Access**:
   - Modern Minecraft (1.21 / 26.x) uses dynamic registries (`Registries.ENCHANTMENT`) and `Holder<Enchantment>`. Never assume static registry access; use `server.registryAccess().lookupOrThrow(...)` or `level.registryAccess()`.
3. **Preserve Anti-Exploit Integrity**:
   - Always verify `hasTradedForCurrentLevel` before modifying trades. Players should not be allowed to reroll a villager once XP has been gained at their current tier.
4. **Coordinate Feature Backports**:
   - When introducing new mechanics or config toggles, update [`BACKPORT_TRACKER.md`](file:///c:/Users/Gulab/Downloads/Projects/Librarian-filter/BACKPORT_TRACKER.md) with configuration names, network packet requirements, and registry differences.
