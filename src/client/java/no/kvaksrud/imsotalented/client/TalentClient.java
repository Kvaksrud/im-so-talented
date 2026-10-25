package no.kvaksrud.imsotalented.client;

import com.mojang.blaze3d.platform.InputConstants;
import no.kvaksrud.imsotalented.ImSoTalented;
import no.kvaksrud.imsotalented.talent.Talents;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

/**
 * Ways into the talent tree: a key (N by default) and a button above the survival inventory. Both open it on a server
 * without the mod too, where the screen says why nothing can be earned or bought.
 */
public final class TalentClient {
	/** The mod's own section in the controls screen; the backpack's key goes here too. */
	public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(ImSoTalented.id("main"));

	/**
	 * Not a GLFW code: 26.3 uses SDL scancodes. Also closes the tree. The id is new with the four trees: under the old
	 * {@code key.imsotalented.open_talents} every player's options.txt already holds K, which would win over the new
	 * default. Vanilla's spectator toggle is F3 + N; it only acts with F3 held, and so this key skips those presses.
	 */
	public static final KeyMapping OPEN_TALENTS = new KeyMapping(Talents.TALENT_TREE_KEY, InputConstants.KEY_N, KEY_CATEGORY);

	/** The rightmost inventory button; the backpack's sits to its left. */
	private static final int INVENTORY_BUTTON_ORDER = 0;

	private TalentClient() {
	}

	public static void init() {
		KeyMappingHelper.registerKeyMapping(OPEN_TALENTS);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_TALENTS.consumeClick()) {
				if (client.player != null && client.gui.screen() == null && !client.options.keyDebugModifier.isDown()) {
					client.gui.setScreen(new TalentScreen(false));
				}
			}
		});

		InventoryButtons.register(INVENTORY_BUTTON_ORDER, Items.EXPERIENCE_BOTTLE, Component.translatable(OPEN_TALENTS.getName()), () -> {
			Minecraft minecraft = Minecraft.getInstance();
			LocalPlayer player = minecraft.player;

			if (player == null) {
				return;
			}

			// The inventory's menu closes first, as Esc would, so the server takes back the crafting grid and the
			// cursor item; leaving the talent screen opens a fresh inventory.
			InventoryButtons.closeInventoryMenu(player);
			minecraft.gui.setScreen(new TalentScreen(true));
		});
	}
}
