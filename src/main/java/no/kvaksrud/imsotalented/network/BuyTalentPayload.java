package no.kvaksrud.imsotalented.network;

import no.kvaksrud.imsotalented.ImSoTalented;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: buy the next level of this talent (by id). The server checks everything and spends a talent point of its tree. */
public record BuyTalentPayload(String talentId) implements CustomPacketPayload {
	public static final Type<BuyTalentPayload> TYPE = new Type<>(ImSoTalented.id("buy_talent"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuyTalentPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), BuyTalentPayload::talentId,
			BuyTalentPayload::new);

	@Override
	public Type<BuyTalentPayload> type() {
		return TYPE;
	}
}
