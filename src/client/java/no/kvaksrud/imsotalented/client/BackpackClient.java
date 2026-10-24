package no.kvaksrud.imsotalented.client;

import com.mojang.blaze3d.platform.InputConstants;
import no.kvaksrud.imsotalented.client.inventory.BackpackScreen;
import no.kvaksrud.imsotalented.inventory.Backpacks;
import no.kvaksrud.imsotalented.inventory.OpenBackpackPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Ways into the backpack: Shift with the inventory key (E unless rebound), in game and from the survival inventory;
 * a key of its own, unbound unless the player binds one; and a button above the survival inventory, left of the talent
 * tree's.
 *
 * <p>Shift with the inventory key only means the backpack once the Backpack talent is bought: until then it opens the
 * inventory as always, so sneaking while opening the inventory never turns into a "buy Backpack first" message. The
 * key of its own and the button always ask, and the server, which owns the menu, says no until the talent is bought.
 */
public final class BackpackClient {
	/** Unbound by default, since Shift with the inventory key does the job; also closes the backpack. */
	public static final KeyMapping OPEN_KEY = new KeyMapping("key.imsotalented.open_backpack", InputConstants.UNKNOWN.getValue(), TalentClient.KEY_CATEGORY);

	/** Left of the talent tree's button (order 0). */
	private static final int INVENTORY_BUTTON_ORDER = 1;

	private BackpackClient() {
	}

	public static void init() {
		KeyMappingHelper.registerKeyMapping(OPEN_KEY);
		MenuScreens.register(Backpacks.MENU, BackpackScreen::new);

		// At the start of the tick, before vanilla handles key presses and opens the inventory for the same press. Key
		// presses only count as clicks while no screen is open, as in vanilla, so this never fires from a text field.
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			if (client.player == null || client.gui.screen() != null || client.gui.overlay() != null
					|| !client.hasShiftDown() || !canOpen(client.player)) {
				return;
			}

			boolean pressed = false;

			while (client.options.keyInventory.consumeClick()) {
				pressed = true;
			}

			if (pressed) {
				requestOpen();
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_KEY.consumeClick()) {
				requestOpen();
			}
		});

		InventoryButtons.register(INVENTORY_BUTTON_ORDER, Items.BUNDLE, Component.translatable("key.imsotalented.open_backpack"),
				BackpackClient::openFromInventory);
	}

	/** Whether Shift with the inventory key means the backpack: the server has the mod and the talent is bought. */
	private static boolean canOpen(LocalPlayer player) {
		return ClientPlayNetworking.canSend(OpenBackpackPayload.TYPE) && Backpacks.unlockedSlots(player) > 0;
	}

	/**
	 * The survival inventory's side of Shift with the inventory key: called where the inventory would close for that key,
	 * so a key typed into the recipe book's search never gets here. Opens the backpack instead and returns true, or
	 * returns false to let the inventory close as usual.
	 */
	public static boolean openInsteadOfInventory(KeyEvent event) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null || !event.hasShiftDown() || !canOpen(player)) {
			return false;
		}

		openFromInventory();
		return true;
	}

	/** Asks the server to open the backpack. Does nothing on a server without the mod. */
	public static void requestOpen() {
		if (Minecraft.getInstance().player != null && ClientPlayNetworking.canSend(OpenBackpackPayload.TYPE)) {
			ClientPlayNetworking.send(OpenBackpackPayload.INSTANCE);
		}
	}

	/**
	 * From the inventory screen: first tell the server the inventory closed, as Esc would, so it puts the 2x2 crafting
	 * grid and the item on the cursor back into the inventory. Otherwise they would stay in the inventory's menu while
	 * the backpack is open, and an item on the cursor would be lost on leaving the game. The screen itself stays
	 * until the backpack replaces it. Without the talent the server only answers with a message, so the
	 * inventory is left as it is.
	 */
	private static void openFromInventory() {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null || !ClientPlayNetworking.canSend(OpenBackpackPayload.TYPE)) {
			return;
		}

		if (player.containerMenu == player.inventoryMenu && Backpacks.unlockedSlots(player) > 0) {
			player.connection.send(new ServerboundContainerClosePacket(player.inventoryMenu.containerId));
			player.inventoryMenu.setCarried(ItemStack.EMPTY);
		}

		requestOpen();
	}
}
