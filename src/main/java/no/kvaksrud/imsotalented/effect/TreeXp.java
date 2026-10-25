package no.kvaksrud.imsotalented.effect;

import no.kvaksrud.imsotalented.talent.TalentTree;
import no.kvaksrud.imsotalented.talent.TalentValues;
import no.kvaksrud.imsotalented.talent.Talents;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

/**
 * Tree XP from activity: each tree fills only from its own, and every grant goes through {@link Talents#earn} (which
 * skips creative and spectator players and players who haven't turned the camera for a while, adds the tree's XP
 * talent, applies the hourly allowance and carries fractions over). The amounts are in {@link TalentValues}.
 * <ul>
 * <li>Gatherer: ores (the {@code c:ores} tag) broken with the right tool and without Silk Touch, other blocks a pickaxe
 *     mines (a tenth of an ore, at most one every {@link TalentValues#GATHERER_BLOCK_XP_MIN_TICKS}), ripe crops
 *     harvested (sweet berries picked by hand too), and catches reeled in. Blocks for other tools pay nothing.</li>
 * <li>Warrior: damage a player's attacks deal to hostile mobs, up to the health the mob had left.</li>
 * <li>Guardian: damage hostile mobs aim at a player, counted before armor, shield and Block Chance against the
 *     player's own copy of the damage cooldown ({@link GuardianCooldown}), unless the hit kills them or vanilla turns
 *     it away before the cooldown.</li>
 * <li>Explorer: horizontal travel under the player's own power (walking, swimming or gliding, with the client's
 *     movement keys as the proof of "own"), and each chunk entered for the first time (at the gliding rate while
 *     gliding).</li>
 * </ul>
 * Every decision is a pure static function of plain numbers and flags, so it's unit-testable without a game; the
 * game-side hooks below only read those inputs off the game and hand the result to {@link Talents#earn}. The hooks are
 * a block-break event, the loot drop event (fishing here, crops through {@link BountifulHarvest}), a wrap of
 * {@code LivingEntity.hurtServer} ({@code LivingEntityMixin}) and the end of every server tick. What it keeps per
 * player is in {@link TreeXpAttachments}.
 */
public final class TreeXp {
	/** Blocks a player may cover in one tick before the move counts as a teleport (pearl, command, portal) instead of travel. */
	public static final double MAX_STEP = 8.0;

	/** No chunk yet: further out than any chunk a player can stand in. */
	private static final long NO_CHUNK = Long.MAX_VALUE;

	private TreeXp() {
	}

