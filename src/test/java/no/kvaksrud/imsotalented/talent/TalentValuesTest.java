package no.kvaksrud.imsotalented.talent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class TalentValuesTest {
	@Test
	void enchantmentTalentsReachTheVanillaMaximum() {
		assertEquals(5, TalentValues.efficiencyLevel(Talent.FASTER_MINING.maxLevel()), "Faster Mining V = Efficiency V");
		assertEquals(3, TalentValues.luckLevel(Talent.MORE_LUCK.maxLevel()), "More Luck III = Fortune, Looting, Luck of the Sea III");
		assertEquals(3, TalentValues.unbreakingBonus(Talent.CAREFUL_HANDS.maxLevel()), "Careful Hands III = Unbreaking III");
		assertEquals(3, TalentValues.respirationLevel(Talent.WATER_BREATHING.maxLevel()), "Water Breathing III = Respiration III");
	}

	@Test
	void topLevelsMatchTheDesign() {
		assertEquals(1.3, TalentValues.treeXpMultiplier(Talent.GATHERER_XP.maxLevel()), 1e-9, "+30% tree XP at the top level");
		assertEquals(1.0, TalentValues.treeXpMultiplier(0), 1e-9, "no talent, no bonus");
		assertEquals(10.0, TalentValues.extraHealth(Talent.MORE_HEALTH.maxLevel()), 1e-9, "5 hearts more at the top level");
		assertEquals(0.25, TalentValues.armorBonus(Talent.STRONGER_ARMOR.maxLevel()), 1e-9, "25% more armor");
		assertEquals(0.10, TalentValues.blockChance(Talent.BLOCK_CHANCE.maxLevel()), 1e-9, "10% block chance");
		assertEquals(1.0, TalentValues.shieldRaiseReduction(Talent.SHIELD_MASTERY.maxLevel()), 1e-9, "a shield blocks at once");
		assertEquals(0.75, TalentValues.shieldDisableReduction(Talent.SHIELD_MASTERY.maxLevel()), 1e-9, "axe disable 75% shorter");
		assertEquals(0.20, TalentValues.weaponBonus(Talent.STRONGER_WEAPONS.maxLevel()), 1e-9, "+20% damage");
		assertEquals(0.30, TalentValues.specialistBonus(Talent.SWORD_SPECIALIST.maxLevel()), 1e-9, "+30% with the weapon");
		assertEquals(1.5, TalentValues.unarmedDamage(Talent.UNARMED_SPECIALIST.maxLevel()), 1e-9, "+1.5 punch damage, like an iron sword");
		assertEquals(0.20, TalentValues.speedBonus(Talent.FASTER_RUNNING.maxLevel()), 1e-9, "+20% speed");
		assertEquals(0.30, TalentValues.swimSpeedBonus(Talent.SWIM_SPEED.maxLevel()), 1e-9, "+30% swim speed");
		assertEquals(3, TalentValues.harvestBonus(Talent.BOUNTIFUL_HARVEST.maxLevel()), "mature wheat 1 -> 4");
		assertEquals(2.5, TalentValues.growthMultiplier(Talent.FAST_GROWTH.maxLevel()), 1e-9, "crops grow 2.5x");
		assertEquals(1.0, 0.6 + TalentValues.stepBonus(Talent.STEP_ASSIST.maxLevel()), 1e-9, "one level climbs full blocks");
		assertEquals(1.5, TalentValues.reachBonus(Talent.REACH.maxLevel()), 1e-9, "+1.5 block reach");
		assertEquals(0.5, TalentValues.entityReachBonus(Talent.REACH.maxLevel()), 1e-9, "+0.5 entity reach");
		assertEquals(10.0, TalentValues.extraSafeFall(Talent.SAFE_FALLING.maxLevel()), 1e-9);
		assertEquals(0.5, TalentValues.fallDamageReduction(Talent.SAFE_FALLING.maxLevel()), 1e-9);
	}

	@Test
	void talentsWithoutAnEffectYetHaveTheirNumbers() {
		assertEquals(0.15, TalentValues.lifestealFraction(Talent.LIFESTEAL.maxLevel()), 1e-9, "15% of damage dealt");
		assertEquals(0.45, TalentValues.secondWindRegen(Talent.SECOND_WIND.maxLevel()), 1e-9, "about Regeneration I");
		assertEquals(10.0, TalentValues.fireResistDuration(1), 1e-9);
		assertEquals(30.0, TalentValues.fireResistDuration(Talent.FIRE_RESISTANCE.maxLevel()), 1e-9, "10 / 20 / 30 seconds");
		assertEquals(0.4, TalentValues.glideMaxFall(Talent.GLIDING.maxLevel()), 1e-9, "0.4 blocks/tick at the top level");
		assertEquals(0.8, TalentValues.glideMaxFall(1), 1e-9);
		assertEquals(Double.POSITIVE_INFINITY, TalentValues.glideMaxFall(0), "no cap without the talent: vanilla falls at up to about 3.9");
		assertEquals("\u221e", TalentValues.effectArgs(Talent.GLIDING, 0)[0], "an uncapped fall formats instead of throwing");
		assertEquals(1.25, TalentValues.flightSpeedMultiplier(Talent.FLIGHT_SPEED.maxLevel()), 1e-9, "+25% at the top level");
		assertEquals(1, Talent.NIGHT_VISION.maxLevel(), "one level, switched with the toggle");
	}

	@Test
	void vanillasNumbers() {
		assertEquals(0.42, TalentValues.BASE_JUMP_STRENGTH);
		assertEquals(0.6, TalentValues.BASE_STEP_HEIGHT);
		assertEquals(0.08, TalentValues.GRAVITY);
		assertEquals(0.98, TalentValues.AIR_DRAG);
		assertEquals(1.0, TalentValues.BASE_STEP_HEIGHT + TalentValues.stepBonus(Talent.STEP_ASSIST.maxLevel()), 1e-9, "Step Assist climbs full blocks");
	}

	@Test
	void vanillaJumpIsAboutOneAndAQuarterBlocks() {
		assertEquals(1.25, TalentValues.jumpHeight(TalentValues.BASE_JUMP_STRENGTH), 0.01);
		double top = TalentValues.jumpHeight(TalentValues.BASE_JUMP_STRENGTH + TalentValues.jumpStrengthBonus(Talent.HIGHER_JUMPING.maxLevel()));
		assertTrue(top > 2.0 && top < 2.3, "about two blocks at the top level, got " + top);
	}

	/** Every level's effect text gets exactly the numbers its translation asks for, or the tree shows "%s" or drops one. */
	@Test
	void everyTalentsEffectTextGetsItsNumbers() throws IOException {
		JsonObject lang = english();

		for (Talent talent : Talent.values()) {
			assertTrue(lang.has(talent.translationKey()), "no name for " + talent.id());
			assertTrue(lang.has(talent.descriptionKey()), "no description for " + talent.id());
			assertTrue(lang.has(talent.effectKey()), "no effect text for " + talent.id());

			// The tree shows the effect from level 1 (the next level's, before anything is bought) to the top.
			for (int level = 1; level <= talent.maxLevel(); level++) {
				String key = TalentValues.effectKey(talent, level);
				assertTrue(lang.has(key), "no effect text " + key);
				assertEquals(placeholders(lang.get(key).getAsString()), TalentValues.effectArgs(talent, level).length, talent.id() + " at level " + level);
			}
		}

		for (TalentTree tree : TalentTree.values()) {
			assertTrue(lang.has(tree.translationKey()), "no name for the " + tree.id() + " tree");
		}
	}

	@Test
	void numbersAreFormattedForReading() {
		assertEquals("20", TalentValues.percent(0.2));
		assertEquals("30", TalentValues.percent(TalentValues.treeXpMultiplier(3) - 1.0), "no floating point noise");
		assertEquals("2.5", TalentValues.number(2.5));
		assertEquals("7", TalentValues.number(7.0));
		assertEquals("0.45", TalentValues.number(0.45, 2));
	}

	@Test
	void romanNumerals() {
		assertEquals("0", TalentValues.roman(0));
		assertEquals("I", TalentValues.roman(1));
		assertEquals("III", TalentValues.roman(3));
		assertEquals("IV", TalentValues.roman(4));
		assertEquals("V", TalentValues.roman(5));
		assertEquals("VIII", TalentValues.roman(8));
		assertEquals("X", TalentValues.roman(10));
	}

	private static JsonObject english() throws IOException {
		try (InputStream in = TalentValuesTest.class.getResourceAsStream("/assets/imsotalented/lang/en_us.json")) {
			assertNotNull(in, "the English lang file is on the test classpath");

			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		}
	}

	/** How many {@code %s} (or {@code %1$s}) a translation fills in; {@code %%} is a plain percent sign. */
	private static int placeholders(String text) {
		return text.replace("%%", "").split("%(\\d+\\$)?s", -1).length - 1;
	}
}
