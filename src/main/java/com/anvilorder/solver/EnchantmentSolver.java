package com.anvilorder.solver;

import java.util.*;

/**
 * Brute-force enchantment ordering solver.
 * 1:1 port of iamcal/enchant-order reference (work.js).
 * Finds the cheapest sequence of anvil combines for a given target item
 * and set of desired enchantment (level, weight) pairs.
 *
 * Ported from iamcal/enchant-order (MIT license).
 */
public class EnchantmentSolver {

    private static final int MAX_MERGE_LEVELS = 39;

    public record EnchantInput(int enchantId, int level, int weight) {}

    /**
     * Solve for the optimal combination order.
     *
     * @param isBook true if the target item is a book (not a tool/weapon/armor)
     * @param enchants list of desired enchantments, each with id, level, and weight
     * @param nameMap map from enchant ID to display name (enchant path), or null
     * @return the optimal SolverResult
     */
    public static SolverResult solve(boolean isBook, List<EnchantInput> enchants, Map<Integer, String> nameMap) {
        if (enchants.isEmpty()) return SolverResult.empty();

        // Build enchant ID to weight lookup
        Map<Integer, Integer> weightMap = new HashMap<>();
        for (EnchantInput e : enchants) weightMap.put(e.enchantId, e.weight);

        // === Reference: build enchant_objs ===
        // Reference: e_obj = new item_obj('book', level*weight, [id])
        //            e_obj.c = {I: id, l: e_obj.l, w: e_obj.w}
        List<VirtualItem> enchantObjs = new ArrayList<>();
        for (EnchantInput e : enchants) {
            int value = e.level * weightMap.get(e.enchantId);
            VirtualItem book = VirtualItem.freshBook(e.enchantId, value);
            enchantObjs.add(book);
        }

        // === Reference: find most expensive ===
        int mostIdx = 0;
        int mostVal = 0;
        for (int i = 0; i < enchantObjs.size(); i++) {
            if (enchantObjs.get(i).value > mostVal) { mostVal = enchantObjs.get(i).value; mostIdx = i; }
        }

        // === Reference: build base item ===
        VirtualItem item;
        if (isBook) {
            // Reference: id = enchant_objs[mostExpensive].e[0]
            //            item = new item_obj(id, enchant_objs[mostExpensive].l)
            //            item.e.push(id)
            VirtualItem top = enchantObjs.remove(mostIdx);
            int bookBaseId = top.enchantIds.get(0);
            item = new VirtualItem(bookBaseId, // namespace is Integer (enchant ID)
                    new ArrayList<>(top.enchantIds), top.value, 0, 0,
                    top.combineNode); // reuse leaf {I: id, l: value, w: 0}

            // Reference: recompute most expensive from remaining
            mostIdx = 0;
            mostVal = 0;
            for (int i = 0; i < enchantObjs.size(); i++) {
                if (enchantObjs.get(i).value > mostVal) { mostVal = enchantObjs.get(i).value; mostIdx = i; }
            }
        } else {
            // Reference: item = new item_obj('item')
            item = VirtualItem.freshBaseItem();
        }

        // Reference: handle empty remaining list
        if (enchantObjs.isEmpty()) {
            return singleStepResult(item, null, weightMap, isBook, nameMap);
        }

        // === Reference: first merge with most expensive remaining ===
        VirtualItem toMerge = enchantObjs.remove(mostIdx);
        VirtualItem mergedItem = mergeOrNull(item, toMerge);
        if (mergedItem == null) return SolverResult.empty();

        // Reference: merged_item.c.L = {I: item.i, l: 0, w: 0}
        // Override left child with a fresh leaf (value=0, work=0)
        mergedItem.combineNode.left = new CombineNode(
                isBook ? String.valueOf(item.namespace) : "item",
                0, 0);

        // Reference: handle no more remaining
        if (enchantObjs.isEmpty()) {
            return singleStepResult(mergedItem, mergedItem.combineNode, weightMap, isBook, nameMap);
        }

        // === Reference: all_objs = enchant_objs.concat(merged_item) ===
        List<VirtualItem> allObjs = new ArrayList<>(enchantObjs);
        allObjs.add(mergedItem);

        // === Reference: cheapest_items = cheapestItemsFromList(all_objs) ===
        resultsCache.clear();
        Map<Integer, VirtualItem> workToItem = cheapestItemsFromList(allObjs);

        // Reference: pick cheapest by item.x (totalXp)
        VirtualItem cheapest = null;
        int cheapestCost = Integer.MAX_VALUE;
        for (VirtualItem vi : workToItem.values()) {
            if (vi.totalXp < cheapestCost) { cheapestCost = vi.totalXp; cheapest = vi; }
        }
        if (cheapest == null) return SolverResult.empty();

        // === Reference: getInstructions(cheapest_item.c) ===
        Map<Integer, String> nm = nameMap != null ? nameMap : Map.of();
        List<CombineStep> steps = extractInstructions(cheapest.combineNode, weightMap, isBook, nm);
        if (steps.isEmpty()) return SolverResult.empty();

        int tl = 0, tx = 0;
        for (CombineStep s : steps) { tl += s.levelCost; tx += s.xpCost; }
        return new SolverResult(steps, tl, tx, 0, true);
    }

