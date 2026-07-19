package com.gbdhapa;

import java.util.Map;

public class EnchantmentDescriptions {
    public static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
        Map.entry("aqua_affinity", "Increases underwater mining speed."),
        Map.entry("bane_of_arthropods", "Increases damage to arthropods (spiders, bees, silverfish)."),
        Map.entry("blast_protection", "Reduces explosion damage and knockback."),
        Map.entry("breach", "Reduces the effectiveness of the target's armor."),
        Map.entry("channeling", "Summons a lightning bolt when a trident hits a target during a thunderstorm."),
        Map.entry("curse_of_binding", "Prevents removal of cursed items from armor slots."),
        Map.entry("curse_of_vanishing", "Destroys the item upon death."),
        Map.entry("depth_strider", "Increases underwater movement speed."),
        Map.entry("density", "Increases damage dealt by fall distance (Mace)."),
        Map.entry("efficiency", "Increases mining speed."),
        Map.entry("feather_falling", "Reduces fall and ender pearl teleportation damage."),
        Map.entry("fire_aspect", "Sets target on fire."),
        Map.entry("fire_protection", "Reduces fire damage and burn time."),
        Map.entry("flame", "Sets shot arrows on fire."),
        Map.entry("fortune", "Increases block drops (ores, seeds, etc.)."),
        Map.entry("frost_walker", "Freezes water into ice under the player's feet."),
        Map.entry("impaling", "Deals extra damage to aquatic mobs."),
        Map.entry("infinity", "Shoots arrows without consuming them."),
        Map.entry("knockback", "Increases knockback dealt to mobs."),
        Map.entry("looting", "Increases mob drops."),
        Map.entry("loyalty", "Trident returns after being thrown."),
        Map.entry("luck_of_the_sea", "Increases chance of getting good loot while fishing."),
        Map.entry("lure", "Decreases bite time while fishing."),
        Map.entry("mending", "Repairs item durability using experience."),
        Map.entry("multishot", "Shoots 3 arrows for the cost of 1."),
        Map.entry("piercing", "Arrows pass through entities."),
        Map.entry("power", "Increases bow arrow damage."),
        Map.entry("projectile_protection", "Reduces projectile damage (arrows, fireballs)."),
        Map.entry("protection", "Reduces most types of damage."),
        Map.entry("punch", "Increases bow knockback."),
        Map.entry("quick_charge", "Decreases crossbow reloading time."),
        Map.entry("respiration", "Extends underwater breathing time."),
        Map.entry("riptide", "Launches the player when throwing trident in water/rain."),
        Map.entry("sharpness", "Increases melee damage."),
        Map.entry("silk_touch", "Blocks drop themselves instead of their normal items."),
        Map.entry("smite", "Increases damage to undead mobs (zombies, skeletons, etc.)."),
        Map.entry("sweeping_edge", "Increases sweeping attack damage."),
        Map.entry("thorns", "Damages attackers."),
        Map.entry("unbreaking", "Decreases durability usage chance."),
        Map.entry("soul_speed", "Increases walking speed on soul sand and soul soil."),
        Map.entry("swift_sneak", "Increases movement speed while sneaking."),
        Map.entry("wind_burst", "Emits a wind burst that launches the attacker upward upon landing a hit.")
    );

    public static String get(String path) {
        return DESCRIPTIONS.get(path);
    }
}
