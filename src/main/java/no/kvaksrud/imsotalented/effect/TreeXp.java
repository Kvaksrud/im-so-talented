package no.kvaksrud.imsotalented.effect;

import no.kvaksrud.imsotalented.ImSoTalented;
import no.kvaksrud.imsotalented.talent.TalentTree;
import no.kvaksrud.imsotalented.talent.TalentValues;
import no.kvaksrud.imsotalented.talent.Talents;
import org.jspecify.annotations.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

/**
 * Tree XP from activity: each tree fills only from its own, and every grant goes through
 * {@link Talents#earn} (which skips creative and spectator players, adds the tree's XP talent and carries fractions
 * over). The amounts are in {@link TalentValues}.
 * <ul>
 * <li>Gatherer: ores (the {@code c:ores} tag) broken with the right tool and without Silk Touch, fully grown crops
 *     harvested, and catches reeled in. No other block pays.</li>
 * <li>Warrior: damage a player's attacks deal to hostile mobs, up to the health the mob had left.</li>
 * <li>Guardian: damage hostile mobs aim at a player, counted before armor, shield and Block Chance, unless the hit
 *     kills them.</li>
 * <li>Explorer: horizontal travel under the player's own power (walking, swimming or gliding, with the client's
 *     movement keys as the proof of "own"), and each chunk entered for the first time.</li>
 * </ul>
 * Every decision is a pure static function of plain numbers and flags, so it's unit-testable without a game; the
 * game-side hooks below only read those inputs off the game and hand the result to {@link Talents#earn}. The hooks are
 * a block-break event, the loot drop event (fishing here, crops through {@link TalentEffects}), a wrap of
 * {@code LivingEntity.hurtServer} ({@code LivingEntityMixin}) and the end of every server tick.
 */
public final class TreeXp {
	/** Blocks a player may cover in one tick before the move counts as a teleport (pearl, command, portal) instead of travel. */
	public static final double MAX_STEP = 8.0;

	/**
	 * Vanilla ignores a hit that lands while more than this many ticks of the damage cooldown are left, unless it is
	 * harder than the hit that started the cooldown (then only the difference counts).
	 */
	private static final int COOLDOWN_GRACE_TICKS = 10;

	/** The crop growth property every one of the seven Bountiful Harvest crops uses. */
	private static final String AGE = "age";

	/** No chunk yet: further out than any chunk a player can stand in. */
	private static final long NO_CHUNK = Long.MAX_VALUE;

	/**
	 * Every chunk each player has been in, per dimension: saved, kept on death, never synced (the client has no use for
	 * it). Created in {@link #init} (not as a static initializer) so the pure math in this class loads without the
	 * Fabric attachment implementation, as the unit tests need.
	 */
	public static AttachmentType<VisitedChunks> VISITED_CHUNKS;

	/** Where each player was at the end of the last tick, for measuring travel. Server bookkeeping only. Created in {@link #init}. */
	public static AttachmentType<Travel> TRAVEL;

	private TreeXp() {
	}

