package no.kvaksrud.imsotalented.talent;

import java.util.Arrays;

/**
 * What the server keeps per player between ticks for tree XP: not saved and not synced, kept through death. It
 * holds the XP earned since the last commit (whole points and fractions, committed once per tick by
 * {@link Talents#commitPending}), each tree's hourly full-rate allowance, and when the player last turned the camera,
 * which is how an AFK setup (a held key, an auto-clicker) is told apart from someone playing.
 *
 * <p>All times are server ticks ({@code MinecraftServer.getTickCount()}), which only count up while the server runs,
 * so a ledger made before a restart can't be read against the new count: the ledger isn't saved.
 */
public final class TreeXpLedger {
	private final double[] pending = new double[TalentTree.values().length];
	private final long[] windowStart = new long[TalentTree.values().length];
	private final double[] windowEarned = new double[TalentTree.values().length];
	private boolean seen;
	private long lastActiveTick;
	private float lastYRot;
	private float lastXRot;

	/** XP earned in this tree and not yet committed to the player's talent data, fractions included. */
	public double pending(TalentTree tree) {
		return this.pending[tree.ordinal()];
	}

	/**
	 * Credits {@code amount} tree XP earned at {@code tick}: in full while the tree's hourly allowance lasts
	 * ({@link TalentValues#TREE_XP_FULL_RATE_PER_HOUR}), and at {@link TalentValues#TREE_XP_OVER_CAP_SHARE} past it.
	 * The hour starts at the first XP earned in it.
	 *
	 * @return the XP actually credited
	 */
	public double credit(TalentTree tree, double amount, long tick) {
		int index = tree.ordinal();

		if (tick - this.windowStart[index] >= TalentValues.TREE_XP_WINDOW_TICKS || this.windowEarned[index] == 0.0) {
			this.windowStart[index] = tick;
			this.windowEarned[index] = 0.0;
		}

		double full = Math.max(0.0, Math.min(amount, TalentValues.TREE_XP_FULL_RATE_PER_HOUR - this.windowEarned[index]));
		double credited = full + (amount - full) * TalentValues.TREE_XP_OVER_CAP_SHARE;
		this.windowEarned[index] += full;
		this.pending[index] += credited;
		return credited;
	}

	/** Takes the whole points out of a tree's pending XP, leaving the fraction for later. */
	public long takeWhole(TalentTree tree) {
		int index = tree.ordinal();
		long whole = (long) this.pending[index];
		this.pending[index] -= whole;
		return whole;
	}

	/** Forgets everything pending and every hourly allowance, as {@code /talents reset} does. */
	public void clear() {
		Arrays.fill(this.pending, 0.0);
		Arrays.fill(this.windowStart, 0L);
		Arrays.fill(this.windowEarned, 0.0);
	}

	/**
	 * Notes where the player is looking at {@code tick}. Any turn of the camera counts as activity; the first look
	 * after the ledger is made counts too, so a player who just joined or respawned starts active.
	 */
	public void look(float yRot, float xRot, long tick) {
		if (!this.seen || yRot != this.lastYRot || xRot != this.lastXRot) {
			this.seen = true;
			this.lastActiveTick = tick;
			this.lastYRot = yRot;
			this.lastXRot = xRot;
		}
	}

	/**
	 * Whether the player hasn't turned the camera for {@link TalentValues#AFK_TICKS}: a held key or an auto-clicker
	 * never does, someone playing always does.
	 */
	public boolean idle(long tick) {
		return this.seen && tick - this.lastActiveTick > TalentValues.AFK_TICKS;
	}
}
