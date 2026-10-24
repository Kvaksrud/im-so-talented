package no.kvaksrud.imsotalented.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import no.kvaksrud.imsotalented.ImSoTalented;
import no.kvaksrud.imsotalented.ImSoTalentedConfig;
import no.kvaksrud.imsotalented.client.ImSoTalentedClientConfig;
import no.kvaksrud.imsotalented.client.TalentClient;
import no.kvaksrud.imsotalented.client.TalentScreen;
import no.kvaksrud.imsotalented.client.TreeXpHud;
import no.kvaksrud.imsotalented.client.inventory.BackpackScreen;
import no.kvaksrud.imsotalented.effect.TreeXp;
import no.kvaksrud.imsotalented.inventory.Backpack;
import no.kvaksrud.imsotalented.inventory.Backpacks;
import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentRules;
import no.kvaksrud.imsotalented.talent.TalentRules.EnchantmentStacking;
import no.kvaksrud.imsotalented.talent.TalentTree;
import no.kvaksrud.imsotalented.talent.TalentValues;
import no.kvaksrud.imsotalented.talent.Talents;
import org.joml.Vector2d;
import org.joml.Vector2i;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.MixinEnvironment;

import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Plays the mod in a real client, in survival. First as a new player: the settings file; the tabbed talent screen (N
 * opens and closes it, the tabs switch trees); buying with talent points (none without tree XP, one per tree level
 * from {@code /talents xp}, a locked talent, Lifesteal at Stronger Weapons 5) and the level-up chat line; tree XP
 * earned through real play in each tree (mining an ore, stone and a Silk Touch ore, but not a log or sand, and
 * harvesting wheat on the client for Gatherer, a sword hit on a husk for Warrior, a husk hitting the player for
 * Guardian, walking with the forward key and entering new chunks for Explorer, each with a control that earns
 * nothing); the HUD bar (a flash after earning, the pin button). Then with every talent at its top level: the
 * attribute talents; toggling (command
 * and right-click); the three farming talents; Step Assist (attribute and a real walk up a block), Reach and Swim
 * Speed; mining speed under both stacking rules; Quarrying (sneaking while mining an ore); More Luck; weapon
 * damage; Block Chance; the backpack (pickup overflow, Shift + inventory key in game and from the inventory,
 * shrinking, and the plain inventory without the talent); the inventory buttons; death and respawn; and a save and
 * reload. Screenshots land in build/run/clientGameTest/screenshots (prefixed ist_).
 */
