package com.anvilorder.solver;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal representation of a virtual item during the combination search.
 * 1:1 port of item_obj from iamcal/enchant-order reference (work.js).
 * Ported from iamcal/enchant-order (MIT license).
 */
class VirtualItem {
    /**
     * Namespace: "book" for single-enchant books, "item" for non-book target,
     * an Integer for the book-base (most expensive book turned into base).
     * Matches reference item_obj.i.
     */
    final Object namespace;
    /** Enchantment IDs present on this item */
    final List<Integer> enchantIds;
    /** Raw enchantment cost sum (level * weight per enchant) = reference .l */
    final int value;
    /** Prior work count (0 = fresh, 1 = used once, etc.) = reference .w */
    final int work;
    /** Total XP points spent to create this item = reference .x */
    final int totalXp;
    /** For constructing combine instructions = reference .c */
    CombineNode combineNode;

    VirtualItem(Object namespace, List<Integer> enchantIds, int value, int work, int totalXp, CombineNode combineNode) {
        this.namespace = namespace;
        this.enchantIds = enchantIds;
        this.value = value;
        this.work = work;
        this.totalXp = totalXp;
        this.combineNode = combineNode;
    }

    /** True if this is the non-book target item (namespace == "item"). Matches reference: i === 'item' */
    boolean isTargetItem() {
        return "item".equals(namespace);
    }

    /** Create a fresh single-enchant book */
    static VirtualItem freshBook(int enchantId, int value) {
        List<Integer> ids = new ArrayList<>();
        ids.add(enchantId);
        CombineNode leaf = new CombineNode(enchantId, value, 0);
        return new VirtualItem("book", ids, value, 0, 0, leaf);
    }

    /** Create a fresh base item (the target item with zero enchants) */
    static VirtualItem freshBaseItem() {
        List<Integer> ids = new ArrayList<>();
        CombineNode leaf = new CombineNode("item", 0, 0);
        return new VirtualItem("item", ids, 0, 0, 0, leaf);
    }

    /**
     * Merge two virtual items on the anvil. Returns null if too expensive (>39 levels).
     * Matches reference MergeEnchants constructor.
     */
    static VirtualItem merge(VirtualItem left, VirtualItem right) {
        int priorWorkLeft = (int) Math.pow(2, left.work) - 1;
        int priorWorkRight = (int) Math.pow(2, right.work) - 1;
        int mergeLevelCost = right.value + priorWorkLeft + priorWorkRight;
        if (mergeLevelCost > 39) {
            return null;
        }

        int newValue = left.value + right.value;         // reference: this.l
        List<Integer> newIds = new ArrayList<>(left.enchantIds);
        newIds.addAll(right.enchantIds);                  // reference: this.e
        int newWork = Math.max(left.work, right.work) + 1; // reference: this.w
        int newTotalXp = left.totalXp + right.totalXp + experienceFromLevels(mergeLevelCost); // reference: this.x

        // Match reference combineNode shape:
        // Leaf:   {I: id, l: value, w: work}
        // Internal: {L: left.c, R: right.c, l: merge_cost, w: work, v: total_value}
        CombineNode newNode = new CombineNode(left.combineNode, right.combineNode, mergeLevelCost, newValue, newWork);

        // reference: super(left.i, new_value) — always uses left's namespace
        return new VirtualItem(left.namespace, newIds, newValue, newWork, newTotalXp, newNode);
    }

    /**
     * Convert anvil level cost to XP points, matching vanilla's experience curve.
     * Matches reference experience() function.
     */
    static int experienceFromLevels(int levels) {
        if (levels == 0) return 0;
        if (levels <= 16) {
            return levels * levels + 6 * levels;
        } else if (levels <= 31) {
            return (int) (2.5 * levels * levels - 40.5 * levels + 360);
        } else {
            return (int) (4.5 * levels * levels - 162.5 * levels + 2220);
        }
    }
}
