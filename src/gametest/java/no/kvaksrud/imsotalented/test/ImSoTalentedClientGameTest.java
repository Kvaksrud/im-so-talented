package no.kvaksrud.imsotalented.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import no.kvaksrud.imsotalented.ImSoTalented;
import no.kvaksrud.imsotalented.ImSoTalentedConfig;
import no.kvaksrud.imsotalented.client.BackpackClient;
import no.kvaksrud.imsotalented.client.TalentClient;
import no.kvaksrud.imsotalented.client.TalentScreen;
import no.kvaksrud.imsotalented.client.talent.TalentTreeLayout;
import no.kvaksrud.imsotalented.client.inventory.BackpackScreen;
import no.kvaksrud.imsotalented.converter.ExperienceConverter;
import no.kvaksrud.imsotalented.effect.XpEarners;
import no.kvaksrud.imsotalented.inventory.Backpacks;
import no.kvaksrud.imsotalented.inventory.Backpack;
import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentRules;
import no.kvaksrud.imsotalented.talent.TalentRules.EnchantmentStacking;
import no.kvaksrud.imsotalented.talent.Talents;
import org.joml.Vector2d;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.MixinEnvironment;

import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Plays the mod in a real client, in survival: the settings file; the talent tree (K) with buying, a locked talent and
 * too little XP; every effect at the top level (attributes, mining speed on both sides, Fortune/Looting/Luck of the Sea,
 * weapon damage, block chance, the XP multiplier); the three farming talents (the extra harvest copies with a
 * no-player and a level-0 control, the growth multiplier against a 63-blocks-away plot, the virtual unbreaking with
 * and without the talent); the three mobility talents (Step Assist's 0.6 → 0.9 step height and Reach's 4.5/3.0 →
 * 6.0/4.5 interaction ranges through the 26.3 attributes, each with a level-0 control, and Swim Speed's x1.3
 * in-water movement measured as a 60-tick coast against the level-0 control and a toggle-off control); the
 * toggleable talents (command on/off, the non-toggleable reject, the right-click
 * payload round-trip); the extra inventory (J) with pickup overflow and shrinking; the
 * Experience Converter (one item, and a sneaking whole-stack trade); death; and a save and reload.
 * Screenshots land in build/run/clientGameTest/screenshots (prefixed ist_).
 */
@SuppressWarnings("UnstableApiUsage")
public class ImSoTalentedClientGameTest implements FabricClientGameTest {
	private static final double EPSILON = 1e-4;
	private static final int STONE_PICKAXE_SPEED = 8;

	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			save = singleplayer.getWorldSave();
			singleplayer.getServer().runCommand("difficulty easy");
			singleplayer.getServer().runCommand("time set day");
			singleplayer.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
			singleplayer.getConnection().waitForChunksRender();
			// Loads every mixin target now, so one that doesn't match fails here instead of somewhere later.
			MixinEnvironment.getCurrentEnvironment().audit();

			checkSettingsFile();
			buyingInTheTree(context, singleplayer);
			layout(context);
			attributesAtTheTopLevel(context, singleplayer);
			toggling(context, singleplayer);
			xpEarners(context, singleplayer);
			farming(context, singleplayer);
			mobility(context, singleplayer);
			miningSpeed(context, singleplayer);
			luck(singleplayer);
			weaponDamage(singleplayer);
			blockChance(singleplayer);
			xpMultiplier(singleplayer);
			extraInventory(context, singleplayer);
			experienceConverter(context, singleplayer);
			inventoryButtons(context);
			death(context, singleplayer);

