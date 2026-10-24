package no.kvaksrud.imsotalented.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FastGrowthTest {
	private static final double WHEAT = 1.0 / 26.0; // vanilla wheat bound 25/growthSpeed + 1 = 26

	@Test
	void levelZeroIsExactlyVanilla() {
		assertEquals(WHEAT, FastGrowth.growthChance(WHEAT, 0), 1e-12);
		assertEquals(0.1, FastGrowth.growthChance(0.1, 0), 1e-12, "no level, no multiplier");
	}

	@Test
	void theMultiplierScalesWithTheLevel() {
		assertEquals(WHEAT * 1.5, FastGrowth.growthChance(WHEAT, 1), 1e-12, "L1 = 1.5x");
		assertEquals(WHEAT * 2.5, FastGrowth.growthChance(WHEAT, 3), 1e-12, "top level L3 = 2.5x");
	}

	@Test
	void theChanceNeverExceedsOne() {
		assertEquals(1.0, FastGrowth.growthChance(0.5, 3), 1e-12, "0.5 x 2.5 clamps to 1.0");
		assertEquals(1.0, FastGrowth.growthChance(1.0, 1), 1e-12);
		assertTrue(FastGrowth.growthChance(0.9, 3) <= 1.0);
	}

	@Test
	void theExtraChanceIsZeroWithoutTheTalent() {
		assertEquals(0.0, FastGrowth.extraChance(26, 0), 1e-12);
		assertEquals(0.0, FastGrowth.extraChance(26, -1), 1e-12);
		assertEquals(0.0, FastGrowth.extraChance(1, 3), 1e-12, "bound 1: vanilla already grows always");
	}

	@Test
	void theExtraChanceIsTheConditionalRemainder() {
		// Given the vanilla roll failed (probability 25/26), the talent must make the TOTAL chance
		// 2.5/26: extra = (2.5/26 - 1/26) / (25/26).
		double expected = (WHEAT * 2.5 - WHEAT) / (1.0 - WHEAT);
		assertEquals(expected, FastGrowth.extraChance(26, 3), 1e-12);
		assertTrue(expected > 0.0);
	}

	@Test
	void aVanillaGrowAlwaysStands() {
		assertEquals(0, FastGrowth.resolve(0, 26, 0, 0.99F), "roll 0 grows regardless of talent or draw");
		assertEquals(0, FastGrowth.resolve(0, 26, 3, 0.99F));
	}

	@Test
	void levelZeroNeverGrowsOnTheExtraDraw() {
		assertEquals(7, FastGrowth.resolve(7, 26, 0, 0.0F), "no talent: the failed roll stays failed");
	}

	@Test
	void theTalentCanFlipAFailedRoll() {
		assertEquals(0, FastGrowth.resolve(5, 26, 3, 0.0F), "a draw below the extra chance grows");
		assertEquals(5, FastGrowth.resolve(5, 26, 3, 0.99F), "a draw above it does not");
	}

	@Test
	void higherLevelsAreNeverLessLikelyToGrow() {
		// For a fixed failed roll and a sweep of draws, L3 grows at least as often as L1 grows.
		int grown1 = 0;
		int grown3 = 0;

		for (int i = 0; i < 1000; i++) {
			float draw = (float) (i / 999.0);

			if (FastGrowth.resolve(5, 26, 1, draw) == 0) {
				grown1++;
			}

			if (FastGrowth.resolve(5, 26, 3, draw) == 0) {
				grown3++;
			}
		}

		assertTrue(grown3 >= grown1, "L3 grew " + grown3 + "x, L1 grew " + grown1 + "x of 1000 draws");
	}
}
