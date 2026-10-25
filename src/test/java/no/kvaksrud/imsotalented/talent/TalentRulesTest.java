package no.kvaksrud.imsotalented.talent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import no.kvaksrud.imsotalented.talent.TalentRules.EnchantmentStacking;
import no.kvaksrud.imsotalented.talent.TalentRules.Progress;
import org.junit.jupiter.api.Test;

import net.minecraft.network.codec.ByteBufCodecs;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

class TalentRulesTest {
	private static final TalentRules RULES = TalentRules.DEFAULTS;

	@Test
	void everyLevelCostsAtLeastTheLast() {
		assertEquals(25, RULES.cost(0), "level 1 takes cost_base");
		assertEquals(30, RULES.cost(1), "25 * 1.18 = 29.5, rounded up");
		assertEquals(35, RULES.cost(2));
		assertEquals(42, RULES.cost(3));
		assertEquals(111, RULES.cost(9), "level 10 takes 111, as the design says");
		assertEquals(581, RULES.cost(19), "level 20 takes 581");

		for (int i = 0; i < Talent.totalLevels(); i++) {
			assertTrue(RULES.cost(i + 1) >= RULES.cost(i), "level " + (i + 2) + " must not cost less than level " + (i + 1));
		}
	}

	@Test
	void eachTreeTakesTheDesignedXpToMax() {
		assertEquals(27_602, RULES.xpForLevel(TalentTree.GATHERER.maxLevel()));
		assertEquals(16_749, RULES.xpForLevel(TalentTree.WARRIOR.maxLevel()));
		assertEquals(23_372, RULES.xpForLevel(TalentTree.GUARDIAN.maxLevel()));
		assertEquals(19_787, RULES.xpForLevel(TalentTree.EXPLORER.maxLevel()));
		assertEquals(4_230, RULES.cost(TalentTree.GATHERER.maxLevel() - 1), "Gatherer's last level");
	}

	@Test
	void xpForLevelSumsTheLevelsBelow() {
		assertEquals(0, RULES.xpForLevel(0));
		assertEquals(25, RULES.xpForLevel(1));
		assertEquals(25 + 30, RULES.xpForLevel(2));
		assertEquals(25 + 30 + 35, RULES.xpForLevel(3));
		assertEquals(0, RULES.xpForLevel(-4), "a negative level is level 0");
	}

	@Test
	void levelForFindsTheLevelReached() {
		int max = TalentTree.GATHERER.maxLevel();
		assertEquals(0, RULES.levelFor(0, max));
		assertEquals(0, RULES.levelFor(24, max), "one short of level 1");
		assertEquals(1, RULES.levelFor(25, max));
		assertEquals(1, RULES.levelFor(54, max));
		assertEquals(2, RULES.levelFor(55, max));
		assertEquals(0, RULES.levelFor(-100, max), "negative XP is level 0");

		for (int level = 1; level <= max; level++) {
			long xp = RULES.xpForLevel(level);
			assertEquals(level, RULES.levelFor(xp, max), "exactly the XP of level " + level);
			assertEquals(level - 1, RULES.levelFor(xp - 1, max), "one XP short of level " + level);
		}
	}

	@Test
	void levelForStopsAtTheTreesTop() {
		int max = TalentTree.WARRIOR.maxLevel();
		assertEquals(max, RULES.levelFor(RULES.xpForLevel(max), max));
		assertEquals(max, RULES.levelFor(Long.MAX_VALUE, max), "more XP than the top never goes past it");
		assertEquals(0, RULES.levelFor(Long.MAX_VALUE, 0), "a tree without levels stays at 0");
	}

	@Test
	void freeLevelsReachTheTopWithNoXp() {
		TalentRules free = new TalentRules(0, 1.18, EnchantmentStacking.HIGHEST);
		assertEquals(TalentTree.GUARDIAN.maxLevel(), free.levelFor(0, TalentTree.GUARDIAN.maxLevel()));
		assertTrue(free.progress(0, TalentTree.GUARDIAN.maxLevel()).maxed());
	}

