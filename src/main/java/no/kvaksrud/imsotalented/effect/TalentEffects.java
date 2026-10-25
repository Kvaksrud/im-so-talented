package no.kvaksrud.imsotalented.effect;

/**
 * The talents that change gameplay on the fly, as opposed to the attribute ones in
 * {@link no.kvaksrud.imsotalented.talent.TalentAttributes}. The mixin-driven ones hook into vanilla through the
 * mixins in {@code no.kvaksrud.imsotalented.mixin} (listed in {@code imsotalented.mixins.json}), which hand the work
 * to the classes in this package:
 * <ul>
 * <li>{@link MiningSpeed}: Faster Mining, a virtual Efficiency (client and server).</li>
 * <li>{@link MoreLuck}: More Luck, a virtual Fortune, Looting and Luck of the Sea.</li>
 * <li>{@link WeaponDamage} and {@link WeaponType}: Stronger Weapons and the weapon specialists.</li>
 * <li>{@link BlockChance}: shrugging off an attack completely.</li>
 * <li>{@link ShieldMastery}: raising the shield faster and getting it back sooner after an axe.</li>
 * <li>{@link FastGrowth}: Fast Growth, an extra chance on a crop's growth roll ({@code FastGrowthMixin} wraps the one
 *     {@code nextInt} roll in the random tick of the {@link Crops seven crops'} blocks).</li>
 * <li>{@link CarefulHands}: Careful Hands, a virtual Unbreaking (the durability mixin wraps the read site's caller).</li>
 * <li>{@link SwimSpeed}: Swim Speed, the softened per-tick underwater damping in {@code travelInWater} (a
 *     {@code LivingEntityMixin} wrap).</li>
 * <li>{@link Quarrying}: Quarrying, the rest of an ore's vein broken with it while the player sneaks (a wrap of
 *     {@code ServerPlayerGameMode.destroyBlock}, and {@code BlockMixin} moving the drops to the player's feet).</li>
 * </ul>
 * Step Assist and Reach have no entry here: 26.3 reads step height and both interaction ranges off
 * attributes, so they are plain rows in
 * {@link no.kvaksrud.imsotalented.talent.TalentAttributes#apply} like the other attribute talents.
 * The event-based ones are registered in {@link #init()} (called from {@code ImSoTalented.onInitialize}):
 * <ul>
 * <li>{@link BountifulHarvest}: the extra main-item copies of a ripe crop, through a loot drop event (the level is
 *     read from the harvesting player in the context at loot time).</li>
 * <li>{@link TreeXp}: every tree's XP from activity, through a block-break event, the loot drop events (fishing,
 *     and the crop harvests in the Bountiful Harvest listener), the end of each server tick, and the
 *     {@code hurtServer} wrap in {@code LivingEntityMixin} for damage dealt and taken.</li>
 * </ul>
 * Everything is read from the player's talents when it happens, so nothing needs updating when talents change. Every
 * effect reads {@link no.kvaksrud.imsotalented.talent.Talents#effectiveLevel}, which is 0 for a talent the player
 * switched off.
 */
public final class TalentEffects {
	private TalentEffects() {
	}

	/** Registers the event-based effects; the mixin-based ones apply themselves when their classes load. */
	public static void init() {
		TreeXp.init();
		BountifulHarvest.init();
	}
}