	/** Creates the attachments and registers the event hooks; called from {@link TalentEffects#init}. */
	static void init() {
		VISITED_CHUNKS = AttachmentRegistry.create(ImSoTalented.id("visited_chunks"),
				builder -> builder.initializer(VisitedChunks::new).persistent(VisitedChunks.CODEC).copyOnDeath());
		TRAVEL = AttachmentRegistry.create(ImSoTalented.id("explorer_travel"));

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
				onTick(player);
			}
		});
	}

	// Gatherer

	/**
	 * Gatherer XP for one broken block: only ores pay, and only when the ore drops what it's mined for. So nothing
	 * without the right tool (a pickaxe below the ore's tier gets no drops), and nothing with Silk Touch (the ore drops
	 * itself and could be placed and mined again). Stone, logs and every other block pay nothing.
	 */
	public static double blockXp(boolean ore, boolean correctTool, boolean silkTouch) {
		return ore && correctTool && !silkTouch ? TalentValues.GATHERER_XP_PER_ORE : 0.0;
	}

	/**
	 * Gatherer XP for breaking one of the seven Bountiful Harvest crops: only when fully grown and the harvest produced
	 * the crop's main item. Young carrots, potatoes, nether wart and cocoa drop their main item too, so without the age
	 * check planting and breaking one would pay over and over.
	 *
	 * @param mainCount how many of the main item the harvest dropped, before Bountiful Harvest's bonus
	 */
	public static double cropXp(int mainCount, int age, int maxAge) {
		return mainCount > 0 && age >= maxAge ? TalentValues.GATHERER_XP_PER_CROP : 0.0;
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

		Talents.earn(player, TalentTree.GATHERER, blockXp(ore, tool.isCorrectToolForDrops(state), silkTouch));
	}

	/**
	 * Gatherer XP for a crop loot roll, from the Bountiful Harvest drop listener in {@link TalentEffects}: the breaking
	 * player is the context's {@code this_entity} and the broken crop its {@code block_state}. Explosions, mobs and
	 * other non-players earn nothing.
	 */
	public static void onCropHarvest(LootContext context, int mainCount) {
		if (mainCount <= 0 || !(context.getOptional(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player)) {
			return;
		}

		BlockState state = context.getOptional(LootContextParams.BLOCK_STATE);
		IntegerProperty age = state != null ? ageProperty(state) : null;

		if (age != null) {
			Talents.earn(player, TalentTree.GATHERER, cropXp(mainCount, state.getValue(age), age.getPossibleValues().getLast()));
		}
	}

	private static @Nullable IntegerProperty ageProperty(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property instanceof IntegerProperty age && AGE.equals(property.getName())) {
				return age;
			}
		}

		return null;
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
	 * Guardian XP for one hit a hostile mob aims at the player, from the raw damage (after difficulty, before armor,
	 * shield and Block Chance, so better defenses never slow the meter down).
	 * <ul>
	 * <li>During the damage cooldown vanilla only takes the part above the previous hit, and ignores a hit that isn't
	 *     harder; that's what counts here too, so a crowd swinging at once doesn't pay for hits that couldn't land.</li>
	 * <li>A hit counts for at most the player's max health: anything above that would kill an unarmored player
	 *     outright, and the cap keeps one point-blank charged creeper from paying like an hour of fighting.</li>
	 * </ul>
	 *
	 * @param inCooldown whether the hit lands in the damage cooldown and doesn't bypass it
	 * @param lastHurt   the damage of the hit that started the cooldown
	 */
	public static double guardianXp(float damage, boolean inCooldown, float lastHurt, float maxHealth) {
		double counted = inCooldown ? (double) damage - lastHurt : damage;

		if (!(counted > 0.0) || !Double.isFinite(counted)) {
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
	 * and still count for Guardian.
	 *
	 * @param damage         the damage the hit arrived with
	 * @param healthBefore   the victim's health before the hit
	 * @param cooldownBefore the victim's damage cooldown ticks before the hit
	 * @param lastHurtBefore the damage of the hit that started that cooldown
	 */
	public static void afterHurt(LivingEntity victim, DamageSource source, float damage, float healthBefore, int cooldownBefore, float lastHurtBefore) {
		if (victim instanceof ServerPlayer player) {
			// Only attacks by a hostile mob (its melee, arrows, fireballs, explosion…), not its thorns, and not the
			// hit that killed the player.
			if (source.getEntity() instanceof Enemy && !source.is(DamageTypes.THORNS) && !player.isDeadOrDying()) {
				boolean inCooldown = cooldownBefore > COOLDOWN_GRACE_TICKS && !source.is(DamageTypeTags.BYPASSES_COOLDOWN);
				Talents.earn(player, TalentTree.GUARDIAN, guardianXp(damage, inCooldown, lastHurtBefore, player.getMaxHealth()));
			}
		} else if (victim instanceof Enemy && source.getEntity() instanceof ServerPlayer attacker
				// A real attack, the same ones Stronger Weapons boosts: melee, or an arrow or trident the player shot.
				// Thorns, fire and explosions the player is to blame for don't count.
				&& WeaponType.of(attacker, source) != null) {
			Talents.earn(attacker, TalentTree.WARRIOR, warriorXp(healthBefore, victim.getHealth()));
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
	 * Explorer, once per player per tick. A player standing still costs one attachment read and two comparisons. A
	 * moving one earns for the distance, and crossing into another chunk checks whether it's a new one. The first tick
	 * after joining, respawning or changing dimension only notes where the player is.
	 */
	private static void onTick(ServerPlayer player) {
		Travel travel = player.getAttached(TRAVEL);
		ResourceKey<Level> dimension = player.level().dimension();
		double x = player.getX();
		double z = player.getZ();

		if (travel == null || travel.dimension != dimension) {
			travel = new Travel(x, z, dimension);
			player.setAttached(TRAVEL, travel);
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
	 * A new chunk pays however the player got there (riding and teleports too), since each pays only once. Creative and
	 * spectator players earn nothing, so their chunks aren't marked either: those still pay once they play survival.
	 */
	private static void visitChunk(ServerPlayer player, Travel travel) {
		ChunkPos chunk = player.chunkPosition();
		long key = chunk.pack();

		if (key == travel.chunk || player.isCreative() || player.isSpectator()) {
			return;
		}

		travel.chunk = key;

		if (player.getAttachedOrCreate(VISITED_CHUNKS).visit(travel.dimension.identifier().toString(), chunk.x(), chunk.z())) {
			Talents.earn(player, TalentTree.EXPLORER, TalentValues.EXPLORER_XP_PER_NEW_CHUNK);
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
