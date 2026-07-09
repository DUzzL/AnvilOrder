package com.anvilorder.solver;

import java.util.List;

/**
 * Result of the enchantment ordering solver.
 */
public class SolverResult {
    /** Ordered list of combine steps */
    public final List<CombineStep> steps;
    /** Total XP levels needed */
    public final int totalLevels;
    /** Total XP points needed */
    public final int totalXp;
    /** The cost of the naive worst-case order for comparison */
    public final int naiveTotalLevels;
    /** Whether the optimization succeeded (could be empty if no enchantments) */
    public final boolean success;

    public SolverResult(List<CombineStep> steps, int totalLevels, int totalXp, int naiveTotalLevels, boolean success) {
        this.steps = steps;
        this.totalLevels = totalLevels;
        this.totalXp = totalXp;
        this.naiveTotalLevels = naiveTotalLevels;
        this.success = success;
    }

    public static SolverResult empty() {
        return new SolverResult(List.of(), 0, 0, 0, false);
    }
}