    /** Wrapper: merge with book semantics — always merge left=target, right=sacrifice. */
    private static VirtualItem mergeOrNull(VirtualItem left, VirtualItem right) {
        return VirtualItem.merge(left, right);
    }

    private static SolverResult singleStepResult(VirtualItem item, CombineNode node,
            Map<Integer, Integer> weightMap, boolean isBook, Map<Integer, String> nameMap) {
        if (node == null) return new SolverResult(List.of(), 0, 0, 0, false);
        Map<Integer, String> nm = nameMap != null ? nameMap : Map.of();
        List<CombineStep> steps = extractInstructions(node, weightMap, isBook, nm);
        if (steps.isEmpty()) return SolverResult.empty();
        int tl = 0;
        for (CombineStep s : steps) tl += s.levelCost;
        return new SolverResult(steps, tl, 0, 0, true);
    }

    // ---- Memoization cache ----
    private static final Map<String, Map<Integer, VirtualItem>> resultsCache = new HashMap<>();

    /**
     * Hash an item list for memoization.
     * Matches reference hashFromItem: [item.i[0], sorted_ids, work]
     * For Integer namespace (book-base), item.i[0] is undefined in JS → map to '0'.
     */
    private static String hashItemList(List<VirtualItem> items) {
        List<String> parts = new ArrayList<>();
        for (VirtualItem item : items) {
            List<Integer> sortedIds = new ArrayList<>(item.enchantIds);
            Collections.sort(sortedIds);
            char ns = item.namespace instanceof String s ? s.charAt(0) : '0';
            parts.add(ns + ":" + sortedIds + ":" + item.work);
        }
        Collections.sort(parts);
        return String.join("|", parts);
    }

    // ---- Core algorithm (1:1 reference port) ----

    /**
     * Matches reference cheapestItemsFromList (memoized).
     */
    private static Map<Integer, VirtualItem> cheapestItemsFromList(List<VirtualItem> items) {
        String key = hashItemList(items);
        if (resultsCache.containsKey(key)) return resultsCache.get(key);

        Map<Integer, VirtualItem> result;
        int n = items.size();

        if (n == 1) {
            // Reference case 1: return {work: item}
            result = new HashMap<>();
            VirtualItem item = items.get(0);
            result.put(item.work, item);
        } else if (n == 2) {
            // Reference: calls cheapestItemFromItems2(left, right)
            result = new HashMap<>();
            VirtualItem cheapest = cheapestItemFromItems2(items.get(0), items.get(1));
            result.put(cheapest.work, cheapest);
        } else {
            // Reference default: cheapestItemsFromListN(items, floor(n/2))
            result = cheapestItemsFromListN(items, n / 2);
        }

        resultsCache.put(key, result);
        return result;
    }

