package com.anvilorder.data;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.*;

/**
 * Bridges vanilla Minecraft enchantment data to the solver.
 *
 * Weights and incompatibilities are hardcoded because vanilla doesn't expose
 * these programmatically. The data mirrors iamcal/enchant-order's data.js.
 *
 * MC 26.1+ uses Mojang class names: ResourceKey<Enchantment> instead of
 * RegistryKey, BuiltInRegistries instead of Registries, Holder instead of
 * RegistryEntry.
 */
public class EnchantmentData {

    /** Enchantment weights: 1=common, 2=uncommon, 4=rare, 8=very rare. */
    private static final Map<String, Integer> WEIGHTS = new HashMap<>();
    /** Incompatible enchantment groups. */
    private static final Map<String, Set<String>> INCOMPATIBLE = new HashMap<>();
    /** Which enchantments are valid for which item types. */
    private static final Map<String, Set<String>> ITEM_ENCHANTS = new HashMap<>();

    /** Maps enchantment ID string to its ResourceKey — lazily initialized */
    private static Map<String, ResourceKey<Enchantment>> ENCHANT_KEYS;
    private static boolean keysInitialized = false;

    static {
        initWeights();
        initIncompatibilities();
        initItemEnchants();
    }

    // ---- Enchantment weights (rarity-based cost multipliers) ----
    private static void initWeights() {
        // Common (1)
        WEIGHTS.put("protection", 1);
        WEIGHTS.put("bane_of_arthropods", 1);
        WEIGHTS.put("efficiency", 1);
        WEIGHTS.put("feather_falling", 1);
        WEIGHTS.put("fire_protection", 1);
        WEIGHTS.put("knockback", 1);
        WEIGHTS.put("loyalty", 1);
        WEIGHTS.put("lunge", 1);
        WEIGHTS.put("piercing", 1);
        WEIGHTS.put("power", 1);
        WEIGHTS.put("projectile_protection", 1);
        WEIGHTS.put("quick_charge", 1);
        WEIGHTS.put("sharpness", 1);
        WEIGHTS.put("smite", 1);
        WEIGHTS.put("unbreaking", 1);
        WEIGHTS.put("density", 1);

        // Uncommon (2)
        WEIGHTS.put("aqua_affinity", 2);
        WEIGHTS.put("blast_protection", 2);
        WEIGHTS.put("depth_strider", 2);
        WEIGHTS.put("fire_aspect", 2);
        WEIGHTS.put("flame", 2);
        WEIGHTS.put("fortune", 2);
        WEIGHTS.put("frost_walker", 2);
        WEIGHTS.put("impaling", 2);
        WEIGHTS.put("looting", 2);
        WEIGHTS.put("luck_of_the_sea", 2);
        WEIGHTS.put("lure", 2);
        WEIGHTS.put("mending", 2);
        WEIGHTS.put("multishot", 2);
        WEIGHTS.put("punch", 2);
        WEIGHTS.put("respiration", 2);
        WEIGHTS.put("riptide", 2);
        WEIGHTS.put("sweeping", 2);
        WEIGHTS.put("breach", 2);
        WEIGHTS.put("wind_burst", 2);

        // Rare (4)
        WEIGHTS.put("channeling", 4);
        WEIGHTS.put("infinity", 4);
        WEIGHTS.put("silk_touch", 4);
        WEIGHTS.put("soul_speed", 4);
        WEIGHTS.put("swift_sneak", 4);
        WEIGHTS.put("thorns", 4);
        WEIGHTS.put("binding_curse", 4);
        WEIGHTS.put("vanishing_curse", 4);
    }

