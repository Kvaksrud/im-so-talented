package no.kvaksrud.imsotalented.client;

import no.kvaksrud.imsotalented.ImSoTalented;
import no.kvaksrud.imsotalented.network.TalentRulesPayload;
import no.kvaksrud.imsotalented.talent.TalentRules;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ImSoTalentedClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(TalentRulesPayload.TYPE, (payload, context) -> ImSoTalented.setClientRules(payload.rules()));
		// Back to the defaults when leaving, so a server without the mod's rules doesn't inherit the last one's.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ImSoTalented.setClientRules(TalentRules.DEFAULTS));

		TalentClient.init();
		BackpackClient.init();
		QuarryClient.init();
		TreeXpHud.init();
	}
}
