package no.kvaksrud.imsotalented.network;

import java.util.Arrays;

import no.kvaksrud.imsotalented.ImSoTalented;

import net.minecraft.server.level.ServerPlayer;

/**
 * Handles at most one of each kind of a player's requests (buy, toggle, open the backpack) every {@link #MIN_TICKS}
 * server ticks and ignores the rest. The client only sends one on a click or a key press, so a player never notices; a
 * modified client sending a flood would otherwise have the server re-apply and re-sync the talents, or rebuild the
 * backpack menu, for every one.
 *
 * <p>Kept per player in {@link ImSoTalented#REQUEST_THROTTLE}: not saved, not synced and not copied on respawn, so it
 * goes away with the player entity and needs no clean-up on leaving.
 */
public final class RequestThrottle {
	/** The fewest server ticks from one handled request to the next of the same kind. */
	public static final int MIN_TICKS = 2;

	private static final long NEVER = Long.MIN_VALUE;

	public enum Request {
		BUY,
		TOGGLE,
		OPEN_BACKPACK
	}

	/** The server tick each kind of request was last handled at, by {@link Request} ordinal. */
	private final long[] lastHandled = new long[Request.values().length];

	public RequestThrottle() {
		Arrays.fill(this.lastHandled, NEVER);
	}

	/**
	 * Whether to handle this request now. If so it counts as the last of its kind; if not, it is logged at debug and
	 * the caller drops it.
	 */
	public static boolean allow(ServerPlayer player, Request request) {
		RequestThrottle throttle = player.getAttachedOrCreate(ImSoTalented.REQUEST_THROTTLE);
		long tick = player.level().getServer().getTickCount();
		long last = throttle.lastHandled[request.ordinal()];

		if (last != NEVER && tick - last < MIN_TICKS) {
			ImSoTalented.LOGGER.debug("Ignored a {} request from {} ({}): {} ticks after the last one", request, player.getName().getString(),
					player.getUUID(), tick - last);
			return false;
		}

		throttle.lastHandled[request.ordinal()] = tick;
		return true;
	}
}
