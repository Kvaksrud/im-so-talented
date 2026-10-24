package no.kvaksrud.imsotalented.client;

import com.mojang.blaze3d.platform.InputConstants;
import no.kvaksrud.imsotalented.network.QuarryKeyPayload;
import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.Talents;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * The Quarry key: held while breaking an ore, it takes the vein with it (the server does the breaking, see
 * {@code effect.Quarrying}). The client only says when the key goes down or up.
 *
 * <p>Q by default, which is also vanilla's Drop key, so pressing it with a pickaxe in hand would throw the pickaxe
 * away. While both share a key, the player owns Quarrying and holds a pickaxe, that key quarries and doesn't drop;
 * with anything else in hand, or without the talent, it drops as always. Binding either one to another key in the
 * controls ends the sharing.
 */
public final class QuarryClient {
	public static final KeyMapping QUARRY_KEY = new KeyMapping("key.imsotalented.quarry", InputConstants.KEY_Q, TalentClient.KEY_CATEGORY);

	/** What the server was last told, and for which player entity; a new entity (join, respawn) is told again. */
	private static boolean sentHeld;
	private static @Nullable LocalPlayer sentTo;

	private QuarryClient() {
	}

	public static void init() {
		KeyMappingHelper.registerKeyMapping(QUARRY_KEY);

		// At the start of the tick, before vanilla drops the held item for the clicks the Drop key collected.
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			LocalPlayer player = client.player;

			if (player != null && QUARRY_KEY.same(client.options.keyDrop) && quarriesInsteadOfDropping(player)) {
				while (client.options.keyDrop.consumeClick()) {
					// Swallowed: the shared key quarries with this pickaxe.
				}
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			LocalPlayer player = client.player;

			if (player == null) {
				sentTo = null;
				sentHeld = false;
				return;
			}

			boolean held = QUARRY_KEY.isDown();

			if ((player != sentTo || held != sentHeld) && ClientPlayNetworking.canSend(QuarryKeyPayload.TYPE)) {
				ClientPlayNetworking.send(new QuarryKeyPayload(held));
				sentTo = player;
				sentHeld = held;
			}
		});
	}

	/** Whether the key shared with Drop means Quarry right now: the talent is bought and a pickaxe is in hand. */
	private static boolean quarriesInsteadOfDropping(LocalPlayer player) {
		return Talents.level(player, Talent.QUARRYING) > 0 && player.getMainHandItem().is(ItemTags.PICKAXES);
	}
}