	@Test
	void progressSplitsTheXpIntoLevelAndBar() {
		int max = TalentTree.EXPLORER.maxLevel();
		assertEquals(new Progress(0, 0, 25, false), RULES.progress(0, max), "a new tree: empty bar toward level 1");
		assertEquals(new Progress(1, 5, 30, false), RULES.progress(30, max), "5 XP into level 1's 30");
		assertEquals(5.0F / 30.0F, RULES.progress(30, max).fraction(), 1e-6F);
		assertEquals(new Progress(2, 0, 35, false), RULES.progress(55, max), "a level just reached starts an empty bar");
	}

	@Test
	void progressAtTheTopIsAFullBar() {
		int max = TalentTree.EXPLORER.maxLevel();
		Progress top = RULES.progress(RULES.xpForLevel(max), max);
		assertEquals(new Progress(max, 0, 0, true), top);
		assertEquals(1.0F, top.fraction());
		assertEquals(top, RULES.progress(Long.MAX_VALUE, max), "past the top is still the top");

		Progress almost = RULES.progress(RULES.xpForLevel(max) - 1, max);
		assertFalse(almost.maxed());
		assertEquals(max - 1, almost.level());
		assertEquals(almost.needed() - 1, almost.into(), "one XP short of the top");
	}

	@Test
	void theBarNeverLeavesZeroToOne() {
		assertEquals(0.0F, new Progress(3, -5, 30, false).fraction());
		assertEquals(1.0F, new Progress(3, 50, 30, false).fraction());
		assertEquals(1.0F, new Progress(3, 0, 0, false).fraction(), "a level that takes nothing is full");
	}

	@Test
	void flatGrowthKeepsTheBasePrice() {
		TalentRules rules = new TalentRules(10, 1.0, EnchantmentStacking.HIGHEST);

		for (int i = 0; i <= Talent.totalLevels(); i++) {
			assertEquals(10, rules.cost(i), "growth 1.0 must keep every level at the base price");
		}
	}

	@Test
	void doublingGrowthClampsToTheIntCeiling() {
		TalentRules rules = new TalentRules(10, 2.0, EnchantmentStacking.HIGHEST);
		assertEquals(1_342_177_280, rules.cost(27), "10 * 2^27 still fits in an int");
		assertEquals(Integer.MAX_VALUE, rules.cost(28), "10 * 2^28 overflows int: the clamp engages");

		for (int i = 29; i <= Talent.totalLevels(); i++) {
			assertEquals(Integer.MAX_VALUE, rules.cost(i), "the clamp holds for every later level");
		}

		assertEquals(TalentTree.GUARDIAN.maxLevel(), rules.levelFor(Long.MAX_VALUE, TalentTree.GUARDIAN.maxLevel()),
				"the sum of clamped levels still fits a long");
	}

	@Test
	void totalCostSumsTheSteps() {
		assertEquals(25 + 30 + 35, RULES.totalCost(0, 3));
		assertEquals(35 + 42, RULES.totalCost(2, 2));
		assertEquals(0, RULES.totalCost(5, 0));
	}

	@Test
	void costNeverOverflows() {
		TalentRules rules = new TalentRules(1_000_000, 2.0, EnchantmentStacking.HIGHEST);
		assertEquals(1_000_000, rules.cost(0));
		assertEquals(2_048_000_000, rules.cost(11), "1_000_000 * 2^11 still fits in an int");
		assertEquals(Integer.MAX_VALUE, rules.cost(12), "1_000_000 * 2^12 overflows int");
		assertEquals(Integer.MAX_VALUE, rules.cost(Integer.MAX_VALUE), "even an absurd level stays int-safe");
		assertEquals(1_000_000, rules.cost(-3), "a negative level is treated as level 0");
		assertEquals(10, new TalentRules(10, Double.NaN, EnchantmentStacking.HIGHEST).cost(0),
				"the first level is always costBase, even for a garbage growth");
		assertEquals(Integer.MAX_VALUE, new TalentRules(10, Double.NaN, EnchantmentStacking.HIGHEST).cost(1),
				"a NaN result clamps to the ceiling instead of escaping as garbage");
	}

