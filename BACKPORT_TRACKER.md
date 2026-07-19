# Features Backport Tracker

This file serves as a reference guide for newly added features in the `26.2` (Minecraft 1.21.x+) branch. It outlines the specific files, configurations, and core logic implemented for each feature so they can be easily referenced when backporting to older Minecraft versions (like 1.20.1 or 1.20.6).

---

## 1. Treasure Enchantments Config Toggle (`allowTreasureEnchantments`)

**Description:** Allows the inclusion of treasure enchantments (Soul Speed, Swift Sneak, Wind Burst) in the reroll process if enabled by the user via the configuration screen.

### Core Configuration
* **`common/.../config/TradeConfig.java`**: Added the boolean `allowTreasureEnchantments`.

### Network Synchronization (Critical for Client-Server Parity)
Because GUI settings are strictly client-side, the config requires syncing to the server.
* **`TradeConfigUpdatePayload`**: Sent from Client -> Server when the "Save" button is clicked in the config GUI.
* **`TradeConfigSyncPayload`**: Sent from Server -> Client during the player join event (`PlayerEvent.PLAYER_JOIN`) to ensure the client has the server's authoritative config state.
* **Payload Registration**: Handled in both `LibrarianfilterFabric.java` and `LibrarianfilterNeoForge.java` initialization blocks using Architectury's `NetworkManager`.

### Reroll Logic & Injection
* **`RerollLogic.java`**: 
  * `injectTreasureEnchantments(Villager, ServerLevel)`: Called before filtering. If the config is enabled, this method programmatically inserts the treasure enchantments (up to their max level) into the villager's `MerchantOffers`. It prevents duplicates by checking if the villager already offers them.
  * `executeFind()`: Updated the `/reroll find <enchantment>` command to properly format underscore names (e.g. `soul_speed`) by replacing them with spaces so string containment works as expected. Added fallback to support finding standard items (e.g. "glass").

### Client UI & Suggestions
* **Config Screens (`FabricTradeConfigScreen.java`, `NeoForgeTradeConfigScreen.java`)**: Added a cycle button for "Allow Treasure Enchants" and updated the save logic to dispatch the `TradeConfigUpdatePayload`.
* **Command Autocomplete (`LibrarianfilterFabric.java`, `LibrarianfilterNeoForge.java`)**: Updated the `.suggests()` block for the `/reroll find` command to conditionally suggest treasure enchantment names if `TradeConfig.INSTANCE.allowTreasureEnchantments` is true.
* **Sign UI (`AbstractSignEditScreenMixin.java`)**: The sign autocomplete respects the config flag when generating the `getEnchantments()` list.
* **Descriptions (`EnchantmentDescriptions.java`)**: Added description mappings for `soul_speed`, `swift_sneak`, and `wind_burst`.

---

## 2. Creative Setup Command (`/reroll setup`)

**Description:** A creative-mode-only command that instantly generates a 3x3 glass interior room, spawns a fresh Villager, and places a Lectern with an Oak Wall Sign.

### Execution Logic
* **`RerollLogic.java`**: 
  * `executeSetup(CommandSourceStack)`: Handles the entire structure generation, entity spawning, and block placement. 
  * *Version Note:* In 1.21.x+, instantiating the villager requires using `EntityTypes.VILLAGER` (plural registry class) rather than `EntityType.VILLAGER`.

### Command Registration
* **`LibrarianfilterFabric.java` & `LibrarianfilterNeoForge.java`**: Registered inside the main command dispatcher tree under `Commands.literal("setup")` alongside the existing `start`, `stop`, and `find` commands.

---

*(Append new features below this line as they are developed)*