    /**
     * Matches reference cheapestItemFromItems2.
     * If right is target item, swap. If left is target item, merge left+right only.
     * Otherwise try both orderings, compare via compareCheapest, pick first work.
     */
    private static VirtualItem cheapestItemFromItems2(VirtualItem leftItem, VirtualItem rightItem) {
        if (rightItem.isTargetItem()) {
            return mergeOrNull(rightItem, leftItem);
        }
        if (leftItem.isTargetItem()) {
            return mergeOrNull(leftItem, rightItem);
        }

        // Neither is target — try both orderings
        VirtualItem normalObj = mergeOrNull(leftItem, rightItem);
        VirtualItem reversedObj = mergeOrNull(rightItem, leftItem);

        if (normalObj != null && reversedObj != null) {
            Map<Integer, VirtualItem> cheapestW2I = compareCheapest(normalObj, reversedObj);
            // Reference: const prior_works = Object.keys(cheapest_work2item);
            //            const prior_work = prior_works[0];
            //            return cheapest_work2item[prior_work];
            int firstWork = cheapestW2I.keySet().iterator().next();
            return cheapestW2I.get(firstWork);
        }
        return normalObj != null ? normalObj : reversedObj;
    }

    /**
     * Matches reference cheapestItemsFromListN.
     */
    private static Map<Integer, VirtualItem> cheapestItemsFromListN(List<VirtualItem> items, int maxSubcount) {
        Map<Integer, VirtualItem> cheapestByWork = new HashMap<>();
        List<Integer> cheapestPriorWorks = new ArrayList<>();

        for (int subcount = 1; subcount <= maxSubcount; subcount++) {
            List<List<VirtualItem>> combos = combinations(items, subcount);
            for (List<VirtualItem> leftGroup : combos) {
                List<VirtualItem> rightGroup = new ArrayList<>(items);
                rightGroup.removeAll(leftGroup);

                Map<Integer, VirtualItem> leftResults = cheapestItemsFromList(leftGroup);
                Map<Integer, VirtualItem> rightResults = cheapestItemsFromList(rightGroup);
                Map<Integer, VirtualItem> merged = cheapestItemsFromTwoDictionaries(leftResults, rightResults);

                for (Map.Entry<Integer, VirtualItem> entry : merged.entrySet()) {
                    int work = entry.getKey();
                    VirtualItem newItem = entry.getValue();

                    if (cheapestPriorWorks.contains(work)) {
                        VirtualItem existing = cheapestByWork.get(work);
                        Map<Integer, VirtualItem> cmp = compareCheapest(existing, newItem);
                        cheapestByWork.put(work, cmp.get(work));
                    } else {
                        cheapestByWork.put(work, newItem);
                        cheapestPriorWorks.add(work);
                    }
                }
            }
        }
        // Reference: cheapestItemsFromListN does NOT call removeExpensiveCandidates
        return cheapestByWork;
    }

    /**
     * Matches reference cheapestItemsFromDictionaries2.
     */
    private static Map<Integer, VirtualItem> cheapestItemsFromTwoDictionaries(
            Map<Integer, VirtualItem> left, Map<Integer, VirtualItem> right) {
        Map<Integer, VirtualItem> cheapestByWork = new HashMap<>();
        List<Integer> cheapestPriorWorks = new ArrayList<>();

        for (VirtualItem leftItem : left.values()) {
            for (VirtualItem rightItem : right.values()) {
                Map<Integer, VirtualItem> newW2I;
                try {
                    // Reference: new_work2item = cheapestItemsFromList([left_item, right_item])
                    List<VirtualItem> pair = List.of(leftItem, rightItem);
                    newW2I = cheapestItemsFromList(pair);
                } catch (Exception e) {
                    continue;
                }

                if (newW2I == null) continue;

                for (Map.Entry<Integer, VirtualItem> entry : newW2I.entrySet()) {
                    int work = entry.getKey();
                    VirtualItem newItem = entry.getValue();

                    if (cheapestPriorWorks.contains(work)) {
                        VirtualItem existing = cheapestByWork.get(work);
                        Map<Integer, VirtualItem> cmp = compareCheapest(existing, newItem);
                        cheapestByWork.put(work, cmp.get(work));
                    } else {
                        cheapestByWork.put(work, newItem);
                        cheapestPriorWorks.add(work);
                    }
                }
            }
        }

        return removeExpensiveCandidates(cheapestByWork);
    }

