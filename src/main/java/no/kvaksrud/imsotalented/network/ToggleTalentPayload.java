package no.kvaksrud.imsotalented.network;

import no.kvaksrud.imsotalented.ImSoTalented;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: switch this talent (by id) on or off. The server validates and re-applies the effects. */
public record ToggleTalentPayload(String talentId, boolean on) implements CustomPacketPayload {
	public static final Type<ToggleTalentPayload> TYPE = new Type<>(ImSoTalented.id("toggle_talent"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ToggleTalentPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), ToggleTalentPayload::talentId,
			ByteBufCodecs.BOOL, ToggleTalentPayload::on,
			ToggleTalentPayload::new);

	@Override
	public Type<ToggleTalentPayload> type() {
		return TYPE;
	}
}
