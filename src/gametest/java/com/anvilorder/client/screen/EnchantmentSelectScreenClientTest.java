package com.anvilorder.client.screen;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class EnchantmentSelectScreenClientTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext ignored = context.worldBuilder().create()) {
            context.getInput().resizeWindow(854, 480);
            context.setScreen(() -> new EnchantmentSelectScreen(
                    Component.translatable("screen.anvilorder.enchant_select"),
                    new ItemStack(Items.DIAMOND_SWORD),
                    null
            ));
            context.waitForScreen(EnchantmentSelectScreen.class);
            context.waitTicks(2);

            context.runOnClient(client -> {
                EnchantmentSelectScreen screen = (EnchantmentSelectScreen) client.gui.screen();
                setScroll(screen, 13.0);
            });
            context.waitTick();
            context.takeScreenshot("anvilorder-selection-clipped-scroll");

            context.runOnClient(client -> {
                EnchantmentSelectScreen screen = (EnchantmentSelectScreen) client.gui.screen();
                EnchantmentSelectScreen.EnchantSlot sweeping = slots(screen).stream()
                        .filter(slot -> "sweeping_edge".equals(slot.path))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Sweeping Edge is missing from the sword selection"));

                if (sweeping.maxLvl != 3 || sweeping.slider == null) {
                    throw new AssertionError("Sweeping Edge must have a three-level slider");
                }

                setSliderValue(sweeping.slider, 1.0);
                if (sweeping.lvl != 3) {
                    throw new AssertionError("Slider did not update Sweeping Edge to level 3 in real time");
                }

                setScroll(screen, Math.max(0, (sweeping.index - 1) * 26.0));
            });
            context.waitTick();
            context.takeScreenshot("anvilorder-sweeping-edge-level-3");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<EnchantmentSelectScreen.EnchantSlot> slots(EnchantmentSelectScreen screen) {
        try {
            Field slots = EnchantmentSelectScreen.class.getDeclaredField("slots");
            slots.setAccessible(true);
            return (List<EnchantmentSelectScreen.EnchantSlot>) slots.get(screen);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect enchantment slots", exception);
        }
    }

    private static void setScroll(EnchantmentSelectScreen screen, double amount) {
        try {
            Field scrollAmount = EnchantmentSelectScreen.class.getDeclaredField("scrollAmount");
            scrollAmount.setAccessible(true);
            scrollAmount.setDouble(screen, amount);

            Method updatePositions = EnchantmentSelectScreen.class.getDeclaredMethod("updateSlotPositions");
            updatePositions.setAccessible(true);
            updatePositions.invoke(screen);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not set the selection scroll position", exception);
        }
    }

    private static void setSliderValue(EnchantmentSelectScreen.LevSlider slider, double value) {
        try {
            Field sliderValue = AbstractSliderButton.class.getDeclaredField("value");
            sliderValue.setAccessible(true);
            sliderValue.setDouble(slider, value);

            Method applyValue = EnchantmentSelectScreen.LevSlider.class.getDeclaredMethod("applyValue");
            applyValue.setAccessible(true);
            applyValue.invoke(slider);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not move the enchantment level slider", exception);
        }
    }
}