	/** Creates the attachments and registers the event hooks; called from {@link TalentEffects#init}. */
	static void init() {
		TreeXpAttachments.init();

		// Gatherer, blocks. AFTER runs once the block is gone, before the tool takes its durability, so the main hand
		// still holds what broke it.
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer) {
				onBlockBroken(serverPlayer, state);
			}
		});

		// Gatherer, fishing. FishingHook.retrieve rolls gameplay/fishing with the hook (not the player) as this_entity,
		// once per catch. Nested tables (fish, junk, treasure) fire this event too, so only the top table counts.
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (table.is(BuiltInLootTables.FISHING)
					&& context.getOptional(LootContextParams.THIS_ENTITY) instanceof FishingHook hook
					&& hook.getPlayerOwner() instanceof ServerPlayer player) {
				int items = 0;

				for (ItemStack stack : drops) {
					items += stack.isEmpty() ? 0 : 1;
				}

				Talents.earn(player, TalentTree.GATHERER, catchXp(items));
			}
		});

		// Explorer: one look at every player per tick, after the network has moved them.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				trackTravel(player);
			}
		});
	}

	// Gatherer

	/**
	 * Whether a block pays as an ore: one from {@code c:ores}, mined without Silk Touch. An ore mined with Silk Touch
	 * counts as a plain pickaxe block, since it drops itself and could be placed and mined again.
	 */
	public static boolean paysAsOre(boolean ore, boolean silkTouch) {
		return ore && !silkTouch;
	}

	/**
	 * Gatherer XP for one broken block, mined with a tool that gets its drops: an ore ({@link #paysAsOre}) always pays
	 * the ore's share, and any other block a pickaxe mines pays a tenth of that, but at most once every
	 * {@link TalentValues#GATHERER_BLOCK_XP_MIN_TICKS}: netherrack under a good pickaxe or stone under Haste II breaks
	 * in one hit, so sweeping the crosshair over it would otherwise pay every tick. Nothing pays without the right tool
	 * (a bare hand, or a pickaxe below the block's tier), and blocks for other tools (sand, dirt, logs) never pay.
	 *
	 * @param pickaxeBlock        whether a pickaxe is the tool for the block ({@code minecraft:mineable/pickaxe})
	 * @param ticksSincePaidBlock ticks since a plain pickaxe block last paid this player ({@link Long#MAX_VALUE} if none
	 *                            has)
	 */
	public static double blockXp(boolean ore, boolean pickaxeBlock, boolean correctTool, boolean silkTouch, long ticksSincePaidBlock) {
		if (!correctTool) {
			return 0.0;
		}

		if (paysAsOre(ore, silkTouch)) {
			return TalentValues.GATHERER_XP_PER_ORE;
		}

		return pickaxeBlock && ticksSincePaidBlock >= TalentValues.GATHERER_BLOCK_XP_MIN_TICKS ? TalentValues.GATHERER_XP_PER_BLOCK : 0.0;
	}

	/**
	 * Gatherer XP for harvesting one of the {@link Crops seven crops}: only a ripe crop ({@link Crops.Harvest#isRipe},
	 * the same check Bountiful Harvest makes) whose harvest produced its main item. Young carrots, potatoes, nether wart
	 * and cocoa drop their main item too, so without the age check planting and breaking one would pay over and over.
	 *
	 * @param mainCount how many of the main item the harvest dropped, before Bountiful Harvest's bonus
	 */
	public static double cropXp(int mainCount, boolean ripe) {
		return mainCount > 0 && ripe ? TalentValues.GATHERER_XP_PER_CROP : 0.0;
	}

	/** Gatherer XP for one reel-in: a catch if anything came up, nothing otherwise. */
	public static double catchXp(int items) {
		return items > 0 ? TalentValues.GATHERER_XP_PER_FISH : 0.0;
	}

	private static void onBlockBroken(ServerPlayer player, BlockState state) {
		ItemStack tool = player.getMainHandItem();
		boolean ore = state.is(ConventionalBlockTags.ORES);
		// The registry lookup only for ores, the one case it changes the answer.
		boolean silkTouch = ore && player.registryAccess().get(Enchantments.SILK_TOUCH)
				.map(silk -> EnchantmentHelper.getItemEnchantmentLevel(silk, tool) > 0)
				.orElse(false);
		long tick = player.level().getServer().getTickCount();
		Long paidTick = player.getAttached(TreeXpAttachments.PAID_BLOCK_TICK);
		double xp = blockXp(ore, state.is(BlockTags.MINEABLE_WITH_PICKAXE), tool.isCorrectToolForDrops(state), silkTouch,
				paidTick != null ? tick - paidTick : Long.MAX_VALUE);

		if (xp > 0.0 && !paysAsOre(ore, silkTouch)) {
			player.setAttached(TreeXpAttachments.PAID_BLOCK_TICK, tick);
		}

		Talents.earn(player, TalentTree.GATHERER, xp);
	}

	/**
	 * Gatherer XP for a crop harvest, from the Bountiful Harvest drop listener: the player who broke the crop or picked
	 * the berries, with the ripeness Bountiful Harvest goes by.
	 */
	static void onCropHarvest(ServerPlayer player, int mainCount, boolean ripe) {
		Talents.earn(player, TalentTree.GATHERER, cropXp(mainCount, ripe));
	}

	// Warrior and Guardian

	/**
	 * Warrior XP for one hit on a hostile mob: the health it lost, which is the damage that got through armor and the
	 * like, and never more than it had left, so overkill (a mace smash on a zombie at 2 health) pays for the 2.
	 */
	public static double warriorXp(float healthBefore, float healthAfter) {
		double dealt = (double) healthBefore - Math.max(0.0F, healthAfter);
		return dealt > 0.0 && Double.isFinite(dealt) ? dealt * TalentValues.WARRIOR_XP_PER_DAMAGE : 0.0;
	}

	/**
	 * Guardian XP for one hit a hostile mob aims at the player, from the part of its raw damage (after difficulty,
	 * before armor, shield and Block Chance, so better defenses never slow the meter down) that counts against the
	 * player's damage cooldown ({@link GuardianCooldown#count}): a crowd swinging at once pays like its hardest hit, as
	 * vanilla only takes that much.
	 * <p>
	 * A hit counts for at most the player's max health: anything above that would kill an unarmored player outright,
	 * and the cap keeps one point-blank charged creeper from paying like an hour of fighting.
	 *
	 * @param counted the raw damage that counts
	 */
	public static double guardianXp(float counted, float maxHealth) {
		if (!(counted > 0.0F) || !Float.isFinite(counted)) {
			return 0.0;
		}

		return Math.min(counted, Math.max(0.0F, maxHealth)) * TalentValues.GUARDIAN_XP_PER_DAMAGE;
	}

	/**
	 * Whether a hit can earn anyone tree XP: a player being hurt (Guardian) or a player hurting a hostile mob (Warrior).
	 * The hurt wrap checks this first, so every other hit in the world costs two type checks and nothing else.
	 */
	public static boolean earnsFromHurt(LivingEntity victim, DamageSource source) {
		return victim instanceof ServerPlayer || victim instanceof Enemy && source.getEntity() instanceof ServerPlayer;
	}

	/**
	 * Warrior and Guardian XP once a hit has been dealt with, from the wrap of {@code LivingEntity.hurtServer}. The
	 * wrap sees every outcome: a hit Block Chance shrugged off and one a shield fully blocked come back as "not hurt",
	 * and still count for Guardian. A hit vanilla turned away before its damage cooldown (Fire Resistance against a
	 * fireball, an immune or dying player) did nothing at all and counts for nothing.
	 *
	 * @param damage       the damage the hit arrived with
	 * @param healthBefore the victim's health before the hit
	 * @param rejected     whether vanilla turned the hit on a player away before the cooldown
	 *                     ({@link DamageCooldown#rejectsBeforeCooldown}, asked before the hit)
	 */
	public static void afterHurt(LivingEntity victim, DamageSource source, float damage, float healthBefore, boolean rejected) {
		if (victim instanceof ServerPlayer player) {
			if (!rejected) {
				onPlayerHurt(player, source, damage);
			}
		} else if (victim instanceof Enemy && source.getEntity() instanceof ServerPlayer attacker
				// A real attack, the same ones Stronger Weapons boosts: melee, or an arrow or trident the player shot.
				// Thorns, fire and explosions the player is to blame for don't count.
				&& WeaponType.of(attacker, source) != null) {
			Talents.earn(attacker, TalentTree.WARRIOR, warriorXp(healthBefore, victim.getHealth()));
		}
	}

	private static void onPlayerHurt(ServerPlayer player, DamageSource source, float damage) {
		// Every hit that gets to the cooldown moves Guardian's copy of it, whoever dealt it, as every hit moves vanilla's.
		float counted = player.getAttachedOrCreate(TreeXpAttachments.GUARDIAN_COOLDOWN)
				.count(damage, player.level().getServer().getTickCount(), source.is(DamageTypeTags.BYPASSES_COOLDOWN));

		// Only attacks by a hostile mob (its melee, arrows, fireballs, explosion…), not its thorns, and not the hit that
		// killed the player.
		if (source.getEntity() instanceof Enemy && !source.is(DamageTypes.THORNS) && !player.isDeadOrDying()) {
			Talents.earn(player, TalentTree.GUARDIAN, guardianXp(counted, player.getMaxHealth()));
		}
	}

	// Explorer

	/**
	 * How much a block of this movement is worth, as a share of walking. Only travel under the player's own power pays:
	 * <ul>
	 * <li>Riding anything (horses, boats, minecarts) and flying (creative, or a mod's) earn nothing.</li>
	 * <li>An elytra earns {@link TalentValues#EXPLORER_GLIDE_FACTOR}, since it covers ground so much faster. It's the
	 *     one move that pays without a movement key held, since a glide is steered by looking.</li>
	 * <li>Anything else pays only while the player holds a movement key ({@code steering}), so being pushed by pistons,
	 *     mobs, other players or a hit's knockback earns nothing.</li>
	 * <li>Floating in water or lava (in it, not swimming and not standing on the bottom) earns nothing, since currents
	 *     carry a floating player. Swimming counts in full, and wading on the bottom is walking.</li>
	 * </ul>
	 * What's left is on foot, in full: walking, sprinting and the jumps in between.
	 */
	public static double movementFactor(boolean passenger, boolean flying, boolean gliding, boolean steering,
			boolean swimming, boolean inLiquid, boolean onGround) {
		if (passenger || flying) {
			return 0.0;
		}

		if (gliding) {
			return TalentValues.EXPLORER_GLIDE_FACTOR;
		}

		if (!steering) {
			return 0.0;
		}

		if (swimming) {
			return 1.0;
		}

		return inLiquid && !onGround ? 0.0 : 1.0;
	}

	/** Whether the player is holding a key that walks or swims them somewhere; jump and sneak alone don't. */
	public static boolean isSteering(Input input) {
		return input.forward() || input.backward() || input.left() || input.right();
	}

	/**
	 * Explorer XP for one tick's horizontal move at this movement's {@link #movementFactor factor}. A move longer than
	 * {@link #MAX_STEP} is a teleport, not travel, and earns nothing.
	 */
	public static double travelXp(double dx, double dz, double factor) {
		double distance = Math.sqrt(dx * dx + dz * dz);

		if (!(distance > 0.0) || distance > MAX_STEP || !(factor > 0.0)) {
			return 0.0;
		}

		return distance * TalentValues.EXPLORER_XP_PER_BLOCK * factor;
	}

	/**
	 * Explorer XP for entering a chunk for the first time: in full however the player got there (exploring by boat or
	 * on horseback counts), and at {@link TalentValues#EXPLORER_GLIDE_FACTOR} while gliding, since an elytra and
	 * rockets cross chunks far faster than anything else.
	 */
	public static double newChunkXp(boolean gliding) {
		return gliding ? TalentValues.EXPLORER_XP_PER_NEW_CHUNK * TalentValues.EXPLORER_GLIDE_FACTOR : TalentValues.EXPLORER_XP_PER_NEW_CHUNK;
	}

	/**
	 * Explorer, once per player per tick. A player standing still costs one attachment read and two comparisons. A
	 * moving one earns for the distance, and crossing into another chunk checks whether it's a new one. The first tick
	 * after joining, respawning or changing dimension only notes where the player is.
	 */
	private static void trackTravel(ServerPlayer player) {
		Travel travel = player.getAttached(TreeXpAttachments.TRAVEL);
		ResourceKey<Level> dimension = player.level().dimension();
		double x = player.getX();
		double z = player.getZ();

		if (travel == null || travel.dimension != dimension) {
			travel = new Travel(x, z, dimension);
			player.setAttached(TreeXpAttachments.TRAVEL, travel);
			visitChunk(player, travel);
			return;
		}

		double dx = x - travel.x;
		double dz = z - travel.z;

		if (dx == 0.0 && dz == 0.0) {
			return;
		}

		travel.x = x;
		travel.z = z;
		double factor = movementFactor(player.isPassenger(), player.getAbilities().flying, player.isFallFlying(),
				isSteering(player.getLastClientInput()), player.isSwimming(), player.isInLiquid(), player.onGround());
		Talents.earn(player, TalentTree.EXPLORER, travelXp(dx, dz, factor));
		visitChunk(player, travel);
	}

	/**
	 * A new chunk pays ({@link #newChunkXp}) however the player got there (riding and teleports too), since each pays
	 * only once. Creative and spectator players earn nothing, so their chunks aren't marked either: those still pay once
	 * they play survival.
	 */
	private static void visitChunk(ServerPlayer player, Travel travel) {
		ChunkPos chunk = player.chunkPosition();
		long key = chunk.pack();

		if (key == travel.chunk || Talents.isCreative(player) || player.isSpectator()) {
			return;
		}

		travel.chunk = key;

		if (player.getAttachedOrCreate(TreeXpAttachments.VISITED_CHUNKS).visit(travel.dimension.identifier().toString(), chunk.x(), chunk.z())) {
			Talents.earn(player, TalentTree.EXPLORER, newChunkXp(player.isFallFlying()));
		}
	}

	/** Where a player was at the end of the last tick: changed in place, so moving allocates nothing. */
	public static final class Travel {
		private double x;
		private double z;
		private final ResourceKey<Level> dimension;
		/** The last chunk checked against the visited ones, packed; {@link #NO_CHUNK} until the first check. */
		private long chunk = NO_CHUNK;

		private Travel(double x, double z, ResourceKey<Level> dimension) {
			this.x = x;
			this.z = z;
			this.dimension = dimension;
		}
	}
}
