package no.kvaksrud.imsotalented.network;

import no.kvaksrud.imsotalented.ImSoTalented;
import no.kvaksrud.imsotalented.talent.TalentRules;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client, on join: the server's tree level prices and enchantment stacking, so the XP bars and mining speed match it. */
public record TalentRulesPayload(TalentRules rules) implements CustomPacketPayload {
	public static final Type<TalentRulesPayload> TYPE = new Type<>(ImSoTalented.id("talent_rules"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TalentRulesPayload> CODEC = TalentRules.STREAM_CODEC
			.<RegistryFriendlyByteBuf>cast()
			.map(TalentRulesPayload::new, TalentRulesPayload::rules);

	@Override
	public Type<TalentRulesPayload> type() {
		return TYPE;
	}
}
