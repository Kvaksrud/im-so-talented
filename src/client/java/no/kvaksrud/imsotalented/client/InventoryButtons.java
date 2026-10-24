package no.kvaksrud.imsotalented.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import no.kvaksrud.imsotalented.client.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

/**
 * Small item-icon buttons in a row above the survival inventory's top right corner, right to left in {@code order}.
 * They follow the panel when the recipe book opens or closes. The creative inventory has none; the keys still work.
 */
public final class InventoryButtons {
	public static final int SIZE = 20;
	private static final int GAP = 2;
	private static final int PANEL_WIDTH = 176;

	private static final List<Entry> ENTRIES = new ArrayList<>();
	private static boolean registered;

	private InventoryButtons() {
	}

	/**
	 * Adds a button; lower {@code order} sits further right. Call from a client initializer. The icon is an item, not a
	 * stack: stacks can't be made that early (item components aren't bound yet), so it becomes one when a screen opens.
	 */
	public static void register(int order, ItemLike icon, Component tooltip, Runnable onPress) {
		ENTRIES.add(new Entry(order, icon, tooltip, onPress));
		ENTRIES.sort(Comparator.comparingInt(Entry::order));

		if (!registered) {
			registered = true;
			ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
				if (screen instanceof InventoryScreen inventory) {
					addTo(inventory);
				}
			});
		}
	}

	private static void addTo(InventoryScreen screen) {
		List<Button> buttons = new ArrayList<>();
		List<ItemStack> icons = new ArrayList<>();

		for (Entry entry : ENTRIES) {
			Button button = Button.builder(Component.empty(), _ -> entry.onPress.run())
					.size(SIZE, SIZE)
					.tooltip(Tooltip.create(entry.tooltip))
					.build();
			buttons.add(button);
			icons.add(new ItemStack(entry.icon));
			Screens.getWidgets(screen).add(button);
		}

		place(screen, buttons);
		// The recipe book moves the panel after init, so keep the row in place every tick.
		ScreenEvents.afterTick(screen).register(_ -> place(screen, buttons));
		ScreenEvents.afterExtract(screen).register((_, graphics, _, _, _) -> {
			for (int i = 0; i < buttons.size(); i++) {
				Button button = buttons.get(i);
				graphics.item(icons.get(i), button.getX() + (SIZE - 16) / 2, button.getY() + (SIZE - 16) / 2);
			}
		});
	}

	private static void place(InventoryScreen screen, List<Button> buttons) {
		AbstractContainerScreenAccessor panel = (AbstractContainerScreenAccessor) screen;
		int right = panel.imsotalented$leftPos() + PANEL_WIDTH;
		int y = panel.imsotalented$topPos() - SIZE - GAP;

		for (int i = 0; i < buttons.size(); i++) {
			buttons.get(i).setPosition(right - (i + 1) * SIZE - i * GAP, y);
		}
	}

	private record Entry(int order, ItemLike icon, Component tooltip, Runnable onPress) {
	}
}
