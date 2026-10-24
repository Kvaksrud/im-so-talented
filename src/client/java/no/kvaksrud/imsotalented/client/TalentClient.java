package no.kvaksrud.imsotalented.client;

import com.mojang.blaze3d.platform.InputConstants;
import no.kvaksrud.imsotalented.ImSoTalented;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

/** Ways into the talent tree: a key (N by default) and a button above the survival inventory. */
public final class TalentClient {
	/** The mod's own section in the controls screen; the backpack's key goes here too. */
	public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(ImSoTalented.id("main"));

	/** Not a GLFW code: 26.3 uses SDL scancodes, and N is free in vanilla. Also closes the tree. */
	public static final KeyMapping OPEN_TALENTS = new KeyMapping("key.imsotalented.open_talents", InputConstants.KEY_N, KEY_CATEGORY);

	/** The rightmost inventory button; the backpack's sits to its left. */
	private static final int INVENTORY_BUTTON_ORDER = 0;

	private TalentClient() {
	}

	public static void init() {
		KeyMappingHelper.registerKeyMapping(OPEN_TALENTS);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_TALENTS.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					client.gui.setScreen(new TalentScreen(null));
				}
			}
		});

		InventoryButtons.register(INVENTORY_BUTTON_ORDER, Items.EXPERIENCE_BOTTLE, Component.translatable("key.imsotalented.open_talents"), () -> {
			Minecraft minecraft = Minecraft.getInstance();
			// Esc goes back to the inventory, which also keeps anything held on the cursor in its menu.
			minecraft.gui.setScreen(new TalentScreen(minecraft.gui.screen()));
		});
	}
}
