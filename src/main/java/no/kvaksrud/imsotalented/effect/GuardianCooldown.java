package no.kvaksrud.imsotalented.effect;

/**
 * Guardian's own copy of a player's damage cooldown, kept in raw damage (before armor, shield and Block Chance), so a
 * hit pays the way vanilla would count it if nothing stood in the way. Vanilla's {@code lastHurt} can't be used: a
 * shield that takes a whole hit leaves it at 0, and then every other hit in the cooldown would pay in full, so a mob
 * trapped in front of a raised shield would pay for every swing.
 *
 * <p>Only hits that get as far as vanilla's cooldown come here (see {@link DamageCooldown#rejectsBeforeCooldown}). Kept
 * per player by {@link TreeXpAttachments#GUARDIAN_COOLDOWN}: not saved, not kept on death (a new life starts without a
 * cooldown). Server thread only. Plain Java, so the unit tests run it without a game.
 */
public final class GuardianCooldown {
	/** The raw damage of the hit that started the cooldown, raised by harder hits within it. */
	private float damage;
	/** The server tick the cooldown started at. */
	private long startTick;
	/** Whether any hit has come yet: until then there's no cooldown to be in. */
	private boolean started;

	/**
	 * Counts a hit of {@code damage} raw damage at server tick {@code tick}, as vanilla counts one against its cooldown,
	 * and returns the part of it that counts.
	 * <ul>
	 * <li>Within {@link DamageCooldown#WINDOW_TICKS} of the hit that started the cooldown, only what the hit has over
	 *     that one counts (nothing if it isn't harder), and a harder hit raises the mark without restarting the
	 *     cooldown. A crowd hitting at once pays like its hardest hit.</li>
	 * <li>Otherwise, or for a hit that bypasses the cooldown, the whole hit counts and starts a new cooldown.</li>
	 * </ul>
	 * Negative damage and NaN count as 0, as vanilla clamps them.
	 */
	public float count(float damage, long tick, boolean bypassesCooldown) {
		float raw = damage > 0.0F ? damage : 0.0F;

		if (this.started && !bypassesCooldown && tick - this.startTick < DamageCooldown.WINDOW_TICKS) {
			if (raw <= this.damage) {
				return 0.0F;
			}

			float counted = raw - this.damage;
			this.damage = raw;
			return counted;
		}

		this.started = true;
		this.startTick = tick;
		this.damage = raw;
		return raw;
	}
}
