package com.anvilorder.solver;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnchantmentSolverTest {

    @Test
    void singleStepIncludesXpInSummary() {
        SolverResult result = EnchantmentSolver.solve(false,
                List.of(enchant(0, 5, 1)),
                Map.of(0, "sharpness 5"));

        assertResult(result, List.of(5), List.of(55), List.of(1), 5, 55);
        assertTrue(result.steps.getFirst().rightDescription.contains("Sharpness 5"));
    }

    @Test
    void twoEnchantmentsMatchUpstreamGoldenResult() {
        SolverResult result = EnchantmentSolver.solve(false,
                List.of(enchant(0, 5, 1), enchant(1, 1, 2)),
                Map.of(0, "sharpness 5", 1, "mending"));

        assertResult(result, List.of(5, 3), List.of(55, 27), List.of(1, 3), 8, 82);
    }

    @Test
    void sevenEnchantmentsMatchUpstreamGoldenResult() {
        SolverResult result = EnchantmentSolver.solve(false,
                List.of(
                        enchant(0, 5, 1),
                        enchant(1, 3, 2),
                        enchant(2, 3, 1),
                        enchant(3, 1, 2),
                        enchant(4, 3, 2),
                        enchant(5, 2, 1),
                        enchant(6, 2, 2)
                ),
                Map.of(
                        0, "sharpness 5",
                        1, "looting 3",
                        2, "unbreaking 3",
                        3, "mending",
                        4, "sweeping_edge 3",
                        5, "knockback 2",
                        6, "fire_aspect 2"
                ));

        assertResult(result,
                List.of(6, 3, 11, 2, 11, 2, 14),
                List.of(72, 27, 187, 16, 187, 16, 280),
                List.of(1, 1, 3, 1, 7, 1, 15),
                49, 785);
    }

    @Test
    void enchantedBookCombinationMatchesUpstreamGoldenResult() {
        SolverResult result = EnchantmentSolver.solve(true,
                List.of(enchant(0, 5, 1), enchant(1, 3, 2), enchant(2, 1, 2)),
                Map.of(0, "sharpness 5", 1, "looting 3", 2, "mending"));

        assertResult(result, List.of(5, 3), List.of(55, 27), List.of(1, 3), 8, 82);
    }

    @Test
    void experienceCurveMatchesUpstreamAtPiecewiseBoundaries() {
        assertEquals(0, VirtualItem.experienceFromLevels(0));
        assertEquals(7, VirtualItem.experienceFromLevels(1));
        assertEquals(352, VirtualItem.experienceFromLevels(16));
        assertEquals(394, VirtualItem.experienceFromLevels(17));
        assertEquals(1507, VirtualItem.experienceFromLevels(31));
        assertEquals(1628, VirtualItem.experienceFromLevels(32));
        assertEquals(2727, VirtualItem.experienceFromLevels(39));
    }

    private static EnchantmentSolver.EnchantInput enchant(int id, int level, int weight) {
        return new EnchantmentSolver.EnchantInput(id, level, weight);
    }

    private static void assertResult(SolverResult result, List<Integer> levelCosts,
            List<Integer> xpCosts, List<Integer> penalties, int totalLevels, int totalXp) {
        assertTrue(result.success);
        assertEquals(levelCosts, result.steps.stream().map(step -> step.levelCost).toList());
        assertEquals(xpCosts, result.steps.stream().map(step -> step.xpCost).toList());
        assertEquals(penalties, result.steps.stream().map(step -> step.priorWorkPenalty).toList());
        assertEquals(totalLevels, result.totalLevels);
        assertEquals(totalXp, result.totalXp);
    }
}
