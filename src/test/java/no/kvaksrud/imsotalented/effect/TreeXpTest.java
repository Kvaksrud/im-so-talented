package no.kvaksrud.imsotalented.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import no.kvaksrud.imsotalented.talent.TalentValues;
import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.player.Input;

/** The tree XP rules from the design's table, as the pure functions the game hooks hand their inputs to. */
class TreeXpTest {
	// Gatherer

	/** Never paid a plain pickaxe block before: the pacing doesn't hold anything back. */
	private static final long NEVER = Long.MAX_VALUE;

	@Test
	void theDesignsGathererRates() {
		assertEquals(5.0, TalentValues.GATHERER_XP_PER_ORE, "an ore");
		assertEquals(0.5, TalentValues.GATHERER_XP_PER_BLOCK, "a tenth of an ore for other pickaxe blocks");
		assertEquals(10, TalentValues.GATHERER_BLOCK_XP_MIN_TICKS, "at most one paid plain block every half second");
		assertEquals(1.0, TalentValues.GATHERER_XP_PER_CROP);
		assertEquals(5.0, TalentValues.GATHERER_XP_PER_FISH);
	}

	@Test
	void oresPayFivePoints() {
		assertEquals(5.0, TreeXp.blockXp(true, true, true, false, NEVER), 1e-9, "an ore with the right pickaxe");
	}

	@Test
	void otherPickaxeBlocksPayATenthOfAnOre() {
		assertEquals(0.5, TreeXp.blockXp(false, true, true, false, NEVER), 1e-9, "stone, deepslate or netherrack with a pickaxe");
	}

	@Test
	void blocksForOtherToolsPayNothing() {
		assertEquals(0.0, TreeXp.blockXp(false, false, true, false, NEVER), 1e-9, "sand or dirt with a shovel, a log with an axe");
	}

	@Test
	void silkTouchOresPayLikeStone() {
		assertTrue(TreeXp.paysAsOre(true, false));
		assertFalse(TreeXp.paysAsOre(true, true), "Silk Touch keeps the ore to place and mine again");
		assertFalse(TreeXp.paysAsOre(false, false));
		assertEquals(0.5, TreeXp.blockXp(true, true, true, true, NEVER), 1e-9, "a Silk Touch ore pays like stone");
	}

	@Test
	void theWrongToolPaysNothing() {
		assertEquals(0.0, TreeXp.blockXp(true, true, false, false, NEVER), 1e-9, "diamond ore with a stone pickaxe");
		assertEquals(0.0, TreeXp.blockXp(false, true, false, false, NEVER), 1e-9, "stone with a fist");
	}

	@Test
	void plainBlocksPayAtMostOnceEveryTenTicks() {
		assertEquals(0.0, TreeXp.blockXp(false, true, true, false, 0L), 1e-9, "a second block in the same tick (instant-mined netherrack)");
		assertEquals(0.0, TreeXp.blockXp(false, true, true, false, 9L), 1e-9, "nine ticks after the last paid block");
		assertEquals(0.5, TreeXp.blockXp(false, true, true, false, 10L), 1e-9, "ten ticks after: pays again");
		assertEquals(0.5, TreeXp.blockXp(true, true, true, true, 10L), 1e-9, "a Silk Touch ore is paced like stone");
		assertEquals(0.0, TreeXp.blockXp(true, true, true, true, 3L), 1e-9);
	}

	@Test
	void oresAlwaysPayWhateverThePacing() {
		assertEquals(5.0, TreeXp.blockXp(true, true, true, false, 0L), 1e-9, "a quarry breaks a whole vein in one tick");
		assertEquals(5.0, TreeXp.blockXp(true, true, true, false, 1L), 1e-9);
	}

	@Test
	void ripeCropsPayOncePerHarvest() {
		assertEquals(1.0, TreeXp.cropXp(1, true), 1e-9, "ripe wheat");
		assertEquals(1.0, TreeXp.cropXp(4, true), 1e-9, "ripe carrots: one harvest, not one per carrot");
		assertEquals(1.0, TreeXp.cropXp(3, true), 1e-9, "ripe cocoa");
	}

	@Test
	void youngOrEmptyCropsPayNothing() {
		assertEquals(0.0, TreeXp.cropXp(1, false), 1e-9, "a young carrot drops a carrot, but can be replanted at once");
		assertEquals(0.0, TreeXp.cropXp(0, true), 1e-9, "a harvest without the main item");
		assertEquals(0.0, TreeXp.cropXp(0, false), 1e-9);
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
		assertEquals(2.0, TalentValues.GUARDIAN_XP_PER_DAMAGE);
		assertEquals(12.0, TreeXp.guardianXp(6.0F, 20.0F), 1e-6, "6 damage aimed at the player, armor or not");
		assertEquals(1.0, TreeXp.guardianXp(0.5F, 20.0F), 1e-6);
	}

	@Test
	void oneHitCountsForAtMostTheMaxHealth() {
		assertEquals(40.0, TreeXp.guardianXp(60.0F, 20.0F), 1e-6, "a point-blank creeper on a player with 20 health");
		assertEquals(60.0, TreeXp.guardianXp(60.0F, 30.0F), 1e-6, "More Health raises the cap");
		assertEquals(0.0, TreeXp.guardianXp(6.0F, -1.0F), 1e-9, "a broken max health caps at nothing");
	}

	@Test
	void harmlessHitsPayNothing() {
		assertEquals(0.0, TreeXp.guardianXp(0.0F, 20.0F), 1e-9, "the cooldown let nothing of it count");
		assertEquals(0.0, TreeXp.guardianXp(-2.0F, 20.0F), 1e-9);
		assertEquals(0.0, TreeXp.guardianXp(Float.NaN, 20.0F), 1e-9);
		assertEquals(0.0, TreeXp.guardianXp(Float.POSITIVE_INFINITY, 20.0F), 1e-9);
	}

	@Test
	void aCrowdOnARaisedShieldPaysLikeItsHardestHit() {
		// Five zombies swing at once at a shield: vanilla takes the first and ignores the rest in its cooldown, and so
		// does Guardian, though the shield left vanilla's own lastHurt at 0.
		GuardianCooldown cooldown = new GuardianCooldown();
		double xp = 0.0;

		for (int zombie = 0; zombie < 5; zombie++) {
			xp += TreeXp.guardianXp(cooldown.count(3.0F, 100L, false), 20.0F);
		}

		assertEquals(6.0, xp, 1e-6);
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

	@Test
	void aNewChunkPaysOnceAndHalfWhileGliding() {
		assertEquals(1.0, TalentValues.EXPLORER_XP_PER_NEW_CHUNK);
		assertEquals(1.0, TreeXp.newChunkXp(false), 1e-9, "walking, riding or teleporting into a new chunk");
		assertEquals(0.5, TreeXp.newChunkXp(true), 1e-9, "an elytra crosses chunks far faster, so half");
	}

	private static double factor(boolean passenger, boolean flying, boolean gliding, boolean steering, boolean swimming,
			boolean inLiquid, boolean onGround) {
		return TreeXp.movementFactor(passenger, flying, gliding, steering, swimming, inLiquid, onGround);
	}
}
