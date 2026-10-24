package no.kvaksrud.imsotalented.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

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
 * <li>{@link FastGrowth}: Fast Growth, the crop growth roll's extra chance (the four growth mixins wrap the
 *     per-tick {@code nextInt} roll of wheat/carrots/potatoes/beetroots, nether wart, the sweet berry bush and cocoa).</li>
 * <li>{@link CarefulHands}: Careful Hands, a virtual Unbreaking (the durability mixin wraps the read site's caller).</li>
 * <li>{@link SwimSpeed}: Swim Speed, the softened per-tick underwater damping in {@code travelInWater} (the
 *     {@code LivingEntityMixin} wrap reads the player's EFFECTIVE level — it is toggleable, and P8/P9 copy
 *     that read).</li>
 * <li>{@link Quarrying}: Quarrying, the rest of an ore's vein broken with it while the Quarry key is held (a wrap of
 *     {@code ServerPlayerGameMode.destroyBlock}, and {@code BlockMixin} moving the drops in front of the player). Its
 *     key attachment and payload are set up in {@link #init()}.</li>
 * </ul>
 * Step Assist and Reach have no entry here: 26.3 reads step height and both interaction ranges off
 * attributes, so they are plain rows in
 * {@link no.kvaksrud.imsotalented.talent.TalentAttributes#apply} like the other attribute talents.
 * The event-based ones are registered in {@link #init()} (called from {@code ImSoTalented.onInitialize}):
 * <ul>
 * <li>{@link BountifulHarvest}: the extra main-item copies of the classic farm harvests, through a loot drop
 *     event (the level is read from the breaking player in the context at loot time).</li>
 * <li>{@link TreeXp}: every tree's XP from activity, through a block-break event, the loot drop events (fishing,
 *     and the crop harvests in the Bountiful Harvest listener below), the end of each server tick, and the
 *     {@code hurtServer} wrap in {@code LivingEntityMixin} for damage dealt and taken.</li>
 * </ul>
 * Everything is read from the player's talents when it happens, so nothing needs updating when talents change.
 */
public final class TalentEffects {
	private TalentEffects() {
	}

	/** Registers the event-based effects; the mixin-based ones apply themselves when their classes load. */
	public static void init() {
		TreeXp.init();
		Quarrying.init();

		// Bountiful Harvest: the fabric-loot-api drop event, server side (loot never runs on the client). For the
		// seven farm harvest tables, the breaking player's level in the context's THIS_ENTITY adds that many extra
		// copies of the crop's main item — but only when the rolled drops already contain it, so a stage that
		// produces no main item (young wheat's seeds, a young sweet berry bush) is never credited one. Explosions,
		// animals and talent-less players add nothing.
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			Item main = table.unwrapKey()
					.map(ResourceKey::identifier)
					.map(Identifier::getPath)
					.map(BountifulHarvest::mainDropForTablePath)
					.orElse(null);

			if (main != null) {
				int mainCount = 0;
				for (ItemStack stack : drops) {
					if (stack.is(main)) {
						mainCount += stack.getCount();
					}
				}

				// Gatherer XP for a fully grown crop that produced its main item; counted before the bonus is added.
				TreeXp.onCropHarvest(context, mainCount);

				int extra = BountifulHarvest.bonusFor(mainCount, BountifulHarvest.extraItems(context));

				if (extra > 0) {
					drops.add(new ItemStack(main, extra));
				}
			}
		});
	}
}
