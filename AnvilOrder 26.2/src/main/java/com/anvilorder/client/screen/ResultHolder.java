package com.anvilorder.client.screen;

import com.anvilorder.solver.SolverResult;

/**
 * Static holder for passing solver results between screens.
 */
public class ResultHolder {
    public static SolverResult pendingResult;
    public static boolean showGuidePanel;
    /** Saved anvil name to restore after enchantment select screen closes */
    public static String savedAnvilName;

    public static void setResult(SolverResult result) {
        pendingResult = result;
        showGuidePanel = true;
    }

    public static void clear() {
        pendingResult = null;
        showGuidePanel = false;
        savedAnvilName = null;
    }
}
