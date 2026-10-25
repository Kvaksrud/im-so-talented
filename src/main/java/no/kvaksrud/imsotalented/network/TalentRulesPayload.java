package no.kvaksrud.imsotalented.network;

import no.kvaksrud.imsotalented.ImSoTalented;
import no.kvaksrud.imsotalented.talent.TalentRules;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client, on join: the mod's network protocol version, then the server's tree level prices and enchantment
 * stacking, so the XP bars and mining speed match it. The version comes first and never moves. A client reading
 * another version skips the rest (its shape may differ) and keeps the default rules, so it can tell the player to
 * install the server's version instead of failing on the packet.
 */
public record TalentRulesPayload(int protocol, TalentRules rules) implements CustomPacketPayload {
	/** Raise it whenever a payload or the synced talent data changes shape. */
	public static final int PROTOCOL = 2;

	/** By the mod id rather than {@code ImSoTalented.id}: loading {@code ImSoTalented} creates attachments, which the unit tests can't. */
	public static final Type<TalentRulesPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ImSoTalented.MOD_ID, "talent_rules"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TalentRulesPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				ByteBufCodecs.VAR_INT.encode(buf, payload.protocol());
				TalentRules.STREAM_CODEC.encode(buf, payload.rules());
			},
			buf -> {
				int protocol = ByteBufCodecs.VAR_INT.decode(buf);

				if (protocol != PROTOCOL) {
					buf.skipBytes(buf.readableBytes());
					return new TalentRulesPayload(protocol, TalentRules.DEFAULTS);
				}

				return new TalentRulesPayload(protocol, TalentRules.STREAM_CODEC.decode(buf));
			});

	@Override
	public Type<TalentRulesPayload> type() {
		return TYPE;
	}
}