    // ---- Incompatibilities ----
    private static void initIncompatibilities() {
        addConflict("protection", "blast_protection", "fire_protection", "projectile_protection");
        addConflict("blast_protection", "fire_protection", "protection", "projectile_protection");
        addConflict("fire_protection", "blast_protection", "protection", "projectile_protection");
        addConflict("projectile_protection", "protection", "blast_protection", "fire_protection");

        addConflict("sharpness", "smite", "bane_of_arthropods");
        addConflict("smite", "sharpness", "bane_of_arthropods", "density", "breach");
        addConflict("bane_of_arthropods", "smite", "sharpness", "density", "breach");

        addConflict("depth_strider", "frost_walker");
        addConflict("frost_walker", "depth_strider");

        addConflict("fortune", "silk_touch");
        addConflict("silk_touch", "fortune");

        addConflict("infinity", "mending");
        addConflict("mending", "infinity");

        addConflict("channeling", "riptide");
        addConflict("riptide", "channeling", "loyalty");
        addConflict("loyalty", "riptide");

        addConflict("multishot", "piercing");
        addConflict("piercing", "multishot");

        addConflict("density", "breach", "smite", "bane_of_arthropods");
        addConflict("breach", "density", "smite", "bane_of_arthropods");
    }

    private static void addConflict(String enchant, String... conflicts) {
        INCOMPATIBLE.computeIfAbsent(enchant, k -> new HashSet<>()).addAll(Arrays.asList(conflicts));
    }

    // ---- Item-to-enchantment mapping ----
    // These mirror data.js items arrays. We use vanilla item simple names.
    private static void initItemEnchants() {
        addItemEnchants("helmet", "protection", "blast_protection", "fire_protection", "projectile_protection",
                "aqua_affinity", "respiration", "thorns", "unbreaking", "mending",
                "binding_curse", "vanishing_curse");
        addItemEnchants("chestplate", "protection", "blast_protection", "fire_protection", "projectile_protection",
                "thorns", "unbreaking", "mending", "vanishing_curse");
        addItemEnchants("leggings", "protection", "blast_protection", "fire_protection", "projectile_protection",
                "thorns", "unbreaking", "mending", "swift_sneak", "vanishing_curse");
        addItemEnchants("boots", "protection", "blast_protection", "fire_protection", "projectile_protection",
                "feather_falling", "depth_strider", "frost_walker", "thorns", "unbreaking",
                "mending", "soul_speed", "vanishing_curse");
        addItemEnchants("turtle_helmet", "protection", "blast_protection", "fire_protection", "projectile_protection",
                "aqua_affinity", "respiration", "thorns", "unbreaking", "mending",
                "vanishing_curse");

        addItemEnchants("sword", "sharpness", "smite", "bane_of_arthropods", "knockback",
                "fire_aspect", "looting", "sweeping", "unbreaking", "mending", "vanishing_curse");
        addItemEnchants("axe", "sharpness", "smite", "bane_of_arthropods", "efficiency",
                "fortune", "silk_touch", "unbreaking", "mending", "vanishing_curse");
        addItemEnchants("mace", "density", "breach", "smite", "bane_of_arthropods",
                "fire_aspect", "wind_burst", "unbreaking", "mending", "vanishing_curse");

        addItemEnchants("pickaxe", "efficiency", "fortune", "silk_touch", "unbreaking", "mending", "vanishing_curse");
        addItemEnchants("shovel", "efficiency", "fortune", "silk_touch", "unbreaking", "mending", "vanishing_curse");
        addItemEnchants("hoe", "efficiency", "fortune", "silk_touch", "unbreaking", "mending", "vanishing_curse");

        addItemEnchants("bow", "power", "punch", "flame", "infinity", "unbreaking", "mending", "vanishing_curse");
        addItemEnchants("crossbow", "quick_charge", "multishot", "piercing", "unbreaking", "mending", "vanishing_curse");

        addItemEnchants("trident", "loyalty", "channeling", "riptide", "impaling", "unbreaking", "mending", "vanishing_curse");

        addItemEnchants("fishing_rod", "luck_of_the_sea", "lure", "unbreaking", "mending", "vanishing_curse");

        addItemEnchants("shears", "efficiency", "unbreaking", "mending", "vanishing_curse");
        addItemEnchants("shield", "unbreaking", "mending");
        addItemEnchants("elytra", "unbreaking", "mending", "vanishing_curse");
        addItemEnchants("brush", "unbreaking", "mending", "vanishing_curse");
        addItemEnchants("flint_and_steel", "unbreaking", "mending", "vanishing_curse");
        addItemEnchants("carrot_on_a_stick", "unbreaking", "mending", "vanishing_curse");
        addItemEnchants("warped_fungus_on_a_stick", "unbreaking", "mending", "vanishing_curse");

        // Allow books to have any enchantment
        addItemEnchants("book", WEIGHTS.keySet().toArray(new String[0]));
    }