	@Test
	void highestKeepsTheGearsLevel() {
		assertEquals(3, EnchantmentStacking.HIGHEST.combine(1, 3), "Fortune I pick + More Luck III = Fortune III");
		assertEquals(3, EnchantmentStacking.HIGHEST.combine(3, 2), "Fortune III pick + More Luck II stays Fortune III");
		assertEquals(3, RULES.combine(3, 2), "highest is the default");
		assertEquals(0, EnchantmentStacking.HIGHEST.combine(0, 0));
	}

	@Test
	void addPutsThemTogether() {
		assertEquals(6, EnchantmentStacking.ADD.combine(3, 3), "Fortune III pick + More Luck III = Fortune VI");
		assertEquals(3, EnchantmentStacking.ADD.combine(3, 0));
		assertEquals(2, EnchantmentStacking.ADD.combine(-1, 2), "negative levels count as 0");
	}

	@Test
	void stackingIdsParse() {
		assertEquals(EnchantmentStacking.ADD, EnchantmentStacking.byId(" Add ").orElseThrow());
		assertEquals(EnchantmentStacking.HIGHEST, EnchantmentStacking.byId("highest").orElseThrow());
		assertTrue(EnchantmentStacking.byId("max").isEmpty());
	}

	@Test
	void theCachedThresholdsMatchTheSumOfTheLevels() {
		// The first 128 levels' thresholds are cached per set of rules; past them they're summed. Both must agree.
		for (TalentRules rules : List.of(RULES, new TalentRules(10, 1.0, EnchantmentStacking.ADD), new TalentRules(1_000_000, 2.0, EnchantmentStacking.HIGHEST),
				new TalentRules(0, 1.5, EnchantmentStacking.HIGHEST))) {
			for (int level = 0; level <= 200; level++) {
				assertEquals(rules.totalCost(0, level), rules.xpForLevel(level), rules + " at level " + level);
			}
		}

		assertEquals(RULES.xpForLevel(32), new TalentRules(25, 1.18, EnchantmentStacking.ADD).xpForLevel(32),
				"the stacking rule plays no part in the prices");
	}

	@Test
	void theRulesSyncWithEveryField() {
		for (TalentRules rules : List.of(RULES, new TalentRules(40, 1.5, EnchantmentStacking.ADD), new TalentRules(0, 2.0, EnchantmentStacking.HIGHEST))) {
			ByteBuf buffer = Unpooled.buffer();

			try {
				TalentRules.STREAM_CODEC.encode(buffer, rules);
				assertEquals(rules, TalentRules.STREAM_CODEC.decode(buffer));
				assertEquals(0, buffer.readableBytes(), "the whole message is read");
			} finally {
				buffer.release();
			}
		}
	}

	@Test
	void anUnknownStackingIdReadsAsHighest() {
		// A newer or broken server's id must not fail the whole packet (and disconnect the client).
		for (int id : new int[] {2, 7, 1_000_000, -1}) {
			ByteBuf buffer = Unpooled.buffer();

			try {
				ByteBufCodecs.VAR_INT.encode(buffer, 30);
				ByteBufCodecs.DOUBLE.encode(buffer, 1.25);
				ByteBufCodecs.VAR_INT.encode(buffer, id);
				assertEquals(new TalentRules(30, 1.25, EnchantmentStacking.HIGHEST), TalentRules.STREAM_CODEC.decode(buffer), "stacking id " + id);
				assertEquals(0, buffer.readableBytes());
			} finally {
				buffer.release();
			}
		}
	}
}
