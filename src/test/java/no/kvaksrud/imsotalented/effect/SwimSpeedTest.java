package no.kvaksrud.imsotalented.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SwimSpeedTest {
	@Test
	void noLevelIsTheIdentity() {
		assertEquals(0.8f, SwimSpeed.dampFactor(0.8f, 0), 1e-9, "level 0 leaves the vanilla factor alone");
		assertEquals(0.8f, SwimSpeed.dampFactor(0.8f, -1), 1e-9, "negative levels count as none");
		assertEquals(0.9f, SwimSpeed.dampFactor(0.9f, 0), 1e-9, "the sprinting base is untouched too");
	}

	@Test
	void theDampingIsSoftenedPerLevel() {
		// L3: 1 - (1 - 0.8)/(1 + 0.3); the higher the level, the gentler the per-tick damping.
		assertEquals(1.0 - 0.2 / 1.1, SwimSpeed.dampFactor(0.8f, 1), 1e-6, "L1");
		assertEquals(1.0 - 0.2 / 1.2, SwimSpeed.dampFactor(0.8f, 2), 1e-6, "L2");
		assertEquals(1.0 - 0.2 / 1.3, SwimSpeed.dampFactor(0.8f, 3), 1e-6, "top level L3");
	}

	@Test
	void theImpliedMovementIsTimesOnePlusBonus() {
		// Sustained movement is inversely proportional to 1 - factor, so the mapping must give exactly
		// 1 + 0.1 per level of movement, for every base factor the vanilla travel path uses.
		for (float base : new float[] {0.8f, 0.9f, 0.96f}) {
			for (int level = 1; level <= 3; level++) {
				float factor = SwimSpeed.dampFactor(base, level);
				double ratio = (1.0 - base) / (1.0 - factor);
				assertEquals(1.0 + 0.1 * level, ratio, 1e-6, "base " + base + " at L" + level);
			}
		}
	}

	@Test
	void theFactorAlwaysStaysBelowOne() {
		// A factor of 1 or more would re-amplify the velocity every tick and run away; the mapping must
		// never do that, and a speed-up must soften (never strengthen) the damping.
		for (float base : new float[] {0.8f, 0.9f, 0.96f}) {
			for (int level = 0; level <= 3; level++) {
				float factor = SwimSpeed.dampFactor(base, level);
				assertTrue(factor < 1.0f, "factor " + factor + " must stay < 1 (base " + base + ", L" + level + ")");
				assertTrue(level > 0 ? factor > base : factor == base,
						"a speed-up softens the damping, base " + base + " L" + level + " -> " + factor);
			}
		}
	}
}
