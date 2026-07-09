package com.anvilorder.solver;

/**
 * A node in the binary tree of combine operations.
 * 1:1 port of the combine node structure from iamcal/enchant-order (work.js).
 *
 * Leaf nodes:    {I: id_or_name, l: value, w: work}
 * Internal nodes: {L: left_child, R: right_child, l: merge_cost, w: work, v: total_value}
 *
 * Ported from iamcal/enchant-order (MIT license).
 */
class CombineNode {
    CombineNode left;
    CombineNode right;
    /** I: enchantment ID for leaf/book nodes, or "item" for the base item */
    String label;
    /** l: for leaf nodes: the raw enchant value. For internal nodes: the merge level cost. */
    int value;
    /** v: total value (sum of all leaf values), only meaningful for internal nodes */
    int totalValue;
    /** w: prior work count */
    int work;

    /** Leaf node for a single-enchant book */
    CombineNode(int enchantId, int value, int work) {
        this.label = String.valueOf(enchantId);
        this.value = value;
        this.totalValue = 0; // not used for leaves
        this.work = work;
    }

    /** Leaf node for the base item or book-base */
    CombineNode(String label, int value, int work) {
        this.label = label;
        this.value = value;
        this.totalValue = 0; // not used for leaves
        this.work = work;
    }

    /**
     * Internal node for a merge operation.
     * @param left left child
     * @param right right child
     * @param mergeCost merge level cost (= right.l + 2**left.w - 1 + 2**right.w - 1)
     * @param totalValue total enchantment value (= left.l + right.l)
     * @param work prior work (= max(left.w, right.w) + 1)
     */
    CombineNode(CombineNode left, CombineNode right, int mergeCost, int totalValue, int work) {
        this.left = left;
        this.right = right;
        this.value = mergeCost;   // l in reference
        this.totalValue = totalValue; // v in reference
        this.work = work;
    }

    boolean isLeaf() {
        return left == null && right == null;
    }
}
