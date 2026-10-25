package no.kvaksrud.imsotalented.client;

import no.kvaksrud.imsotalented.ImSoTalented;
import no.kvaksrud.imsotalented.network.TalentRulesPayload;
import no.kvaksrud.imsotalented.talent.TalentRules;

import net.minecraft.network.chat.Component;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Sets up the client and keeps track of whether the server it's connected to has the mod: the server sends its rules
 * with its protocol version on join, so a server that sent them with this client's protocol has it. Until then (a
 * vanilla server, or before the rules arrive) the talent screen says so instead of looking live, the HUD bar stays out
 * of the way and the backpack's key and button explain why nothing opens.
 */
public class ImSoTalentedClient implements ClientModInitializer {
	private static boolean serverHasMod;
	/** Whether this connection's chat already says the server runs another version. */
	private static boolean mismatchReported;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(TalentRulesPayload.TYPE, (payload, context) -> {
			if (payload.protocol() == TalentRulesPayload.PROTOCOL) {
				serverHasMod = true;
				ImSoTalented.setClientRules(payload.rules());
				return;
			}

			// Another build: its payloads may not match this one's, so treat it as a server without the mod and keep
			// the default rules.
			serverHasMod = false;
			ImSoTalented.setClientRules(TalentRules.DEFAULTS);

			if (!mismatchReported) {
				mismatchReported = true;
				ImSoTalented.LOGGER.warn("The server runs protocol {} of the mod, this client {}: talents are off on this server",
						payload.protocol(), TalentRulesPayload.PROTOCOL);
				context.player().sendSystemMessage(Component.translatable("message.imsotalented.version_mismatch",
						payload.protocol(), TalentRulesPayload.PROTOCOL));
			}
		});

		// Back to the defaults when leaving, so a server without the mod's rules doesn't inherit the last one's.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			serverHasMod = false;
			mismatchReported = false;
			ImSoTalented.setClientRules(TalentRules.DEFAULTS);
		});

		TalentClient.init();
		BackpackClient.init();
		TreeXpHud.init();
	}

	/**
	 * Whether the server this client is playing on has the mod at this client's protocol version: it sent its rules
	 * with {@link TalentRulesPayload#PROTOCOL}. False while not connected. Call on the client thread.
	 */
	public static boolean serverHasMod() {
		return serverHasMod;
	}
}
