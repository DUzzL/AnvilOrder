package com.anvilorder.solver;

/**
 * Represents a single step in the anvil combination plan.
 * Ported from the iamcal/enchant-order MIT-licensed reference implementation.
 */
public class CombineStep {
    /** Description of the left operand (e.g., "Book A" or "Sword + Books A,B") */
    public final String leftDescription;
    /** Description of the right operand */
    public final String rightDescription;
    /** XP level cost of this single combine operation */
    public final int levelCost;
    /** Total XP points cost (using vanilla XP curve) */
    public final int xpCost;
    /** Prior work penalty after this step (2^(work) - 1) */
    public final int priorWorkPenalty;
    /** Running total of level costs up to and including this step */
    public int runningTotalLevels;
    /** Running total of XP points up to and including this step */
    public int runningTotalXp;

    public CombineStep(String leftDescription, String rightDescription, int levelCost, int xpCost, int priorWorkPenalty) {
        this.leftDescription = leftDescription;
        this.rightDescription = rightDescription;
        this.levelCost = levelCost;
        this.xpCost = xpCost;
        this.priorWorkPenalty = priorWorkPenalty;
    }
}