    private static void addItemEnchants(String itemName, String... enchants) {
        ITEM_ENCHANTS.computeIfAbsent(itemName, k -> new HashSet<>()).addAll(Arrays.asList(enchants));
    }

    // ---- ResourceKey mapping ----
    private static void ensureKeysInitialized() {
        if (keysInitialized) return;
        ENCHANT_KEYS = new HashMap<>();

        // MC 26.2+: Enchantments are data-driven, get registry from Minecraft instance
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null) return;
        Registry<Enchantment> enchantRegistry = mc.getConnection().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);

        for (Map.Entry<ResourceKey<Enchantment>, Enchantment> entry : enchantRegistry.entrySet()) {
            String path = entry.getKey().identifier().getPath();
            if (WEIGHTS.containsKey(path)) {
                ENCHANT_KEYS.put(path, entry.getKey());
            }
        }
        keysInitialized = true;
    }

    // ---- Public API ----

    /** Get the weight (cost multiplier) for an enchantment by its registry key path. */
    public static int getWeight(String enchantPath) {
        return WEIGHTS.getOrDefault(enchantPath, 2);
    }

    /** Get the maximum level for an enchantment. */
    public static int getMaxLevel(Holder<Enchantment> enchant) {
        return enchant.value().getMaxLevel();
    }

    /** Get the names of enchantments incompatible with the given one. */
    public static Set<String> getIncompatible(String enchantPath) {
        return INCOMPATIBLE.getOrDefault(enchantPath, Set.of());
    }

    /** Check if two enchantment paths are compatible. */
    public static boolean areCompatible(String a, String b) {
        Set<String> conflicts = INCOMPATIBLE.get(a);
        return conflicts == null || !conflicts.contains(b);
    }

    /** Get all enchantment paths applicable to a given item by its simple name. */
    public static Set<String> getEnchantmentsForItem(String itemName) {
        // Try exact match first, then strip material prefixes (e.g. diamond_pickaxe -> pickaxe)
        Set<String> result = ITEM_ENCHANTS.get(itemName);
        if (result != null) return result;
        // Strip known material prefix (e.g. diamond_, iron_, golden_, netherite_, stone_, wooden_, leather_, chainmail_)
        int underscoreIdx = itemName.indexOf('_');
        if (underscoreIdx > 0) {
            String baseName = itemName.substring(underscoreIdx + 1);
            result = ITEM_ENCHANTS.get(baseName);
            if (result != null) return result;
        }
        return Set.of();
    }

    /** Get the ResourceKey for an enchantment by its path string. */
    public static Optional<ResourceKey<Enchantment>> getEnchantKey(String path) {
        ensureKeysInitialized();
        return Optional.ofNullable(ENCHANT_KEYS.get(path));
    }

    /** Get the Holder for an enchantment by its path string. */
    public static Optional<Holder<Enchantment>> getEnchantEntry(String path) {
        ensureKeysInitialized();
        ResourceKey<Enchantment> key = ENCHANT_KEYS.get(path);
        if (key == null) return Optional.empty();

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null) return Optional.empty();
        Registry<Enchantment> enchantRegistry = mc.getConnection().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);
        return enchantRegistry.get(key).map(Holder.class::cast);
    }

    /** Get all known enchantment paths with weights. */
    public static Set<String> getAllEnchantPaths() {
        return WEIGHTS.keySet();
    }

    /** Determine the simple item name from an Item. */
    public static String getSimpleItemName(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }
}
