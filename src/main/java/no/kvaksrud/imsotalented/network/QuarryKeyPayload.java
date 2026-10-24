package no.kvaksrud.imsotalented.network;

import no.kvaksrud.imsotalented.ImSoTalented;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the Quarry key went down or up. Sent on every change, and again for a new player entity. */
public record QuarryKeyPayload(boolean held) implements CustomPacketPayload {
	public static final Type<QuarryKeyPayload> TYPE = new Type<>(ImSoTalented.id("quarry_key"));
	public static final StreamCodec<RegistryFriendlyByteBuf, QuarryKeyPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, QuarryKeyPayload::held,
			QuarryKeyPayload::new);

	@Override
	public Type<QuarryKeyPayload> type() {
		return TYPE;
	}
}
