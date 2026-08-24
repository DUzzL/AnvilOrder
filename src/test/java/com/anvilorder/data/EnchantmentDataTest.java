package com.anvilorder.data;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnchantmentDataTest {

    private static final Set<String> COMMON = Set.of(
            "protection", "bane_of_arthropods", "efficiency", "feather_falling",
            "fire_protection", "knockback", "loyalty", "lunge", "piercing", "power",
            "projectile_protection", "quick_charge", "sharpness", "smite", "unbreaking", "density"
    );

    private static final Set<String> UNCOMMON = Set.of(
            "aqua_affinity", "blast_protection", "depth_strider", "fire_aspect", "flame",
            "fortune", "frost_walker", "impaling", "looting", "luck_of_the_sea", "lure",
            "mending", "multishot", "punch", "respiration", "riptide", "sweeping_edge",
            "breach", "wind_burst"
    );

    private static final Set<String> RARE = Set.of(
            "channeling", "infinity", "silk_touch", "soul_speed", "swift_sneak", "thorns",
            "binding_curse", "vanishing_curse"
    );

    @Test
    void coversAllMinecraft26_2EnchantmentsWithReferenceWeights() {
        Set<String> expected = new HashSet<>();
        expected.addAll(COMMON);
        expected.addAll(UNCOMMON);
        expected.addAll(RARE);

        assertEquals(43, expected.size());
        assertEquals(expected, EnchantmentData.getAllEnchantPaths());
        assertFalse(expected.contains("sweeping"));
        assertTrue(expected.contains("sweeping_edge"));

        COMMON.forEach(id -> assertEquals(1, EnchantmentData.getWeight(id), id));
        UNCOMMON.forEach(id -> assertEquals(2, EnchantmentData.getWeight(id), id));
        RARE.forEach(id -> assertEquals(4, EnchantmentData.getWeight(id), id));
    }

    @Test
    void incompatibilitiesMatchMinecraft26_2ExclusiveSetsAndAreSymmetric() {
        Map<String, Set<String>> expected = new HashMap<>();
        addExclusiveGroup(expected, "protection", "blast_protection", "fire_protection", "projectile_protection");
        addExclusiveGroup(expected, "sharpness", "smite", "bane_of_arthropods", "impaling", "density", "breach");
        addExclusiveGroup(expected, "depth_strider", "frost_walker");
        addExclusiveGroup(expected, "fortune", "silk_touch");
        addExclusiveGroup(expected, "infinity", "mending");
        addExclusivePair(expected, "riptide", "channeling");
        addExclusivePair(expected, "riptide", "loyalty");
        addExclusiveGroup(expected, "multishot", "piercing");

        for (String enchantment : EnchantmentData.getAllEnchantPaths()) {
            assertEquals(expected.getOrDefault(enchantment, Set.of()),
                    EnchantmentData.getIncompatible(enchantment), enchantment);
        }
    }

    @Test
    void itemMappingsIncludeSweepingSpearsAndCurseOnlyTargets() {
        assertTrue(EnchantmentData.getEnchantmentsForItem("diamond_sword").contains("sweeping_edge"));
        assertFalse(EnchantmentData.getEnchantmentsForItem("diamond_sword").contains("sweeping"));

        assertEquals(Set.of("sharpness", "smite", "bane_of_arthropods", "knockback",
                        "fire_aspect", "looting", "lunge", "unbreaking", "mending", "vanishing_curse"),
                EnchantmentData.getEnchantmentsForItem("netherite_spear"));

        for (String armor : List.of("diamond_helmet", "diamond_chestplate", "diamond_leggings",
                "diamond_boots", "turtle_helmet", "elytra")) {
            assertTrue(EnchantmentData.getEnchantmentsForItem(armor).contains("binding_curse"), armor);
        }

        assertTrue(EnchantmentData.getEnchantmentsForItem("shield").contains("vanishing_curse"));
        assertEquals(Set.of("binding_curse", "vanishing_curse"),
                EnchantmentData.getEnchantmentsForItem("carved_pumpkin"));
        assertEquals(Set.of("binding_curse", "vanishing_curse"),
                EnchantmentData.getEnchantmentsForItem("player_head"));
        assertEquals(Set.of("vanishing_curse"), EnchantmentData.getEnchantmentsForItem("compass"));
        assertEquals(EnchantmentData.getAllEnchantPaths(), EnchantmentData.getEnchantmentsForItem("book"));
    }

    private static void addExclusiveGroup(Map<String, Set<String>> expected, String... ids) {
        for (String id : ids) {
            Set<String> conflicts = expected.computeIfAbsent(id, ignored -> new HashSet<>());
            for (String other : ids) {
                if (!id.equals(other)) conflicts.add(other);
            }
        }
    }

    private static void addExclusivePair(Map<String, Set<String>> expected, String first, String second) {
        expected.computeIfAbsent(first, ignored -> new HashSet<>()).add(second);
        expected.computeIfAbsent(second, ignored -> new HashSet<>()).add(first);
    }
}