    /**
     * Matches reference compareCheapest.
     * Returns a map. When works differ: keeps both. When works same: picks one.
     */
    private static Map<Integer, VirtualItem> compareCheapest(VirtualItem a, VirtualItem b) {
        Map<Integer, VirtualItem> result = new LinkedHashMap<>();
        if (a.work == b.work) {
            if (a.value == b.value) {
                result.put(a.work, a.totalXp <= b.totalXp ? a : b);
            } else {
                result.put(a.work, a.value < b.value ? a : b);
            }
        } else {
            result.put(a.work, a);
            result.put(b.work, b);
        }
        return result;
    }

    /**
     * Matches reference removeExpensiveCandidatesFromDictionary.
     * Iterates in insertion order; keeps items with strictly lower value.
     */
    private static Map<Integer, VirtualItem> removeExpensiveCandidates(Map<Integer, VirtualItem> workToItem) {
        Map<Integer, VirtualItem> result = new LinkedHashMap<>();
        int cheapestValue = Integer.MAX_VALUE;

        for (Map.Entry<Integer, VirtualItem> entry : workToItem.entrySet()) {
            int work = entry.getKey();
            VirtualItem item = entry.getValue();
            if (item.value < cheapestValue) {
                result.put(work, item);
                cheapestValue = item.value;
            }
        }
        return result;
    }

    // ---- Combinations (1:1 reference port) ----

    static <T> List<List<T>> combinations(List<T> set, int k) {
        List<List<T>> result = new ArrayList<>();
        if (k > set.size() || k <= 0) return result;
        if (k == set.size()) { result.add(new ArrayList<>(set)); return result; }
        if (k == 1) {
            for (T item : set) { result.add(new ArrayList<>(List.of(item))); }
            return result;
        }
        for (int i = 0; i < set.size() - k + 1; i++) {
            T head = set.get(i);
            List<T> tail = set.subList(i + 1, set.size());
            List<List<T>> tailCombos = combinations(new ArrayList<>(tail), k - 1);
            for (List<T> tailCombo : tailCombos) {
                List<T> combo = new ArrayList<>();
                combo.add(head);
                combo.addAll(tailCombo);
                result.add(combo);
            }
        }
        return result;
    }

    // ---- Instruction extraction (1:1 reference getInstructions port) ----

    /**
     * Matches reference getInstructions(comb).
     * Relabels leaf I fields to enchant names / ITEM_NAME.
     * Computes merge_cost using R.v for internal nodes, R.l for leaves.
     * Returns ordered steps in post-order (children first, then parent).
     */
    private static List<CombineStep> extractInstructions(CombineNode node, Map<Integer, Integer> weightMap, boolean isBook, Map<Integer, String> nameMap) {
        List<CombineStep> steps = new ArrayList<>();
        String targetName = isBook ? "Book" : "Target Item";
        extractInstructionsRecursive(node, steps, weightMap, targetName, nameMap);

        // Compute running totals
        int runningLevels = 0, runningXp = 0;
        for (CombineStep step : steps) {
            runningLevels += step.levelCost;
            runningXp += step.xpCost;
            step.runningTotalLevels = runningLevels;
            step.runningTotalXp = runningXp;
        }
        return steps;
    }

    /**
     * Reference: recursive getInstructions on children, then compute merge cost.
     * Relabels leaf I to enchant name (or ITEM_NAME).
     */
    private static void extractInstructionsRecursive(CombineNode node, List<CombineStep> steps,
            Map<Integer, Integer> weightMap, String targetName, Map<Integer, String> nameMap) {
        if (node.isLeaf()) return;

        // Reference: for (const key in comb) { if (key === 'L' || key === 'R') { ... } }
        // Process children first, relabel their I fields
        if (node.left != null && !node.left.isLeaf()) {
            extractInstructionsRecursive(node.left, steps, weightMap, targetName, nameMap);
        }
        if (node.right != null && !node.right.isLeaf()) {
            extractInstructionsRecursive(node.right, steps, weightMap, targetName, nameMap);
        }

        // Relabel leaf I fields
        relabelLeaf(node.left, targetName, nameMap);
        relabelLeaf(node.right, targetName, nameMap);

        // Reference merge_cost computation:
        // if Number.isInteger(comb.R.v): merge_cost = comb.R.v + 2**comb.L.w - 1 + 2**comb.R.w - 1
        // else: merge_cost = comb.R.l + 2**comb.L.w - 1 + 2**comb.R.w - 1
        int rightCost = node.right.totalValue > 0 ? node.right.totalValue : node.right.value;
        int mergeLevelCost = rightCost
                + ((int) Math.pow(2, node.left.work) - 1)
                + ((int) Math.pow(2, node.right.work) - 1);
        int work = Math.max(node.left.work, node.right.work) + 1;
        int xpCost = VirtualItem.experienceFromLevels(mergeLevelCost);
        int priorWorkPenalty = (int) Math.pow(2, work) - 1;

        String leftDesc = describeNode(node.left);
        String rightDesc = describeNode(node.right);

        steps.add(new CombineStep(leftDesc, rightDesc, mergeLevelCost, xpCost, priorWorkPenalty));
    }

