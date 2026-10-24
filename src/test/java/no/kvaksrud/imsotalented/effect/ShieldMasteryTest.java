package no.kvaksrud.imsotalented.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import no.kvaksrud.imsotalented.talent.TalentValues;
import org.junit.jupiter.api.Test;

class ShieldMasteryTest {
	@Test
	void raiseDelayShrinksToNothing() {
		// A vanilla shield needs 5 ticks; each level takes a third off (3.3 and 1.7 round to 3 and 2).
		int[] expected = {5, 3, 2, 0};

		for (int level = 0; level <= 3; level++) {
			assertEquals(expected[level], ShieldMastery.reducedTicks(5, TalentValues.shieldRaiseReduction(level)), "level " + level);
		}
	}

	@Test
	void raiseDelayRoundsToTheNearestTick() {
		assertEquals(6, ShieldMastery.reducedTicks(8, 0.2), "6.4 ticks");
		assertEquals(7, ShieldMastery.reducedTicks(9, 0.2), "7.2 ticks");
		assertEquals(0, ShieldMastery.reducedTicks(0, 0.6));
	}

	@Test
	void axeDisablesForLess() {
		// An axe disables a shield for 5 seconds; Shield Mastery III takes 75% off.
		assertEquals(1.25F, ShieldMastery.reducedSeconds(5.0F, TalentValues.shieldDisableReduction(3)), 1e-5F);
		assertEquals(5.0F, ShieldMastery.reducedSeconds(5.0F, TalentValues.shieldDisableReduction(0)), 1e-5F);
	}
}
