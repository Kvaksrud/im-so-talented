package no.kvaksrud.imsotalented.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.player.Input;

/** The tree XP rules from the design's table, as the pure functions the game hooks hand their inputs to. */
class TreeXpTest {
	// Gatherer

	@Test
	void oresPayFivePoints() {
		assertEquals(5.0, TreeXp.blockXp(true, true, false), 1e-9, "an ore with the right pickaxe");
	}

	@Test
	void otherBlocksPayNothing() {
		assertEquals(0.0, TreeXp.blockXp(false, true, false), 1e-9, "stone with a pickaxe, a log with an axe, dirt with a shovel");
		assertEquals(0.0, TreeXp.blockXp(false, false, false), 1e-9, "stone with a fist");
	}

	@Test
	void silkTouchOresPayNothing() {
		assertEquals(0.0, TreeXp.blockXp(true, true, true), 1e-9, "Silk Touch keeps the ore to place and mine again");
	}

	@Test
	void theWrongToolPaysNothing() {
		assertEquals(0.0, TreeXp.blockXp(true, false, false), 1e-9, "diamond ore with a stone pickaxe");
	}

	@Test
	void matureCropsPayOncePerHarvest() {
		assertEquals(1.0, TreeXp.cropXp(1, 7, 7), 1e-9, "mature wheat");
		assertEquals(1.0, TreeXp.cropXp(4, 7, 7), 1e-9, "mature carrots: one harvest, not one per carrot");
		assertEquals(1.0, TreeXp.cropXp(3, 2, 2), 1e-9, "ripe cocoa");
	}

	@Test
	void youngOrEmptyCropsPayNothing() {
		assertEquals(0.0, TreeXp.cropXp(1, 3, 7), 1e-9, "a young carrot drops a carrot, but can be replanted at once");
		assertEquals(0.0, TreeXp.cropXp(0, 7, 7), 1e-9, "a harvest without the main item");
		assertEquals(0.0, TreeXp.cropXp(0, 0, 7), 1e-9);
	}

	@Test
	void eachCatchPaysFivePoints() {
		assertEquals(5.0, TreeXp.catchXp(1), 1e-9);
		assertEquals(5.0, TreeXp.catchXp(2), 1e-9, "one reel-in is one catch, however many stacks it brings");
		assertEquals(0.0, TreeXp.catchXp(0), 1e-9, "nothing on the hook");
	}

	// Warrior

	@Test
	void warriorCountsTheHealthTheMobLost() {
		assertEquals(6.0, TreeXp.warriorXp(20.0F, 14.0F), 1e-6, "a 6-damage hit on a full zombie");
		assertEquals(2.0, TreeXp.warriorXp(2.0F, 0.0F), 1e-6, "the killing blow counts");
	}

	@Test
	void overkillIsCappedAtTheHealthLeft() {
		assertEquals(2.0, TreeXp.warriorXp(2.0F, -18.0F), 1e-6, "a mace smash on a zombie at 2 health pays 2");
	}

	@Test
	void hitsThatDealNothingPayNothing() {
		assertEquals(0.0, TreeXp.warriorXp(20.0F, 20.0F), 1e-9, "blocked, or ignored in the damage cooldown");
		assertEquals(0.0, TreeXp.warriorXp(10.0F, 12.0F), 1e-9, "healed during the hit");
		assertEquals(0.0, TreeXp.warriorXp(Float.NaN, 0.0F), 1e-9);
		assertEquals(0.0, TreeXp.warriorXp(Float.POSITIVE_INFINITY, 0.0F), 1e-9);
	}

	// Guardian

	@Test
	void guardianCountsTheRawDamageTwice() {
		assertEquals(12.0, TreeXp.guardianXp(6.0F, false, 0.0F, 20.0F), 1e-6, "6 damage aimed at the player, armor or not");
		assertEquals(1.0, TreeXp.guardianXp(0.5F, false, 0.0F, 20.0F), 1e-6);
	}

	@Test
	void inTheDamageCooldownOnlyTheHarderPartCounts() {
		assertEquals(4.0, TreeXp.guardianXp(6.0F, true, 4.0F, 20.0F), 1e-6, "vanilla takes 6 - 4");
		assertEquals(0.0, TreeXp.guardianXp(4.0F, true, 4.0F, 20.0F), 1e-9, "not harder than the last hit: vanilla ignores it");
		assertEquals(0.0, TreeXp.guardianXp(3.0F, true, 4.0F, 20.0F), 1e-9, "a crowd swinging at once pays for the hits that land");
	}

