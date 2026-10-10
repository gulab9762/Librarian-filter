# Instant Reroll specific trade from villagers using sign on workstation

1. Place sign on workstation
2. Type few letters or full name of item/Enchantment book (without space, use _ for spaces)
3. Right click on workstation to reroll trade, villager and workstation will show green particles if success, angry if failed

# What to type on sign ? 
Normal item name like 

bread -> bread

white_wool -> White wool

(Suggestion list added in 26.1 and later versions)

# Why sign?

You cannot ask villagers because you dont know how to say in hmmmmmm, so you write in on sign
Also you want to remember which villager offer which trade once you setup trading hall right?

# Why particles?

To find villager roaming around....

# What about enchantment books? 

For enchantment books, full name or starting partial name followed by enchantment level.
If level not specified, by default gives max level enchantment book

eff -> Efficiency 5

eff 3 -> Efficiency 3

# I am still confused to figure out what to type I need whole list

Wiki for full list- https://github.com/gulab9762/Librarian-filter/wiki

# Mod not working / Need Help?

### In-Game AI Debugging Helper (Free Local LLM)
If reroll isn't working or you're confused what to type on the sign, ask the built-in AI helper directly in game chat:
- `/reroll ai why is reroll not working`
- `/reroll debug`
- `/reroll ai whats max level for eff`
- `/reroll clear` or `/reroll ai clear` (Clears cached context and reroll history so AI starts fresh)
- `/reroll ai <any question>`

The AI reads your recent in-game reroll attempts, logs, villager state, and mod config to pinpoint the exact issue (e.g. villager already traded, typo on sign, missing sign, or Trade Rebalance biome locking).

#### How to start Local AI (100% Free & Private):
1. Download Ollama from https://ollama.com
2. Open terminal/PowerShell and run:
   ```bash
   ollama run qwen2.5-coder:7b
   ```
   *(Or for lower-spec PCs: `ollama run qwen2.5-coder:1.5b` or `ollama run llama3.2`)*
3. If Ollama is not running, the mod will remind you in game chat with instructions and a link!
4. To change the model or URL, edit `config/librarian-filter.json` (`"ollamaModel"` and `"ollamaUrl"`).

Check wiki, if something new, report issues/suggestions on discord server
Discord- https://discord.gg/bWWwkFryuA

# Happy trading !! hmmmmmmm......
