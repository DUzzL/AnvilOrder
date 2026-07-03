package com.anvilorder.client;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client-side entrypoint for AnvilOrder mod.
 */
public class AnvilOrderClient implements ClientModInitializer {

    public static final String MOD_ID = "anvilorder";

    @Override
    public void onInitializeClient() {
        // Mixin handles screen injection.
    }
}