	@Test
	void oneHitCountsForAtMostTheMaxHealth() {
		assertEquals(40.0, TreeXp.guardianXp(60.0F, false, 0.0F, 20.0F), 1e-6, "a point-blank creeper on a player with 20 health");
		assertEquals(60.0, TreeXp.guardianXp(60.0F, false, 0.0F, 30.0F), 1e-6, "More Health raises the cap");
	}

	@Test
	void harmlessHitsPayNothing() {
		assertEquals(0.0, TreeXp.guardianXp(0.0F, false, 0.0F, 20.0F), 1e-9);
		assertEquals(0.0, TreeXp.guardianXp(-2.0F, false, 0.0F, 20.0F), 1e-9);
		assertEquals(0.0, TreeXp.guardianXp(Float.NaN, false, 0.0F, 20.0F), 1e-9);
	}

	// Explorer

	@Test
	void walkingAndSwimmingCountInFull() {
		assertEquals(1.0, factor(false, false, false, true, false, false, true), 1e-9, "walking");
		assertEquals(1.0, factor(false, false, false, true, false, false, false), 1e-9, "mid-jump, or running off a ledge");
		assertEquals(1.0, factor(false, false, false, true, true, true, false), 1e-9, "swimming");
		assertEquals(1.0, factor(false, false, false, true, false, true, true), 1e-9, "wading on the bottom");
	}

	@Test
	void glidingCountsAtHalfWithOrWithoutKeys() {
		assertEquals(0.5, factor(false, false, true, false, false, false, false), 1e-9, "a glide is steered by looking");
		assertEquals(0.5, factor(false, false, true, true, false, false, false), 1e-9);
	}

	@Test
	void travelThatIsntTheirOwnPaysNothing() {
		assertEquals(0.0, factor(true, false, false, true, false, false, true), 1e-9, "riding a horse, a boat or a minecart");
		assertEquals(0.0, factor(false, true, false, true, false, false, false), 1e-9, "creative flight");
		assertEquals(0.0, factor(false, false, false, false, false, false, true), 1e-9, "pushed by a piston, a mob or knockback");
		assertEquals(0.0, factor(false, false, false, true, false, true, false), 1e-9, "floating in a current");
		assertEquals(0.0, factor(false, false, false, false, true, true, false), 1e-9, "carried along in the swimming pose");
	}

	@Test
	void movementKeysAreTheSteering() {
		assertTrue(TreeXp.isSteering(new Input(true, false, false, false, false, false, false)));
		assertTrue(TreeXp.isSteering(new Input(false, true, false, false, false, false, false)));
		assertTrue(TreeXp.isSteering(new Input(false, false, true, false, false, false, false)));
		assertTrue(TreeXp.isSteering(new Input(false, false, false, true, false, false, false)));
		assertFalse(TreeXp.isSteering(Input.EMPTY));
		assertFalse(TreeXp.isSteering(new Input(false, false, false, false, true, true, true)), "jump, sneak and sprint go nowhere alone");
	}

	@Test
	void travelPaysOnePointPer25Blocks() {
		double total = 0.0;

		for (int tick = 0; tick < 25; tick++) {
			total += TreeXp.travelXp(1.0, 0.0, 1.0);
		}

		assertEquals(1.0, total, 1e-9, "25 blocks walked");
		assertEquals(0.2, TreeXp.travelXp(3.0, -4.0, 1.0), 1e-9, "horizontal distance, in any direction");
		assertEquals(0.1, TreeXp.travelXp(3.0, 4.0, 0.5), 1e-9, "gliding at half");
		assertEquals(TreeXp.MAX_STEP / 25.0, TreeXp.travelXp(TreeXp.MAX_STEP, 0.0, 1.0), 1e-9, "a fast elytra still counts");
	}

	@Test
	void teleportsAndStandingStillPayNothing() {
		assertEquals(0.0, TreeXp.travelXp(TreeXp.MAX_STEP + 0.01, 0.0, 1.0), 1e-9, "an ender pearl, a command, a portal");
		assertEquals(0.0, TreeXp.travelXp(0.0, 0.0, 1.0), 1e-9);
		assertEquals(0.0, TreeXp.travelXp(3.0, 4.0, 0.0), 1e-9, "riding");
		assertEquals(0.0, TreeXp.travelXp(Double.NaN, 0.0, 1.0), 1e-9);
	}

	private static double factor(boolean passenger, boolean flying, boolean gliding, boolean steering, boolean swimming,
			boolean inLiquid, boolean onGround) {
		return TreeXp.movementFactor(passenger, flying, gliding, steering, swimming, inLiquid, onGround);
	}
}