@SuppressWarnings("UnstableApiUsage")
public class ImSoTalentedClientGameTest implements FabricClientGameTest {
	private static final double EPSILON = 1e-4;
	private static final int STONE_PICKAXE_SPEED = 8;
	/** Where every scenario starts: on the superflat ground near spawn, facing south (+z). */
	private static final String HOME = "tp @p 0.5 -60 0.5 0 0";
	/** Chat lines (not action bar texts) the client has received, for the level-up message. */
	private static final List<Component> CHAT = new CopyOnWriteArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		// A settings file as 0.1.0 wrote it: the world's start must replace its vanilla-XP prices with the tree prices
		// (checkSettingsFile). And nothing is pinned.
		writeFile(settingsFile(), """
				cost_base=10
				cost_growth=1.07
				enchantment_stacking=highest
				converter_values=minecraft:diamond=40
				extra_inventory_pickup=true
				""");
		context.runOnClient(_ -> {
			TreeXpHud.setPinned(null);
			ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
				if (!overlay) {
					CHAT.add(message);
				}
			});
		});
		TestWorldSave save;
		int visitedChunks;

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			save = singleplayer.getWorldSave();
			singleplayer.getServer().runCommand("difficulty easy");
			singleplayer.getServer().runCommand("time set day");
			// No slimes or other strays: every hit on the player here comes from a mob the test spawned.
			singleplayer.getServer().runCommand("gamerule spawn_mobs false");
			singleplayer.getServer().runCommand(HOME);
			singleplayer.getConnection().waitForChunksRender();
			// Loads every mixin target now, so one that doesn't match fails here instead of somewhere later.
			MixinEnvironment.getCurrentEnvironment().audit();

			checkSettingsFile();
			openAndClose(context);
			buyingInTheTree(context, singleplayer);
			gathererXp(context, singleplayer);
			warriorXp(context, singleplayer);
			guardianXp(context, singleplayer);
			explorerXp(context, singleplayer);
			tabsAndPin(context, singleplayer);

			attributesAtTheTopLevel(context, singleplayer);
			toggling(context, singleplayer);
			farming(context, singleplayer);
			mobility(context, singleplayer);
			miningSpeed(context, singleplayer);
			quarrying(context, singleplayer);
			luck(singleplayer);
			weaponDamage(singleplayer);
			blockChance(singleplayer);
			backpack(context, singleplayer);
			inventoryButtons(context);
			death(context, singleplayer);

			// Left with a little less than full health, which a reload must not clamp back to 20.
			visitedChunks = singleplayer.getServer().computeOnServer(_ -> {
				ServerPlayer player = player(singleplayer);
				player.setHealth(25.0F);
				Backpacks.get(player).setItem(0, new ItemStack(Items.APPLE, 7));
				return player.getAttachedOrCreate(TreeXp.VISITED_CHUNKS).count();
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

				for (TalentTree tree : TalentTree.values()) {
					check(Talents.treeLevel(player, tree) == tree.maxLevel(), tree.id() + " should still be at its top level after a reload");
				}

				check(player.getMaxHealth() == 30.0F, "Max health should be 30 after a reload, got " + player.getMaxHealth());
				// Natural regeneration may have healed a little since; what matters is that it wasn't clamped to 20.
				check(player.getHealth() >= 25.0F, "Health above 20 must survive a reload, got " + player.getHealth());
				check(countOf(Backpacks.get(player), Items.APPLE) == 7, "The backpack should be saved");
				int chunks = player.getAttachedOrCreate(TreeXp.VISITED_CHUNKS).count();
				check(chunks == visitedChunks, "The chunks Explorer paid for should be saved: " + visitedChunks + " before, " + chunks + " after");
			});
			context.waitFor(client -> client.player != null && Talents.level(client.player, Talent.FASTER_MINING) == 5);
			check(context.computeOnClient(_ -> TreeXpHud.pinned()) == TalentTree.WARRIOR, "The pin is a client setting and outlives the world");
			context.waitFor(_ -> TreeXpHud.shownTree() == TalentTree.WARRIOR, TreeXpHud.FLASH_TICKS + 40);
			screenshot(context, "ist_15_after_reload");
		}
	}

	private static Path settingsFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(ImSoTalentedConfig.FILE_NAME);
	}

	/** The world's start rewrote the 0.1.0 settings file with the defaults: the four-tree prices and the backpack, nothing older. */
	private static void checkSettingsFile() {
		Path configFile = settingsFile();
		check(Files.isRegularFile(configFile), "Expected the settings file at " + configFile);
		check(ImSoTalented.config().equals(ImSoTalentedConfig.DEFAULTS), "Expected the default settings, got " + ImSoTalented.config());
		check(ImSoTalentedConfig.DEFAULTS.rules().costBase() == 25 && ImSoTalentedConfig.DEFAULTS.rules().costGrowth() == 1.18,
				"The default prices should be 25 and 1.18, got " + ImSoTalentedConfig.DEFAULTS.rules());

		String text = readFile(configFile);

		for (String line : List.of("level_cost_base=25", "level_cost_growth=1.18", "enchantment_stacking=highest", "backpack_pickup=true")) {
			check(text.contains(line), "The settings file should say " + line + ":\n" + text);
		}

		check(!text.contains("converter") && !text.contains("extra_inventory") && !text.contains("\ncost_base") && !text.contains("\ncost_growth"),
				"The settings file should have nothing about the converter, the extra inventory or the old prices:\n" + text);
	}

	/** N opens the talent screen (on the Gatherer tab, the first time) and closes it again. */
	private static void openAndClose(ClientGameTestContext context) {
		openTalents(context);
		check(context.computeOnClient(_ -> TalentScreen.activeTree()) == TalentTree.GATHERER, "The talent screen should first open on the Gatherer tab");
		closeTalents(context);
	}

	/**
	 * Buying spends points, and a tree's points come from its levels. Without tree XP a click buys nothing. After
	 * {@code /talents xp} brings Gatherer to level 1, with its chat line, the click buys Faster Mining. Backpack stays
	 * locked until Careful Hands has a level. In Warrior, Lifesteal stays locked through four levels of Stronger Weapons
	 * and unlocks at the fifth.
	 */
	private static void buyingInTheTree(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TalentRules rules = ImSoTalented.config().rules();

		openTalents(context);
		clickTab(context, TalentTree.GATHERER);
		clickFails(context, singleplayer, Talent.FASTER_MINING, Talents.BuyResult.NO_POINTS);
		closeTalents(context);

		CHAT.clear();
		singleplayer.getServer().runCommand("talents xp @p gatherer add " + rules.xpForLevel(1));
		String levelUp = waitForChat(context, "message.imsotalented.level_up");
		check(levelUp.contains("Gatherer is now level 1") && levelUp.contains("Talent points to spend there: 1"),
				"Unexpected level-up line: " + levelUp);
		screenshotWithChat(context, "ist_01_level_up_chat");

		openTalents(context);
		check(context.computeOnClient(_ -> TalentScreen.activeTree()) == TalentTree.GATHERER, "The screen should open on the last tab");
		hover(context, Talent.FASTER_MINING);
		screenshot(context, "ist_02_tooltip");
		buy(context, singleplayer, Talent.FASTER_MINING, 1);
		checkPoints(context, TalentTree.GATHERER, 0, "the only point is spent");

		// Level 3: two more points. Backpack needs Careful Hands first.
		singleplayer.getServer().runCommand("talents xp @p gatherer set " + rules.xpForLevel(3));
		context.waitFor(client -> Talents.points(client.player, TalentTree.GATHERER) == 2, 40);
		clickFails(context, singleplayer, Talent.BACKPACK, Talents.BuyResult.LOCKED);
		checkPoints(context, TalentTree.GATHERER, 2, "a locked talent costs nothing");
		buy(context, singleplayer, Talent.CAREFUL_HANDS, 1);
		buy(context, singleplayer, Talent.BACKPACK, 1);
		checkPoints(context, TalentTree.GATHERER, 0, "both points are spent");

		// Warrior's points are Warrior's alone: six levels, five for Stronger Weapons and one for Lifesteal.
		clickTab(context, TalentTree.WARRIOR);
		singleplayer.getServer().runCommand("talents xp @p warrior set " + rules.xpForLevel(6));
		context.waitFor(client -> Talents.points(client.player, TalentTree.WARRIOR) == 6, 40);
		checkPoints(context, TalentTree.GATHERER, 0, "Warrior's levels give no Gatherer points");

		for (int level = 1; level <= 4; level++) {
			buy(context, singleplayer, Talent.STRONGER_WEAPONS, level);
		}

		hover(context, Talent.LIFESTEAL);
		screenshot(context, "ist_03_locked_tooltip");
		clickFails(context, singleplayer, Talent.LIFESTEAL, Talents.BuyResult.LOCKED);
		buy(context, singleplayer, Talent.STRONGER_WEAPONS, 5);
		buy(context, singleplayer, Talent.LIFESTEAL, 1);
		checkPoints(context, TalentTree.WARRIOR, 0, "six points, six levels");
		closeTalents(context);
	}

	/**
	 * Gatherer XP from mining and farming on the client. A log chopped with an axe and sand dug with a shovel earn
	 * nothing; stone mined with a pickaxe and an ore mined with Silk Touch earn a tenth of an ore each; an ore mined
	 * with a plain pickaxe earns 5. A fully grown wheat harvested earns 1, young wheat nothing. With nothing pinned, the
	 * HUD shows the Gatherer bar only for a moment after earning a whole point.
	 */
	private static void gathererXp(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		context.waitFor(_ -> TreeXpHud.shownTree() == null, TreeXpHud.FLASH_TICKS + 40);
		BlockPos block = new BlockPos(0, -59, 2);
		double start = earnedXp(singleplayer, TalentTree.GATHERER);

		holdInMainHand(context, singleplayer, new ItemStack(Items.DIAMOND_AXE));
		setBlock(singleplayer, block, Blocks.OAK_LOG.defaultBlockState());
		mine(context, singleplayer, block);
		checkNear(start, earnedXp(singleplayer, TalentTree.GATHERER), "a log chopped with an axe earns nothing");

		holdInMainHand(context, singleplayer, new ItemStack(Items.DIAMOND_SHOVEL));
		setBlock(singleplayer, block, Blocks.SAND.defaultBlockState());
		mine(context, singleplayer, block);
		checkNear(start, earnedXp(singleplayer, TalentTree.GATHERER), "sand dug with a shovel earns nothing");

		holdInMainHand(context, singleplayer, new ItemStack(Items.DIAMOND_PICKAXE));
		setBlock(singleplayer, block, Blocks.STONE.defaultBlockState());
		mine(context, singleplayer, block);
		checkNear(start + TalentValues.GATHERER_XP_PER_BLOCK, earnedXp(singleplayer, TalentTree.GATHERER), "stone mined with a pickaxe earns a tenth of an ore");
		check(context.computeOnClient(_ -> TreeXpHud.shownTree()) == null, "Half a point isn't a whole one yet, so the HUD bar should stay hidden");

		holdInMainHand(context, singleplayer, enchanted(singleplayer, new ItemStack(Items.DIAMOND_PICKAXE), Enchantments.SILK_TOUCH, 1));
		setBlock(singleplayer, block, Blocks.COAL_ORE.defaultBlockState());
		mine(context, singleplayer, block);
		checkNear(start + 2 * TalentValues.GATHERER_XP_PER_BLOCK, earnedXp(singleplayer, TalentTree.GATHERER), "an ore mined with Silk Touch earns what stone does");

		holdInMainHand(context, singleplayer, new ItemStack(Items.DIAMOND_PICKAXE));
		setBlock(singleplayer, block, Blocks.COAL_ORE.defaultBlockState());
		mine(context, singleplayer, block);
		checkNear(start + 2 * TalentValues.GATHERER_XP_PER_BLOCK + TalentValues.GATHERER_XP_PER_ORE, earnedXp(singleplayer, TalentTree.GATHERER),
				"an ore mined with a pickaxe");

		context.waitFor(_ -> TreeXpHud.shownTree() == TalentTree.GATHERER, 20);
		context.waitFor(_ -> TreeXpHud.shownTree() == null, TreeXpHud.FLASH_TICKS + 20);

		BlockPos crop = new BlockPos(0, -60, 2);
		BlockState ground = singleplayer.getServer().computeOnServer(_ -> player(singleplayer).level().getBlockState(crop.below()));
		setBlock(singleplayer, crop.below(), Blocks.FARMLAND.defaultBlockState());
		double beforeCrops = earnedXp(singleplayer, TalentTree.GATHERER);

		setBlock(singleplayer, crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 1));
		harvest(context, singleplayer, crop);
		checkNear(beforeCrops, earnedXp(singleplayer, TalentTree.GATHERER), "young wheat earns nothing");

		setBlock(singleplayer, crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
		harvest(context, singleplayer, crop);
		checkNear(beforeCrops + TalentValues.GATHERER_XP_PER_CROP, earnedXp(singleplayer, TalentTree.GATHERER), "a fully grown wheat");

		setBlock(singleplayer, crop.below(), ground);
		singleplayer.getServer().runCommand("kill @e[type=item]");
		holdInMainHand(context, singleplayer, ItemStack.EMPTY);
	}

	/**
	 * Warrior XP from a sword hit on the client: exactly the health a husk lost to it (after the husk's armor), and
	 * nothing for hitting a pig. A husk, since a zombie would burn in the daylight between the readings.
	 */
	private static void warriorXp(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		holdInMainHand(context, singleplayer, new ItemStack(Items.DIAMOND_SWORD));

		Mob husk = singleplayer.getServer().computeOnServer(_ -> spawn(singleplayer, EntityTypes.HUSK, 0, 2));
		double before = earnedXp(singleplayer, TalentTree.WARRIOR);
		double lost = hitFromClient(context, singleplayer, husk);
		checkNear(before + lost, earnedXp(singleplayer, TalentTree.WARRIOR), "Warrior XP is the " + lost + " health the husk lost");
		singleplayer.getServer().runOnServer(_ -> husk.discard());

		Mob pig = singleplayer.getServer().computeOnServer(_ -> spawn(singleplayer, EntityTypes.PIG, 0, 2));
		before = earnedXp(singleplayer, TalentTree.WARRIOR);
		hitFromClient(context, singleplayer, pig);
		checkNear(before, earnedXp(singleplayer, TalentTree.WARRIOR), "hitting a passive mob earns nothing");
		singleplayer.getServer().runOnServer(_ -> pig.discard());

		holdInMainHand(context, singleplayer, ItemStack.EMPTY);
	}

	/**
	 * Guardian XP from a husk with its AI on: it walks up and swings at the player. On easy its attack lands as
	 * min(damage / 2 + 1, damage), and Guardian pays 2 per point of it.
	 */
	private static void guardianXp(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		double before = earnedXp(singleplayer, TalentTree.GUARDIAN);
		Mob husk = singleplayer.getServer().computeOnServer(_ -> {
			Mob mob = spawn(singleplayer, EntityTypes.HUSK, 0, 2);
			mob.setNoAi(false);
			return mob;
		});
		float attack = singleplayer.getServer().computeOnServer(server -> {
			check(server.overworld().getDifficulty() == Difficulty.EASY, "The test plays on easy");
			return (float) husk.getAttributeValue(Attributes.ATTACK_DAMAGE);
		});

		singleplayer.getServer().waitFor(_ -> serverEarnedXp(player(singleplayer), TalentTree.GUARDIAN) > before, 200);
		// A husk swings at most once a second, so this is the one hit.
		singleplayer.getServer().runOnServer(_ -> husk.discard());
		double expected = before + TalentValues.GUARDIAN_XP_PER_DAMAGE * Math.min(attack / 2.0F + 1.0F, attack);
		checkNear(expected, earnedXp(singleplayer, TalentTree.GUARDIAN), "Guardian XP for one husk hit on easy");

		singleplayer.getServer().runOnServer(_ -> player(singleplayer).setHealth(player(singleplayer).getMaxHealth()));
		singleplayer.getServer().runCommand("effect clear @p");
		singleplayer.getServer().runCommand(HOME);
		context.waitTicks(5);
	}

	/**
	 * Explorer XP. A teleport into a chunk the player has never been in pays the chunk and nothing for the distance. A
	 * push with no movement key held pays nothing. Walking with the forward key held pays a point per 25 blocks, plus a
	 * point for every new chunk on the way. The walk is on a lane cleared far from spawn, so every chunk on it is new
	 * and nothing the world generated stands in the way.
	 */
	private static void explorerXp(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		double before = earnedXp(singleplayer, TalentTree.EXPLORER);
		singleplayer.getServer().runCommand("tp @p 1000.5 -60 8.5 -90 0");
		singleplayer.getConnection().waitForChunksRender();
		context.waitTicks(5);
		checkNear(before + TalentValues.EXPLORER_XP_PER_NEW_CHUNK, earnedXp(singleplayer, TalentTree.EXPLORER),
				"a teleport into a new chunk pays the chunk, not the distance");

		singleplayer.getServer().runOnServer(_ -> {
			ServerLevel level = player(singleplayer).level();

			for (int x = 996; x < 1048; x++) {
				for (int z = 6; z <= 10; z++) {
					level.setBlockAndUpdate(new BlockPos(x, -61, z), Blocks.STONE.defaultBlockState());

					for (int y = -60; y <= -57; y++) {
						level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		});
		context.waitTicks(5);

		// Pushed: moving, but not under the player's own power.
		before = earnedXp(singleplayer, TalentTree.EXPLORER);
		double pushStart = serverX(singleplayer);
		context.runOnClient(client -> client.player.setDeltaMovement(0.8, 0.0, 0.0));
		context.waitTicks(30);
		double pushed = serverX(singleplayer) - pushStart;
		check(pushed > 0.5, "The push should have moved the player, moved " + pushed);
		checkNear(before, earnedXp(singleplayer, TalentTree.EXPLORER), "being pushed with no movement key held earns nothing");

		// Walking east with the forward key.
		context.getInput().lookAt(-90.0F, 0.0F);
		context.waitTick();
		before = earnedXp(singleplayer, TalentTree.EXPLORER);
		double startX = serverX(singleplayer);
		context.getInput().holdKeyFor(options -> options.keyUp, 100);
		context.waitTicks(10);
		double endX = serverX(singleplayer);
		double walked = endX - startX;
		int newChunks = (Mth.floor(endX) >> 4) - (Mth.floor(startX) >> 4);
		check(walked > 15.0, "Five seconds of walking should cover more than 15 blocks, walked " + walked);
		check(newChunks >= 1, "The walk should cross into a new chunk, from x " + startX + " to " + endX);
		double expected = walked * TalentValues.EXPLORER_XP_PER_BLOCK + newChunks * TalentValues.EXPLORER_XP_PER_NEW_CHUNK;
		double earned = earnedXp(singleplayer, TalentTree.EXPLORER) - before;
		check(Math.abs(earned - expected) <= 0.1, "Walking " + walked + " blocks through " + newChunks + " new chunks should earn about "
				+ expected + " Explorer XP, earned " + earned);

		singleplayer.getServer().runCommand(HOME);
		singleplayer.getConnection().waitForChunksRender();
		context.waitTicks(5);
	}

	/**
	 * Each tab shows its tree (a screenshot of each, with what was bought and earned so far). The pin button pins the
	 * active tab's tree, a second click unpins it, and the pin is saved in the client's own settings file. Once the
	 * screen is closed the pinned bar stays on the HUD, earning in another tree borrows the slot for a moment, and then
	 * the pinned bar is back.
	 */
	private static void tabsAndPin(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		openTalents(context);
		int shot = 4;

		for (TalentTree tree : TalentTree.values()) {
			clickTab(context, tree);
			// The cursor off the tabs and nodes, so no tooltip covers the tree.
			moveCursor(context, new Vector2i(1, 1));
			screenshot(context, "ist_0" + shot++ + "_tab_" + tree.id());
		}

		clickTab(context, TalentTree.WARRIOR);
		clickPin(context);
		check(context.computeOnClient(_ -> TreeXpHud.pinned()) == TalentTree.WARRIOR, "The pin button should pin the Warrior tree");
		clickPin(context);
		check(context.computeOnClient(_ -> TreeXpHud.pinned()) == null, "A second click should unpin it");
		clickPin(context);
		check(context.computeOnClient(_ -> TreeXpHud.pinned()) == TalentTree.WARRIOR, "A third click pins it again");
		String clientSettings = readFile(FabricLoader.getInstance().getConfigDir().resolve(ImSoTalentedClientConfig.FILE_NAME));
		check(clientSettings.contains("pinned_tree=warrior"), "The pin should be saved:\n" + clientSettings);
		closeTalents(context);

		context.waitFor(_ -> TreeXpHud.shownTree() == TalentTree.WARRIOR, TreeXpHud.FLASH_TICKS + 40);
		screenshot(context, "ist_08_hud_pinned");

		singleplayer.getServer().runCommand("talents xp @p gatherer add 1");
		context.waitFor(_ -> TreeXpHud.shownTree() == TalentTree.GATHERER, 20);
		screenshot(context, "ist_09_hud_flash");
		context.waitFor(_ -> TreeXpHud.shownTree() == TalentTree.WARRIOR, TreeXpHud.FLASH_TICKS + 20);
	}

	/** /talents max: health, armor, speed, jumping, falling, step height and reach are attribute modifiers. */
	private static void attributesAtTheTopLevel(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("talents max @p");
		singleplayer.getServer().runOnServer(_ -> player(singleplayer).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE)));
		context.waitTicks(5);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			checkNear(30.0, player.getAttributeValue(Attributes.MAX_HEALTH), "max health with 5 extra hearts");
			checkNear(6.0 * 1.25, player.getAttributeValue(Attributes.ARMOR), "an iron chestplate's 6 armor, 25% stronger");
			checkNear(0.1 * 1.2, player.getAttributeValue(Attributes.MOVEMENT_SPEED), "20% faster");
			checkNear(0.42 + 0.15, player.getAttributeValue(Attributes.JUMP_STRENGTH), "jump strength");
			checkNear(3.0 + 10.0, player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE), "safe fall distance");
			checkNear(0.5, player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), "half the fall damage");
			checkNear(1.0, player.getAttributeValue(Attributes.STEP_HEIGHT), "step height with Step Assist");
			checkNear(4.5 + 1.5, player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), "block reach");
			checkNear(3.0 + 0.5, player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), "entity reach");
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);

			for (TalentTree tree : TalentTree.values()) {
				check(Talents.treeLevel(player, tree) == tree.maxLevel(), tree.id() + " should be at its top level after /talents max");
			}
		});

		openTalents(context);
		clickTab(context, TalentTree.GUARDIAN);
		moveCursor(context, new Vector2i(1, 1));
		screenshot(context, "ist_10_maxed_tree");
		closeTalents(context);
	}

	/**
	 * Toggleable talents: /talents toggle off removes the attribute modifier, on re-adds it, a non-toggleable
	 * talent is rejected, and a right-click in the Explorer tab flips the state through the payload round-trip.
	 */
	private static void toggling(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
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

		// The right-click path: open the Explorer tab, right-click the node, and the payload round-trip must flip it.
		openTalents(context);
		clickTab(context, TalentTree.EXPLORER);
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
		closeTalents(context);
	}

	/**
	 * The three farming talents, each against a vanilla control:
	 * <ul>
	 * <li><b>Bountiful Harvest</b> — 40 loot rolls of ripe wheat: talent III gives exactly 4 wheat per roll (the 1 main
	 *     drop + 3 extra), a no-entity roll and a talent-0 player each give the vanilla 1, and a young-wheat roll
	 *     (26.3: seeds, not wheat) gets no bonus.</li>
	 * <li><b>Fast Growth</b> — two identical 60-stalk wheat plots at age 0, one within 16 blocks of the player
	 *     (Fast Growth III in reach) and one 63 blocks away (the control: no player in reach). Each stalk is forced
	 *     through 26 of the exact {@code randomTick} rolls the random-tick system makes (natural ticking here is
	 *     ~3 positions per 4096 per section-tick, which would move almost nothing in the time this test has), and the
	 *     in-range plot's mean age must be at least 1.5x the control's (~2.5x expected).</li>
	 * <li><b>Careful Hands</b> — a 600-point durability change on a diamond pickaxe: with Careful Hands III the item
	 *     behaves as if Unbreaking III (each point lands 1 time in 4); gear Unbreaking I with talent III counts as III
	 *     under the default {@code highest} rule, and the gear's own enchantment is left as it was; under {@code add}
	 *     gear III with talent III counts as VI (1 in 7); without the talent the full 600 lands.</li>
	 * </ul>
	 * Ends with every talent maxed, the player at home with an empty hand, and the plot blocks removed, for the
	 * remaining scenarios and the save/reload block.
	 */
	private static void farming(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		bountifulHarvest(singleplayer);
		fastGrowth(singleplayer);
		carefulHands(singleplayer);

		// Restore the all-maxed state the save/reload block asserts, and a clean position and hand.
		singleplayer.getServer().runCommand("talents max @p");
		singleplayer.getServer().runCommand(HOME);
		holdInMainHand(context, singleplayer, ItemStack.EMPTY);
		context.waitTicks(2);
	}

	/**
	 * The three mobility talents, each against a vanilla control:
	 * <ul>
	 * <li><b>Step Assist</b> — 26.3 reads the step-up height off the STEP_HEIGHT attribute (vanilla 0.6), and the
	 *     player's own {@code maxUpStep()} — the accessor the step-up logic in {@code Entity.collide} uses —
	 *     returns that value: 1.0 with the talent's one level, 0.6 without. And for real: walking north with the
	 *     forward key into a full block, the player ends up on top of it with the talent and stays in front of it
	 *     without.</li>
	 * <li><b>Reach</b> — 26.3's {@code blockInteractionRange()} and {@code entityInteractionRange()} are pure
	 *     reads of the BLOCK_INTERACTION_RANGE and ENTITY_INTERACTION_RANGE attributes (vanilla 4.5/3.0), so the
	 *     additive rows are asserted through the attributes and the accessors at level 3 (6.0/3.5) and a level-0
	 *     control (4.5/3.0).</li>
	 * <li><b>Swim Speed</b> — 26.3 has no swim-speed attribute (WATER_MOVEMENT_EFFICIENCY only slows), so the
	 *     effect softens the per-tick underwater damping in {@code travelInWater} (0.8) to
	 *     {@code 1 - (1 - 0.8)/(1 + 0.1*level)}, which multiplies the sustained in-water movement by exactly
	 *     1 + 0.1 per level. Measured functionally: a 16x4x16 water volume at the test site, the player given
	 *     the same initial horizontal velocity (0.4) in fresh lanes and left to coast for 60 ticks — the
	 *     level-3 coast must cover at least 20% more ground than the level-0 control (~30% expected), and the
	 *     toggled-off coast must match the control (the effective level the mixin reads is 0 while off).</li>
	 * </ul>
	 * Ends with every talent maxed (the toggle back on), the player at home on dry land with no residual
	 * velocity, and the water removed, for the remaining scenarios and the save/reload block.
	 */
	private static void mobility(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// STEP ASSIST: the attribute row and the public step-up accessor, then a real walk up a block.
		singleplayer.getServer().runCommand("talents set @p step_assist 1");
		context.waitTicks(2);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			checkNear(1.0, player.getAttributeValue(Attributes.STEP_HEIGHT), "step height attribute with Step Assist (0.6 + 0.4)");
			checkNear(1.0, player.maxUpStep(), "the step-up accessor the collide logic uses, with Step Assist");
		});
		check(walkIntoStep(context, singleplayer) > -59.05, "With Step Assist the player should walk up onto the block");

		singleplayer.getServer().runCommand("talents set @p step_assist 0");
		context.waitTicks(2);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			checkNear(0.6, player.getAttributeValue(Attributes.STEP_HEIGHT), "level 0 restores the vanilla 0.6 step height");
			checkNear(0.6, player.maxUpStep(), "the vanilla step-up accessor at level 0");
		});
		check(walkIntoStep(context, singleplayer) < -59.5, "Without Step Assist a full block should stop the player");

		// REACH: level-0 control first (the survival baselines), then level 3.
		singleplayer.getServer().runCommand("talents set @p reach 0");
		context.waitTicks(2);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			checkNear(4.5, player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), "the vanilla 4.5 block interaction range");
			checkNear(3.0, player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), "the vanilla 3.0 entity interaction range");
		});
		singleplayer.getServer().runCommand("talents set @p reach 3");
		context.waitTicks(2);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			checkNear(4.5 + 1.5, player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), "block range 4.5 + 1.5 at level 3");
			checkNear(4.5 + 1.5, player.blockInteractionRange(), "the accessor block breaking and item use go through");
			checkNear(3.0 + 0.5, player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), "entity range 3.0 + 0.5 at level 3");
			checkNear(3.0 + 0.5, player.entityInteractionRange(), "the accessor attacks and item use against entities go through");
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
		// water out, player at home on dry land, no residual velocity.
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
		singleplayer.getServer().runCommand(HOME);
		// The pool's water volume deliberately starts two blocks east of home: the client can hold a
		// stale water update for a cell while a big block change is in flight, and the integrated client
		// drives the player, so a player standing in such a cell drifts on a constant diagonal with no
		// input. Home is kept out of the water volume, and the settle below lets the teleport and the
		// pool removal sync before the re-zero, so no residual velocity survives into the next scenario.
		context.waitTicks(4);
		singleplayer.getServer().runOnServer(_ -> player(singleplayer).setDeltaMovement(0, 0, 0));
		context.runOnClient(client -> client.player.setDeltaMovement(0, 0, 0));
	}

	/**
	 * Walks north for a second with the forward key into a 3-wide, 5-deep step of stone one block high, two blocks
	 * north of home, and returns the height the player ends at: -59 on top of the step, -60 in front of it. Auto-jump
	 * is off (the vanilla default), so only the step height can get the player up. The step is removed afterwards.
	 */
	private static double walkIntoStep(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(_ -> {
			ServerLevel level = player(singleplayer).level();

			for (int x = -1; x <= 1; x++) {
				for (int z = -6; z <= -2; z++) {
					level.setBlockAndUpdate(new BlockPos(x, -60, z), Blocks.STONE.defaultBlockState());
				}
			}
		});
		context.runOnClient(client -> client.options.autoJump().set(false));
		singleplayer.getServer().runCommand("tp @p 0.5 -60 0.5 180 0");
		context.waitFor(client -> client.level.getBlockState(new BlockPos(0, -60, -2)).is(Blocks.STONE), 40);
		context.waitTicks(5);
		context.getInput().lookAt(180.0F, 0.0F);
		context.getInput().holdKeyFor(options -> options.keyUp, 20);
		context.waitTicks(10);
		double y = singleplayer.getServer().computeOnServer(_ -> player(singleplayer).getY());

		singleplayer.getServer().runOnServer(_ -> {
			ServerLevel level = player(singleplayer).level();

			for (int x = -1; x <= 1; x++) {
				for (int z = -6; z <= -2; z++) {
					level.setBlockAndUpdate(new BlockPos(x, -60, z), Blocks.AIR.defaultBlockState());
				}
			}
		});
		singleplayer.getServer().runCommand(HOME);
		context.waitTicks(5);
		return y;
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
	 * Every cell is snapshotted and restored on cleanup.
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
	 * offset from home keeps the player's home cells out of the water volume, so a stale client-side
	 * water update can never put the player (who the integrated client drives) in water at home.
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
	 * home), so the whole 60-tick coast (~2.6 blocks at the level-3 factor) stays inside the water.
	 * Returns the horizontal ground covered, read from the server player.
	 */
	private static double swimCoast(ClientGameTestContext context, TestSingleplayerContext singleplayer, double laneZ) {
		singleplayer.getServer().runCommand("tp @p 3.5 -60 " + laneZ);
		context.waitTicks(2);
		Vec3 push = new Vec3(0.4, 0.0, 0.0);
		singleplayer.getServer().runOnServer(_ -> player(singleplayer).setDeltaMovement(push));
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
			BlockState ripe = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE);
			BlockState young = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 1);
			BlockPos pos = player.blockPosition();
			ItemStack tool = new ItemStack(Items.IRON_HOE);
			int top = Talent.BOUNTIFUL_HARVEST.maxLevel();
			int perHarvest = 1 + TalentValues.harvestBonus(top);

			Talents.setLevel(player, Talent.BOUNTIFUL_HARVEST, top);
			int withTalent = 0;
			for (int i = 0; i < 40; i++) {
				withTalent += countOf(Block.getDrops(ripe, level, pos, null, player, tool), Items.WHEAT);
			}
			check(withTalent == 40 * perHarvest, "Bountiful Harvest III should give exactly " + perHarvest + " wheat per mature harvest, got " + withTalent + " from 40");

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
			Talents.setLevel(player, Talent.BOUNTIFUL_HARVEST, top);
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
			BlockPos near = new BlockPos(4, -60, 0);
			level.getChunk(-5, 0); // ensure the far plot's chunk is generated before writing into it
			BlockPos far = new BlockPos(-72, -60, 0);
			BlockState ground = level.getBlockState(near.below());
			plotWheat(level, near);
			plotWheat(level, far);

			tickPlot(level, near, 26);
			tickPlot(level, far, 26);

			double nearAge = meanAge(level, near);
			double farAge = meanAge(level, far);
			check(farAge >= 1.0, "The control plot should have grown a little (vanilla), mean age " + farAge);
			check(nearAge >= 1.5 * farAge,
					"Fast Growth III in reach should grow at least 1.5x the 63-blocks-away control (~2.5x expected), got " + nearAge + " vs " + farAge);

			// Leave the ground as it was for the remaining scenarios and the save/reload.
			removePlot(level, near, ground);
			removePlot(level, far, ground);
		});
	}

	/** A 10x6 age-0 wheat plot on farmland from {@code origin}, each stalk under a sea lantern. */
	private static void plotWheat(ServerLevel level, BlockPos origin) {
		for (int x = 0; x < 10; x++) {
			for (int z = 0; z < 6; z++) {
				BlockPos wheat = origin.offset(x, 0, z);
				level.setBlockAndUpdate(wheat.below(), Blocks.FARMLAND.defaultBlockState());
				level.setBlockAndUpdate(wheat.above(), Blocks.SEA_LANTERN.defaultBlockState());
				level.setBlockAndUpdate(wheat, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0));
			}
		}
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

	/** Clears a plot's wheat and lanterns to air and puts the ground back under it. */
	private static void removePlot(ServerLevel level, BlockPos origin, BlockState ground) {
		for (int x = 0; x < 10; x++) {
			for (int z = 0; z < 6; z++) {
				BlockPos pos = origin.offset(x, 0, z);
				level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(pos.below(), ground);
			}
		}
	}

	/** Careful Hands: a 600-point durability change lands as Unbreaking at the level the stacking rule gives. */
	private static void carefulHands(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			ServerLevel level = player.level();
			Holder<Enchantment> unbreaking = level.registryAccess().getOrThrow(Enchantments.UNBREAKING);

			// Control: no talent, no unbreaking gear -> the full 600 points land, deterministically.
			Talents.setLevel(player, Talent.CAREFUL_HANDS, 0);
			int control = damageTaken(level, player, new ItemStack(Items.DIAMOND_PICKAXE), 600);
			check(control == 600, "Without the talent or unbreaking, all 600 points should land, got " + control);

			// Careful Hands III on bare gear: the item works as if Unbreaking III, so about 1 point in 4 lands.
			Talents.setLevel(player, Talent.CAREFUL_HANDS, 3);
			int talented = damageTaken(level, player, new ItemStack(Items.DIAMOND_PICKAXE), 600);
			check(talented <= 0.4 * control,
					"Careful Hands III should land well under 40% of the control (~1/4 expected), got " + talented + " vs " + control);
			check(talented >= 1, "Some of the 600 points should still land, got " + talented);

			// The default rule: gear Unbreaking I + talent III = the higher, III; the gear's real component is restored.
			ItemStack geared = new ItemStack(Items.DIAMOND_PICKAXE);
			geared.enchant(unbreaking, 1);
			int highest = damageTaken(level, player, geared, 600);
			check(highest <= 0.4 * control,
					"Unbreaking I gear with Careful Hands III should count as III (~1/4 expected), got " + highest + " vs " + control);
			ItemEnchantments gearEnc = geared.getOrDefault(EnchantmentHelper.getComponentType(geared), ItemEnchantments.EMPTY);
			check(gearEnc.getLevel(unbreaking) == 1,
					"The gear's real Unbreaking I must be restored after the virtual level is removed, got " + gearEnc.getLevel(unbreaking));

			// The add rule: gear Unbreaking III + talent III = VI, so about 1 point in 7 lands (III would be 1 in 4).
			setRules(new TalentRules(TalentRules.DEFAULTS.costBase(), TalentRules.DEFAULTS.costGrowth(), EnchantmentStacking.ADD));
			ItemStack strong = new ItemStack(Items.DIAMOND_PICKAXE);
			strong.enchant(unbreaking, 3);
			int added = damageTaken(level, player, strong, 600);
			setRules(TalentRules.DEFAULTS);
			check(added <= 125,
					"Unbreaking III gear with Careful Hands III added up should count as VI (~86 of 600 expected), got " + added);
		});
	}

	/** Runs one durability change of {@code amount} points through the player's path; returns the points that landed. */
	private static int damageTaken(ServerLevel level, ServerPlayer player, ItemStack stack, int amount) {
		int before = stack.getDamageValue();
		stack.hurtAndBreak(amount, level, player, _ -> {
		});
		return stack.getDamageValue() - before;
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

	/** Where the test vein starts: the ore in front of home at eye level. It runs four coal ores away from the player. */
	private static final BlockPos VEIN_START = new BlockPos(0, -59, 2);
	/**
	 * Where the player mines the vein from: four blocks back from its first ore, facing it. Quarry drops land a block in
	 * front of the eyes (z -0.5) and get picked up, while the coal a plain break scatters around its ore (up to about
	 * 0.75 blocks from z 2.5) stays out of the player's reach, so the two are told apart by {@link #QUARRY_DROP_LINE}.
	 */
	private static final String QUARRY_SPOT = "tp @p 0.5 -60 -1.5 0 0";
	private static final double QUARRY_DROP_LINE = 0.75;

	/**
	 * Quarrying, sneaking (the sneak key held on the client) while an ore is mined. Level 1 takes the connected ore of the
	 * same kind within the 3x3x3 cube around it: of the line of four coal ores, the first two; the iron ore and the stone
	 * beside it stay. Without sneaking only the mined ore goes, and its coal drops at the ore. The top level takes the
	 * whole line, its last ore a deepslate coal ore (the same kind of ore, another block). Every ore broken pays Gatherer
	 * XP, and every drop lands in front of the player. A pickaxe with 3 durability left takes two ores and stops at 1,
	 * still in hand. Careful Hands, More Luck and Gatherer XP are off here, so every ore wears the tool by exactly 1, drops
	 * exactly 1 coal and pays exactly 5; {@code /talents max} puts them back.
	 */
	private static void quarrying(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		for (String talent : List.of("careful_hands", "more_luck", "gatherer_xp")) {
			singleplayer.getServer().runCommand("talents set @p " + talent + " 0");
		}

		// A Gatherer tree with room to earn: /talents max left it at its top level.
		singleplayer.getServer().runCommand("talents xp @p gatherer set 0");

		singleplayer.getServer().runCommand("talents set @p quarrying 1");
		check(quarry(context, singleplayer, true, new ItemStack(Items.DIAMOND_PICKAXE)) == 2,
				"Quarrying I should take the mined ore and the one next to it in the 3x3x3 cube");

		singleplayer.getServer().runCommand("talents set @p quarrying " + Talent.QUARRYING.maxLevel());
		check(quarry(context, singleplayer, false, new ItemStack(Items.DIAMOND_PICKAXE)) == 1, "Without sneaking only the mined ore goes");
		check(quarry(context, singleplayer, true, new ItemStack(Items.DIAMOND_PICKAXE)) == 4,
				"The top level should take the whole vein, the deepslate coal ore at its end too");

		ItemStack worn = new ItemStack(Items.DIAMOND_PICKAXE);
		worn.setDamageValue(worn.getMaxDamage() - 3);
		check(quarry(context, singleplayer, true, worn) == 2, "A pickaxe with 3 durability left should take two ores and stop");
		singleplayer.getServer().runOnServer(_ -> {
			ItemStack held = player(singleplayer).getMainHandItem();
			check(held.is(Items.DIAMOND_PICKAXE) && held.getMaxDamage() - held.getDamageValue() == 1,
					"Quarrying should leave the pickaxe in hand with 1 durability, got " + held);
		});

		singleplayer.getServer().runCommand("talents max @p");
		singleplayer.getServer().runCommand("kill @e[type=item]");
		singleplayer.getServer().runCommand("clear @p");
		singleplayer.getServer().runCommand(HOME);
		singleplayer.getServer().runOnServer(_ -> {
			ServerLevel level = player(singleplayer).level();

			for (int x = -1; x <= 1; x++) {
				for (int z = 2; z <= 5; z++) {
					level.setBlockAndUpdate(new BlockPos(x, -59, z), Blocks.AIR.defaultBlockState());
				}
			}
		});
		context.waitTicks(5);
	}

	/**
	 * Lays the test vein (three coal ores and a deepslate coal ore in a line away from home, an iron ore and a stone
	 * beside the first), mines its first ore from the client with this tool, sneaking or not, and returns how many coal
	 * ores broke. Checks what every break must do: the iron ore and the stone stay, every broken ore paid its Gatherer XP,
	 * and every coal is in front of the player (picked up or on the ground there), none left at the ores, unless the
	 * player wasn't sneaking: then the one coal drops at its ore, as in vanilla.
	 */
	private static int quarry(ClientGameTestContext context, TestSingleplayerContext singleplayer, boolean sneaking, ItemStack tool) {
		singleplayer.getServer().runCommand("kill @e[type=item]");
		singleplayer.getServer().runCommand("clear @p");
		singleplayer.getServer().runCommand(QUARRY_SPOT);
		singleplayer.getServer().runOnServer(_ -> {
			ServerLevel level = player(singleplayer).level();

			for (int i = 0; i < 3; i++) {
				level.setBlockAndUpdate(VEIN_START.south(i), Blocks.COAL_ORE.defaultBlockState());
			}

			// Another block, but the same kind of ore (both are c:ores/coal).
			level.setBlockAndUpdate(VEIN_START.south(3), Blocks.DEEPSLATE_COAL_ORE.defaultBlockState());
			level.setBlockAndUpdate(VEIN_START.east(), Blocks.IRON_ORE.defaultBlockState());
			level.setBlockAndUpdate(VEIN_START.west(), Blocks.STONE.defaultBlockState());
		});
		holdInMainHand(context, singleplayer, tool);
		double before = earnedXp(singleplayer, TalentTree.GATHERER);

		if (sneaking) {
			context.getInput().holdKey(options -> options.keyShift);
			singleplayer.getServer().waitFor(_ -> player(singleplayer).isShiftKeyDown(), 20);
			// The crouch lowers the eyes; the aim below is taken from where they end up.
			context.waitTicks(5);
		}

		mine(context, singleplayer, VEIN_START);

		if (sneaking) {
			context.getInput().releaseKey(options -> options.keyShift);
			singleplayer.getServer().waitFor(_ -> !player(singleplayer).isShiftKeyDown(), 20);
		}

		// Long enough for the coal in front of the player to be picked up.
		context.waitTicks(20);
		return singleplayer.getServer().computeOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			ServerLevel level = player.level();
			int broken = 0;

			for (int i = 0; i < 4; i++) {
				broken += level.getBlockState(VEIN_START.south(i)).isAir() ? 1 : 0;
			}

			check(level.getBlockState(VEIN_START.east()).is(Blocks.IRON_ORE), "Iron ore isn't the same kind as coal ore and must stay");
			check(level.getBlockState(VEIN_START.west()).is(Blocks.STONE), "Stone isn't ore and must stay");
			checkNear(before + broken * TalentValues.GATHERER_XP_PER_ORE, serverEarnedXp(player, TalentTree.GATHERER),
					"Gatherer XP for " + broken + " ores broken");

			List<ItemEntity> coal = level.getEntitiesOfClass(ItemEntity.class, new AABB(VEIN_START).inflate(6.0), item -> item.getItem().is(Items.COAL));
			int atTheOres = (int) coal.stream().filter(item -> item.getZ() > QUARRY_DROP_LINE).count();
			int total = player.getInventory().countItem(Items.COAL) + coal.stream().mapToInt(item -> item.getItem().getCount()).sum();
			check(total == broken, "Every ore broken should drop exactly 1 coal without Fortune: " + broken + " broken, " + total + " coal");
			check(atTheOres == (sneaking ? 0 : 1), sneaking
					? "Every quarry drop should land in front of the player, found " + atTheOres + " at the ores"
					: "Without sneaking the coal should drop at its ore, found " + atTheOres + " there");
			return broken;
		});
	}

	/** More Luck III works as Looting III, Luck of the Sea III and Fortune III. */
	private static void luck(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			ServerLevel level = player.level();
			Holder<Enchantment> looting = level.registryAccess().getOrThrow(Enchantments.LOOTING);
			check(EnchantmentHelper.getEnchantmentLevel(looting, player) == 3, "More Luck III should loot like Looting III");
			check(EnchantmentHelper.getFishingLuckBonus(level, new ItemStack(Items.FISHING_ROD), player) == 3, "More Luck III should fish like Luck of the Sea III");

			BlockState ore = Blocks.DIAMOND_ORE.defaultBlockState();
			BlockPos pos = player.blockPosition();
			int withTalent = 0;
			int without = 0;

			for (int i = 0; i < 40; i++) {
				withTalent += totalCount(Block.getDrops(ore, level, pos, null, player, new ItemStack(Items.IRON_PICKAXE)));
				without += totalCount(Block.getDrops(ore, level, pos, null, null, new ItemStack(Items.IRON_PICKAXE)));
			}

			check(without == 40, "Without a player there's no luck: 1 diamond per ore, got " + without);
			// Fortune III gives 2.2 per ore on average; 60 is very far below that.
			check(withTalent > 60, "Fortune III should give a lot more than 1 diamond per ore, got " + withTalent + " from 40");
		});
	}

	/** Stronger Weapons V (+20%) and the specialists: +30% for a sword, +1.5 for a punch; damage that isn't an attack is left alone. */
	private static void weaponDamage(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);

			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
			checkNear(4.0 * 1.5, damageDealt(singleplayer, player.damageSources().playerAttack(player), 4.0F), "a sword hit");

			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			checkNear((1.0 + 1.5) * 1.2, damageDealt(singleplayer, player.damageSources().playerAttack(player), 1.0F), "a punch");

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

	/**
	 * The backpack: pickups that don't fit the inventory go into it; Shift + the inventory key opens it in game and from
	 * inside the inventory; lowering the talent to level 2 leaves 18 slots and moves the items out of the locked ones;
	 * without the talent Shift + the inventory key opens the plain inventory.
	 */
	private static void backpack(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
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
				"Diamonds that don't fit the inventory should go into the backpack"));

		shiftInventoryKey(context);
		context.waitForScreen(BackpackScreen.class);
		context.waitTicks(2);
		screenshot(context, "ist_12_backpack");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			Backpacks.get(player).setItem(30, new ItemStack(Items.EMERALD, 5));
			Talents.setLevel(player, Talent.BACKPACK, 2);
			Backpack backpack = Backpacks.get(player);
			check(backpack.getItem(30).isEmpty(), "A slot that's locked now must be emptied");
			check(countOf(backpack, Items.EMERALD) == 5, "Its items should move to an unlocked backpack slot");
			check(Backpacks.unlockedSlots(player) == 18, "Level 2 unlocks two rows of 9");
		});
		context.waitFor(client -> Backpacks.unlockedSlots(client.player) == 18, 40);

		// From inside the inventory: Shift + the inventory key opens the backpack instead of closing the inventory.
		context.getInput().pressKey(options -> options.keyInventory);
		context.waitForScreen(InventoryScreen.class);
		context.waitTicks(2);
		pressShiftInventoryKeyInScreen(context);
		context.waitForScreen(BackpackScreen.class);
		context.waitTicks(2);
		screenshot(context, "ist_13_backpack_two_rows");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		// Without the talent it's the plain inventory, which Shift + the inventory key also closes.
		singleplayer.getServer().runCommand("clear @p");
		singleplayer.getServer().runOnServer(_ -> Backpacks.get(player(singleplayer)).clearContent());
		singleplayer.getServer().runCommand("talents set @p backpack 0");
		context.waitFor(client -> Backpacks.unlockedSlots(client.player) == 0, 40);
		shiftInventoryKey(context);
		context.waitForScreen(InventoryScreen.class);
		context.waitTicks(2);
		pressShiftInventoryKeyInScreen(context);
		context.waitForScreen(null);
		context.waitTicks(5);
		check(context.computeOnClient(client -> client.gui.screen() == null), "Without the talent nothing else should open");

		singleplayer.getServer().runCommand("talents set @p backpack 4");
		context.waitFor(client -> Backpacks.unlockedSlots(client.player) == 36, 40);
	}

	/** Shift held with the inventory key, in game: the keys a player presses. */
	private static void shiftInventoryKey(ClientGameTestContext context) {
		context.getInput().holdShift();
		context.waitTick();
		context.getInput().pressKey(options -> options.keyInventory);
		context.getInput().releaseShift();
	}

	/**
	 * The inventory key with Shift, as the open screen gets it from the keyboard. The test input sends key events
	 * without modifiers, so this one is handed to the screen directly, the way vanilla's keyboard handler would.
	 */
	private static void pressShiftInventoryKeyInScreen(ClientGameTestContext context) {
		context.runOnClient(client -> {
			InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(client.options.keyInventory);
			client.gui.screen().keyPressed(new KeyEvent(key.getValue(), 'e', InputConstants.MOD_SHIFT));
		});
		context.waitTick();
	}

	/** The survival inventory has the two buttons above its top right corner. */
	private static void inventoryButtons(ClientGameTestContext context) {
		context.getInput().pressKey(options -> options.keyInventory);
		context.waitForScreen(InventoryScreen.class);
		context.waitTicks(2);
		screenshot(context, "ist_14_inventory_buttons");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	/**
	 * The backpack drops on death (no keepInventory); talents and tree XP are kept, and the extra hearts come back with
	 * the respawn.
	 */
	private static void death(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		BlockPos deathPos = singleplayer.getServer().computeOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			Backpacks.get(player).setItem(3, new ItemStack(Items.GOLD_INGOT, 3));
			return player.blockPosition();
		});
		long[] xpBefore = singleplayer.getServer().computeOnServer(_ -> treeXps(player(singleplayer)));
		singleplayer.getServer().runCommand("kill @p");
		context.waitForScreen(DeathScreen.class);
		context.waitTicks(5);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			check(Backpacks.get(player).isEmpty(), "The backpack should be dropped on death");
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
			check(Talents.level(player, Talent.MORE_HEALTH) == Talent.MORE_HEALTH.maxLevel(), "Talents are kept through death");
			check(Arrays.equals(treeXps(player), xpBefore), "Tree XP is kept through death");
			check(player.getMaxHealth() == 30.0F, "Extra hearts come back after respawning, got " + player.getMaxHealth());
			check(player.getHealth() == 30.0F, "Respawning fills the extra hearts too, got " + player.getHealth());
		});
		singleplayer.getServer().runCommand("kill @e[type=item]");
	}

	private static long[] treeXps(ServerPlayer player) {
		long[] xp = new long[TalentTree.values().length];

		for (TalentTree tree : TalentTree.values()) {
			xp[tree.ordinal()] = Talents.treeXp(player, tree);
		}

		return xp;
	}

	/** Clears advancement toasts and chat first, so they don't cover what the screenshot is for. */
	private static void screenshot(ClientGameTestContext context, String name) {
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
		screenshotWithChat(context, name);
	}

	/** Clears advancement toasts only, for a screenshot of a chat line. */
	private static void screenshotWithChat(ClientGameTestContext context, String name) {
		context.runOnClient(client -> client.gui.toastManager().clear());
		context.waitTick();
		context.takeScreenshot(name);
	}

	/** Waits for a chat line with this translation key and returns it as the player reads it. */
	private static String waitForChat(ClientGameTestContext context, String key) {
		context.waitFor(_ -> chatLine(key) != null, 40);
		return context.computeOnClient(_ -> chatLine(key).getString());
	}

	private static @Nullable Component chatLine(String key) {
		for (Component message : CHAT) {
			if (message.getContents() instanceof TranslatableContents translatable && translatable.getKey().equals(key)) {
				return message;
			}
		}

		return null;
	}

	private static void openTalents(ClientGameTestContext context) {
		context.getInput().pressKey(TalentClient.OPEN_TALENTS);
		context.waitForScreen(TalentScreen.class);
		context.waitTicks(2);
	}

	/** The key that opens the talent screen closes it too. */
	private static void closeTalents(ClientGameTestContext context) {
		context.getInput().pressKey(TalentClient.OPEN_TALENTS);
		context.waitForScreen(null);
	}

	private static void clickTab(ClientGameTestContext context, TalentTree tree) {
		moveCursor(context, context.computeOnClient(_ -> TalentScreen.tabCenter(tree)));
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitFor(_ -> TalentScreen.activeTree() == tree, 20);
	}

	private static void clickPin(ClientGameTestContext context) {
		moveCursor(context, context.computeOnClient(_ -> TalentScreen.pinCenter()));
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
	}

	/** Clicks a talent's node and waits until both sides have it at this level. */
	private static void buy(ClientGameTestContext context, TestSingleplayerContext singleplayer, Talent talent, int level) {
		check(context.computeOnClient(client -> Talents.canBuy(client.player, talent)) == Talents.BuyResult.BOUGHT, talent.id() + " should be buyable now");
		hover(context, talent);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitFor(client -> Talents.level(client.player, talent) == level, 40);
		check(serverLevel(singleplayer, talent) == level, talent.id() + " should be at level " + level + " on the server");
	}

	/** Clicks a talent's node that can't be bought, for this reason on both sides, and checks nothing was bought. */
	private static void clickFails(ClientGameTestContext context, TestSingleplayerContext singleplayer, Talent talent, Talents.BuyResult why) {
		int before = serverLevel(singleplayer, talent);
		check(context.computeOnClient(client -> Talents.canBuy(client.player, talent)) == why, talent.id() + " should not be buyable: " + why);
		check(singleplayer.getServer().computeOnServer(_ -> Talents.canBuy(player(singleplayer), talent)) == why, "The server should agree: " + why);
		hover(context, talent);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(10);
		check(serverLevel(singleplayer, talent) == before, talent.id() + " must not be bought (" + why + ")");
	}

	private static void checkPoints(ClientGameTestContext context, TalentTree tree, int expected, String why) {
		int points = context.computeOnClient(client -> Talents.points(client.player, tree));
		check(points == expected, "Expected " + expected + " " + tree.id() + " points (" + why + "), got " + points);
	}

	/** Holds the attack key while looking at a block until the server has it broken, as a player mines. */
	private static void mine(ClientGameTestContext context, TestSingleplayerContext singleplayer, BlockPos pos) {
		context.waitFor(client -> !client.level.getBlockState(pos).isAir(), 40);
		aimAt(context, Vec3.atCenterOf(pos));
		context.getInput().holdKey(options -> options.keyAttack);
		singleplayer.getServer().waitFor(_ -> player(singleplayer).level().getBlockState(pos).isAir(), 100);
		context.getInput().releaseKey(options -> options.keyAttack);
		context.waitTicks(5);
	}

	/** One click on a crop, aimed low enough to hit young wheat too; crops break at once. */
	private static void harvest(ClientGameTestContext context, TestSingleplayerContext singleplayer, BlockPos pos) {
		context.waitFor(client -> client.level.getBlockState(pos).is(Blocks.WHEAT), 40);
		aimAt(context, new Vec3(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5));
		context.getInput().pressKey(options -> options.keyAttack);
		singleplayer.getServer().waitFor(_ -> player(singleplayer).level().getBlockState(pos).isAir(), 40);
		context.waitTicks(5);
	}

	/**
	 * One swing at a mob from the client with a full attack charge, aimed at its chest. Returns the health it lost on
	 * the server.
	 */
	private static double hitFromClient(ClientGameTestContext context, TestSingleplayerContext singleplayer, Mob mob) {
		context.waitFor(client -> client.level.getEntity(mob.getId()) != null, 40);
		// The held item's attack cooldown recharges in well under a second.
		context.waitTicks(25);
		Vec3 chest = singleplayer.getServer().computeOnServer(_ -> mob.position().add(0.0, mob.getBbHeight() * 0.6, 0.0));
		aimAt(context, chest);
		float before = singleplayer.getServer().computeOnServer(_ -> mob.getHealth());
		context.getInput().pressKey(options -> options.keyAttack);
		singleplayer.getServer().waitFor(_ -> mob.getHealth() < before, 20);
		context.waitTicks(2);
		double lost = before - singleplayer.getServer().computeOnServer(_ -> mob.getHealth());
		check(lost > 0.0, "The swing should have hurt the " + mob.getType());
		return lost;
	}

	/**
	 * Turns the client player's eyes to this point. The mouse is grabbed first, as when a player clicks into the game,
	 * since vanilla only keeps mining while it is.
	 */
	private static void aimAt(ClientGameTestContext context, Vec3 point) {
		context.runOnClient(client -> {
			if (!client.mouseHandler.isMouseGrabbed()) {
				client.mouseHandler.grabMouse();
			}

			client.player.lookAt(EntityAnchorArgument.Anchor.EYES, point);
		});
		context.waitTicks(2);
	}

	/** A tree's XP with the fraction carried over towards its next whole point: everything earned, read on the server. */
	private static double earnedXp(TestSingleplayerContext singleplayer, TalentTree tree) {
		return singleplayer.getServer().computeOnServer(_ -> serverEarnedXp(player(singleplayer), tree));
	}

	private static double serverEarnedXp(ServerPlayer player, TalentTree tree) {
		return Talents.treeXp(player, tree) + player.getAttachedOrCreate(ImSoTalented.TREE_XP_FRACTIONS)[tree.ordinal()];
	}

	private static double serverX(TestSingleplayerContext singleplayer) {
		return singleplayer.getServer().computeOnServer(_ -> player(singleplayer).getX());
	}

	private static void setBlock(TestSingleplayerContext singleplayer, BlockPos pos, BlockState state) {
		singleplayer.getServer().runOnServer(_ -> player(singleplayer).level().setBlockAndUpdate(pos, state));
	}

	private static String readFile(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void writeFile(Path file, String text) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, text);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
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

	/** A mob without AI (it stands still), offset from the player. Call on the server thread. */
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
		ImSoTalented.setConfig(new ImSoTalentedConfig(rules, ImSoTalentedConfig.DEFAULTS.backpackPickup()));
		ImSoTalented.setClientRules(rules);
	}

	private static void holdInMainHand(ClientGameTestContext context, TestSingleplayerContext singleplayer, ItemStack stack) {
		singleplayer.getServer().runOnServer(_ -> player(singleplayer).setItemInHand(InteractionHand.MAIN_HAND, stack.copy()));
		context.waitTicks(5);
	}

	private static int serverLevel(TestSingleplayerContext singleplayer, Talent talent) {
		return singleplayer.getServer().computeOnServer(_ -> Talents.level(player(singleplayer), talent));
	}

	private static void hover(ClientGameTestContext context, Talent talent) {
		moveCursor(context, context.computeOnClient(_ -> TalentScreen.nodeCenter(talent)));
	}

	/** Puts the cursor on this GUI-scaled point of the window. */
	private static void moveCursor(ClientGameTestContext context, Vector2i gui) {
		Vector2d cursor = context.computeOnClient(client -> {
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
