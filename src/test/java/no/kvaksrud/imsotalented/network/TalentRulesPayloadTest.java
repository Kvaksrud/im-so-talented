package no.kvaksrud.imsotalented.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import no.kvaksrud.imsotalented.talent.TalentRules;
import no.kvaksrud.imsotalented.talent.TalentRules.EnchantmentStacking;
import org.junit.jupiter.api.Test;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;

import io.netty.buffer.Unpooled;

/** The rules packet a server sends on join: its protocol version first, so a client of another version can say so. */
class TalentRulesPayloadTest {
	private static final TalentRules CUSTOM = new TalentRules(40, 1.5, EnchantmentStacking.ADD);

	private static RegistryFriendlyByteBuf buffer() {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
	}

	@Test
	void theProtocolIsTwo() {
		assertEquals(2, TalentRulesPayload.PROTOCOL, "raised with the toggle packet's new shape and this packet's version field");
	}

	@Test
	void theRulesGoThroughWithTheProtocol() {
		RegistryFriendlyByteBuf buffer = buffer();

		try {
			TalentRulesPayload sent = new TalentRulesPayload(TalentRulesPayload.PROTOCOL, CUSTOM);
			TalentRulesPayload.CODEC.encode(buffer, sent);
			assertEquals(sent, TalentRulesPayload.CODEC.decode(buffer));
			assertEquals(0, buffer.readableBytes(), "the whole packet is read");
		} finally {
			buffer.release();
		}
	}

	@Test
	void theProtocolComesFirst() {
		RegistryFriendlyByteBuf buffer = buffer();

		try {
			TalentRulesPayload.CODEC.encode(buffer, new TalentRulesPayload(TalentRulesPayload.PROTOCOL, CUSTOM));
			assertEquals(TalentRulesPayload.PROTOCOL, ByteBufCodecs.VAR_INT.decode(buffer), "a client of any version can read it");
			assertEquals(CUSTOM, TalentRules.STREAM_CODEC.decode(buffer), "then the rules");
		} finally {
			buffer.release();
		}
	}

	@Test
	void anotherProtocolSkipsTheRestAndKeepsTheDefaults() {
		RegistryFriendlyByteBuf buffer = buffer();

		try {
			// A newer server whose packet has a different shape after the version: here, something no rules codec reads.
			ByteBufCodecs.VAR_INT.encode(buffer, 3);
			buffer.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 1, 2, 3, 4, 5, 6, 7});
			assertEquals(new TalentRulesPayload(3, TalentRules.DEFAULTS), TalentRulesPayload.CODEC.decode(buffer));
			assertEquals(0, buffer.readableBytes(), "the rest is skipped, so the packet doesn't fail for leftover bytes");
		} finally {
			buffer.release();
		}

		buffer = buffer();

		try {
			// An older server (protocol 1 had no version field: its first int is the cost base, here 25).
			TalentRules.STREAM_CODEC.encode(buffer, CUSTOM);
			TalentRulesPayload decoded = TalentRulesPayload.CODEC.decode(buffer);
			assertEquals(40, decoded.protocol(), "the old packet's first number reads as a protocol that isn't this one");
			assertEquals(TalentRules.DEFAULTS, decoded.rules());
			assertEquals(0, buffer.readableBytes());
		} finally {
			buffer.release();
		}
	}
}
