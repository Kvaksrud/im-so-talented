package no.kvaksrud.imsotalented.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Guardian's copy of the damage cooldown, and vanilla's rule it copies: within the first half of the 20-tick cooldown
 * a hit only counts for what it has over the hit that started it.
 */
class GuardianCooldownTest {
	@Test
	void theCooldownsNumbersAreVanillas() {
		assertEquals(20, DamageCooldown.TICKS);
		assertEquals(10, DamageCooldown.REDUCED_ABOVE);
		assertEquals(10, DamageCooldown.WINDOW_TICKS);
	}

	@Test
	void vanillaIgnoresAHitNoHarderThanTheLastInItsCooldown() {
		assertTrue(DamageCooldown.ignores(true, 3.0F, 4.0F), "softer than the hit that started it");
		assertTrue(DamageCooldown.ignores(true, 4.0F, 4.0F), "exactly as hard");
		assertFalse(DamageCooldown.ignores(true, 5.0F, 4.0F), "harder: the extra lands");
		assertFalse(DamageCooldown.ignores(false, 1.0F, 4.0F), "out of the cooldown every hit lands");
		assertTrue(DamageCooldown.ignores(true, 0.0F, 0.0F), "a shield left lastHurt at 0: a blocked hit is ignored too");
	}

	@Test
	void theFirstHitCountsInFull() {
		assertEquals(4.0F, new GuardianCooldown().count(4.0F, 100L, false));
	}

	@Test
	void withinTheWindowOnlyTheHarderPartCounts() {
		GuardianCooldown cooldown = new GuardianCooldown();
		assertEquals(4.0F, cooldown.count(4.0F, 100L, false));
		assertEquals(0.0F, cooldown.count(4.0F, 100L, false), "the same hit again, in the same tick");
		assertEquals(0.0F, cooldown.count(3.0F, 104L, false), "a softer one");
		assertEquals(2.0F, cooldown.count(6.0F, 105L, false), "vanilla takes 6 - 4");
		assertEquals(0.0F, cooldown.count(5.0F, 106L, false), "the harder hit raised the mark to 6");
		assertEquals(1.0F, cooldown.count(7.0F, 109L, false), "the last tick of the window");
	}

	@Test
	void aHarderHitDoesntRestartTheCooldown() {
		GuardianCooldown cooldown = new GuardianCooldown();
		cooldown.count(4.0F, 100L, false);
		cooldown.count(6.0F, 108L, false);
		assertEquals(3.0F, cooldown.count(3.0F, 110L, false), "ten ticks after the first hit, not the harder one, a hit counts in full");
	}

	@Test
	void afterTheWindowAHitCountsInFullAndStartsANewOne() {
		GuardianCooldown cooldown = new GuardianCooldown();
		cooldown.count(4.0F, 100L, false);
		assertEquals(2.0F, cooldown.count(2.0F, 110L, false));
		assertEquals(0.0F, cooldown.count(2.0F, 115L, false), "the new cooldown started at 110");
		assertEquals(2.0F, cooldown.count(2.0F, 120L, false));
	}

	@Test
	void aHitThatBypassesTheCooldownCountsInFullAndRestartsIt() {
		GuardianCooldown cooldown = new GuardianCooldown();
		cooldown.count(4.0F, 100L, false);
		assertEquals(3.0F, cooldown.count(3.0F, 101L, true), "a bypassing hit isn't cut down");
		assertEquals(1.0F, cooldown.count(4.0F, 102L, false), "and it set the new mark: 4 - 3");
	}

	@Test
	void aRaisedShieldCantBeFarmed() {
		// One zombie trapped in front of a raised shield swings every 20 ticks for an hour: each swing starts a new
		// cooldown and counts once, never more often.
		GuardianCooldown cooldown = new GuardianCooldown();
		double counted = 0.0;

		for (long tick = 0; tick < 72_000L; tick++) {
			// A crowd of five, all swinging on the same tick.
			for (int zombie = 0; zombie < 5 && tick % 20 == 0; zombie++) {
				counted += cooldown.count(3.0F, tick, false);
			}
		}

		assertEquals(3.0 * 72_000 / 20, counted, 1e-6, "one hit's worth per swing, not five");
	}

	@Test
	void negativeOrBrokenDamageCountsAsNothing() {
		assertEquals(0.0F, new GuardianCooldown().count(-3.0F, 100L, false));
		assertEquals(0.0F, new GuardianCooldown().count(Float.NaN, 100L, false));

		GuardianCooldown cooldown = new GuardianCooldown();
		cooldown.count(Float.NaN, 100L, false);
		assertEquals(2.0F, cooldown.count(2.0F, 101L, false), "a NaN hit set the mark to 0, not NaN");
	}
}
