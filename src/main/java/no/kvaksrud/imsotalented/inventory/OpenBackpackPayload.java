package no.kvaksrud.imsotalented.inventory;

import no.kvaksrud.imsotalented.ImSoTalented;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: open my backpack (the key or the inventory button). The server checks the talent, since a
 * menu can only be opened from there.
 */
public record OpenBackpackPayload() implements CustomPacketPayload {
	public static final OpenBackpackPayload INSTANCE = new OpenBackpackPayload();
	public static final Type<OpenBackpackPayload> TYPE = new Type<>(ImSoTalented.id("open_backpack"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenBackpackPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<OpenBackpackPayload> type() {
		return TYPE;
	}
}