    /** Relabel a leaf node: if I is an integer ID, replace with enchant name; if not a known enchant, set to targetName. */
    private static void relabelLeaf(CombineNode node, String targetName, Map<Integer, String> nameMap) {
        if (node == null || !node.isLeaf()) return;
        if ("item".equals(node.label)) {
            node.label = targetName;
            return;
        }
        try {
            int id = Integer.parseInt(node.label);
            String name = nameMap.get(id);
            node.label = (name != null) ? name : targetName;
        } catch (NumberFormatException e) {
            // Not a numeric ID — check if it's a known enchant key
            if (!nameMap.containsValue(node.label)) {
                node.label = targetName;
            }
        }
    }

    /** Describe a node for display in "Item (Enchant1, Enchant2)" format. */
    private static String describeNode(CombineNode node) {
        if (node == null) return "?";

        // Collect all enchantment leaf names
        List<String> enchNames = new ArrayList<>();
        collectLeafLabels(node, enchNames);

        // Determine the item type prefix
        String prefix = findItemType(node);

        if (enchNames.isEmpty()) {
            return prefix;
        }
        return prefix + " (" + String.join(", ", enchNames) + ")";
    }

    /** Find item type by looking for "Target Item" or "Book" leaf labels. */
    private static String findItemType(CombineNode node) {
        if (node == null) return "Book";
        if (node.isLeaf()) {
            if ("Target Item".equals(node.label) || "Book".equals(node.label)) return node.label;
            return "Book"; // single enchant leaf = a book
        }
        // Internal node: if any leaf is the target item, this is the target
        String left = findItemType(node.left);
        if ("Target Item".equals(left)) return "Target Item";
        return findItemType(node.right);
    }

    private static void collectLeafLabels(CombineNode node, List<String> out) {
        if (node == null) return;
        if (node.isLeaf()) {
            if (!"Target Item".equals(node.label) && !"Book".equals(node.label)) {
                out.add(formatEnchantDisplayName(node.label));
            }
        } else {
            collectLeafLabels(node.left, out);
            collectLeafLabels(node.right, out);
        }
    }

    /** Convert enchant path (e.g. "silk_touch") to display name ("Silk Touch"). */
    private static String formatEnchantDisplayName(String name) {
        if (name == null || name.isEmpty()) return name;
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (c == '_') { sb.append(' '); capitalize = true; }
            else if (capitalize) { sb.append(Character.toUpperCase(c)); capitalize = false; }
            else { sb.append(c); }
        }
        return sb.toString();
    }

    // ---- Naive cost calculation (for comparison display) ----

    private static int calculateNaiveCost(boolean isBook, List<EnchantInput> enchants) {
        if (enchants.size() <= 1) return 0;
        int totalLevels = 0, priorWorkTarget = 0, priorWorkBook = 0;
        for (EnchantInput e : enchants) {
            int bookValue = e.level * e.weight;
            int mergeCost = bookValue + (int) Math.pow(2, priorWorkTarget) - 1 + (int) Math.pow(2, priorWorkBook) - 1;
            if (mergeCost > MAX_MERGE_LEVELS) break;
            totalLevels += mergeCost;
            priorWorkTarget = Math.max(priorWorkTarget, priorWorkBook) + 1;
        }
        return totalLevels;
    }
}
