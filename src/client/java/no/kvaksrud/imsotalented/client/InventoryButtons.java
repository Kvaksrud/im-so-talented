package no.kvaksrud.imsotalented.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import no.kvaksrud.imsotalented.client.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
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
	/** Puts the 16 px item in the middle of the button. */
	private static final int ICON_INSET = (SIZE - 16) / 2;

	private static final List<Entry> ENTRIES = new ArrayList<>();
	private static boolean registered;

	private InventoryButtons() {
	}

	/**
	 * Adds a button; lower {@code order} sits further right. Call from a client initializer. The {@code label} is the
	 * button's tooltip and what the narrator reads. The icon is an item, not a stack: stacks can't be made that early
	 * (item components aren't bound yet), so it becomes one when a screen opens.
	 */
	public static void register(int order, ItemLike icon, Component label, Runnable onPress) {
		ENTRIES.add(new Entry(order, icon, label, onPress));
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

	/**
	 * Before a button replaces the survival inventory with another screen: tells the server the inventory closed, as Esc
	 * would, so it puts the 2x2 crafting grid and the item on the cursor back into the inventory. Otherwise they would
	 * stay in the inventory's menu, the client would clear its crafting result when the screen goes, and an item on the
	 * cursor would be lost on leaving the game. Does nothing while another menu is open.
	 */
	public static void closeInventoryMenu(LocalPlayer player) {
		if (player.containerMenu == player.inventoryMenu) {
			player.connection.send(new ServerboundContainerClosePacket(player.inventoryMenu.containerId));
			player.inventoryMenu.setCarried(ItemStack.EMPTY);
		}
	}

	private static void addTo(InventoryScreen screen) {
		List<Button> buttons = new ArrayList<>();

		for (Entry entry : ENTRIES) {
			Button button = new ItemIconButton(entry.label, new ItemStack(entry.icon), entry.onPress);
			buttons.add(button);
			Screens.getWidgets(screen).add(button);
		}

		place(screen, buttons);
		// The recipe book moves the panel after init, so keep the row in place every tick.
		ScreenEvents.afterTick(screen).register(_ -> place(screen, buttons));
	}

	private static void place(InventoryScreen screen, List<Button> buttons) {
		AbstractContainerScreenAccessor panel = (AbstractContainerScreenAccessor) screen;
		int right = panel.imsotalented$leftPos() + panel.imsotalented$imageWidth();
		int y = panel.imsotalented$topPos() - SIZE - GAP;

		for (int i = 0; i < buttons.size(); i++) {
			buttons.get(i).setPosition(right - (i + 1) * SIZE - i * GAP, y);
		}
	}

	private record Entry(int order, ItemLike icon, Component label, Runnable onPress) {
	}

	/**
	 * A vanilla button showing an item instead of its label. The label stays its message, so the narrator reads it,
	 * and is its tooltip. The icon is drawn with the button, so the item on the cursor and tooltips go over it.
	 */
	private static final class ItemIconButton extends Button {
		private final ItemStack icon;

		private ItemIconButton(Component label, ItemStack icon, Runnable onPress) {
			super(0, 0, SIZE, SIZE, label, _ -> onPress.run(), DEFAULT_NARRATION);
			this.icon = icon;
			// The message is narrated already; the tooltip only shows it.
			this.setTooltip(Tooltip.create(label, null));
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			this.extractDefaultSprite(graphics);
			graphics.item(this.icon, this.getX() + ICON_INSET, this.getY() + ICON_INSET);
		}
	}
}