			// Left with a little less than full health, which a reload must not clamp back to 20.
			singleplayer.getServer().runOnServer(_ -> {
				ServerPlayer player = player(singleplayer);
				player.setHealth(35.0F);
				Backpacks.get(player).setItem(0, new ItemStack(Items.APPLE, 7));
			});
			context.waitTicks(5);
		}

		try (TestSingleplayerContext singleplayer = save.open()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runOnServer(_ -> {
				ServerPlayer player = player(singleplayer);

				for (Talent talent : Talent.values()) {
					check(Talents.level(player, talent) == talent.maxLevel(), talent.id() + " should still be maxed after a reload");
				}

				check(player.getMaxHealth() == 40.0F, "Max health should be 40 after a reload, got " + player.getMaxHealth());
				// Natural regeneration may have healed a little since; what matters is that it wasn't clamped to 20.
				check(player.getHealth() >= 35.0F, "Health above 20 must survive a reload, got " + player.getHealth());
				check(countOf(Backpacks.get(player), Items.APPLE) == 7, "The extra inventory should be saved");
			});
			context.waitFor(client -> client.player != null && Talents.level(client.player, Talent.FASTER_MINING) == 5);
			screenshot(context, "ist_10_after_reload");
		}
	}

	private static void checkSettingsFile() {
		Path configFile = FabricLoader.getInstance().getConfigDir().resolve(ImSoTalentedConfig.FILE_NAME);
		check(Files.isRegularFile(configFile), "Expected the settings file at " + configFile);
		check(ImSoTalented.config().equals(ImSoTalentedConfig.DEFAULTS), "Expected the default settings, got " + ImSoTalented.config());
	}

	/** K opens the tree; clicking buys with XP points, the next point costs more, and locked or unaffordable talents stay put. */
	private static void buyingInTheTree(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		setXpPoints(singleplayer, 100);
		context.getInput().pressKey(TalentClient.OPEN_TALENTS);
		context.waitForScreen(TalentScreen.class);
		context.waitTicks(2);
		screenshot(context, "ist_01_tree");

		hover(context, Talent.FASTER_MINING);
		screenshot(context, "ist_02_tooltip");
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		waitForLevel(singleplayer, Talent.FASTER_MINING, 1);
		checkXpPoints(singleplayer, 90, "the first talent point costs 10");

		// A specialist needs the whole Combat spine first (Stronger Weapons needs Careful Hands).
		hover(context, Talent.SWORD_SPECIALIST);
		screenshot(context, "ist_03_locked_tooltip");
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(10);
		check(serverLevel(singleplayer, Talent.SWORD_SPECIALIST) == 0, "A locked specialist must not be bought");
		checkXpPoints(singleplayer, 90, "a locked talent costs nothing");

		// The spine continues from Faster Mining: Higher Jumping, then Faster Running.
		hover(context, Talent.HIGHER_JUMPING);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		waitForLevel(singleplayer, Talent.HIGHER_JUMPING, 1);
		checkXpPoints(singleplayer, 79, "the second point costs 11");

		hover(context, Talent.FASTER_RUNNING);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		waitForLevel(singleplayer, Talent.FASTER_RUNNING, 1);
		checkXpPoints(singleplayer, 67, "the third point costs 12");
		context.waitTicks(2);
		screenshot(context, "ist_04_bought");

		// The next point costs 13; with 5 points nothing happens (Step Assist is unlocked but unaffordable).
		setXpPoints(singleplayer, 5);
		hover(context, Talent.STEP_ASSIST);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(10);
		check(serverLevel(singleplayer, Talent.STEP_ASSIST) == 0, "Too little XP must not buy a talent");
		checkXpPoints(singleplayer, 5, "a failed purchase costs nothing");

		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	/**
	 * The tree is one serpentine path: five rows of eight (the last holds three), and for every talent the node
	 * center sits inside the panel and the hit test round-trips to the same talent.
	 */
	private static void layout(ClientGameTestContext context) {
		context.getInput().pressKey(TalentClient.OPEN_TALENTS);
		context.waitForScreen(TalentScreen.class);
		context.waitTicks(2);
		context.runOnClient(client -> {
			Window window = client.getWindow();
			TalentTreeLayout panel = TalentTreeLayout.forScreen(window.getGuiScaledWidth(), window.getGuiScaledHeight());

			check(panel.rowCount() == 5, "Expected the 35-talent spine in 5 rows of 8, got " + panel.rowCount());
			check(panel.lastRowCount() == 3, "Expected the last row to hold exactly 3 nodes, got " + panel.lastRowCount());

			for (Talent talent : Talent.values()) {
				Vector2i center = panel.nodeCenter(talent);
				check(center != null, talent.id() + " should have a node center");
				check(center.x >= panel.left() && center.x < panel.left() + panel.width()
						&& center.y >= panel.top() && center.y < panel.top() + panel.height(),
						talent.id() + " center (" + center.x + "," + center.y + ") should be inside the panel");
				check(panel.talentAt(center.x, center.y) == talent, "Hit test at (" + center.x + "," + center.y +
						") should give " + talent.id() + ", got " + panel.talentAt(center.x, center.y));
			}
		});
		context.waitTicks(2);
		screenshot(context, "ist_12_serpentine");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	/** /talents max: health, armor, speed, jumping and falling are attribute modifiers. */
	private static void attributesAtTheTopLevel(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("talents max @p");
		singleplayer.getServer().runOnServer(_ -> player(singleplayer).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE)));
		context.waitTicks(5);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			checkNear(40.0, player.getAttributeValue(Attributes.MAX_HEALTH), "max health with 10 extra hearts");
			checkNear(6.0 * 1.25, player.getAttributeValue(Attributes.ARMOR), "an iron chestplate's 6 armor, 25% stronger");
			checkNear(0.1 * 1.2, player.getAttributeValue(Attributes.MOVEMENT_SPEED), "20% faster");
			checkNear(0.42 + 0.15, player.getAttributeValue(Attributes.JUMP_STRENGTH), "jump strength");
			checkNear(3.0 + 10.0, player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE), "safe fall distance");
			checkNear(0.5, player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), "half the fall damage");
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		});

		context.getInput().pressKey(TalentClient.OPEN_TALENTS);
		context.waitForScreen(TalentScreen.class);
		context.waitTicks(2);
		screenshot(context, "ist_05_maxed_tree");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	/**
	 * Toggleable talents: /talents toggle off removes the attribute modifier, on re-adds it, a non-toggleable
	 * talent is rejected, and a right-click in the tree flips the state through the payload round-trip.
	 */
	private static void toggling(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("talents set @p higher_jumping 5");
		singleplayer.getServer().runOnServer(_ ->
				checkNear(0.42 + 0.15, player(singleplayer).getAttributeValue(Attributes.JUMP_STRENGTH), "jump strength at level 5"));

		singleplayer.getServer().runCommand("talents toggle @p higher_jumping off");
		context.waitTicks(5);
		singleplayer.getServer().runOnServer(_ ->
				checkNear(0.42, player(singleplayer).getAttributeValue(Attributes.JUMP_STRENGTH), "toggling off removes the jump bonus"));

		singleplayer.getServer().runCommand("talents toggle @p higher_jumping on");
		context.waitTicks(5);
		singleplayer.getServer().runOnServer(_ ->
				checkNear(0.42 + 0.15, player(singleplayer).getAttributeValue(Attributes.JUMP_STRENGTH), "toggling on puts the jump bonus back"));

		// Faster Mining isn't toggleable: the command must reject it and change nothing.
		singleplayer.getServer().runOnServer(server -> {
			boolean rejected = false;

			try {
				server.getCommands().getDispatcher().execute(
						server.getCommands().getDispatcher().parse("talents toggle @p faster_mining off", server.createCommandSourceStack()));
			} catch (CommandSyntaxException expected) {
				rejected = true;
			}

			check(rejected, "Toggling a non-toggleable talent must be rejected");
			check(Talents.level(player(singleplayer), Talent.FASTER_MINING) == 5, "A rejected toggle must not change the level");
			check(!Talents.data(player(singleplayer)).isDisabled(Talent.HIGHER_JUMPING), "A rejected toggle must not change anything else either");
		});

		// The right-click path: open the tree, right-click the node, and the payload round-trip must flip it.
		context.getInput().pressKey(TalentClient.OPEN_TALENTS);
		context.waitForScreen(TalentScreen.class);
		context.waitTicks(2);
		hover(context, Talent.HIGHER_JUMPING);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		singleplayer.getServer().waitFor(_ -> Talents.data(player(singleplayer)).isDisabled(Talent.HIGHER_JUMPING), 40);
		context.waitTicks(5);
		singleplayer.getServer().runOnServer(_ ->
				checkNear(0.42, player(singleplayer).getAttributeValue(Attributes.JUMP_STRENGTH), "the right-click toggle removes the jump bonus"));
		screenshot(context, "ist_11_toggle_off");

		// Leave it on, so the final save/reload sees the top-level state.
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		singleplayer.getServer().waitFor(_ -> !Talents.data(player(singleplayer)).isDisabled(Talent.HIGHER_JUMPING), 40);
		context.waitTicks(5);
		singleplayer.getServer().runOnServer(_ ->
				checkNear(0.42 + 0.15, player(singleplayer).getAttributeValue(Attributes.JUMP_STRENGTH), "the second right-click flips it back on"));
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	/**
	 * The XP earners grant flat XP points for activity: Mining XP per broken block, Running XP per block moved
	 * on foot, Jumping XP per jump, Punching XP per point of bare-fist damage. First a control with all four at
	 * level 0 (a broken block and 8 blocks of movement earn nothing), then each earner at a chosen level with the
	 * XP delta asserted, and finally a restore of the all-maxed state (and a clean position) for the save/reload
	 * block, which expects every talent at its top level.
	 */
	private static void xpEarners(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// The four earners are maxed from the earlier `talents max`; zero them for clean deltas.
		singleplayer.getServer().runCommand("talents set @p mining_xp 0");
		singleplayer.getServer().runCommand("talents set @p running_xp 0");
		singleplayer.getServer().runCommand("talents set @p jumping_xp 0");
		singleplayer.getServer().runCommand("talents set @p punching_xp 0");
		setXpPoints(singleplayer, 0);
		holdInMainHand(context, singleplayer, new ItemStack(Items.DIAMOND_PICKAXE));

		// CONTROL: with no earner talents, a broken block and 8 blocks of movement earn nothing.
		BlockPos controlBlock = placeStoneAt(singleplayer, 2, 0);
		breakBlock(singleplayer, controlBlock);
		singleplayer.getServer().runCommand("tp @p 8.5 -60 0.5");
		context.waitTicks(2);
		singleplayer.getServer().runCommand("tp @p 0.5 -60 0.5");
		context.waitTicks(2);
		checkXpPoints(singleplayer, 0, "no earner talents: a broken block and 8 blocks of movement earn nothing");

		// MINING: L5 = 1.0 point per broken block; five blocks grant 5.
		singleplayer.getServer().runCommand("talents set @p mining_xp 5");
		context.waitTicks(2);
		long beforeMining = xp(singleplayer);
		for (int i = 0; i < 5; i++) {
			breakBlock(singleplayer, placeStoneAt(singleplayer, 2 + i, 0));
		}
		long miningDelta = xp(singleplayer) - beforeMining;
		check(miningDelta >= 5 && miningDelta <= 6, "Mining XP V should grant ~5 points for 5 broken blocks (1.0 each), got " + miningDelta);

		// RUNNING: L5 = 0.05 point per block; ten 4-block steps = 40 blocks ≈ 2 points.
		singleplayer.getServer().runCommand("talents set @p running_xp 5");
		context.waitTicks(2);
		long beforeRunning = xp(singleplayer);
		double startRunX = singleplayer.getServer().computeOnServer(_ -> player(singleplayer).getX());
		for (int i = 1; i <= 10; i++) {
			singleplayer.getServer().runCommand("tp @p " + (startRunX + 4.0 * i) + " -60 0.5");
			context.waitTick();
		}
		long runningDelta = xp(singleplayer) - beforeRunning;
		check(runningDelta >= 1 && runningDelta <= 4, "Running XP V should grant ~2 points for 40 blocks moved (0.05 each), got " + runningDelta);

		// JUMPING: L3 = 0.6 point per jump; four real jumps ≈ 2.4 → 2 whole points. Walking off a ledge never
		// counts (no upward velocity), so each jump here is a ground-to-air transition set with vy = 0.5.
		singleplayer.getServer().runCommand("talents set @p jumping_xp 3");
		context.waitTicks(2);
		long beforeJumping = xp(singleplayer);
		for (int i = 0; i < 4; i++) {
			singleplayer.getServer().waitFor(_ -> player(singleplayer).onGround(), 100);
			singleplayer.getServer().runOnServer(_ -> {
				ServerPlayer player = player(singleplayer);
				if (player.onGround()) {
					player.setDeltaMovement(player.getDeltaMovement().add(0.0, 0.5, 0.0));
				}
			});
			singleplayer.getServer().waitFor(_ -> !player(singleplayer).onGround(), 100);
			singleplayer.getServer().waitFor(_ -> player(singleplayer).onGround(), 300);
		}
		long jumpingDelta = xp(singleplayer) - beforeJumping;
		check(jumpingDelta >= 2 && jumpingDelta <= 3, "Jumping XP III should grant ~2 points for 4 jumps (0.6 each), got " + jumpingDelta);

		// PUNCHING: L5 = 0.5 point per damage point. A bare fist does (1 + 5) x 1.2 = 7.2 damage here (Unarmed
		// Specialist V + Stronger Weapons V, both still maxed). A fresh pig per punch: in 26.3 a repeated hit that
		// deals no more than the last damage to the same victim is ignored (lastHurt), the same pattern the
		// weaponDamage scenario relies on.
		singleplayer.getServer().runCommand("talents set @p punching_xp 5");
		holdInMainHand(context, singleplayer, ItemStack.EMPTY);
		long beforePunching = xp(singleplayer);
		double totalDealt = 0.0;
		for (int i = 0; i < 2; i++) {
			totalDealt += singleplayer.getServer().computeOnServer(_ -> {
				ServerPlayer player = player(singleplayer);
				Mob pig = spawn(singleplayer, EntityTypes.PIG, 3, 0);
				float before = pig.getHealth();
				pig.hurtServer((ServerLevel) pig.level(), player.damageSources().playerAttack(player), 1.0F);
				double dealt = before - pig.getHealth();
				pig.discard();
				return dealt;
			});
		}
		long punchingDelta = xp(singleplayer) - beforePunching;
		long expectedWhole = (long) Math.floor(totalDealt * 0.5 - 1e-9);
		check(punchingDelta >= expectedWhole && punchingDelta <= expectedWhole + 1,
				"Punching XP V should grant ~" + totalDealt * 0.5 + " points for " + totalDealt + " punch damage (0.5 each), got " + punchingDelta);

		// Restore the all-maxed state (the save/reload block asserts every talent is maxed) and a clean position
		// and hand for the remaining scenarios.
		singleplayer.getServer().runCommand("talents max @p");
		singleplayer.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
		holdInMainHand(context, singleplayer, ItemStack.EMPTY);
		context.waitTicks(2);
	}

	/**
	 * The three farming talents, each against a vanilla control:
	 * <ul>
	 * <li><b>Bountiful Harvest</b> — 40 loot rolls of ripe wheat: talent V gives exactly 6 wheat per roll (the 1 main
	 *     drop + 5 extra), a no-entity roll and a talent-0 player each give the vanilla 1, and a young-wheat roll
	 *     (26.3: seeds, not wheat) gets no bonus.</li>
	 * <li><b>Fast Growth</b> — two identical 60-stalk wheat plots at age 0, one within 16 blocks of the player
	 *     (Fast Growth III in reach) and one 63 blocks away (the control: no player in reach). Each stalk is forced
	 *     through 26 of the exact {@code randomTick} rolls the random-tick system makes (natural ticking here is
	 *     ~3 positions per 4096 per section-tick, which would move almost nothing in the time this test has), and the
	 *     in-range plot's mean age must be at least 1.5x the control's (~2.5x expected).</li>
	 * <li><b>Careful Hands</b> — a 600-point durability change on a diamond pickaxe: with Careful Hands V the item
	 *     behaves as if Unbreaking V (each point is skipped 5 of 6 times, so about 1/6 lands), and so does gear
	 *     Unbreaking III plus talent II (the sum rule: level 5); without the talent the full 600 lands.</li>
	 * </ul>
	 * Ends with every talent maxed, the player at the origin with an empty hand, and the plot blocks removed, for the
	 * remaining scenarios and the save/reload block.
	 */
	private static void farming(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		bountifulHarvest(singleplayer);
		fastGrowth(singleplayer);
		carefulHands(singleplayer);

		// Restore the all-maxed state the save/reload block asserts, and a clean position and hand.
		singleplayer.getServer().runCommand("talents max @p");
		singleplayer.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
		holdInMainHand(context, singleplayer, ItemStack.EMPTY);
		context.waitTicks(2);
	}

	/**
	 * The three mobility talents, each against a vanilla control:
	 * <ul>
	 * <li><b>Step Assist</b> — 26.3 reads the step-up height off the STEP_HEIGHT attribute (vanilla 0.6), and the
	 *     player's own {@code maxUpStep()} — the accessor the step-up logic in {@code Entity.collide} uses —
	 *     returns that value, so the attribute row is asserted through both at level 3 (0.9 = 0.6 + 0.3) and a
	 *     level-0 control (back to 0.6). A functional walk-up is skipped: no vanilla block is 0.6 &lt; x &le; 0.9
	 *     high, so no cheap obstacle exists.</li>
	 * <li><b>Reach</b> — 26.3's {@code blockInteractionRange()} and {@code entityInteractionRange()} are pure
	 *     reads of the BLOCK_INTERACTION_RANGE and ENTITY_INTERACTION_RANGE attributes (vanilla 4.5/3.0), so the
	 *     additive rows are asserted through the attributes and the accessors at level 3 (6.0/4.5) and a level-0
	 *     control (4.5/3.0). The creative/spectator extended reach is a transient modifier on the same
	 *     attributes, so the permanent row lands additively on top of it in that state too (not exercised here:
	 *     the test player is in survival).</li>
	 * <li><b>Swim Speed</b> — 26.3 has no swim-speed attribute (WATER_MOVEMENT_EFFICIENCY only slows), so the
	 *     effect softens the per-tick underwater damping in {@code travelInWater} (0.8) to
	 *     {@code 1 - (1 - 0.8)/(1 + 0.1*level)}, which multiplies the sustained in-water movement by exactly
	 *     1 + 0.1 per level. Measured functionally: a 16x4x16 water volume at the test site, the player given
	 *     the same initial horizontal velocity (0.4) in fresh lanes and left to coast for 60 ticks — the
	 *     level-3 coast must cover at least 20% more ground than the level-0 control (~30% expected), and the
	 *     toggled-off coast must match the control (the effective level the mixin reads is 0 while off).</li>
	 * </ul>
	 * Ends with every talent maxed (the toggle back on), the player at the origin on dry land with no residual
	 * velocity, and the water removed, for the remaining scenarios and the save/reload block.
	 */
	private static void mobility(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// STEP ASSIST: the attribute row and the public step-up accessor.
		singleplayer.getServer().runCommand("talents set @p step_assist 3");
		context.waitTicks(2);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			double attr = player.getAttributeValue(Attributes.STEP_HEIGHT);
			double up = player.maxUpStep();
			checkNear(0.9, attr, "step height attribute at level 3 (0.6 + 0.3)");
			checkNear(0.9, up, "the step-up accessor the collide logic uses, at level 3");
		});
		singleplayer.getServer().runCommand("talents set @p step_assist 0");
		context.waitTicks(2);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			double attr = player.getAttributeValue(Attributes.STEP_HEIGHT);
			double up = player.maxUpStep();
			checkNear(0.6, attr, "level 0 restores the vanilla 0.6 step height");
			checkNear(0.6, up, "the vanilla step-up accessor at level 0");
		});

		// REACH: level-0 control first (the survival baselines), then level 3.
		singleplayer.getServer().runCommand("talents set @p reach 0");
		context.waitTicks(2);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			double block = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
			double entity = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
			checkNear(4.5, block, "the vanilla 4.5 block interaction range");
			checkNear(3.0, entity, "the vanilla 3.0 entity interaction range");
		});
		singleplayer.getServer().runCommand("talents set @p reach 3");
		context.waitTicks(2);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			double block = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
			double entity = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
			double blockR = player.blockInteractionRange();
			double entityR = player.entityInteractionRange();
			checkNear(4.5 + 1.5, block, "block range 4.5 + 1.5 at level 3");
			checkNear(4.5 + 1.5, blockR, "the accessor block breaking and item use go through");
			checkNear(3.0 + 1.5, entity, "entity range 3.0 + 1.5 at level 3");
			checkNear(3.0 + 1.5, entityR, "the accessor item use against entities goes through");
		});

		// SWIM SPEED: the 60-tick coasts — level-0 control, talent, toggled-off. Swim Speed comes maxed
		// from the earlier `talents max`, so the control is set explicitly. The pool is built as a sealed
		// box (plugged floor over the Fast Growth farmland holes, stone walls on all four sides) so the
		// source water can neither drain through the floor nor flow out into the test site; every cell it
		// covers is snapshotted and restored exactly on cleanup.
		Map<BlockPos, BlockState> floor = singleplayer.getServer().computeOnServer(_ -> makePoolFloor(player(singleplayer).level()));
		singleplayer.getServer().runOnServer(_ -> fillSwimPool(player(singleplayer).level()));
		context.waitTicks(2);
		singleplayer.getServer().runCommand("talents set @p swim_speed 0");
		context.waitTicks(2);
		double control = swimCoast(context, singleplayer, 2.0);
		check(control > 1.0, "The level-0 control should coast a couple of blocks in water, got " + control);

		singleplayer.getServer().runCommand("talents set @p swim_speed 3");
		context.waitTicks(2);
		double talented = swimCoast(context, singleplayer, 4.0);
		check(talented >= 1.2 * control,
				"Swim Speed III should coast at least 20% farther than the control (~30% expected), got " + talented + " vs " + control);

		// The toggle: level bought, talent off — the effective level is 0, and the coast must match the control.
		singleplayer.getServer().runCommand("talents toggle @p swim_speed off");
		context.waitTicks(2);
		double toggledOff = swimCoast(context, singleplayer, 6.0);
		check(Math.abs(toggledOff - control) <= 0.05 * control,
				"A toggled-off Swim Speed must coast like the level-0 control, got " + toggledOff + " vs " + control);
		singleplayer.getServer().runOnServer(_ ->
				check(Talents.data(player(singleplayer)).effectiveLevel(Talent.SWIM_SPEED) == 0,
						"the effective level the mixin reads is 0 while the talent is toggled off"));

		// Back on for the save/reload block (which expects every talent at its top level), then dry off:
		// water out, player at the origin on dry land, no residual velocity.
		singleplayer.getServer().runCommand("talents toggle @p swim_speed on");
		context.waitTicks(2);
		singleplayer.getServer().runOnServer(_ ->
				check(Talents.data(player(singleplayer)).effectiveLevel(Talent.SWIM_SPEED) == 3,
						"toggling back on restores the effective level"));
		// The step control left Step Assist at 0; restore the all-maxed state the save/reload block asserts.
		singleplayer.getServer().runCommand("talents max @p");
		context.waitTicks(2);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			clearSwimPool(player.level(), floor);
			player.setDeltaMovement(0, 0, 0);
		});
		context.runOnClient(client -> client.player.setDeltaMovement(0, 0, 0));
		singleplayer.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
		// The pool's water volume deliberately starts two blocks east of the origin: the client can hold a
		// stale water update for a cell while a big block change is in flight, and the integrated client
		// drives the player, so a player standing in such a cell drifts on a constant diagonal with no
		// input. Home is kept out of the water volume, and the settle below lets the teleport and the
		// pool removal sync before the re-zero, so no residual velocity survives into the next scenario.
		context.waitTicks(4);
		singleplayer.getServer().runOnServer(_ -> player(singleplayer).setDeltaMovement(0, 0, 0));
		context.runOnClient(client -> client.player.setDeltaMovement(0, 0, 0));
	}

	/** Snapshots a cell's original state (once) and plugs it with stone. */
	private static void plugCell(Map<BlockPos, BlockState> snapshot, ServerLevel level, BlockPos pos) {
		if (!snapshot.containsKey(pos)) {
			snapshot.put(pos, level.getBlockState(pos));
		}
		level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
	}

	/**
	 * Builds the pool's containment: the floor row (y = -61, x/z 2..17) plus solid stone walls on all four
	 * sides of the water volume (west x=1, east x=18, north z=-1, south z=16, each y -60..-57, corners
	 * included). The walls are essential: without them the source water flows out of the open sides (up to
	 * 7 blocks) and, at this test site, ran west into the player's home cells — a stale client-side water
	 * update for a flowed-into cell puts the integrated (client-driven) player in water and makes it drift.
	 * The Fast Growth cleanup also sets its plot blocks back to air, which removes the floor under part of
	 * the pool area; a coasting player could fall through there. Every cell is snapshotted and restored on
	 * cleanup.
	 */
	private static Map<BlockPos, BlockState> makePoolFloor(ServerLevel level) {
		Map<BlockPos, BlockState> snapshot = new HashMap<>();
		for (int x = 2; x < 18; x++) {
			for (int z = 0; z < 16; z++) {
				plugCell(snapshot, level, new BlockPos(x, -61, z));
			}
		}
		for (int y = -60; y <= -57; y++) {
			for (int z = -1; z <= 16; z++) {
				plugCell(snapshot, level, new BlockPos(1, y, z));   // west
				plugCell(snapshot, level, new BlockPos(18, y, z));  // east
			}
			for (int x = 1; x <= 18; x++) {
				plugCell(snapshot, level, new BlockPos(x, y, -1));  // north
				plugCell(snapshot, level, new BlockPos(x, y, 16));  // south
			}
		}
		return snapshot;
	}

	/**
	 * Fills the 16x4x16 still-water pool at (x 2..17, y -60..-57, z 0..15) at the test site. The two-block
	 * offset from the origin keeps the player's home cells out of the water volume, so a stale client-side
	 * water update can never put the player (who the integrated client drives) in water at the origin.
	 */
	private static void fillSwimPool(ServerLevel level) {
		for (int x = 2; x < 18; x++) {
			for (int y = -60; y <= -57; y++) {
				for (int z = 0; z < 16; z++) {
					level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState());
				}
			}
		}
	}

	/** Empties the pool back to air and restores the containment (floor and walls) from the snapshot exactly. */
	private static void clearSwimPool(ServerLevel level, Map<BlockPos, BlockState> floor) {
		for (int x = 2; x < 18; x++) {
			for (int y = -60; y <= -57; y++) {
				for (int z = 0; z < 16; z++) {
					level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		for (Map.Entry<BlockPos, BlockState> entry : floor.entrySet()) {
			level.setBlockAndUpdate(entry.getKey(), entry.getValue());
		}
	}

	/**
	 * One 60-tick coast run in the pool: a fresh tp into the water lane at z = {@code laneZ} (which also
	 * resets any residual velocity), the same initial horizontal push (+x, 0.4 blocks/tick) set on both
	 * sides — the integrated singleplayer simulates the player on the client and the server, and both must
	 * get the push so neither side's position is corrected back — then 60 ticks of free coast with no input.
	 * The lane's x = 3.5 sits 1.5 blocks in from the pool's west edge (the pool starts two blocks east of
	 * the origin), so the whole 60-tick coast (~2.6 blocks at the level-3 factor) stays inside the water.
	 * Returns the horizontal ground covered, read from the server player.
	 */
	private static double swimCoast(ClientGameTestContext context, TestSingleplayerContext singleplayer, double laneZ) {
		singleplayer.getServer().runCommand("tp @p 3.5 -60 " + laneZ);
		context.waitTicks(2);
		Vec3 push = new Vec3(0.4, 0.0, 0.0);
		singleplayer.getServer().computeOnServer(_ -> {
			player(singleplayer).setDeltaMovement(push);
			return 0.0;
		});
		context.runOnClient(client -> client.player.setDeltaMovement(push));
		context.waitTicks(60);
		double covered = singleplayer.getServer().computeOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			player.setDeltaMovement(0, 0, 0);
			return player.getX() - 3.5;
		});
		context.runOnClient(client -> client.player.setDeltaMovement(0, 0, 0));
		return covered;
	}

	/** Bountiful Harvest: exactly the level's extra copies of the main item, and only for a harvest that produced it. */
	private static void bountifulHarvest(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			ServerLevel level = player.level();
			BlockState ripe = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
			BlockState young = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 1);
			BlockPos pos = player.blockPosition();
			ItemStack tool = new ItemStack(Items.IRON_HOE);

			Talents.setLevel(player, Talent.BOUNTIFUL_HARVEST, 5);
			int withTalent = 0;
			for (int i = 0; i < 40; i++) {
				withTalent += countOf(Block.getDrops(ripe, level, pos, null, player, tool), Items.WHEAT);
			}
			check(withTalent == 40 * 6, "Bountiful Harvest V should give exactly 6 wheat per mature harvest (1 + 5), got " + withTalent + " from 40");

			// Control 1: no breaking entity (an explosion-style roll) adds nothing.
			int noEntity = 0;
			for (int i = 0; i < 40; i++) {
				noEntity += countOf(Block.getDrops(ripe, level, pos, null, null, tool), Items.WHEAT);
			}
			check(noEntity == 40, "A harvest with no breaking entity should stay at the vanilla 1 wheat, got " + noEntity + " from 40");

			// Control 2: a talent-0 player keeps the vanilla 1.
			Talents.setLevel(player, Talent.BOUNTIFUL_HARVEST, 0);
			int noTalent = 0;
			for (int i = 0; i < 40; i++) {
				noTalent += countOf(Block.getDrops(ripe, level, pos, null, player, tool), Items.WHEAT);
			}
			check(noTalent == 40, "A talent-less player should keep the vanilla 1 wheat per mature harvest, got " + noTalent + " from 40");

			// Young wheat in 26.3 drops seeds, not wheat: no main item produced, so nothing to increase.
			Talents.setLevel(player, Talent.BOUNTIFUL_HARVEST, 5);
			int youngWheat = 0;
			int youngSeeds = 0;
			for (int i = 0; i < 40; i++) {
				List<ItemStack> drops = Block.getDrops(young, level, pos, null, player, tool);
				youngWheat += countOf(drops, Items.WHEAT);
				youngSeeds += countOf(drops, Items.WHEAT_SEEDS);
			}
			check(youngWheat == 0, "Young wheat produces seeds, not wheat: the bonus must not conjure the main item, got " + youngWheat + " wheat from 40");
			check(youngSeeds >= 40, "Young wheat should still drop its vanilla seed, got " + youngSeeds + " seeds from 40");
		});
	}

	/** Fast Growth: the in-reach plot grows at least 1.5x the 63-blocks-away control (~2.5x expected). */
	private static void fastGrowth(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("talents set @p fast_growth 3");
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			ServerLevel level = player.level();

			// Two identical 10x6 age-0 wheat plots on farmland, each stalk lit from directly above by a sea lantern
			// (26.3 gates growth on raw brightness >= 9 and this bottom-of-world pocket has none): plot A is 4-14
			// blocks from the player (within the 16-block reach), plot B starts 63 blocks west (the control).
			// 26 rolls per stalk keeps both plots well below the age-7 saturation point, so the ratio stays
			// measurable (~2.5x expected; the assertion only requires 1.5x).
			BlockPos near = plotWheat(level, 4, 0);
			level.getChunk(-5, 0); // ensure the far plot's chunk is generated before writing into it
			BlockPos far = plotWheat(level, -72, 0);

			tickPlot(level, near, 26);
			tickPlot(level, far, 26);

			double nearAge = meanAge(level, near);
			double farAge = meanAge(level, far);
			check(farAge >= 1.0, "The control plot should have grown a little (vanilla), mean age " + farAge);
			check(nearAge >= 1.5 * farAge,
					"Fast Growth III in reach should grow at least 1.5x the 63-blocks-away control (~2.5x expected), got " + nearAge + " vs " + farAge);

			// Leave the ground clean for the remaining scenarios and the save/reload.
			removePlot(level, near);
			removePlot(level, far);
		});
	}

	/** A 10x6 age-0 wheat plot on farmland at (ox, -60, oz), each stalk under a sea lantern. Returns the origin. */
	private static BlockPos plotWheat(ServerLevel level, int ox, int oz) {
		for (int x = 0; x < 10; x++) {
			for (int z = 0; z < 6; z++) {
				BlockPos wheat = new BlockPos(ox + x, -60, oz + z);
				level.setBlockAndUpdate(wheat.below(), Blocks.FARMLAND.defaultBlockState());
				level.setBlockAndUpdate(wheat.above(), Blocks.SEA_LANTERN.defaultBlockState());
				level.setBlockAndUpdate(wheat, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0));
			}
		}
		return new BlockPos(ox, -60, oz);
	}

	/** Calls the same per-stalk roll the random-tick system makes, directly, {@code ticks} times per stalk. */
	private static void tickPlot(ServerLevel level, BlockPos origin, int ticks) {
		RandomSource random = level.getRandom();
		for (int x = 0; x < 10; x++) {
			for (int z = 0; z < 6; z++) {
				BlockPos pos = origin.offset(x, 0, z);
				for (int t = 0; t < ticks; t++) {
					level.getBlockState(pos).randomTick(level, pos, random);
				}
			}
		}
	}

	/** The mean wheat age across a 10x6 plot. */
	private static double meanAge(ServerLevel level, BlockPos origin) {
		long sum = 0;
		for (int x = 0; x < 10; x++) {
			for (int z = 0; z < 6; z++) {
				sum += level.getBlockState(origin.offset(x, 0, z)).getValue(CropBlock.AGE);
			}
		}
		return (double) sum / 60.0;
	}

	/** Clears a plot's wheat, lanterns and farmland back to air. */
	private static void removePlot(ServerLevel level, BlockPos origin) {
		for (int x = 0; x < 10; x++) {
			for (int z = 0; z < 6; z++) {
				BlockPos pos = origin.offset(x, 0, z);
				level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
			}
		}
	}

	/** Careful Hands: a 600-point durability change lands as Unbreaking (gear + talent, summed) for the player. */
	private static void carefulHands(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			ServerLevel level = player.level();
			Holder<Enchantment> unbreaking = level.registryAccess().getOrThrow(Enchantments.UNBREAKING);

			// Control: no talent, no unbreaking gear -> the full 600 points land, deterministically.
			Talents.setLevel(player, Talent.CAREFUL_HANDS, 0);
			int control = damageTaken(level, player, new ItemStack(Items.DIAMOND_PICKAXE), 600);
			check(control == 600, "Without the talent or unbreaking, all 600 points should land, got " + control);

			// Careful Hands V on bare gear: the item works as if Unbreaking V — each point is skipped 5 of 6 times,
			// so about 1/6 lands.
			Talents.setLevel(player, Talent.CAREFUL_HANDS, 5);
			int talented = damageTaken(level, player, new ItemStack(Items.DIAMOND_PICKAXE), 600);
			check(talented <= 0.4 * control,
					"Careful Hands V should land well under 40% of the control (~1/6 expected), got " + talented + " vs " + control);
			check(talented >= 1, "Some of the 600 points should still land, got " + talented);

			// The sum rule in game: gear Unbreaking III + talent II = level 5, again ~1/6 lands — and the gear's real
			// component must be restored exactly.
			Talents.setLevel(player, Talent.CAREFUL_HANDS, 2);
			ItemStack geared = new ItemStack(Items.DIAMOND_PICKAXE);
			geared.enchant(unbreaking, 3);
			int summed = damageTaken(level, player, geared, 600);
			check(summed <= 0.4 * control,
					"Unbreaking III + Careful Hands II should combine to level 5 (~1/6 expected), got " + summed + " vs " + control);
			ItemEnchantments gearEnc = geared.getOrDefault(EnchantmentHelper.getComponentType(geared), ItemEnchantments.EMPTY);
			check(gearEnc.getLevel(unbreaking) == 3,
					"The gear's real Unbreaking III must be restored after the virtual level is removed, got " + gearEnc.getLevel(unbreaking));

			Talents.setLevel(player, Talent.CAREFUL_HANDS, 5);
		});
	}

	/** Runs one durability change of {@code amount} points through the player's path; returns the points that landed. */
	private static int damageTaken(ServerLevel level, ServerPlayer player, ItemStack stack, int amount) {
		int before = stack.getDamageValue();
		stack.hurtAndBreak(amount, level, player, _ -> {
		});
		return stack.getDamageValue() - before;
	}

	/** Places a stone block offset from the player's current position and returns its pos. */
	private static BlockPos placeStoneAt(TestSingleplayerContext singleplayer, int dx, int dz) {
		return singleplayer.getServer().computeOnServer(server -> {
			ServerPlayer player = player(singleplayer);
			BlockPos pos = player.blockPosition().east(dx).south(dz);
			player.level().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
			return pos;
		});
	}

	/**
	 * Destroys a placed block through the player's own game mode. This goes through the same
	 * {@code PlayerBlockBreakEvents.AFTER} injection that real mining completion does (the Fabric mixin sits on
	 * this same destroyBlock path), so the Mining XP talent sees a genuine player block break with drops and sound.
	 * Server-driven and one tick per block: holding the client attack key in this harness proved flaky — after a
	 * teleport round-trip the client's block cache can lag the server, and an attack fired at the stale view targets
	 * a solid block behind the target, which never completes.
	 */
	private static void breakBlock(TestSingleplayerContext singleplayer, BlockPos pos) {
		singleplayer.getServer().runOnServer(_ -> player(singleplayer).gameMode.destroyBlock(pos));
		boolean air = singleplayer.getServer().computeOnServer(_ -> player(singleplayer).level().getBlockState(pos).isAir());
		check(air, "The block at " + pos + " should be broken");
	}

	/** Faster Mining V mines like Efficiency V, on the client and the server alike; the stacking setting decides how it meets real Efficiency. */
	private static void miningSpeed(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		BlockState stone = Blocks.STONE.defaultBlockState();
		holdInMainHand(context, singleplayer, new ItemStack(Items.DIAMOND_PICKAXE));
		checkDestroySpeed(context, singleplayer, stone, STONE_PICKAXE_SPEED + 26, "plain pickaxe with Faster Mining V (Efficiency V adds 5^2+1)");

		ItemStack efficient = enchanted(singleplayer, new ItemStack(Items.DIAMOND_PICKAXE), Enchantments.EFFICIENCY, 3);
		holdInMainHand(context, singleplayer, efficient);
		checkDestroySpeed(context, singleplayer, stone, STONE_PICKAXE_SPEED + 26, "Efficiency III pickaxe with Faster Mining V: the higher one counts");

		setRules(new TalentRules(TalentRules.DEFAULTS.costBase(), TalentRules.DEFAULTS.costGrowth(), EnchantmentStacking.ADD));
		checkDestroySpeed(context, singleplayer, stone, STONE_PICKAXE_SPEED + 65, "Efficiency III + Faster Mining V added up is Efficiency VIII (8^2+1)");
		setRules(TalentRules.DEFAULTS);

		singleplayer.getServer().runCommand("talents set @p faster_mining 0");
		context.waitTicks(5);
		checkDestroySpeed(context, singleplayer, stone, STONE_PICKAXE_SPEED + 10, "Efficiency III without the talent");
		holdInMainHand(context, singleplayer, new ItemStack(Items.DIAMOND_PICKAXE));
		checkDestroySpeed(context, singleplayer, stone, STONE_PICKAXE_SPEED, "a plain pickaxe without the talent");
		singleplayer.getServer().runCommand("talents set @p faster_mining 5");
		holdInMainHand(context, singleplayer, ItemStack.EMPTY);
	}

	/** More Luck V works as Looting V, Luck of the Sea V and Fortune V. */
	private static void luck(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			ServerLevel level = player.level();
			Holder<Enchantment> looting = level.registryAccess().getOrThrow(Enchantments.LOOTING);
			check(EnchantmentHelper.getEnchantmentLevel(looting, player) == 5, "More Luck V should loot like Looting V");
			check(EnchantmentHelper.getFishingLuckBonus(level, new ItemStack(Items.FISHING_ROD), player) == 5, "More Luck V should fish like Luck of the Sea V");

			BlockState ore = Blocks.DIAMOND_ORE.defaultBlockState();
			BlockPos pos = player.blockPosition();
			int withTalent = 0;
			int without = 0;

			for (int i = 0; i < 40; i++) {
				withTalent += totalCount(Block.getDrops(ore, level, pos, null, player, new ItemStack(Items.IRON_PICKAXE)));
				without += totalCount(Block.getDrops(ore, level, pos, null, null, new ItemStack(Items.IRON_PICKAXE)));
			}

			check(without == 40, "Without a player there's no luck: 1 diamond per ore, got " + without);
			// Fortune V gives about 3.1 per ore on average; 60 is very far below that.
			check(withTalent > 60, "Fortune V should give a lot more than 1 diamond per ore, got " + withTalent + " from 40");
		});
	}

	/** Stronger Weapons V (+20%) and the specialists: +30% for a sword, +5 for a punch; damage that isn't an attack is left alone. */
	private static void weaponDamage(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);

			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
			checkNear(4.0 * 1.5, damageDealt(singleplayer, player.damageSources().playerAttack(player), 4.0F), "a sword hit");

			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			checkNear((1.0 + 5.0) * 1.2, damageDealt(singleplayer, player.damageSources().playerAttack(player), 1.0F), "a punch");

			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
			checkNear(4.0 * 1.2, damageDealt(singleplayer, player.damageSources().playerAttack(player), 4.0F), "an axe: only Stronger Weapons");

			checkNear(4.0, damageDealt(singleplayer, player.damageSources().thorns(player), 4.0F), "thorns aren't an attack");
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		});
	}

	/** Block Chance V shrugs off about 10% of attacks from mobs; at level 0 none. */
	private static void blockChance(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			Mob zombie = spawn(singleplayer, EntityTypes.ZOMBIE, 6, 0);
			int blocked = countBlocked(player, zombie, 400);
			check(blocked >= 15 && blocked <= 75, "Expected about 40 of 400 attacks blocked at 10%, got " + blocked);

			Talents.setLevel(player, Talent.BLOCK_CHANCE, 0);
			check(countBlocked(player, zombie, 50) == 0, "Nothing should be blocked without the talent");
			Talents.setLevel(player, Talent.BLOCK_CHANCE, Talent.BLOCK_CHANCE.maxLevel());
			zombie.discard();
			player.setHealth(player.getMaxHealth());
		});
	}

	/** XP Multiplier V doubles what an orb gives. */
	private static void xpMultiplier(TestSingleplayerContext singleplayer) {
		setXpPoints(singleplayer, 0);
		ExperienceOrb orb = singleplayer.getServer().computeOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			ExperienceOrb o = new ExperienceOrb(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), 10);
			check(player.level().addFreshEntity(o), "Could not spawn an experience orb");
			return o;
		});
		singleplayer.getServer().waitFor(_ -> orb.isRemoved(), 100);
		checkXpPoints(singleplayer, 20, "a 10 XP orb with XP Multiplier V");
	}

	/** Pickups that don't fit go into the extra inventory; J opens it; shrinking the talent moves items out of locked slots. */
	private static void extraInventory(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		ItemEntity diamonds = singleplayer.getServer().computeOnServer(_ -> {
			ServerPlayer player = player(singleplayer);

			for (int i = 0; i < 36; i++) {
				player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			}

			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.DIAMOND, 10));
			item.setNoPickUpDelay();
			check(player.level().addFreshEntity(item), "Could not drop diamonds");
			return item;
		});
		singleplayer.getServer().waitFor(_ -> diamonds.isRemoved(), 100);
		singleplayer.getServer().runOnServer(_ -> check(countOf(Backpacks.get(player(singleplayer)), Items.DIAMOND) == 10,
				"Diamonds that don't fit the inventory should go into the extra inventory"));

		context.getInput().pressKey(BackpackClient.OPEN_KEY);
		context.waitForScreen(BackpackScreen.class);
		context.waitTicks(2);
		screenshot(context, "ist_06_extra_inventory");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			Backpacks.get(player).setItem(30, new ItemStack(Items.EMERALD, 5));
			Talents.setLevel(player, Talent.BACKPACK, 2);
			Backpack extra = Backpacks.get(player);
			check(extra.getItem(30).isEmpty(), "A slot that's locked now must be emptied");
			check(countOf(extra, Items.EMERALD) == 5, "Its items should move to an unlocked extra slot");
			check(Backpacks.unlockedSlots(player) == 14, "Level 2 unlocks 14 slots");
		});

		context.getInput().pressKey(BackpackClient.OPEN_KEY);
		context.waitForScreen(BackpackScreen.class);
		context.waitTicks(2);
		screenshot(context, "ist_07_extra_inventory_locked");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		singleplayer.getServer().runCommand("talents set @p extra_inventory 5");
		singleplayer.getServer().runCommand("clear @p");
		singleplayer.getServer().runOnServer(_ -> Backpacks.get(player(singleplayer)).clearContent());
	}

	/** Right-click trades one diamond for 40 XP points; sneaking trades the whole stack. */
	private static void experienceConverter(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		BlockPos converter = singleplayer.getServer().computeOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			BlockPos pos = player.blockPosition().south(2);
			player.level().setBlockAndUpdate(pos, ExperienceConverter.BLOCK.defaultBlockState());
			return pos;
		});
		setXpPoints(singleplayer, 0);
		holdInMainHand(context, singleplayer, new ItemStack(Items.DIAMOND, 2));
		context.getInput().lookAt(converter);
		context.waitTick();
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(5);
		checkXpPoints(singleplayer, 40, "one diamond");
		singleplayer.getServer().runOnServer(_ -> check(player(singleplayer).getMainHandItem().getCount() == 1, "One diamond should be used up"));
		screenshot(context, "ist_08_converter");

		holdInMainHand(context, singleplayer, new ItemStack(Items.DIAMOND, 3));
		context.getInput().holdKey(options -> options.keyShift);
		context.waitTicks(3);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(5);
		context.getInput().releaseKey(options -> options.keyShift);
		checkXpPoints(singleplayer, 40 + 3 * 40, "a sneaking right-click with 3 diamonds");
		singleplayer.getServer().runOnServer(_ -> check(player(singleplayer).getMainHandItem().isEmpty(), "Sneaking should trade the whole stack"));
		context.waitTicks(5);
	}

	/** The survival inventory has the two buttons above its top right corner. */
	private static void inventoryButtons(ClientGameTestContext context) {
		context.getInput().pressKey(options -> options.keyInventory);
		context.waitForScreen(InventoryScreen.class);
		context.waitTicks(2);
		screenshot(context, "ist_09_inventory_buttons");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	/** The extra inventory drops on death (no keepInventory); talents and their extra hearts come back with the respawn. */
	private static void death(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		BlockPos deathPos = singleplayer.getServer().computeOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			Backpacks.get(player).setItem(3, new ItemStack(Items.GOLD_INGOT, 3));
			return player.blockPosition();
		});
		singleplayer.getServer().runCommand("kill @p");
		context.waitForScreen(DeathScreen.class);
		context.waitTicks(5);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			check(Backpacks.get(player).isEmpty(), "The extra inventory should be dropped on death");
			int dropped = player.level().getEntitiesOfClass(ItemEntity.class, new AABB(deathPos).inflate(4.0)).stream()
					.filter(item -> item.getItem().is(Items.GOLD_INGOT))
					.mapToInt(item -> item.getItem().getCount())
					.sum();
			check(dropped == 3, "Expected the 3 gold ingots on the ground, found " + dropped);
		});
		context.clickScreenButton("deathScreen.respawn");
		context.waitForScreen(null);
		singleplayer.getConnection().waitForChunksRender();
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			check(Talents.level(player, Talent.MORE_HEALTH) == 10, "Talents are kept through death");
			check(player.getMaxHealth() == 40.0F, "Extra hearts come back after respawning, got " + player.getMaxHealth());
			check(player.getHealth() == 40.0F, "Respawning fills the extra hearts too, got " + player.getHealth());
		});
	}

	/** Clears advancement toasts and chat first, so they don't cover what the screenshot is for. */
	private static void screenshot(ClientGameTestContext context, String name) {
		context.runOnClient(client -> {
			client.gui.toastManager().clear();
			client.gui.hud.getChat().clearMessages(false);
		});
		context.waitTick();
		context.takeScreenshot(name);
	}

	private static int countBlocked(ServerPlayer player, Mob attacker, int attacks) {
		int blocked = 0;

		for (int i = 0; i < attacks; i++) {
			player.damageCooldownTime = 0;
			player.setHealth(player.getMaxHealth());

			if (!player.hurtServer(player.level(), player.damageSources().mobAttack(attacker), 2.0F)) {
				blocked++;
			}
		}

		return blocked;
	}

	/** Health a fresh pig (no armor) loses from this damage. */
	private static double damageDealt(TestSingleplayerContext singleplayer, net.minecraft.world.damagesource.DamageSource source, float damage) {
		Mob pig = spawn(singleplayer, EntityTypes.PIG, 3, 3);
		float before = pig.getHealth();
		pig.hurtServer((ServerLevel) pig.level(), source, damage);
		double dealt = before - pig.getHealth();
		pig.discard();
		return dealt;
	}

	private static Mob spawn(TestSingleplayerContext singleplayer, EntityType<? extends Mob> type, int dx, int dz) {
		ServerPlayer player = player(singleplayer);
		Mob mob = type.create(player.level(), EntitySpawnReason.COMMAND);
		check(mob != null, "Could not create a " + type);
		mob.snapTo(player.getX() + dx, player.getY(), player.getZ() + dz, 0.0F, 0.0F);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		check(player.level().addFreshEntity(mob), "Could not spawn a " + type);
		return mob;
	}

	private static void checkDestroySpeed(ClientGameTestContext context, TestSingleplayerContext singleplayer, BlockState state, double expected, String what) {
		float server = singleplayer.getServer().computeOnServer(_ -> player(singleplayer).getDestroySpeed(state));
		float client = context.computeOnClient(client2 -> client2.player.getDestroySpeed(state));
		checkNear(expected, server, what + " (server)");
		checkNear(expected, client, what + " (client)");
	}

	private static ItemStack enchanted(TestSingleplayerContext singleplayer, ItemStack stack, net.minecraft.resources.ResourceKey<Enchantment> key, int level) {
		return singleplayer.getServer().computeOnServer(server -> {
			stack.enchant(server.registryAccess().getOrThrow(key), level);
			return stack;
		});
	}

	/** Same rules on both sides: in singleplayer the client and the server share these statics. */
	private static void setRules(TalentRules rules) {
		ImSoTalentedConfig defaults = ImSoTalentedConfig.DEFAULTS;
		ImSoTalented.setConfig(new ImSoTalentedConfig(rules, defaults.converterValues(), defaults.backpackPickup()));
		ImSoTalented.setClientRules(rules);
	}

	private static void holdInMainHand(ClientGameTestContext context, TestSingleplayerContext singleplayer, ItemStack stack) {
		singleplayer.getServer().runOnServer(_ -> player(singleplayer).setItemInHand(InteractionHand.MAIN_HAND, stack.copy()));
		context.waitTicks(5);
	}

	private static void setXpPoints(TestSingleplayerContext singleplayer, long points) {
		singleplayer.getServer().runOnServer(_ -> Talents.setXpPoints(player(singleplayer), points));
	}

	private static long xp(TestSingleplayerContext singleplayer) {
		return singleplayer.getServer().computeOnServer(_ -> Talents.xpPoints(player(singleplayer)));
	}

	private static void checkXpPoints(TestSingleplayerContext singleplayer, long expected, String why) {
		long points = singleplayer.getServer().computeOnServer(_ -> Talents.xpPoints(player(singleplayer)));
		check(points == expected, "Expected " + expected + " XP points (" + why + "), got " + points);
	}

	private static int serverLevel(TestSingleplayerContext singleplayer, Talent talent) {
		return singleplayer.getServer().computeOnServer(_ -> Talents.level(player(singleplayer), talent));
	}

	private static void waitForLevel(TestSingleplayerContext singleplayer, Talent talent, int level) {
		singleplayer.getServer().waitFor(_ -> Talents.level(player(singleplayer), talent) == level, 40);
	}

	private static void hover(ClientGameTestContext context, Talent talent) {
		Vector2d cursor = context.computeOnClient(client -> {
			Vector2i gui = TalentScreen.nodeCenter(talent);
			Window window = client.getWindow();
			return new Vector2d(
					gui.x * (double) window.getScreenWidth() / window.getGuiScaledWidth(),
					gui.y * (double) window.getScreenHeight() / window.getGuiScaledHeight());
		});
		context.getInput().setCursorPos(cursor.x, cursor.y);
		context.waitTick();
	}

	private static ServerPlayer player(TestSingleplayerContext singleplayer) {
		return singleplayer.getConnection().getServerPlayer();
	}

	private static int countOf(Backpack inventory, Item item) {
		int count = 0;

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(item)) {
				count += inventory.getItem(i).getCount();
			}
		}

		return count;
	}

	private static int totalCount(List<ItemStack> stacks) {
		return stacks.stream().mapToInt(ItemStack::getCount).sum();
	}

	/** The total count of one item across a loot result. */
	private static int countOf(List<ItemStack> stacks, Item item) {
		int count = 0;

		for (ItemStack stack : stacks) {
			if (stack.is(item)) {
				count += stack.getCount();
			}
		}

		return count;
	}

	private static void checkNear(double expected, double actual, String what) {
		check(Math.abs(expected - actual) < EPSILON, "Expected " + expected + " for " + what + ", got " + actual);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
