package no.kvaksrud.imsotalented.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import no.kvaksrud.imsotalented.ImSoTalented;
import no.kvaksrud.imsotalented.ImSoTalentedConfig;
import no.kvaksrud.imsotalented.client.ImSoTalentedClient;
import no.kvaksrud.imsotalented.client.ImSoTalentedClientConfig;
import no.kvaksrud.imsotalented.client.TalentClient;
import no.kvaksrud.imsotalented.client.TalentScreen;
import no.kvaksrud.imsotalented.client.TreeXpHud;
import no.kvaksrud.imsotalented.client.inventory.BackpackScreen;
import no.kvaksrud.imsotalented.client.talent.TalentTreeLayout;
import no.kvaksrud.imsotalented.client.talent.TreeXpBar;
import no.kvaksrud.imsotalented.effect.Crops;
import no.kvaksrud.imsotalented.effect.TreeXpAttachments;
import no.kvaksrud.imsotalented.inventory.Backpack;
import no.kvaksrud.imsotalented.inventory.BackpackMenu;
import no.kvaksrud.imsotalented.inventory.Backpacks;
import no.kvaksrud.imsotalented.network.BuyTalentPayload;
import no.kvaksrud.imsotalented.network.RequestThrottle;
import no.kvaksrud.imsotalented.network.ToggleTalentPayload;
import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentData;
import no.kvaksrud.imsotalented.talent.TalentRules;
import no.kvaksrud.imsotalented.talent.TalentRules.EnchantmentStacking;
import no.kvaksrud.imsotalented.talent.TalentTree;
import no.kvaksrud.imsotalented.talent.TalentValues;
import no.kvaksrud.imsotalented.talent.Talents;
import org.joml.Vector2d;
import org.joml.Vector2i;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.MixinEnvironment;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Plays the mod in a real client, in survival unless a section says otherwise. Every section sets up what it needs
 * first ({@link #startSection}: survival, at home, healthy, empty-handed, no other entities, exactly the talents and
 * tree XP it asks for), so the sections can run in any order.
 *
 * <p>As a new player: the settings file (0.1.0's carried over); the tabbed talent screen (N opens and closes it, the
 * tabs switch trees); buying with talent points (none without tree XP, one per tree level from {@code /talents xp}, a
 * locked talent, an unfinished one marked SOON) with the level-up chat lines; the keyboard (Tab, the arrows, Enter and
 * Shift+Enter); the server turning away buy and toggle packets a modified client sends, and floods of them. Tree XP
 * earned through real play in each tree with a control that earns nothing (mining, the pacing of plain blocks, crops,
 * berries, fishing and the Gatherer XP talent; a sword hit and the damage that isn't an attack; a husk's swing, fall
 * damage, armor and Block Chance, and a crowd on a raised shield; walking, pushing and new chunks); the HUD bar and the
 * pin; creative (free talents, no XP, toggles); {@code /talents} (list, set, toggle, xp, reset and their feedback).
 * Then with every talent at its top level: the attribute talents; toggling (command and right-click); the farming
 * talents; Step Assist, Reach and Swim Speed; mining speed under both stacking rules; Quarrying (sneaking while mining
 * an ore, its tool and block limits); More Luck; weapon damage for every weapon; Block Chance; the backpack (pickup
 * overflow and the setting that turns it off, Shift + inventory key, shrinking, the menu's locked slots, the plain
 * inventory without the talent); the inventory buttons; death and respawn. Last a save and a reload with a settings
 * file that isn't the default, which must reach the client. Statistical checks hold within four standard deviations of
 * the design's mean, with enough samples that a level off by one falls outside. Screenshots land in
 * build/run/clientGameTest/screenshots (prefixed ist_).
 */
@SuppressWarnings("UnstableApiUsage")
public class ImSoTalentedClientGameTest implements FabricClientGameTest {
	private static final double EPSILON = 1e-4;
	/** Statistical checks pass within this many standard deviations of the expected mean. */
	private static final double SIGMAS = 4.0;
	private static final int STONE_PICKAXE_SPEED = 8;
	/** Where every scenario starts: on the superflat ground near spawn, facing south (+z). */
	private static final String HOME = "tp @p 0.5 -60 0.5 0 0";
	/** Chat lines and action bar texts the client has received. */
	private static final List<Component> CHAT = new CopyOnWriteArrayList<>();
	private static final List<Component> OVERLAY = new CopyOnWriteArrayList<>();
	/** What the reload's settings file says; nothing in it is the default. */
	private static final ImSoTalentedConfig RELOAD_SETTINGS = new ImSoTalentedConfig(new TalentRules(30, 1.2, EnchantmentStacking.ADD), false);

	@Override
	public void runTest(ClientGameTestContext context) {
		// A settings file as 0.1.0 wrote it: the world's start must replace its vanilla-XP prices with the tree prices
		// and carry the pickup setting over (checkSettingsFile). And nothing is pinned.
		writeFile(settingsFile(), """
				cost_base=10
				cost_growth=1.07
				enchantment_stacking=highest
				converter_values=minecraft:diamond=40
				extra_inventory_pickup=true
				""");
		context.runOnClient(_ -> {
			TreeXpHud.setPinned(null);
			ClientReceiveMessageEvents.GAME.register((message, overlay) -> (overlay ? OVERLAY : CHAT).add(message));
		});
		TestWorldSave save;
		int visitedChunks;
		long[] xpBeforeReload;

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

			checkSettingsFile(context);
			openAndClose(context, singleplayer);
			buyingInTheTree(context, singleplayer);
			keyboard(context, singleplayer);
			serverTurnsAwayBadRequests(context, singleplayer);
			gathererXp(context, singleplayer);
			warriorXp(context, singleplayer);
			guardianXp(context, singleplayer);
			explorerXp(context, singleplayer);
			tabsAndPin(context, singleplayer);
			creative(context, singleplayer);
			commands(context, singleplayer);

			attributesAtTheTopLevel(context, singleplayer);
			toggling(context, singleplayer);
			farming(context, singleplayer);
			mobility(context, singleplayer);
			miningSpeed(context, singleplayer);
			quarrying(context, singleplayer);
			luck(context, singleplayer);
			weaponDamage(context, singleplayer);
			blockChance(context, singleplayer);
			backpack(context, singleplayer);
			inventoryButtons(context, singleplayer);
			death(context, singleplayer);

			// What the reload must find: every talent and tree at the top, a little less than full health (which a reload
			// must not clamp back to 20), an apple in the backpack, the chunks Explorer paid for, and the Warrior pin.
			startSection(context, singleplayer, maxedTalents());
			context.runOnClient(_ -> TreeXpHud.setPinned(TalentTree.WARRIOR));
			visitedChunks = singleplayer.getServer().computeOnServer(_ -> {
				ServerPlayer player = player(singleplayer);
				player.setHealth(25.0F);
				Backpacks.get(player).setItem(0, new ItemStack(Items.APPLE, 7));
				return player.getAttachedOrCreate(TreeXpAttachments.VISITED_CHUNKS).count();
			});
			xpBeforeReload = singleplayer.getServer().computeOnServer(_ -> treeXps(player(singleplayer)));
		}

		// Settings are read when a world starts: the reload's, none of them the default, must reach the client too.
		writeFile(settingsFile(), """
				level_cost_base=30
				level_cost_growth=1.2
				enchantment_stacking=add
				backpack_pickup=false
				""");

		try (TestSingleplayerContext singleplayer = save.open()) {
			singleplayer.getConnection().waitForChunksRender();
			check(ImSoTalented.config().equals(RELOAD_SETTINGS), "The world's start should read the new settings file, got " + ImSoTalented.config());
			context.waitFor(_ -> ImSoTalentedClient.serverHasMod() && ImSoTalented.clientRules().equals(RELOAD_SETTINGS.rules()), 40);
			TalentRules rules = RELOAD_SETTINGS.rules();

			singleplayer.getServer().runOnServer(_ -> {
				ServerPlayer player = player(singleplayer);

				for (Talent talent : Talent.values()) {
					check(Talents.level(player, talent) == talent.maxLevel(), talent.id() + " should still be maxed after a reload");
				}

				check(Arrays.equals(treeXps(player), xpBeforeReload), "Tree XP should be saved: " + Arrays.toString(xpBeforeReload)
						+ " before, " + Arrays.toString(treeXps(player)) + " after");

				for (TalentTree tree : TalentTree.values()) {
					int level = rules.levelFor(xpBeforeReload[tree.ordinal()], tree.maxLevel());
					check(level < tree.maxLevel(), "With dearer levels the old top-level XP should no longer reach the top of " + tree.id());
					check(Talents.treeLevel(player, tree) == level, tree.id() + " should be at level " + level + " at the new prices, got "
							+ Talents.treeLevel(player, tree));
				}

				check(player.getMaxHealth() == 30.0F, "Max health should be 30 after a reload, got " + player.getMaxHealth());
				// Natural regeneration may have healed a little since; what matters is that it wasn't clamped to 20.
				check(player.getHealth() >= 25.0F, "Health above 20 must survive a reload, got " + player.getHealth());
				check(countOf(Backpacks.get(player), Items.APPLE) == 7, "The backpack should be saved");
				int chunks = player.getAttachedOrCreate(TreeXpAttachments.VISITED_CHUNKS).count();
				check(chunks == visitedChunks, "The chunks Explorer paid for should be saved: " + visitedChunks + " before, " + chunks + " after");
			});
			context.waitFor(client -> client.player != null && Talents.level(client.player, Talent.FASTER_MINING) == 5);

			for (TalentTree tree : TalentTree.values()) {
				int expected = rules.levelFor(xpBeforeReload[tree.ordinal()], tree.maxLevel());
				int client = context.computeOnClient(c -> Talents.treeLevel(c.player, tree));
				check(client == expected, "The client should show " + tree.id() + " at level " + expected + " under the server's prices, got " + client);
			}

			check(context.computeOnClient(_ -> TreeXpHud.pinned()) == TalentTree.WARRIOR, "The pin is a client setting and outlives the world");
			context.waitFor(_ -> TreeXpHud.shownTree() == TalentTree.WARRIOR, TreeXpHud.FLASH_TICKS + 40);
			screenshot(context, "ist_17_after_reload");
		}

		// The next start of a test world reads the 0.1.0 file again; nothing else should find this one.
		deleteFile(settingsFile());
	}

	private static Path settingsFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(ImSoTalentedConfig.FILE_NAME);
	}

	/**
	 * The world's start rewrote the 0.1.0 settings file with the defaults: the four-tree prices and the backpack,
	 * nothing older. The client got the server's rules and knows the server has the mod.
	 */
	private static void checkSettingsFile(ClientGameTestContext context) {
		Path configFile = settingsFile();
		check(Files.isRegularFile(configFile), "Expected the settings file at " + configFile);
		check(ImSoTalented.config().equals(ImSoTalentedConfig.DEFAULTS), "Expected the default settings, got " + ImSoTalented.config());

		String text = readFile(configFile);

		for (String line : List.of("level_cost_base=25", "level_cost_growth=1.18", "enchantment_stacking=highest", "backpack_pickup=true")) {
			check(text.contains(line), "The settings file should say " + line + ":\n" + text);
		}

		check(!text.contains("converter") && !text.contains("extra_inventory") && !text.contains("\ncost_base") && !text.contains("\ncost_growth"),
				"The settings file should have nothing about the converter, the extra inventory or the old prices:\n" + text);
		context.waitFor(_ -> ImSoTalentedClient.serverHasMod(), 40);
		check(context.computeOnClient(_ -> ImSoTalented.clientRules()).equals(TalentRules.DEFAULTS), "The client should have the server's rules");
	}

	/**
	 * N opens the talent screen (on the Gatherer tab, the first time) and closes it again. The window with its tabs is
	 * 320 wide, so it fits the narrowest GUI-scaled screen (a 4:3 window at its largest GUI scale) border and all.
	 */
	private static void openAndClose(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		check(TalentTreeLayout.WIDTH == 292 && TalentTreeLayout.TOTAL_WIDTH == 320, "The window is 292 wide, 320 with the tabs");
		TalentTreeLayout narrowest = TalentTreeLayout.forScreen(320, 240);
		check(narrowest.left() - (TalentTreeLayout.TAB_WIDTH - TalentTreeLayout.TAB_OVERLAP) == 0 && narrowest.left() + TalentTreeLayout.WIDTH == 320,
				"At a 320-wide GUI the tabs start at the left edge and the window ends at the right one, got left " + narrowest.left());
		check(TreeXpHud.SLOT_HEIGHT == 13 && TreeXpBar.FULL_WIDTH == 182 && TreeXpBar.HEIGHT == 5, "The HUD strip is 13 high, its bar as wide as vanilla's");

		startSection(context, singleplayer, TalentData.EMPTY);
		openTalents(context);
		check(context.computeOnClient(_ -> TalentScreen.activeTree()) == TalentTree.GATHERER, "The talent screen should first open on the Gatherer tab");
		closeTalents(context);
	}

	/**
	 * Buying spends points, and a tree's points come from its levels. Without tree XP a click buys nothing. After
	 * {@code /talents xp} brings Gatherer to level 1, with its chat line, the click buys Faster Mining. Backpack stays
	 * locked until Careful Hands has a level. In Warrior, Sword Specialist stays locked until Stronger Weapons has a
	 * level, and Lifesteal, unfinished, can't be bought even with its prereq at 5. The top level's chat line is its own.
	 */
	private static void buyingInTheTree(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, TalentData.EMPTY);
		TalentRules rules = ImSoTalented.config().rules();

		openTalents(context);
		clickTab(context, TalentTree.GATHERER);
		hover(context, Talent.FASTER_MINING);
		screenshot(context, "ist_01_no_points_tooltip");
		clickFails(context, singleplayer, Talent.FASTER_MINING, Talents.BuyResult.NO_POINTS);
		closeTalents(context);

		CHAT.clear();
		singleplayer.getServer().runCommand("talents xp @p gatherer add " + rules.xpForLevel(1));
		String levelUp = waitForChat(context, "message.imsotalented.level_up");
		check(levelUp.contains("Gatherer is now level 1") && levelUp.contains("Talent points to spend there: 1"),
				"Unexpected level-up line: " + levelUp);
		screenshotWithChat(context, "ist_02_level_up_chat");

		openTalents(context);
		check(context.computeOnClient(_ -> TalentScreen.activeTree()) == TalentTree.GATHERER, "The screen should open on the last tab");
		hover(context, Talent.FASTER_MINING);
		screenshot(context, "ist_03_buy_tooltip");
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

		// Warrior's points are Warrior's alone: six levels, five for Stronger Weapons and one for Sword Specialist.
		clickTab(context, TalentTree.WARRIOR);
		singleplayer.getServer().runCommand("talents xp @p warrior set " + rules.xpForLevel(6));
		context.waitFor(client -> Talents.points(client.player, TalentTree.WARRIOR) == 6, 40);
		checkPoints(context, TalentTree.GATHERER, 0, "Warrior's levels give no Gatherer points");
		clickFails(context, singleplayer, Talent.SWORD_SPECIALIST, Talents.BuyResult.LOCKED);

		for (int level = 1; level <= 5; level++) {
			buy(context, singleplayer, Talent.STRONGER_WEAPONS, level);
		}

		// Lifesteal's prereq is met now, but it does nothing yet: SOON, and not for sale.
		hover(context, Talent.LIFESTEAL);
		screenshot(context, "ist_04_soon_tooltip");
		clickFails(context, singleplayer, Talent.LIFESTEAL, Talents.BuyResult.NOT_YET);
		buy(context, singleplayer, Talent.SWORD_SPECIALIST, 1);
		checkPoints(context, TalentTree.WARRIOR, 0, "six points, six levels");
		closeTalents(context);

		// The last level says the tree is done.
		long explorerTop = rules.xpForLevel(TalentTree.EXPLORER.maxLevel());
		singleplayer.getServer().runCommand("talents xp @p explorer set " + (explorerTop - 1));
		context.waitFor(client -> Talents.treeXp(client.player, TalentTree.EXPLORER) == explorerTop - 1, 40);
		CHAT.clear();
		singleplayer.getServer().runCommand("talents xp @p explorer add 1");
		String top = waitForChat(context, "message.imsotalented.level_up_top");
		check(top.contains("Explorer reached its top level, " + TalentTree.EXPLORER.maxLevel()) && top.contains("earns no more XP"),
				"Unexpected top-level line: " + top);
		check(chatLine(CHAT, "message.imsotalented.level_up") == null, "The top level gets its own line, not the usual one");
	}

	/**
	 * The talent screen with the keyboard: Tab goes through the tabs, the pin and then the nodes; the arrows move to the
	 * nearest node that way; Enter does what a left click does (shows a tab, buys, pins) and Shift+Enter flips a
	 * node's toggle. The focused element is outlined and narrated.
	 */
	private static void keyboard(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TalentRules rules = ImSoTalented.config().rules();
		// One Gatherer point to spend, and Faster Running owned to toggle.
		startSection(context, singleplayer, TalentData.EMPTY.with(Talent.FASTER_MINING, 1).with(Talent.FASTER_RUNNING, 1)
				.withXp(TalentTree.GATHERER, rules.xpForLevel(2)));
		context.runOnClient(_ -> TreeXpHud.setPinned(null));

		openTalents(context);
		clickTab(context, TalentTree.GATHERER);
		// The mouse off every tab and node, so only the keyboard's focus has a tooltip.
		moveCursor(context, new Vector2i(1, 1));
		check(context.computeOnClient(_ -> TalentScreen.focusedTab() == null && TalentScreen.focusedTalent() == null && !TalentScreen.isPinFocused()),
				"Nothing has the focus until a key is pressed");

		for (TalentTree tab : TalentTree.values()) {
			context.getInput().pressKey(InputConstants.KEY_TAB);
			check(context.computeOnClient(_ -> TalentScreen.focusedTab()) == tab, "Tab should step through the tabs, expected " + tab);
		}

		context.getInput().pressKey(InputConstants.KEY_TAB);
		check(context.computeOnClient(_ -> TalentScreen.isPinFocused()), "After the tabs, Tab should focus the pin");
		context.getInput().pressKey(InputConstants.KEY_TAB);
		check(context.computeOnClient(_ -> TalentScreen.focusedTalent()) == Talent.FASTER_MINING, "Then the active tree's root");
		context.waitTicks(2);
		screenshot(context, "ist_05_keyboard_focus");
		String narration = context.computeOnClient(_ -> TalentScreen.focusedNarration());
		check(narration.contains("Faster Mining") && narration.contains("Level 1/5"), "The narrator should read the focused node: " + narration);

		// Tab goes row by row: Gatherer XP is first in the second row. Enter buys it with the one point.
		context.getInput().pressKey(InputConstants.KEY_TAB);
		check(context.computeOnClient(_ -> TalentScreen.focusedTalent()) == Talent.GATHERER_XP, "Tab should go on to the second row");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		singleplayer.getServer().waitFor(_ -> Talents.level(player(singleplayer), Talent.GATHERER_XP) == 1, 40);
		waitServerTicks(singleplayer, RequestThrottle.MIN_TICKS);

		// Down from Gatherer XP is Quarrying, right under it.
		context.getInput().pressKey(InputConstants.KEY_DOWN);
		check(context.computeOnClient(_ -> TalentScreen.focusedTalent()) == Talent.QUARRYING, "The down arrow should move to the node below");

		// Back to the tabs: Enter on a tab shows its tree.
		closeTalents(context);
		openTalents(context);
		moveCursor(context, new Vector2i(1, 1));
		context.getInput().pressKey(InputConstants.KEY_TAB);
		context.getInput().pressKey(InputConstants.KEY_TAB);
		check(context.computeOnClient(_ -> TalentScreen.focusedTab()) == TalentTree.WARRIOR, "The second tab");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		check(context.computeOnClient(_ -> TalentScreen.activeTree()) == TalentTree.WARRIOR, "Enter on a tab should show its tree");

		// Enter on the pin pins the shown tree, and again unpins it.
		context.getInput().pressKey(InputConstants.KEY_TAB);
		context.getInput().pressKey(InputConstants.KEY_TAB);
		context.getInput().pressKey(InputConstants.KEY_TAB);
		check(context.computeOnClient(_ -> TalentScreen.isPinFocused()), "Three more Tabs reach the pin");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		check(context.computeOnClient(_ -> TreeXpHud.pinned()) == TalentTree.WARRIOR, "Enter on the pin should pin the shown tree");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		check(context.computeOnClient(_ -> TreeXpHud.pinned()) == null, "And again unpin it");

		// Shift+Tab goes back to the Explorer tab; Enter shows it, and two Tabs reach its root, Faster Running.
		pressInScreen(context, InputConstants.KEY_TAB, 9, InputConstants.MOD_SHIFT);
		check(context.computeOnClient(_ -> TalentScreen.focusedTab()) == TalentTree.EXPLORER, "Shift+Tab should go back a step");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.getInput().pressKey(InputConstants.KEY_TAB);
		context.getInput().pressKey(InputConstants.KEY_TAB);
		check(context.computeOnClient(_ -> TalentScreen.focusedTalent()) == Talent.FASTER_RUNNING, "The Explorer root");

		// Shift+Enter is the node's right click: it flips the toggle, and back.
		pressInScreen(context, InputConstants.KEY_RETURN, 13, InputConstants.MOD_SHIFT);
		singleplayer.getServer().waitFor(_ -> Talents.data(player(singleplayer)).isDisabled(Talent.FASTER_RUNNING), 40);
		waitServerTicks(singleplayer, RequestThrottle.MIN_TICKS);
		context.waitFor(client -> Talents.data(client.player).isDisabled(Talent.FASTER_RUNNING), 40);
		pressInScreen(context, InputConstants.KEY_RETURN, 13, InputConstants.MOD_SHIFT);
		singleplayer.getServer().waitFor(_ -> !Talents.data(player(singleplayer)).isDisabled(Talent.FASTER_RUNNING), 40);
		check(serverLevel(singleplayer, Talent.FASTER_RUNNING) == 1, "Toggling never changes the level");
		closeTalents(context);
	}

	/**
	 * The client only asks for what its own checks allow, so a modified client is the only way to see whether the server
	 * checks too. Here the packets go out straight from the client: a buy without points, of a locked talent, of a maxed
	 * one, of an unfinished one and of an unknown id, and toggles of an unowned talent, a non-toggleable one and an
	 * unfinished one. The server says why on the action bar and changes nothing. A flood of the same request in one tick
	 * is handled once.
	 */
	private static void serverTurnsAwayBadRequests(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TalentRules rules = ImSoTalented.config().rules();
		// Gatherer and Warrior: one point each, spent nowhere yet; Guardian: no XP; Explorer: three points.
		TalentData start = TalentData.EMPTY.with(Talent.FASTER_MINING, 5).with(Talent.STRONGER_WEAPONS, 5).with(Talent.NIGHT_VISION, 1)
				.with(Talent.FASTER_RUNNING, 1)
				.withXp(TalentTree.GATHERER, rules.xpForLevel(6)).withXp(TalentTree.WARRIOR, rules.xpForLevel(6))
				.withXp(TalentTree.EXPLORER, rules.xpForLevel(4));
		startSection(context, singleplayer, start);

		rejected(context, singleplayer, new BuyTalentPayload(Talent.MORE_HEALTH.id()), "message.imsotalented.no_points", start);
		rejected(context, singleplayer, new BuyTalentPayload(Talent.BACKPACK.id()), "message.imsotalented.locked", start);
		rejected(context, singleplayer, new BuyTalentPayload(Talent.FASTER_MINING.id()), "message.imsotalented.maxed", start);
		rejected(context, singleplayer, new BuyTalentPayload(Talent.LIFESTEAL.id()), "message.imsotalented.not_yet", start);
		rejected(context, singleplayer, new ToggleTalentPayload(Talent.STEP_ASSIST.id(), false), "message.imsotalented.toggle_not_owned", start);
		rejected(context, singleplayer, new ToggleTalentPayload(Talent.FASTER_MINING.id(), false), "message.imsotalented.toggle_not_toggleable", start);
		rejected(context, singleplayer, new ToggleTalentPayload(Talent.NIGHT_VISION.id(), false), "message.imsotalented.toggle_not_toggleable", start);

		// An id the server doesn't know: nothing to say, nothing changes.
		OVERLAY.clear();
		send(context, new BuyTalentPayload("no_such_talent"));
		singleplayer.getConnection().waitForServerboundPackets();
		checkUnchanged(singleplayer, start, "an unknown talent");
		waitServerTicks(singleplayer, RequestThrottle.MIN_TICKS);

		// A flood: three buys in one tick buy one level.
		OVERLAY.clear();
		context.runOnClient(_ -> {
			for (int i = 0; i < 3; i++) {
				ClientPlayNetworking.send(new BuyTalentPayload(Talent.HIGHER_JUMPING.id()));
			}
		});
		waitForOverlay(context, "message.imsotalented.bought");
		singleplayer.getConnection().waitForServerboundPackets();
		check(serverLevel(singleplayer, Talent.HIGHER_JUMPING) == 1, "Three buys in one tick should buy one level, got " + serverLevel(singleplayer, Talent.HIGHER_JUMPING));
		check(singleplayer.getServer().computeOnServer(_ -> Talents.points(player(singleplayer), TalentTree.EXPLORER)) == 2, "One point spent of three");

		// Off and on in one tick: only the first counts, and the next request a moment later is handled again.
		waitServerTicks(singleplayer, RequestThrottle.MIN_TICKS);
		OVERLAY.clear();
		context.runOnClient(_ -> {
			ClientPlayNetworking.send(new ToggleTalentPayload(Talent.FASTER_RUNNING.id(), false));
			ClientPlayNetworking.send(new ToggleTalentPayload(Talent.FASTER_RUNNING.id(), true));
		});
		waitForOverlay(context, "message.imsotalented.toggled_off");
		singleplayer.getConnection().waitForServerboundPackets();
		check(singleplayer.getServer().computeOnServer(_ -> Talents.data(player(singleplayer)).isDisabled(Talent.FASTER_RUNNING)),
				"Only the first of two toggles in one tick should count");
		check(chatLine(OVERLAY, "message.imsotalented.toggled_on") == null, "The second toggle should have been ignored");
		waitServerTicks(singleplayer, RequestThrottle.MIN_TICKS);
		send(context, new ToggleTalentPayload(Talent.FASTER_RUNNING.id(), true));
		singleplayer.getServer().waitFor(_ -> !Talents.data(player(singleplayer)).isDisabled(Talent.FASTER_RUNNING), 40);
	}

	/** Sends a packet the server must turn away with this action bar message, and checks it changed nothing. */
	private static void rejected(ClientGameTestContext context, TestSingleplayerContext singleplayer, CustomPacketPayload payload, String why,
			TalentData expected) {
		OVERLAY.clear();
		send(context, payload);
		waitForOverlay(context, why);
		checkUnchanged(singleplayer, expected, payload.toString());
		// The next request of the same kind must not fall within the throttle.
		waitServerTicks(singleplayer, RequestThrottle.MIN_TICKS);
	}

	private static void checkUnchanged(TestSingleplayerContext singleplayer, TalentData expected, String what) {
		TalentData now = singleplayer.getServer().computeOnServer(_ -> Talents.data(player(singleplayer)));
		check(now.equals(expected), "The server must change nothing for " + what + ": " + expected + " before, " + now + " after");
	}

	/**
	 * Gatherer XP from mining, farming and fishing. A log chopped with an axe and sand dug with a shovel earn nothing;
	 * stone mined with a pickaxe and an ore mined with Silk Touch earn a tenth of an ore each, but plain blocks pay at
	 * most once in 10 ticks; an ore mined with a plain pickaxe earns 5, and 6.5 with Gatherer XP III. A ripe wheat
	 * harvested earns 1, young wheat nothing; berries picked by hand earn 1 and get Bountiful Harvest's bonus; a catch
	 * reeled in earns 5. With nothing pinned, the HUD shows the Gatherer bar only for a moment after earning a whole point.
	 */
	private static void gathererXp(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, TalentData.EMPTY);
		context.runOnClient(_ -> TreeXpHud.setPinned(null));
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
		checkNear(start + 0.5, earnedXp(singleplayer, TalentTree.GATHERER), "stone mined with a pickaxe earns a tenth of an ore");
		check(context.computeOnClient(_ -> TreeXpHud.shownTree()) == null, "Half a point isn't a whole one yet, so the HUD bar should stay hidden");

		waitUntilPlainBlocksPay(singleplayer);
		holdInMainHand(context, singleplayer, enchanted(singleplayer, new ItemStack(Items.DIAMOND_PICKAXE), Enchantments.SILK_TOUCH, 1));
		setBlock(singleplayer, block, Blocks.COAL_ORE.defaultBlockState());
		mine(context, singleplayer, block);
		checkNear(start + 1.0, earnedXp(singleplayer, TalentTree.GATHERER), "an ore mined with Silk Touch earns what stone does");

		holdInMainHand(context, singleplayer, new ItemStack(Items.DIAMOND_PICKAXE));
		setBlock(singleplayer, block, Blocks.COAL_ORE.defaultBlockState());
		mine(context, singleplayer, block);
		checkNear(start + 1.0 + 5.0, earnedXp(singleplayer, TalentTree.GATHERER), "an ore mined with a pickaxe");

		context.waitFor(_ -> TreeXpHud.shownTree() == TalentTree.GATHERER, 20);
		context.waitFor(_ -> TreeXpHud.shownTree() == null, TreeXpHud.FLASH_TICKS + 20);

		// Plain blocks that break in one hit (netherrack, stone under Haste) pay at most once in 10 ticks: two broken
		// in the same tick pay for one, and the next pays once the 10 ticks are up.
		List<BlockPos> stones = List.of(new BlockPos(-2, -60, 4), new BlockPos(-3, -60, 4), new BlockPos(-4, -60, 4));
		stones.forEach(pos -> setBlock(singleplayer, pos, Blocks.STONE.defaultBlockState()));
		waitUntilPlainBlocksPay(singleplayer);
		double paced = singleplayer.getServer().computeOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			double before = serverEarnedXp(player, TalentTree.GATHERER);
			check(player.gameMode.destroyBlock(stones.get(0)) && player.gameMode.destroyBlock(stones.get(1)), "The player should break both stones");
			return serverEarnedXp(player, TalentTree.GATHERER) - before;
		});
		checkNear(0.5, paced, "two stones broken in the same tick pay for one");
		waitUntilPlainBlocksPay(singleplayer);
		double later = singleplayer.getServer().computeOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			double before = serverEarnedXp(player, TalentTree.GATHERER);
			check(player.gameMode.destroyBlock(stones.get(2)), "The player should break the third stone");
			return serverEarnedXp(player, TalentTree.GATHERER) - before;
		});
		checkNear(0.5, later, "ten ticks later a stone pays again");

		// Crops: only a ripe one pays.
		BlockPos crop = new BlockPos(0, -60, 2);
		BlockState ground = singleplayer.getServer().computeOnServer(_ -> player(singleplayer).level().getBlockState(crop.below()));
		setBlock(singleplayer, crop.below(), Blocks.FARMLAND.defaultBlockState());
		holdInMainHand(context, singleplayer, ItemStack.EMPTY);
		double beforeCrops = earnedXp(singleplayer, TalentTree.GATHERER);

		setBlock(singleplayer, crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 1));
		harvest(context, singleplayer, crop);
		checkNear(beforeCrops, earnedXp(singleplayer, TalentTree.GATHERER), "young wheat earns nothing");

		setBlock(singleplayer, crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
		harvest(context, singleplayer, crop);
		checkNear(beforeCrops + 1.0, earnedXp(singleplayer, TalentTree.GATHERER), "a ripe wheat earns 1");
		setBlock(singleplayer, crop.below(), ground);
		discardOthers(singleplayer);

		// Sweet berries picked by hand (a right click, which leaves the bush): the crop XP and Bountiful Harvest III's
		// three extra berries on the bush's 1 or 2 at age 2.
		singleplayer.getServer().runOnServer(_ -> Talents.setLevel(player(singleplayer), Talent.BOUNTIFUL_HARVEST, 3));
		setBlock(singleplayer, crop, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 2));
		double beforeBerries = earnedXp(singleplayer, TalentTree.GATHERER);
		context.waitFor(client -> client.level.getBlockState(crop).is(Blocks.SWEET_BERRY_BUSH), 40);
		aimAt(context, new Vec3(crop.getX() + 0.5, crop.getY() + 0.4, crop.getZ() + 0.5));
		context.getInput().pressKey(options -> options.keyUse);
		singleplayer.getServer().waitFor(_ -> player(singleplayer).level().getBlockState(crop).getValue(SweetBerryBushBlock.AGE) == 1, 40);
		int berries = singleplayer.getServer().computeOnServer(_ -> itemsAround(singleplayer, crop, Items.SWEET_BERRIES));
		check(berries >= 1 + 3 && berries <= 2 + 3, "Picking at age 2 gives 1 or 2 berries, and Bountiful Harvest III adds 3: got " + berries);
		checkNear(beforeBerries + 1.0, earnedXp(singleplayer, TalentTree.GATHERER), "picking ripe berries by hand earns 1");
		setBlock(singleplayer, crop, Blocks.AIR.defaultBlockState());
		discardOthers(singleplayer);

		// One catch reeled in, through vanilla's own retrieve with a fish on the hook.
		double fished = singleplayer.getServer().computeOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			ItemStack rod = new ItemStack(Items.FISHING_ROD);
			player.setItemInHand(InteractionHand.MAIN_HAND, rod);
			FishingHook hook = new FishingHook(player, player.level(), 0, 0);
			setNibble(hook, 20);
			double before = serverEarnedXp(player, TalentTree.GATHERER);
			check(hook.retrieve(rod) > 0, "The hook should have reeled something in");
			return serverEarnedXp(player, TalentTree.GATHERER) - before;
		});
		checkNear(5.0, fished, "one catch reeled in earns 5");
		discardOthers(singleplayer);

		// Gatherer XP III: 30% more, so an ore pays 6.5.
		singleplayer.getServer().runOnServer(_ -> Talents.setLevel(player(singleplayer), Talent.GATHERER_XP, 3));
		holdInMainHand(context, singleplayer, new ItemStack(Items.DIAMOND_PICKAXE));
		setBlock(singleplayer, block, Blocks.COAL_ORE.defaultBlockState());
		double beforeTalent = earnedXp(singleplayer, TalentTree.GATHERER);
		mine(context, singleplayer, block);
		checkNear(beforeTalent + 6.5, earnedXp(singleplayer, TalentTree.GATHERER), "an ore with Gatherer XP III");
		holdInMainHand(context, singleplayer, ItemStack.EMPTY);
	}

	/** Puts a fish on the hook, as the hook's own countdown does when one bites. */
	private static void setNibble(FishingHook hook, int ticks) {
		try {
			Field nibble = FishingHook.class.getDeclaredField("nibble");
			nibble.setAccessible(true);
			nibble.setInt(hook, ticks);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Could not put a fish on the hook", e);
		}
	}

	/** Waits until a plain pickaxe block would pay this player again, 10 ticks after the last one that did. */
	private static void waitUntilPlainBlocksPay(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().waitFor(server -> {
			Long paid = player(singleplayer).getAttached(TreeXpAttachments.PAID_BLOCK_TICK);
			return paid == null || server.getTickCount() - paid >= TalentValues.GATHERER_BLOCK_XP_MIN_TICKS;
		}, 40);
	}

	/**
	 * Warrior XP from a sword hit on the client: exactly the health a husk lost to it (after the husk's armor), and
	 * nothing for hitting a pig. Damage the player is to blame for that isn't an attack (thorns, fire, an explosion)
	 * hurts the husk and earns nothing. A husk, since a zombie would burn in the daylight between the readings.
	 */
	private static void warriorXp(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, TalentData.EMPTY);
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

		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			Mob target = spawn(singleplayer, EntityTypes.HUSK, 3, 3);
			Map<String, DamageSource> notAttacks = Map.of(
					"thorns", player.damageSources().thorns(player),
					"fire the player started", player.damageSources().source(DamageTypes.IN_FIRE, player),
					"the player's explosion", player.damageSources().explosion(player, player));

			for (Map.Entry<String, DamageSource> entry : notAttacks.entrySet()) {
				double xp = serverEarnedXp(player, TalentTree.WARRIOR);
				double hurt = hurtFresh(target, entry.getValue(), 2.0F);
				check(hurt > 0.0, entry.getKey() + " should hurt the husk");
				checkNear(xp, serverEarnedXp(player, TalentTree.WARRIOR), entry.getKey() + " isn't an attack and earns nothing");
			}

			double xp = serverEarnedXp(player, TalentTree.WARRIOR);
			double hurt = hurtFresh(target, player.damageSources().playerAttack(player), 2.0F);
			checkNear(xp + hurt, serverEarnedXp(player, TalentTree.WARRIOR), "a punch, the control, earns the health it took");
			target.discard();
		});
	}

	/** Health a mob loses to one hit, out of its damage cooldown. Call on the server thread. */
	private static double hurtFresh(Mob mob, DamageSource source, float damage) {
		mob.damageCooldownTime = 0;
		float before = mob.getHealth();
		mob.hurtServer((ServerLevel) mob.level(), source, damage);
		return before - mob.getHealth();
	}

	/**
	 * Guardian XP from a husk with its AI on: it walks up and swings at the player. On easy its attack lands as
	 * min(damage / 2 + 1, damage), and Guardian pays 2 per point of it. Then the exclusions: fall damage pays nothing;
	 * armor and Block Chance don't make a hit pay less; a crowd swinging at a raised shield pays for one hit per damage
	 * cooldown.
	 */
	private static void guardianXp(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, TalentData.EMPTY);
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

		startSection(context, singleplayer, TalentData.EMPTY);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			double xp = serverEarnedXp(player, TalentTree.GUARDIAN);
			float health = player.getHealth();
			player.hurtServer(player.level(), player.damageSources().fall(), 4.0F);
			check(player.getHealth() < health, "The fall should hurt");
			checkNear(xp, serverEarnedXp(player, TalentTree.GUARDIAN), "fall damage isn't a mob's attack and earns nothing");

			// A 4-damage swing lands as 3 on easy: 6 Guardian XP, bare or in diamond armor with Stronger Armor V and
			// Block Chance V, whether the hit lands or is shrugged off.
			Mob attacker = spawn(singleplayer, EntityTypes.HUSK, 0, 3);
			xp = serverEarnedXp(player, TalentTree.GUARDIAN);
			guardianHits(player, attacker, 1);
			double bare = serverEarnedXp(player, TalentTree.GUARDIAN) - xp;
			checkNear(6.0, bare, "a 4-damage swing on easy, without armor");

			Talents.setData(player, Talents.data(player).with(Talent.STRONGER_ARMOR, 5).with(Talent.BLOCK_CHANCE, 5));

			for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
				player.setItemSlot(slot, new ItemStack(switch (slot) {
					case HEAD -> Items.DIAMOND_HELMET;
					case CHEST -> Items.DIAMOND_CHESTPLATE;
					case LEGS -> Items.DIAMOND_LEGGINGS;
					default -> Items.DIAMOND_BOOTS;
				}));
			}

			xp = serverEarnedXp(player, TalentTree.GUARDIAN);
			int shrugged = guardianHits(player, attacker, 100);
			check(shrugged >= 1, "Block Chance V should shrug off some of 100 hits, shrugged " + shrugged);
			checkNear(100 * bare, serverEarnedXp(player, TalentTree.GUARDIAN) - xp,
					"100 such swings at a player in armor with Block Chance (" + shrugged + " shrugged off) pay what they would bare");
			attacker.discard();
		});

		// Five husks swing at once at a raised shield. Shield Mastery III raises it at once.
		startSection(context, singleplayer, TalentData.EMPTY.with(Talent.SHIELD_MASTERY, 3));
		List<Mob> crowd = singleplayer.getServer().computeOnServer(_ -> {
			List<Mob> mobs = new ArrayList<>();

			for (int dx = -2; dx <= 2; dx++) {
				mobs.add(spawn(singleplayer, EntityTypes.HUSK, dx, 2));
			}

			player(singleplayer).setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
			return mobs;
		});
		double first = crowdOnShield(singleplayer, crowd);
		int firstTick = serverTick(singleplayer);
		checkNear(6.0, first, "five swings at a raised shield in one tick pay like one");
		waitServerTicks(singleplayer, 3);
		checkNear(0.0, crowdOnShield(singleplayer, crowd), "more swings within the damage cooldown pay nothing");
		singleplayer.getServer().waitFor(server -> server.getTickCount() >= firstTick + 10, 40);
		checkNear(6.0, crowdOnShield(singleplayer, crowd), "once the cooldown's first half is over, a swing pays again");
		singleplayer.getServer().runOnServer(_ -> {
			player(singleplayer).stopUsingItem();
			player(singleplayer).setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			crowd.forEach(Entity::discard);
		});
	}

	/**
	 * Swings from a mob at the player, each out of every damage cooldown (vanilla's and Guardian's), as if a second
	 * passed between them. Returns how many Block Chance shrugged off. Call on the server thread.
	 */
	private static int guardianHits(ServerPlayer player, Mob attacker, int hits) {
		int shrugged = 0;

		for (int i = 0; i < hits; i++) {
			player.removeAttached(TreeXpAttachments.GUARDIAN_COOLDOWN);
			player.damageCooldownTime = 0;
			player.setHealth(player.getMaxHealth());

			if (!player.hurtServer(player.level(), player.damageSources().mobAttack(attacker), 4.0F)) {
				shrugged++;
			}
		}

		player.setHealth(player.getMaxHealth());
		return shrugged;
	}

	/** Every mob of the crowd swings once at the player's raised shield, in one tick; returns the Guardian XP it paid. */
	private static double crowdOnShield(TestSingleplayerContext singleplayer, List<Mob> crowd) {
		return singleplayer.getServer().computeOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			// Facing the crowd, shield up.
			player.setYRot(0.0F);
			player.setYHeadRot(0.0F);

			if (!player.isUsingItem()) {
				player.startUsingItem(InteractionHand.OFF_HAND);
			}

			check(player.isBlocking(), "The shield should be raised at once with Shield Mastery III");
			float health = player.getHealth();
			double before = serverEarnedXp(player, TalentTree.GUARDIAN);

			for (Mob mob : crowd) {
				player.hurtServer(player.level(), player.damageSources().mobAttack(mob), 4.0F);
			}

			check(player.getHealth() == health, "The shield should take every swing, health went from " + health + " to " + player.getHealth());
			return serverEarnedXp(player, TalentTree.GUARDIAN) - before;
		});
	}

	/**
	 * Explorer XP. A teleport into a chunk the player has never been in pays the chunk and nothing for the distance. A
	 * push with no movement key held pays nothing. Walking with the forward key held pays a point per 25 blocks, plus a
	 * point for every new chunk on the way. The walk is on a lane cleared far from spawn, so every chunk on it is new
	 * and nothing the world generated stands in the way.
	 */
	private static void explorerXp(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, TalentData.EMPTY);
		double before = earnedXp(singleplayer, TalentTree.EXPLORER);
		singleplayer.getServer().runCommand("tp @p 1000.5 -60 8.5 -90 0");
		singleplayer.getConnection().waitForChunksRender();
		singleplayer.getServer().waitFor(_ -> visited(player(singleplayer)), 40);
		checkNear(before + 1.0, earnedXp(singleplayer, TalentTree.EXPLORER), "a teleport into a new chunk pays the chunk, not the distance");

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
		context.waitFor(client -> client.level.getBlockState(new BlockPos(1040, -61, 8)).is(Blocks.STONE), 40);

		// Pushed: moving, but not under the player's own power.
		before = earnedXp(singleplayer, TalentTree.EXPLORER);
		double pushStart = serverX(singleplayer);
		context.runOnClient(client -> client.player.setDeltaMovement(0.8, 0.0, 0.0));
		context.waitTick();
		waitUntilStill(context, singleplayer);
		double pushed = serverX(singleplayer) - pushStart;
		check(pushed > 0.5, "The push should have moved the player, moved " + pushed);
		checkNear(before, earnedXp(singleplayer, TalentTree.EXPLORER), "being pushed with no movement key held earns nothing");

		// Walking east with the forward key.
		context.getInput().lookAt(-90.0F, 0.0F);
		context.waitTick();
		before = earnedXp(singleplayer, TalentTree.EXPLORER);
		double startX = serverX(singleplayer);
		context.getInput().holdKeyFor(options -> options.keyUp, 100);
		waitUntilStill(context, singleplayer);
		double endX = serverX(singleplayer);
		double walked = endX - startX;
		int newChunks = (Mth.floor(endX) >> 4) - (Mth.floor(startX) >> 4);
		check(walked > 15.0, "Five seconds of walking should cover more than 15 blocks, walked " + walked);
		check(newChunks >= 1, "The walk should cross into a new chunk, from x " + startX + " to " + endX);
		double expected = walked * TalentValues.EXPLORER_XP_PER_BLOCK + newChunks * TalentValues.EXPLORER_XP_PER_NEW_CHUNK;
		double earned = earnedXp(singleplayer, TalentTree.EXPLORER) - before;
		check(Math.abs(earned - expected) <= 0.1, "Walking " + walked + " blocks through " + newChunks + " new chunks should earn about "
				+ expected + " Explorer XP, earned " + earned);
	}

	/** Whether the chunk the player stands in is among the ones Explorer has paid for. */
	private static boolean visited(ServerPlayer player) {
		ChunkPos chunk = player.chunkPosition();
		return player.getAttachedOrCreate(TreeXpAttachments.VISITED_CHUNKS).contains(player.level().dimension().identifier().toString(), chunk.x(), chunk.z());
	}

	/**
	 * Each tab shows its tree (a screenshot of each, in the middle of a game: some talents bought, one toggled off, some
	 * points to spend, the unfinished ones marked SOON). The pin button pins the active tab's tree, a second click
	 * unpins it, and the pin is saved in the client's own settings file. Once the screen is closed the pinned bar stays
	 * on the HUD, earning in another tree borrows the slot for a moment, and then the pinned bar is back.
	 */
	private static void tabsAndPin(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TalentRules rules = ImSoTalented.config().rules();
		startSection(context, singleplayer, TalentData.EMPTY
				.with(Talent.FASTER_MINING, 3).with(Talent.GATHERER_XP, 1).with(Talent.REACH, 1).with(Talent.CAREFUL_HANDS, 2).with(Talent.BACKPACK, 1)
				.withXp(TalentTree.GATHERER, rules.xpForLevel(10) + 40)
				.with(Talent.STRONGER_WEAPONS, 5).with(Talent.SWORD_SPECIALIST, 2).withXp(TalentTree.WARRIOR, rules.xpForLevel(8) + 15)
				.with(Talent.MORE_HEALTH, 2).with(Talent.STRONGER_ARMOR, 1).withXp(TalentTree.GUARDIAN, rules.xpForLevel(3) + 20)
				.with(Talent.FASTER_RUNNING, 2).with(Talent.HIGHER_JUMPING, 1).with(Talent.STEP_ASSIST, 1).withDisabled(Talent.HIGHER_JUMPING, true)
				.withXp(TalentTree.EXPLORER, rules.xpForLevel(4) + 30));
		context.runOnClient(_ -> TreeXpHud.setPinned(null));
		openTalents(context);
		int shot = 6;

		for (TalentTree tree : TalentTree.values()) {
			clickTab(context, tree);
			// The cursor off the tabs and nodes, so no tooltip covers the tree.
			moveCursor(context, new Vector2i(1, 1));
			screenshot(context, "ist_" + (shot < 10 ? "0" : "") + shot++ + "_tab_" + tree.id());
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
		screenshot(context, "ist_10_hud_pinned");

		singleplayer.getServer().runCommand("talents xp @p gatherer add 1");
		context.waitFor(_ -> TreeXpHud.shownTree() == TalentTree.GATHERER, 20);
		screenshot(context, "ist_11_hud_flash");
		context.waitFor(_ -> TreeXpHud.shownTree() == TalentTree.WARRIOR, TreeXpHud.FLASH_TICKS + 20);
	}

	/**
	 * Creative: talents are free (a buy with no points), no tree XP is earned (an ore mined), and toggles work as in
	 * survival (a right-click flips Higher Jumping off and on again, and the attribute follows).
	 */
	private static void creative(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, TalentData.EMPTY.with(Talent.FASTER_RUNNING, 1).with(Talent.HIGHER_JUMPING, 2));
		singleplayer.getServer().runCommand("gamemode creative @p");
		context.waitFor(client -> client.player.hasInfiniteMaterials(), 40);

		check(context.computeOnClient(client -> Talents.points(client.player, TalentTree.GATHERER)) == 0, "No Gatherer points");
		check(singleplayer.getServer().computeOnServer(_ -> Talents.canBuy(player(singleplayer), Talent.FASTER_MINING)) == Talents.BuyResult.BUYABLE,
				"The server should let a creative player buy without points");
		openTalents(context);
		clickTab(context, TalentTree.GATHERER);
		buy(context, singleplayer, Talent.FASTER_MINING, 1);
		buy(context, singleplayer, Talent.FASTER_MINING, 2);
		checkPoints(context, TalentTree.GATHERER, 0, "free talents spend nothing");

		// Toggles work in creative too.
		clickTab(context, TalentTree.EXPLORER);
		rightClick(context, Talent.HIGHER_JUMPING);
		singleplayer.getServer().waitFor(_ -> Talents.data(player(singleplayer)).isDisabled(Talent.HIGHER_JUMPING), 40);
		singleplayer.getServer().runOnServer(_ ->
				checkNear(TalentValues.BASE_JUMP_STRENGTH, player(singleplayer).getAttributeValue(Attributes.JUMP_STRENGTH), "toggled off in creative"));
		waitServerTicks(singleplayer, RequestThrottle.MIN_TICKS);
		context.waitFor(client -> Talents.data(client.player).isDisabled(Talent.HIGHER_JUMPING), 40);
		rightClick(context, Talent.HIGHER_JUMPING);
		singleplayer.getServer().waitFor(_ -> !Talents.data(player(singleplayer)).isDisabled(Talent.HIGHER_JUMPING), 40);
		singleplayer.getServer().runOnServer(_ -> checkNear(TalentValues.BASE_JUMP_STRENGTH + TalentValues.jumpStrengthBonus(2),
				player(singleplayer).getAttributeValue(Attributes.JUMP_STRENGTH), "toggled on again in creative"));
		closeTalents(context);

		// No tree XP in creative.
		BlockPos ore = new BlockPos(0, -59, 2);
		holdInMainHand(context, singleplayer, new ItemStack(Items.DIAMOND_PICKAXE));
		setBlock(singleplayer, ore, Blocks.COAL_ORE.defaultBlockState());
		double before = earnedXp(singleplayer, TalentTree.GATHERER);
		mine(context, singleplayer, ore);
		checkNear(before, earnedXp(singleplayer, TalentTree.GATHERER), "an ore mined in creative earns nothing");
		singleplayer.getServer().runCommand("gamemode survival @p");
		context.waitFor(client -> !client.player.hasInfiniteMaterials(), 40);
	}

	/**
	 * /talents for operators: list, set (clamped to the top level), toggle (flipped twice, an unowned talent reported to
	 * the operator only, a non-toggleable or unfinished one refused, and only toggleable working talents suggested), xp
	 * (set clamped to the top, add at the top refused, a huge add giving only what's left, add 0 not parsed) and reset
	 * (talents, XP, the XP ledger and the chunks Explorer paid for, so the chunk underfoot pays again). Each returns how
	 * many players it changed.
	 */
	private static void commands(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		TalentRules rules = ImSoTalented.config().rules();
		long gathererTop = rules.xpForLevel(TalentTree.GATHERER.maxLevel());
		startSection(context, singleplayer, TalentData.EMPTY.with(Talent.FASTER_MINING, 2).with(Talent.MORE_HEALTH, 1).with(Talent.HIGHER_JUMPING, 2)
				.withXp(TalentTree.GATHERER, rules.xpForLevel(3)));

		CommandOutcome list = command(singleplayer, "talents list @p");
		check(list.result() == 5, "list returns the talent points spent, got " + list.result());
		String listed = list.text("commands.imsotalented.list");
		check(listed.contains("Gatherer: level 3 (" + rules.xpForLevel(3) + " XP), 1 talent points to spend") && listed.contains("Faster Mining: 2/5")
				&& listed.contains("More Health: 1/5"), "Unexpected list:\n" + listed);

		CommandOutcome set = command(singleplayer, "talents set @p faster_mining 99");
		check(set.result() == 1 && serverLevel(singleplayer, Talent.FASTER_MINING) == 5, "set above the top level should set the top level");
		check(((Number) set.arg("commands.imsotalented.set.single", 1)).intValue() == 5, "and say so");

		// Without on/off, toggle flips.
		CommandOutcome off = command(singleplayer, "talents toggle @p higher_jumping");
		check(off.result() == 1 && isDisabled(singleplayer, Talent.HIGHER_JUMPING), "The first flip turns it off");
		check(off.argKey("commands.imsotalented.toggle.single", 1).equals("commands.imsotalented.off"), "and says off");
		CommandOutcome on = command(singleplayer, "talents toggle @p higher_jumping");
		check(on.result() == 1 && !isDisabled(singleplayer, Talent.HIGHER_JUMPING), "The second flip turns it on again");
		check(on.argKey("commands.imsotalented.toggle.single", 1).equals("commands.imsotalented.on"), "and says on");

		// A talent the target doesn't own: the operator hears why, the target sees nothing.
		OVERLAY.clear();
		CommandOutcome unowned = command(singleplayer, "talents toggle @p step_assist on");
		check(unowned.result() == 0 && unowned.says("commands.imsotalented.toggle.not_owned"), "An unowned talent should be a failure for the operator");
		sentinelOverlay(context, singleplayer);
		check(OVERLAY.stream().noneMatch(line -> find(line, "message.imsotalented.toggle_not_owned") != null),
				"The target's action bar should say nothing about a command's toggle");
		check(command(singleplayer, "talents toggle @p faster_mining off").error() != null, "Faster Mining has no toggle");
		check(command(singleplayer, "talents toggle @p night_vision on").error() != null, "Night Vision isn't in the game yet, so no toggle");
		Set<String> suggested = new HashSet<>(suggestions(singleplayer, "talents toggle @p "));
		check(suggested.equals(Set.of("faster_running", "higher_jumping", "step_assist", "swim_speed", "safe_falling")),
				"toggle should suggest the toggleable talents that work, got " + suggested);

		CommandOutcome setXp = command(singleplayer, "talents xp @p gatherer set " + Long.MAX_VALUE);
		check(setXp.result() == 1 && treeXp(singleplayer, TalentTree.GATHERER) == gathererTop, "xp set past the top should set the top");
		CommandOutcome capped = command(singleplayer, "talents xp @p gatherer add 5");
		check(capped.result() == 0 && capped.says("commands.imsotalented.xp_add.capped"), "xp add at the top should fail as capped");
		check(treeXp(singleplayer, TalentTree.GATHERER) == gathererTop, "and change nothing");

		command(singleplayer, "talents xp @p gatherer set " + (gathererTop - 10));
		CommandOutcome rest = command(singleplayer, "talents xp @p gatherer add " + Long.MAX_VALUE);
		check(rest.result() == 1 && treeXp(singleplayer, TalentTree.GATHERER) == gathererTop, "A huge xp add should stop at the top, never wrap around");
		check(((Number) rest.arg("commands.imsotalented.xp_add.single", 0)).longValue() == 10L, "and report the 10 that went in");
		check(command(singleplayer, "talents xp @p gatherer add 0").error() != null, "xp add 0 shouldn't parse");
		check(command(singleplayer, "talents set @p no_such_talent 1").error() != null, "An unknown talent should be an error");
		check(command(singleplayer, "talents xp @p combat add 5").error() != null, "An unknown tree should be an error");

		// Reset: a toggle, XP, pending XP and the chunk underfoot, all forgotten.
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(singleplayer);
			Talents.setData(player, Talents.data(player).withDisabled(Talent.HIGHER_JUMPING, true).withXp(TalentTree.EXPLORER, 50L));
			Talents.ledger(player).credit(TalentTree.GATHERER, 0.5, server.getTickCount());
			check(visited(player), "The chunk at home was paid for long ago");

			CommandOutcome reset = execute(server, "talents reset @p");
			check(reset.result() == 1 && reset.says("commands.imsotalented.reset.single"), "reset should change one player");
			check(Talents.data(player).equals(TalentData.EMPTY), "Nothing is left after a reset: " + Talents.data(player));
			check(player.getAttached(TreeXpAttachments.VISITED_CHUNKS) == null, "The chunks Explorer paid for are forgotten");
			check(player.getAttached(ImSoTalented.TREE_XP_LEDGER) == null, "So is the XP ledger");
		});
		singleplayer.getServer().waitFor(_ -> earnedXp0(singleplayer, TalentTree.EXPLORER) > 0.0, 40);
		waitServerTicks(singleplayer, 2);
		checkNear(TalentValues.EXPLORER_XP_PER_NEW_CHUNK, earnedXp(singleplayer, TalentTree.EXPLORER), "the chunk underfoot pays again after a reset, once");
		checkNear(0.0, earnedXp(singleplayer, TalentTree.GATHERER), "the pending Gatherer XP is gone");
	}

	/** /talents max: health, armor, speed, jumping, falling, step height and reach are attribute modifiers. */
	private static void attributesAtTheTopLevel(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, TalentData.EMPTY);
		CommandOutcome max = command(singleplayer, "talents max @p");
		check(max.result() == 1 && max.says("commands.imsotalented.max.single"), "max should change one player");
		singleplayer.getServer().runOnServer(_ -> player(singleplayer).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE)));
		context.waitFor(client -> Talents.level(client.player, Talent.MORE_HEALTH) == 5, 40);
		// The chestplate's armor counts from the next tick, when the server notices the new equipment.
		singleplayer.getServer().waitFor(_ -> player(singleplayer).getAttributeValue(Attributes.ARMOR) > 0.0, 40);
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
		screenshot(context, "ist_12_maxed_tree");
		closeTalents(context);
	}

	/**
	 * Toggleable talents: /talents toggle off removes the attribute modifier, on re-adds it, and a right-click in the
	 * Explorer tab flips the state through the packet round trip.
	 */
	private static void toggling(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, maxedTalents());
		singleplayer.getServer().runOnServer(_ ->
				checkNear(0.42 + 0.15, player(singleplayer).getAttributeValue(Attributes.JUMP_STRENGTH), "jump strength at level 5"));

		singleplayer.getServer().runCommand("talents toggle @p higher_jumping off");
		singleplayer.getServer().runOnServer(_ ->
				checkNear(0.42, player(singleplayer).getAttributeValue(Attributes.JUMP_STRENGTH), "toggling off removes the jump bonus"));

		singleplayer.getServer().runCommand("talents toggle @p higher_jumping on");
		singleplayer.getServer().runOnServer(_ ->
				checkNear(0.42 + 0.15, player(singleplayer).getAttributeValue(Attributes.JUMP_STRENGTH), "toggling on puts the jump bonus back"));

		// The right-click path: open the Explorer tab, right-click the node, and the packet round trip must flip it.
		openTalents(context);
		clickTab(context, TalentTree.EXPLORER);
		rightClick(context, Talent.HIGHER_JUMPING);
		singleplayer.getServer().waitFor(_ -> Talents.data(player(singleplayer)).isDisabled(Talent.HIGHER_JUMPING), 40);
		context.waitFor(client -> Talents.data(client.player).isDisabled(Talent.HIGHER_JUMPING), 40);
		singleplayer.getServer().runOnServer(_ ->
				checkNear(0.42, player(singleplayer).getAttributeValue(Attributes.JUMP_STRENGTH), "the right-click toggle removes the jump bonus"));
		screenshot(context, "ist_13_toggle_off");
		waitServerTicks(singleplayer, RequestThrottle.MIN_TICKS);

		rightClick(context, Talent.HIGHER_JUMPING);
		singleplayer.getServer().waitFor(_ -> !Talents.data(player(singleplayer)).isDisabled(Talent.HIGHER_JUMPING), 40);
		singleplayer.getServer().runOnServer(_ ->
				checkNear(0.42 + 0.15, player(singleplayer).getAttributeValue(Attributes.JUMP_STRENGTH), "the second right-click flips it back on"));
		closeTalents(context);
	}

	/**
	 * The farming talents, each against a vanilla control:
	 * <ul>
	 * <li><b>Bountiful Harvest</b>: 40 loot rolls of ripe wheat at level 3 give exactly 4 wheat each, a no-entity roll
	 *     and a talent-0 player each give the vanilla 1, young wheat (seeds, not wheat) gets nothing, and a young carrot
	 *     (which drops a carrot) gets no bonus and no XP.</li>
	 * <li><b>Fast Growth</b>: only the seven crops, short of ripe, can be sped up; two identical 60-stalk wheat plots at
	 *     age 0, one within 16 blocks of the player (Fast Growth III in reach) and one 63 blocks away (the control), each
	 *     stalk forced through 26 of the exact {@code randomTick} rolls the random-tick system makes, and the in-range
	 *     plot's mean age must be at least 1.5x the control's (~2.5x expected).</li>
	 * <li><b>Careful Hands</b>: durability changes on a pickaxe (made sturdy enough not to break): with Careful Hands
	 *     III each point lands 1 time in 4, as with Unbreaking III; gear Unbreaking I with talent III counts as III under
	 *     the default {@code highest} rule, and the gear's own enchantment is left as it was; under {@code add} gear III
	 *     with talent III counts as VI (1 in 7); without the talent every point lands.</li>
	 * </ul>
	 */
	private static void farming(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, maxedTalents());
		bountifulHarvest(singleplayer);
		fastGrowth(singleplayer);
		carefulHands(singleplayer);
	}

	/** Bountiful Harvest: exactly the level's extra copies of the main item, only for a ripe harvest that produced it. */
	private static void bountifulHarvest(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			ServerLevel level = player.level();
			BlockState ripe = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE);
			BlockState young = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 1);
			BlockPos pos = player.blockPosition();
			ItemStack tool = new ItemStack(Items.IRON_HOE);

			Talents.setLevel(player, Talent.BOUNTIFUL_HARVEST, 3);
			int withTalent = 0;
			for (int i = 0; i < 40; i++) {
				withTalent += countOf(Block.getDrops(ripe, level, pos, null, player, tool), Items.WHEAT);
			}
			check(withTalent == 40 * 4, "Bountiful Harvest III should give exactly 4 wheat per ripe harvest, got " + withTalent + " from 40");

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
			check(noTalent == 40, "A talent-less player should keep the vanilla 1 wheat per ripe harvest, got " + noTalent + " from 40");

			// Young wheat drops seeds, not wheat: no main item produced, so nothing to increase.
			Talents.setLevel(player, Talent.BOUNTIFUL_HARVEST, 3);
			int youngWheat = 0;
			int youngSeeds = 0;
			for (int i = 0; i < 40; i++) {
				List<ItemStack> drops = Block.getDrops(young, level, pos, null, player, tool);
				youngWheat += countOf(drops, Items.WHEAT);
				youngSeeds += countOf(drops, Items.WHEAT_SEEDS);
			}
			check(youngWheat == 0, "Young wheat produces seeds, not wheat: the bonus must not conjure the main item, got " + youngWheat + " wheat from 40");
			check(youngSeeds >= 40, "Young wheat should still drop its vanilla seed, got " + youngSeeds + " seeds from 40");

			// A just-planted carrot drops a carrot: no bonus (plant and break over and over for nothing) and no XP.
			Talents.setXp(player, TalentTree.GATHERER, 0L);
			Talents.setLevel(player, Talent.GATHERER_XP, 0);
			double xp = serverEarnedXp(player, TalentTree.GATHERER);
			int youngCarrots = 0;
			for (int i = 0; i < 40; i++) {
				youngCarrots += countOf(Block.getDrops(Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 3), level, pos, null, player, tool), Items.CARROT);
			}
			check(youngCarrots == 40, "A young carrot should drop its vanilla 1 carrot and no bonus, got " + youngCarrots + " from 40");
			checkNear(xp, serverEarnedXp(player, TalentTree.GATHERER), "young carrots earn no crop XP");
			countOf(Block.getDrops(Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE), level, pos, null, player, tool), Items.CARROT);
			checkNear(xp + 1.0, serverEarnedXp(player, TalentTree.GATHERER), "a ripe carrot earns 1");

			// Fast Growth's crops: the seven, short of ripe. Torchflowers are crops in code, but not one of them.
			check(Crops.canGrow(Blocks.WHEAT.defaultBlockState()), "Young wheat can grow faster");
			check(!Crops.canGrow(ripe), "Ripe wheat has nothing left to grow");
			check(!Crops.canGrow(Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.AGE, CocoaBlock.MAX_AGE)), "A ripe cocoa pod neither");
			check(Crops.canGrow(Blocks.SWEET_BERRY_BUSH.defaultBlockState()), "A berry bush is one of the seven");
			check(!Crops.canGrow(Blocks.TORCHFLOWER_CROP.defaultBlockState()), "Torchflowers aren't one of the seven");
			check(!Crops.canGrow(Blocks.MELON_STEM.defaultBlockState()), "Nor are melon stems");
		});
	}

	/** Fast Growth: the in-reach plot grows at least 1.5x the 63-blocks-away control (~2.5x expected). */
	private static void fastGrowth(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			ServerLevel level = player.level();
			Talents.setLevel(player, Talent.FAST_GROWTH, 3);

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

	/**
	 * Careful Hands: a durability change lands as Unbreaking at the level the stacking rule gives, a point at a time
	 * with chance 1 / (level + 1) for a tool.
	 */
	private static void carefulHands(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			ServerLevel level = player.level();
			Holder<Enchantment> unbreaking = level.registryAccess().getOrThrow(Enchantments.UNBREAKING);

			// Control: no talent, no Unbreaking on the gear: every point lands.
			Talents.setLevel(player, Talent.CAREFUL_HANDS, 0);
			int control = damageTaken(level, player, sturdyPickaxe(), 1200);
			check(control == 1200, "Without the talent or Unbreaking, all 1200 points should land, got " + control);

			// Careful Hands III on bare gear: the item works as if Unbreaking III, so 1 point in 4 lands.
			Talents.setLevel(player, Talent.CAREFUL_HANDS, 3);
			checkBinomial(damageTaken(level, player, sturdyPickaxe(), 1200), 1200, 1.0 / 4.0, "Careful Hands III on bare gear (Unbreaking III)");

			// The default rule: gear Unbreaking I + talent III = the higher, III; the gear's real component is restored.
			ItemStack geared = sturdyPickaxe();
			geared.enchant(unbreaking, 1);
			checkBinomial(damageTaken(level, player, geared, 1200), 1200, 1.0 / 4.0, "Unbreaking I gear with Careful Hands III (highest: III)");
			ItemEnchantments gearEnc = geared.getOrDefault(EnchantmentHelper.getComponentType(geared), ItemEnchantments.EMPTY);
			check(gearEnc.getLevel(unbreaking) == 1,
					"The gear's real Unbreaking I must be restored after the virtual level is removed, got " + gearEnc.getLevel(unbreaking));

			// The add rule: gear Unbreaking III + talent III = VI, so 1 point in 7 lands.
			setRules(new TalentRules(TalentRules.DEFAULTS.costBase(), TalentRules.DEFAULTS.costGrowth(), EnchantmentStacking.ADD));
			ItemStack strong = sturdyPickaxe();
			strong.enchant(unbreaking, 3);
			int added = damageTaken(level, player, strong, 12000);
			setRules(TalentRules.DEFAULTS);
			checkBinomial(added, 12000, 1.0 / 7.0, "Unbreaking III gear with Careful Hands III added up (Unbreaking VI)");
		});
	}

	/** A diamond pickaxe that no test can wear out, so every point it takes can be counted. */
	private static ItemStack sturdyPickaxe() {
		ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
		stack.set(DataComponents.MAX_DAMAGE, 1_000_000);
		return stack;
	}

	/** Runs one durability change of {@code amount} points through the player's path; returns the points that landed. */
	private static int damageTaken(ServerLevel level, ServerPlayer player, ItemStack stack, int amount) {
		int before = stack.getDamageValue();
		stack.hurtAndBreak(amount, level, player, _ -> {
		});
		return stack.getDamageValue() - before;
	}

	/**
	 * The mobility talents, each against a vanilla control:
	 * <ul>
	 * <li><b>Step Assist</b>: 26.3 reads the step-up height off the STEP_HEIGHT attribute (vanilla 0.6), and the
	 *     player's own {@code maxUpStep()}, the accessor the step-up logic in {@code Entity.collide} uses, returns that
	 *     value: 1.0 with the talent's one level, 0.6 without. And for real: walking north with the forward key into a
	 *     full block, the player ends up on top of it with the talent and stays in front of it without.</li>
	 * <li><b>Reach</b>: 26.3's {@code blockInteractionRange()} and {@code entityInteractionRange()} are pure reads of the
	 *     BLOCK_INTERACTION_RANGE and ENTITY_INTERACTION_RANGE attributes (vanilla 4.5/3.0), so the additive rows are
	 *     asserted through the attributes and the accessors at level 3 (6.0/3.5) and a level-0 control (4.5/3.0).</li>
	 * <li><b>Swim Speed</b>: 26.3 has no swim-speed attribute (WATER_MOVEMENT_EFFICIENCY only slows), so the effect
	 *     softens the per-tick underwater damping in {@code travelInWater} (0.8) to {@code 1 - (1 - 0.8)/(1 + 0.1*level)},
	 *     which multiplies the sustained in-water movement by exactly 1 + 0.1 per level. Measured functionally: a 16x4x16
	 *     water volume at the test site, the player given the same initial horizontal velocity (0.4) in fresh lanes and
	 *     left to coast for 60 ticks: the level-3 coast must cover at least 20% more ground than the level-0 control
	 *     (~30% expected), and the toggled-off coast must match the control (the effective level the mixin reads is 0
	 *     while off).</li>
	 * </ul>
	 */
	private static void mobility(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, maxedTalents());
		// STEP ASSIST: the attribute row and the public step-up accessor, then a real walk up a block.
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			checkNear(1.0, player.getAttributeValue(Attributes.STEP_HEIGHT), "step height attribute with Step Assist (0.6 + 0.4)");
			checkNear(1.0, player.maxUpStep(), "the step-up accessor the collide logic uses, with Step Assist");
		});
		check(walkIntoStep(context, singleplayer) > -59.05, "With Step Assist the player should walk up onto the block");

		singleplayer.getServer().runCommand("talents set @p step_assist 0");
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			checkNear(0.6, player.getAttributeValue(Attributes.STEP_HEIGHT), "level 0 restores the vanilla 0.6 step height");
			checkNear(0.6, player.maxUpStep(), "the vanilla step-up accessor at level 0");
		});
		context.waitFor(client -> Talents.level(client.player, Talent.STEP_ASSIST) == 0, 40);
		check(walkIntoStep(context, singleplayer) < -59.5, "Without Step Assist a full block should stop the player");

		// REACH: level-0 control first (the survival baselines), then level 3.
		singleplayer.getServer().runCommand("talents set @p reach 0");
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			checkNear(4.5, player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), "the vanilla 4.5 block interaction range");
			checkNear(3.0, player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), "the vanilla 3.0 entity interaction range");
		});
		singleplayer.getServer().runCommand("talents set @p reach 3");
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			checkNear(4.5 + 1.5, player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE), "block range 4.5 + 1.5 at level 3");
			checkNear(4.5 + 1.5, player.blockInteractionRange(), "the accessor block breaking and item use go through");
			checkNear(3.0 + 0.5, player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), "entity range 3.0 + 0.5 at level 3");
			checkNear(3.0 + 0.5, player.entityInteractionRange(), "the accessor attacks and item use against entities go through");
		});

		// SWIM SPEED: the 60-tick coasts: level-0 control, talent, toggled-off. The pool is built as a sealed box
		// (plugged floor, stone walls on all four sides) so the source water can neither drain through the floor nor
		// flow out into the test site; every cell it covers is snapshotted and restored exactly on cleanup.
		Map<BlockPos, BlockState> floor = singleplayer.getServer().computeOnServer(_ -> makePoolFloor(player(singleplayer).level()));
		singleplayer.getServer().runOnServer(_ -> fillSwimPool(player(singleplayer).level()));
		context.waitFor(client -> client.level.getBlockState(new BlockPos(3, -60, 2)).is(Blocks.WATER), 40);
		singleplayer.getServer().runCommand("talents set @p swim_speed 0");
		context.waitFor(client -> Talents.level(client.player, Talent.SWIM_SPEED) == 0, 40);
		double control = swimCoast(context, singleplayer, 2.0);
		check(control > 1.0, "The level-0 control should coast a couple of blocks in water, got " + control);

		singleplayer.getServer().runCommand("talents set @p swim_speed 3");
		context.waitFor(client -> Talents.level(client.player, Talent.SWIM_SPEED) == 3, 40);
		double talented = swimCoast(context, singleplayer, 4.0);
		check(talented >= 1.2 * control,
				"Swim Speed III should coast at least 20% farther than the control (~30% expected), got " + talented + " vs " + control);

		// The toggle: level bought, talent off: the effective level is 0, and the coast must match the control.
		singleplayer.getServer().runCommand("talents toggle @p swim_speed off");
		context.waitFor(client -> Talents.data(client.player).isDisabled(Talent.SWIM_SPEED), 40);
		double toggledOff = swimCoast(context, singleplayer, 6.0);
		check(Math.abs(toggledOff - control) <= 0.05 * control,
				"A toggled-off Swim Speed must coast like the level-0 control, got " + toggledOff + " vs " + control);
		singleplayer.getServer().runOnServer(_ ->
				check(Talents.data(player(singleplayer)).effectiveLevel(Talent.SWIM_SPEED) == 0,
						"the effective level the mixin reads is 0 while the talent is toggled off"));

		singleplayer.getServer().runCommand("talents toggle @p swim_speed on");
		singleplayer.getServer().runOnServer(_ ->
				check(Talents.data(player(singleplayer)).effectiveLevel(Talent.SWIM_SPEED) == 3,
						"toggling back on restores the effective level"));
		// Dry off: water out, the floor and walls back as they were, the player at home on dry land, no residual
		// velocity. The pool starts two blocks east of home: the client can hold a stale water update for a cell while
		// a big block change is in flight, and the integrated client drives the player, so a player standing in such a
		// cell would drift with no input.
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			clearSwimPool(player.level(), floor);
			player.setDeltaMovement(0, 0, 0);
		});
		context.runOnClient(client -> client.player.setDeltaMovement(0, 0, 0));
		context.waitFor(client -> client.level.getBlockState(new BlockPos(3, -60, 2)).isAir(), 40);
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
		context.waitFor(client -> Math.abs(client.player.getZ() - 0.5) < 1e-3 && client.player.onGround(), 40);
		context.getInput().lookAt(180.0F, 0.0F);
		context.getInput().holdKeyFor(options -> options.keyUp, 20);
		waitUntilStill(context, singleplayer);
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
		context.waitFor(client -> client.level.getBlockState(new BlockPos(0, -60, -2)).isAir() && Math.abs(client.player.getZ() - 0.5) < 1e-3, 40);
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
	 * Builds the pool's containment: the floor row (y = -61, x/z 2..17) plus solid stone walls on all four sides of the
	 * water volume (west x=1, east x=18, north z=-1, south z=16, each y -60..-57, corners included). Without the walls
	 * the source water flows out of the open sides and into the player's home cells. Every cell is snapshotted and
	 * restored on cleanup.
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

	/** Fills the 16x4x16 still-water pool at (x 2..17, y -60..-57, z 0..15), two blocks east of home. */
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
	 * One 60-tick coast run in the pool: a fresh tp into the water lane at z = {@code laneZ} (which also resets any
	 * residual velocity), the same initial horizontal push (+x, 0.4 blocks/tick) set on both sides (the integrated
	 * singleplayer simulates the player on the client and the server, and both must get the push so neither side's
	 * position is corrected back), then 60 ticks of free coast with no input. The lane's x = 3.5 sits 1.5 blocks in from
	 * the pool's west edge, so the whole coast (~2.6 blocks at the level-3 factor) stays inside the water. Returns the
	 * horizontal ground covered, read from the server player.
	 */
	private static double swimCoast(ClientGameTestContext context, TestSingleplayerContext singleplayer, double laneZ) {
		singleplayer.getServer().runCommand("tp @p 3.5 -60 " + laneZ);
		context.waitFor(client -> Math.abs(client.player.getZ() - laneZ) < 1e-3 && Math.abs(client.player.getX() - 3.5) < 1e-3, 40);
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

	/** Faster Mining V mines like Efficiency V, on the client and the server alike; the stacking setting decides how it meets real Efficiency. */
	private static void miningSpeed(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, maxedTalents());
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
		context.waitFor(client -> Talents.level(client.player, Talent.FASTER_MINING) == 0, 40);
		checkDestroySpeed(context, singleplayer, stone, STONE_PICKAXE_SPEED + 10, "Efficiency III without the talent");
		holdInMainHand(context, singleplayer, new ItemStack(Items.DIAMOND_PICKAXE));
		checkDestroySpeed(context, singleplayer, stone, STONE_PICKAXE_SPEED, "a plain pickaxe without the talent");
	}

	/** Where the test vein starts: the ore in front of home at eye level. It runs four coal ores away from the player. */
	private static final BlockPos VEIN_START = new BlockPos(0, -59, 2);
	/**
	 * Where the player mines the vein from: four blocks back from its first ore, facing it. Quarry drops land at the
	 * player's feet (z -1.5) and get picked up, while the coal a plain break scatters around its ore (up to about 0.75
	 * blocks from z 2.5) stays out of the player's reach, so the two are told apart by {@link #QUARRY_DROP_LINE}.
	 */
	private static final String QUARRY_SPOT = "tp @p 0.5 -60 -1.5 0 0";
	private static final double QUARRY_DROP_LINE = 0.75;

	/**
	 * Quarrying, sneaking (the sneak key held on the client) while an ore is mined. Level 1 takes the connected ore of the
	 * same kind within the 3x3x3 cube around it: of the line of four coal ores, the first two; the iron ore and the stone
	 * beside it stay. Without sneaking only the mined ore goes, and its coal drops at the ore. The top level takes the
	 * whole line, its last ore a deepslate coal ore (the same kind of ore, another block). Every ore broken pays Gatherer
	 * XP, and every drop lands at the player's feet. A pickaxe with 3 durability left takes two ores and stops at 1,
	 * still in hand, and says so. A blob of 300 ores stops at 256 and says so. Careful Hands, More Luck and Gatherer XP
	 * are off here, so every ore wears the tool by exactly 1, drops exactly 1 coal and pays exactly 5.
	 */
	private static void quarrying(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// A Gatherer tree with room to earn, the talents that would blur the counts off.
		startSection(context, singleplayer, maxedTalents().with(Talent.CAREFUL_HANDS, 0).with(Talent.MORE_LUCK, 0).with(Talent.GATHERER_XP, 0)
				.with(Talent.QUARRYING, 1).withXp(TalentTree.GATHERER, 0L));

		check(quarry(context, singleplayer, true, new ItemStack(Items.DIAMOND_PICKAXE)) == 2,
				"Quarrying I should take the mined ore and the one next to it in the 3x3x3 cube");

		singleplayer.getServer().runCommand("talents set @p quarrying " + Talent.QUARRYING.maxLevel());
		context.waitFor(client -> Talents.level(client.player, Talent.QUARRYING) == Talent.QUARRYING.maxLevel(), 40);
		check(quarry(context, singleplayer, false, new ItemStack(Items.DIAMOND_PICKAXE)) == 1, "Without sneaking only the mined ore goes");
		check(quarry(context, singleplayer, true, new ItemStack(Items.DIAMOND_PICKAXE)) == 4,
				"The top level should take the whole vein, the deepslate coal ore at its end too");

		ItemStack worn = new ItemStack(Items.DIAMOND_PICKAXE);
		worn.setDamageValue(worn.getMaxDamage() - 3);
		OVERLAY.clear();
		check(quarry(context, singleplayer, true, worn) == 2, "A pickaxe with 3 durability left should take two ores and stop");
		singleplayer.getServer().runOnServer(_ -> {
			ItemStack held = player(singleplayer).getMainHandItem();
			check(held.is(Items.DIAMOND_PICKAXE) && held.getMaxDamage() - held.getDamageValue() == 1,
					"Quarrying should leave the pickaxe in hand with 1 durability, got " + held);
		});
		waitForOverlay(context, "message.imsotalented.quarry_stopped_tool");
		clearVein(singleplayer);

		quarryCap(context, singleplayer);
	}

	/**
	 * A 300-ore blob (10 x 6 x 5) mined at the top level: the first ore and its 255 nearest neighbours go, all the coal
	 * lands at the player's feet, every ore pays, and the action bar says where it stopped.
	 */
	private static void quarryCap(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		BlockPos from = VEIN_START.offset(-4, -1, 0);
		BlockPos to = VEIN_START.offset(5, 4, 4);
		discardOthers(singleplayer);
		singleplayer.getServer().runCommand("clear @p");
		singleplayer.getServer().runCommand(QUARRY_SPOT);
		singleplayer.getServer().runOnServer(_ -> {
			for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
				player(singleplayer).level().setBlockAndUpdate(pos, Blocks.COAL_ORE.defaultBlockState());
			}
		});
		holdInMainHand(context, singleplayer, new ItemStack(Items.DIAMOND_PICKAXE));
		double before = earnedXp(singleplayer, TalentTree.GATHERER);
		OVERLAY.clear();
		context.getInput().holdKey(options -> options.keyShift);
		singleplayer.getServer().waitFor(_ -> player(singleplayer).isShiftKeyDown(), 20);
		context.waitFor(client -> client.player.isCrouching(), 20);
		mine(context, singleplayer, VEIN_START);
		context.getInput().releaseKey(options -> options.keyShift);
		singleplayer.getServer().waitFor(_ -> !player(singleplayer).isShiftKeyDown(), 20);
		String stopped = waitForOverlay(context, "message.imsotalented.quarry_stopped_cap");
		check(stopped.contains("256"), "The action bar should say the quarry stopped at 256 blocks: " + stopped);

		singleplayer.getServer().waitFor(_ -> player(singleplayer).getInventory().countItem(Items.COAL) == 256, 100);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			int broken = 0;

			for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
				broken += player.level().getBlockState(pos).isAir() ? 1 : 0;
			}

			check(broken == TalentValues.QUARRY_MAX_BLOCKS, "One break should take 256 blocks of the 300, took " + broken);
			checkNear(before + 256 * 5.0, serverEarnedXp(player, TalentTree.GATHERER), "every one of the 256 ores pays");
			check(player.level().getEntitiesOfClass(ItemEntity.class, new AABB(Vec3.atCenterOf(from), Vec3.atCenterOf(to)).inflate(4.0)).isEmpty(),
					"No coal should be left by the blob: it all went to the player's feet");

			for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
				player.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			}

			// The grass under the blob, which turns to dirt under ore, back as it was.
			for (BlockPos pos : BlockPos.betweenClosed(from.below(), to.atY(from.getY() - 1))) {
				player.level().setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
			}
		});
	}

	/** Removes what is left of the test vein. */
	private static void clearVein(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(_ -> {
			ServerLevel level = player(singleplayer).level();

			for (int x = -1; x <= 1; x++) {
				for (int z = 2; z <= 5; z++) {
					level.setBlockAndUpdate(new BlockPos(x, -59, z), Blocks.AIR.defaultBlockState());
				}
			}
		});
	}

	/**
	 * Lays the test vein (three coal ores and a deepslate coal ore in a line away from home, an iron ore and a stone
	 * beside the first), mines its first ore from the client with this tool, sneaking or not, and returns how many coal
	 * ores broke. Checks what every break must do: the iron ore and the stone stay, every broken ore paid its Gatherer XP,
	 * and every coal is at the player's feet (picked up or on the ground there), none left at the ores, unless the player
	 * wasn't sneaking: then the one coal drops at its ore, as in vanilla.
	 */
	private static int quarry(ClientGameTestContext context, TestSingleplayerContext singleplayer, boolean sneaking, ItemStack tool) {
		discardOthers(singleplayer);
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
			context.waitFor(client -> client.player.isCrouching(), 20);
		}

		mine(context, singleplayer, VEIN_START);

		if (sneaking) {
			context.getInput().releaseKey(options -> options.keyShift);
			singleplayer.getServer().waitFor(_ -> !player(singleplayer).isShiftKeyDown(), 20);
		}

		// The coal at the player's feet is picked up; the coal a plain break leaves at its ore lies on the ground there.
		singleplayer.getServer().waitFor(_ -> {
			ServerPlayer player = player(singleplayer);
			return player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(1.5), item -> item.getItem().is(Items.COAL)).isEmpty();
		}, 40);
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
					? "Every quarry drop should land at the player's feet, found " + atTheOres + " at the ores"
					: "Without sneaking the coal should drop at its ore, found " + atTheOres + " there");
			return broken;
		});
	}

	/**
	 * More Luck III works as Looting III, Luck of the Sea III and Fortune III: diamond ore with Fortune III drops 1 to 4
	 * diamonds, 2.2 on average, which 400 ores must match within four standard deviations (Fortune II's 1.75 and IV's
	 * 2.67 fall well outside).
	 */
	private static void luck(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, maxedTalents());
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			ServerLevel level = player.level();
			Holder<Enchantment> looting = level.registryAccess().getOrThrow(Enchantments.LOOTING);
			check(EnchantmentHelper.getEnchantmentLevel(looting, player) == 3, "More Luck III should loot like Looting III");
			check(EnchantmentHelper.getFishingLuckBonus(level, new ItemStack(Items.FISHING_ROD), player) == 3, "More Luck III should fish like Luck of the Sea III");

			BlockState ore = Blocks.DIAMOND_ORE.defaultBlockState();
			BlockPos pos = player.blockPosition();
			int ores = 400;
			int withTalent = 0;
			int without = 0;

			for (int i = 0; i < ores; i++) {
				withTalent += totalCount(Block.getDrops(ore, level, pos, null, player, new ItemStack(Items.IRON_PICKAXE)));
				without += totalCount(Block.getDrops(ore, level, pos, null, null, new ItemStack(Items.IRON_PICKAXE)));
			}

			check(without == ores, "Without a player there's no luck: 1 diamond per ore, got " + without);
			// Fortune L multiplies the 1 diamond by max(0, n - 1) + 1, n uniform in 0..L+1.
			double mean = 0.0;
			double square = 0.0;

			for (int n = 0; n < 3 + 2; n++) {
				int count = Math.max(0, n - 1) + 1;
				mean += count / 5.0;
				square += count * count / 5.0;
			}

			checkWithin(withTalent, ores * mean, Math.sqrt(ores * (square - mean * mean)), "diamonds from " + ores + " ores with Fortune III");
		});
	}

	/**
	 * Stronger Weapons V (+20%) and each weapon's specialist III (+30%): a sword, an arrow from a bow, a thrown trident
	 * and one in hand, a mace and a spear get both, a punch gets +1.5 before the 20%, and an axe or an arrow without a bow
	 * only the 20%; damage that isn't an attack is left alone.
	 */
	private static void weaponDamage(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, maxedTalents());
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			ServerLevel level = player.level();

			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
			checkNear(4.0 * 1.5, damageDealt(singleplayer, player.damageSources().playerAttack(player), 4.0F), "a sword hit");

			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TRIDENT));
			checkNear(4.0 * 1.5, damageDealt(singleplayer, player.damageSources().playerAttack(player), 4.0F), "a trident in hand");
			ThrownTrident trident = new ThrownTrident(level, player, new ItemStack(Items.TRIDENT));
			checkNear(4.0 * 1.5, damageDealt(singleplayer, player.damageSources().trident(trident, player), 4.0F), "a thrown trident");
			trident.discard();

			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MACE));
			checkNear(4.0 * 1.5, damageDealt(singleplayer, player.damageSources().playerAttack(player), 4.0F), "a mace hit");

			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SPEAR));
			checkNear(4.0 * 1.5, damageDealt(singleplayer, player.damageSources().playerAttack(player), 4.0F), "a spear hit");

			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			Arrow fromBow = new Arrow(level, player, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
			checkNear(4.0 * 1.5, damageDealt(singleplayer, player.damageSources().arrow(fromBow, player), 4.0F), "an arrow shot from a bow");
			Arrow thrown = new Arrow(level, player, new ItemStack(Items.ARROW), null);
			checkNear(4.0 * 1.2, damageDealt(singleplayer, player.damageSources().arrow(thrown, player), 4.0F), "an arrow without a bow: only Stronger Weapons");
			fromBow.discard();
			thrown.discard();

			checkNear((1.0 + 1.5) * 1.2, damageDealt(singleplayer, player.damageSources().playerAttack(player), 1.0F), "a punch");

			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
			checkNear(4.0 * 1.2, damageDealt(singleplayer, player.damageSources().playerAttack(player), 4.0F), "an axe: only Stronger Weapons");

			checkNear(4.0, damageDealt(singleplayer, player.damageSources().thorns(player), 4.0F), "thorns aren't an attack");
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		});
	}

	/**
	 * Block Chance V shrugs off 10% of attacks from mobs: 4000 attacks must land within four standard deviations of 400
	 * (Block Chance IV's 8% falls outside). At level 0 none.
	 */
	private static void blockChance(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, maxedTalents());
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			Mob husk = spawn(singleplayer, EntityTypes.HUSK, 6, 0);
			int attacks = 4000;
			checkBinomial(countBlocked(player, husk, attacks), attacks, TalentValues.blockChance(Talent.BLOCK_CHANCE.maxLevel()),
					"attacks shrugged off by Block Chance V");
			check(TalentValues.blockChance(Talent.BLOCK_CHANCE.maxLevel()) == 0.10, "Block Chance V is 10%");

			Talents.setLevel(player, Talent.BLOCK_CHANCE, 0);
			check(countBlocked(player, husk, 50) == 0, "Nothing should be blocked without the talent");
			husk.discard();
			player.setHealth(player.getMaxHealth());
		});
	}

	/**
	 * The backpack: pickups that don't fit the inventory go into it (unless the server turned that off); Shift + the
	 * inventory key opens it in game and from inside the inventory; shrinking it moves the items out of the slots that
	 * locked, into the backpack's open slots, the inventory and the ground, and says so in chat (and says nothing when
	 * nothing moved); the menu keeps the locked slots shut to clicks and shift-clicks; without the talent Shift + the
	 * inventory key opens the plain inventory.
	 */
	private static void backpack(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, maxedTalents());
		ItemEntity diamonds = dropAtPlayer(singleplayer, new ItemStack(Items.DIAMOND, 10), true);
		singleplayer.getServer().waitFor(_ -> diamonds.isRemoved(), 100);
		singleplayer.getServer().runOnServer(_ -> check(countOf(Backpacks.get(player(singleplayer)), Items.DIAMOND) == 10,
				"Diamonds that don't fit the inventory should go into the backpack"));

		shiftInventoryKey(context);
		context.waitForScreen(BackpackScreen.class);
		context.waitTicks(2);
		screenshot(context, "ist_14_backpack");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		// The server's setting: no pickups into the backpack.
		startSection(context, singleplayer, maxedTalents());
		ImSoTalented.setConfig(new ImSoTalentedConfig(TalentRules.DEFAULTS, false));
		ItemEntity kept = dropAtPlayer(singleplayer, new ItemStack(Items.DIAMOND, 10), true);
		// Nothing to wait for but time: a pickup happens the tick after the item touches the player.
		waitServerTicks(singleplayer, 10);
		singleplayer.getServer().runOnServer(_ -> {
			check(!kept.isRemoved() && kept.getItem().getCount() == 10, "With backpack_pickup=false the diamonds should stay on the ground");
			check(countOf(Backpacks.get(player(singleplayer)), Items.DIAMOND) == 0, "and not go into the backpack");
		});
		ImSoTalented.setConfig(ImSoTalentedConfig.DEFAULTS);

		backpackShrinks(context, singleplayer);
		backpackMenuLocks(context, singleplayer);

		// From inside the inventory: Shift + the inventory key opens the backpack instead of closing the inventory.
		startSection(context, singleplayer, maxedTalents().with(Talent.BACKPACK, 2));
		context.waitFor(client -> Backpacks.unlockedSlots(client.player) == 18, 40);
		context.getInput().pressKey(options -> options.keyInventory);
		context.waitForScreen(InventoryScreen.class);
		context.waitTicks(2);
		pressShiftInventoryKeyInScreen(context);
		context.waitForScreen(BackpackScreen.class);
		context.waitTicks(2);
		screenshot(context, "ist_15_backpack_two_rows");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		// Without the talent it's the plain inventory, which Shift + the inventory key also closes.
		startSection(context, singleplayer, maxedTalents().with(Talent.BACKPACK, 0));
		context.waitFor(client -> Backpacks.unlockedSlots(client.player) == 0, 40);
		shiftInventoryKey(context);
		context.waitForScreen(InventoryScreen.class);
		context.waitTicks(2);
		pressShiftInventoryKeyInScreen(context);
		context.waitForScreen(null);
		// Whatever the client asked for has been answered by now.
		singleplayer.getConnection().waitForServerboundPackets();
		singleplayer.getConnection().waitForClientboundPackets();
		check(context.computeOnClient(client -> client.gui.screen() == null), "Without the talent nothing else should open");
	}

	/**
	 * Lowering the Backpack talent from 4 to 1 locks 27 slots. Their diamonds can't go into the 9 open slots (full of
	 * cobblestone), so 64 go into the inventory's one free slot and the rest drop at the player's feet; nothing is lost,
	 * and the chat line says how many moved and dropped. Shrinking again with nothing in the locked slots says nothing.
	 */
	private static void backpackShrinks(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, maxedTalents());
		CHAT.clear();
		int[] moved = singleplayer.getServer().computeOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			Backpack backpack = Backpacks.get(player);

			for (int slot = 0; slot < 36; slot++) {
				if (slot != 8) {
					player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
				}
			}

			int diamonds = 0;

			for (int slot = 0; slot < BackpackMenu.BACKPACK_SLOTS; slot++) {
				ItemStack stack = slot < 9 ? new ItemStack(Items.COBBLESTONE, 64) : new ItemStack(Items.DIAMOND, slot % 5 + 1);
				diamonds += stack.is(Items.DIAMOND) ? stack.getCount() : 0;
				backpack.setItem(slot, stack);
			}

			Talents.setLevel(player, Talent.BACKPACK, 1);
			check(Backpacks.unlockedSlots(player) == 9, "Level 1 unlocks one row");

			for (int slot = 9; slot < BackpackMenu.BACKPACK_SLOTS; slot++) {
				check(backpack.getItem(slot).isEmpty(), "Locked slot " + slot + " must be emptied");
			}

			int inInventory = player.getInventory().countItem(Items.DIAMOND);
			int onTheGround = itemsOnTheGround(singleplayer, player.blockPosition(), Items.DIAMOND);
			check(countOf(backpack, Items.DIAMOND) == 0, "The open slots were full");
			check(inInventory == 64, "The inventory's free slot takes a stack, got " + inInventory);
			check(inInventory + onTheGround == diamonds, "No diamond is lost: " + diamonds + " before, " + inInventory + " in the inventory and "
					+ onTheGround + " on the ground after");
			return new int[] {inInventory, onTheGround};
		});
		String line = waitForChat(context, "message.imsotalented.backpack_shrank");
		check(line.contains(moved[0] + " items moved") && line.contains(moved[1] + " dropped"), "The chat line should count them: " + line);

		// Nothing in the slots that lock: nothing to say.
		startSection(context, singleplayer, maxedTalents());
		CHAT.clear();
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			Backpacks.get(player).setItem(0, new ItemStack(Items.APPLE));
			Talents.setLevel(player, Talent.BACKPACK, 1);
		});
		sentinelChat(context, singleplayer);
		check(chatLine(CHAT, "message.imsotalented.backpack_shrank") == null, "A shrink that moves nothing should say nothing");
	}

	/**
	 * The backpack menu with one row open: an item on the cursor can't be put into a locked slot, an item in one can't
	 * be taken out, and a shift-click from the inventory only fills the open slots.
	 */
	private static void backpackMenuLocks(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, maxedTalents().with(Talent.BACKPACK, 1));
		singleplayer.getServer().runOnServer(_ -> Backpacks.open(player(singleplayer)));
		context.waitForScreen(BackpackScreen.class);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			check(player.containerMenu instanceof BackpackMenu, "The backpack menu should be open on the server");
			BackpackMenu menu = (BackpackMenu) player.containerMenu;
			Backpack backpack = Backpacks.get(player);
			check(menu.unlockedSlots() == 9, "One row open");

			menu.setCarried(new ItemStack(Items.EMERALD, 3));
			menu.clicked(20, 0, ContainerInput.PICKUP, player);
			check(backpack.getItem(20).isEmpty() && menu.getCarried().getCount() == 3, "A locked slot must not take the cursor's items");
			menu.clicked(0, 0, ContainerInput.PICKUP, player);
			check(backpack.getItem(0).is(Items.EMERALD) && menu.getCarried().isEmpty(), "An open slot takes them");

			// Something in a locked slot (as a hacked client would see it): it can't be taken out either.
			backpack.setItem(30, new ItemStack(Items.GOLD_INGOT, 2));
			menu.clicked(30, 0, ContainerInput.PICKUP, player);
			check(menu.getCarried().isEmpty() && backpack.getItem(30).getCount() == 2, "A locked slot must not give up its items");
			backpack.setItem(30, ItemStack.EMPTY);

			// A shift-click from the inventory (its first slot is menu slot 36): into the open slots only.
			for (int slot = 1; slot < 9; slot++) {
				backpack.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
			}

			player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 64));
			menu.clicked(BackpackMenu.BACKPACK_SLOTS, 0, ContainerInput.QUICK_MOVE, player);
			check(backpack.getItem(0).getCount() == 64, "The shift-click should top up the open emerald stack");
			check(player.getInventory().getItem(9).getCount() == 3, "and leave the rest in the inventory, got " + player.getInventory().getItem(9));

			for (int slot = 9; slot < BackpackMenu.BACKPACK_SLOTS; slot++) {
				check(backpack.getItem(slot).isEmpty(), "Locked slot " + slot + " must stay empty");
			}

			player.closeContainer();
		});
		context.waitForScreen(null);
	}

	/** A stack dropped at the player's feet, picked up as soon as it may be. Call off the server thread. */
	private static ItemEntity dropAtPlayer(TestSingleplayerContext singleplayer, ItemStack stack, boolean fullInventory) {
		return singleplayer.getServer().computeOnServer(_ -> {
			ServerPlayer player = player(singleplayer);

			for (int i = 0; fullInventory && i < 36; i++) {
				player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			}

			ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
			item.setNoPickUpDelay();
			check(player.level().addFreshEntity(item), "Could not drop " + stack);
			return item;
		});
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

	/** A key with modifiers, handed to the open screen as the keyboard handler would (the test input sends none). */
	private static void pressInScreen(ClientGameTestContext context, int key, int keycode, int modifiers) {
		context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(key, keycode, modifiers)));
		context.waitTick();
	}

	/**
	 * The survival inventory has the two buttons above its top right corner. The talent tree's button takes the 2x2
	 * crafting grid back into the inventory before the tree replaces the screen (nothing is lost), and leaving the tree
	 * goes back to the inventory. The backpack's button opens the backpack.
	 */
	private static void inventoryButtons(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		startSection(context, singleplayer, maxedTalents());
		context.getInput().pressKey(options -> options.keyInventory);
		context.waitForScreen(InventoryScreen.class);
		singleplayer.getServer().runOnServer(_ ->
				player(singleplayer).inventoryMenu.getSlot(InventoryMenu.CRAFT_SLOT_START).set(new ItemStack(Items.OAK_LOG)));
		context.waitFor(client -> client.player.inventoryMenu.getSlot(InventoryMenu.RESULT_SLOT).getItem().is(Items.OAK_PLANKS), 40);
		screenshot(context, "ist_16_inventory_buttons");

		clickInventoryButton(context, "key.imsotalented.talent_tree");
		context.waitForScreen(TalentScreen.class);
		singleplayer.getServer().waitFor(_ -> player(singleplayer).getInventory().countItem(Items.OAK_LOG) == 1, 40);
		singleplayer.getServer().runOnServer(_ -> check(player(singleplayer).inventoryMenu.getCraftSlots().isEmpty(),
				"The crafting grid's log should be back in the inventory, not left in the grid"));
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(InventoryScreen.class);

		clickInventoryButton(context, "key.imsotalented.open_backpack");
		context.waitForScreen(BackpackScreen.class);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	/** Clicks the survival inventory's button labelled with this key mapping's name. */
	private static void clickInventoryButton(ClientGameTestContext context, String label) {
		Vector2i center = context.computeOnClient(client -> {
			for (AbstractWidget widget : Screens.getWidgets(client.gui.screen())) {
				if (find(widget.getMessage(), label) != null) {
					return new Vector2i(widget.getX() + widget.getWidth() / 2, widget.getY() + widget.getHeight() / 2);
				}
			}

			throw new AssertionError("No inventory button labelled " + label);
		});
		moveCursor(context, center);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	/**
	 * Death by a husk's swing: the backpack drops (no keepInventory); talents, tree XP and the chunks Explorer paid for
	 * are kept, the killing blow pays no Guardian XP, and the extra hearts come back with the respawn.
	 */
	private static void death(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Block Chance off, so the swing can't be shrugged off; Guardian with room to earn.
		startSection(context, singleplayer, maxedTalents().with(Talent.BLOCK_CHANCE, 0).withXp(TalentTree.GUARDIAN, 0L));
		BlockPos deathPos = singleplayer.getServer().computeOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			Backpacks.get(player).setItem(3, new ItemStack(Items.GOLD_INGOT, 3));
			return player.blockPosition();
		});
		long[] xpBefore = singleplayer.getServer().computeOnServer(_ -> treeXps(player(singleplayer)));
		int chunksBefore = singleplayer.getServer().computeOnServer(_ -> player(singleplayer).getAttachedOrCreate(TreeXpAttachments.VISITED_CHUNKS).count());
		check(chunksBefore >= 1, "At least the chunk at home has been paid for, got " + chunksBefore);
		double guardianBefore = earnedXp(singleplayer, TalentTree.GUARDIAN);

		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			Mob husk = spawn(singleplayer, EntityTypes.HUSK, 0, 2);
			player.hurtServer(player.level(), player.damageSources().mobAttack(husk), 1000.0F);
			check(player.isDeadOrDying(), "A 1000-damage swing should kill the player");
			checkNear(guardianBefore, serverEarnedXp(player, TalentTree.GUARDIAN), "the swing that kills pays no Guardian XP");
			husk.discard();
		});
		context.waitForScreen(DeathScreen.class);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			check(Backpacks.get(player).isEmpty(), "The backpack should be dropped on death");
			int dropped = player.level().getEntitiesOfClass(ItemEntity.class, new AABB(deathPos).inflate(4.0)).stream()
					.filter(item -> item.getItem().is(Items.GOLD_INGOT))
					.mapToInt(item -> item.getItem().getCount())
					.sum();
			check(dropped == 3, "Expected the 3 gold ingots on the ground, found " + dropped);
		});
		context.waitTicks(5);
		context.clickScreenButton("deathScreen.respawn");
		context.waitForScreen(null);
		singleplayer.getConnection().waitForChunksRender();
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			check(Talents.level(player, Talent.MORE_HEALTH) == Talent.MORE_HEALTH.maxLevel(), "Talents are kept through death");
			check(Arrays.equals(treeXps(player), xpBefore), "Tree XP is kept through death");
			int chunks = player.getAttachedOrCreate(TreeXpAttachments.VISITED_CHUNKS).count();
			check(chunks == chunksBefore, "The chunks Explorer paid for are kept through death: " + chunksBefore + " before, " + chunks + " after");
			checkNear(guardianBefore, serverEarnedXp(player, TalentTree.GUARDIAN), "and the killing blow still paid nothing");
			check(player.getMaxHealth() == 30.0F, "Extra hearts come back after respawning, got " + player.getMaxHealth());
			check(player.getHealth() == 30.0F, "Respawning fills the extra hearts too, got " + player.getHealth());
		});
	}

	// Setting up a section

	/**
	 * What every section starts from: no screen open, survival, at home facing south with no motion, full health and
	 * food, no effects, nothing in the inventory or the backpack, no other entity in the world, the default settings,
	 * and exactly these talents, toggles and tree XP, with an empty tree XP ledger and no damage or mining cooldowns.
	 * Waits until the client has the talents too, and the player stands on the ground on both sides.
	 */
	private static void startSection(ClientGameTestContext context, TestSingleplayerContext singleplayer, TalentData talents) {
		context.runOnClient(client -> {
			if (client.gui.screen() instanceof AbstractContainerScreen<?>) {
				client.player.closeContainer();
			}

			client.gui.setScreen(null);
		});
		setRules(TalentRules.DEFAULTS);
		singleplayer.getServer().runCommand("gamemode survival @p");
		singleplayer.getServer().runCommand("effect clear @p");
		singleplayer.getServer().runCommand("clear @p");
		discardOthers(singleplayer);
		singleplayer.getServer().runCommand(HOME);
		singleplayer.getServer().runOnServer(_ -> {
			ServerPlayer player = player(singleplayer);
			player.stopUsingItem();
			Backpacks.get(player).clearContent();
			Talents.setData(player, talents);
			Talents.ledger(player).clear();
			player.removeAttached(TreeXpAttachments.GUARDIAN_COOLDOWN);
			player.removeAttached(TreeXpAttachments.PAID_BLOCK_TICK);
			player.damageCooldownTime = 0;
			player.setHealth(player.getMaxHealth());
			player.getFoodData().setFoodLevel(20);
			player.setDeltaMovement(Vec3.ZERO);
			player.resetFallDistance();
		});
		context.runOnClient(client -> client.player.setDeltaMovement(Vec3.ZERO));
		context.waitFor(client -> client.player != null && client.gui.screen() == null && !client.player.hasInfiniteMaterials()
				&& Talents.data(client.player).equals(talents) && Math.abs(client.player.getZ() - 0.5) < 1e-3 && client.player.onGround(), 40);
		singleplayer.getServer().waitFor(_ -> player(singleplayer).onGround(), 40);
	}

	/** Every talent and every tree at its top level, everything on, as {@code /talents max} leaves a player. */
	private static TalentData maxedTalents() {
		TalentData data = TalentData.EMPTY;

		for (Talent talent : Talent.values()) {
			data = data.with(talent, talent.maxLevel());
		}

		for (TalentTree tree : TalentTree.values()) {
			data = data.withXp(tree, TalentRules.DEFAULTS.xpForLevel(tree.maxLevel()));
		}

		return data;
	}

	/** Removes every entity but the player: the mobs a section spawned and the items it dropped. */
	private static void discardOthers(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(_ -> {
			List<Entity> others = new ArrayList<>();

			for (Entity entity : player(singleplayer).level().getAllEntities()) {
				if (!(entity instanceof Player)) {
					others.add(entity);
				}
			}

			others.forEach(Entity::discard);
		});
	}

	// Chat, the action bar and commands

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
		context.waitFor(_ -> chatLine(CHAT, key) != null, 40);
		return context.computeOnClient(_ -> chatLine(CHAT, key).getString());
	}

	/** Waits for an action bar text with this translation key and returns it as the player reads it. */
	private static String waitForOverlay(ClientGameTestContext context, String key) {
		context.waitFor(_ -> chatLine(OVERLAY, key) != null, 40);
		return context.computeOnClient(_ -> chatLine(OVERLAY, key).getString());
	}

	/**
	 * Sends the player a chat line and waits for it: anything the server sent before it has arrived by then, so the
	 * chat can be checked for what didn't come.
	 */
	private static void sentinelChat(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(_ -> player(singleplayer).sendSystemMessage(Component.translatable("test.imsotalented.sentinel")));
		context.waitFor(_ -> chatLine(CHAT, "test.imsotalented.sentinel") != null, 40);
	}

	/** Like {@link #sentinelChat}, for the action bar. */
	private static void sentinelOverlay(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(_ -> player(singleplayer).sendOverlayMessage(Component.translatable("test.imsotalented.sentinel")));
		context.waitFor(_ -> chatLine(OVERLAY, "test.imsotalented.sentinel") != null, 40);
	}

	private static @Nullable Component chatLine(List<Component> lines, String key) {
		for (Component line : lines) {
			if (find(line, key) != null) {
				return line;
			}
		}

		return null;
	}

	/** The translation with this key in a component: itself, a sibling, or an argument (a failure line wraps its message). */
	private static @Nullable TranslatableContents find(Component component, String key) {
		if (component.getContents() instanceof TranslatableContents translatable) {
			if (translatable.getKey().equals(key)) {
				return translatable;
			}

			for (Object arg : translatable.getArgs()) {
				if (arg instanceof Component nested && find(nested, key) instanceof TranslatableContents found) {
					return found;
				}
			}
		}

		for (Component sibling : component.getSiblings()) {
			if (find(sibling, key) instanceof TranslatableContents found) {
				return found;
			}
		}

		return null;
	}

	/** What a command returned and said to the operator, or why it didn't parse. */
	private record CommandOutcome(int result, List<Component> messages, @Nullable CommandSyntaxException error) {
		boolean says(String key) {
			return this.messages.stream().anyMatch(message -> find(message, key) != null);
		}

		/** The whole line with this key, as the operator reads it. */
		String text(String key) {
			for (Component message : this.messages) {
				if (find(message, key) != null) {
					return message.getString();
				}
			}

			throw new AssertionError("The command didn't say " + key + ": " + this.messages);
		}

		Object arg(String key, int index) {
			for (Component message : this.messages) {
				if (find(message, key) instanceof TranslatableContents found) {
					return found.getArgs()[index];
				}
			}

			throw new AssertionError("The command didn't say " + key + ": " + this.messages);
		}

		/** The translation key of a translatable argument, like the on/off of a toggle line. */
		String argKey(String key, int index) {
			return this.arg(key, index) instanceof Component component && component.getContents() instanceof TranslatableContents translatable
					? translatable.getKey() : "";
		}
	}

	private static CommandOutcome command(TestSingleplayerContext singleplayer, String command) {
		return singleplayer.getServer().computeOnServer(server -> execute(server, command));
	}

	/** Runs a command as an operator who hears everything it says. Call on the server thread. */
	private static CommandOutcome execute(MinecraftServer server, String command) {
		List<Component> messages = new ArrayList<>();
		CommandSource listener = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				messages.add(message);
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		CommandSourceStack source = server.createCommandSourceStack().withSource(listener);

		try {
			return new CommandOutcome(server.getCommands().getDispatcher().execute(command, source), messages, null);
		} catch (CommandSyntaxException e) {
			return new CommandOutcome(0, messages, e);
		}
	}

	/** What the command line suggests at the end of this input. */
	private static List<String> suggestions(TestSingleplayerContext singleplayer, String input) {
		return singleplayer.getServer().computeOnServer(server -> {
			CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
			return dispatcher.getCompletionSuggestions(dispatcher.parse(input, server.createCommandSourceStack())).join()
					.getList().stream().map(Suggestion::getText).toList();
		});
	}

	// The talent screen

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

	/** Clicks a talent's node, waits until both sides have it at this level, and leaves the throttle time to pass. */
	private static void buy(ClientGameTestContext context, TestSingleplayerContext singleplayer, Talent talent, int level) {
		check(context.computeOnClient(client -> Talents.canBuy(client.player, talent)) == Talents.BuyResult.BUYABLE, talent.id() + " should be buyable now");
		hover(context, talent);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitFor(client -> Talents.level(client.player, talent) == level, 40);
		check(serverLevel(singleplayer, talent) == level, talent.id() + " should be at level " + level + " on the server");
		waitServerTicks(singleplayer, RequestThrottle.MIN_TICKS);
	}

	/**
	 * Clicks a talent's node that can't be bought, for this reason on both sides, and checks nothing was bought. The
	 * client doesn't even ask; any packet it did send has been handled before the check.
	 */
	private static void clickFails(ClientGameTestContext context, TestSingleplayerContext singleplayer, Talent talent, Talents.BuyResult why) {
		int before = serverLevel(singleplayer, talent);
		check(context.computeOnClient(client -> Talents.canBuy(client.player, talent)) == why, talent.id() + " should not be buyable: " + why);
		check(singleplayer.getServer().computeOnServer(_ -> Talents.canBuy(player(singleplayer), talent)) == why, "The server should agree: " + why);
		hover(context, talent);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		singleplayer.getConnection().waitForServerboundPackets();
		check(serverLevel(singleplayer, talent) == before, talent.id() + " must not be bought (" + why + ")");
	}

	/** Right-clicks a talent's node: its toggle. */
	private static void rightClick(ClientGameTestContext context, Talent talent) {
		hover(context, talent);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
	}

	private static void checkPoints(ClientGameTestContext context, TalentTree tree, int expected, String why) {
		int points = context.computeOnClient(client -> Talents.points(client.player, tree));
		check(points == expected, "Expected " + expected + " " + tree.id() + " points (" + why + "), got " + points);
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

	private static void send(ClientGameTestContext context, CustomPacketPayload payload) {
		context.runOnClient(_ -> ClientPlayNetworking.send(payload));
	}

	// Playing

	/** Holds the attack key while looking at a block until the server has it broken, as a player mines. */
	private static void mine(ClientGameTestContext context, TestSingleplayerContext singleplayer, BlockPos pos) {
		context.waitFor(client -> !client.level.getBlockState(pos).isAir(), 40);
		aimAt(context, Vec3.atCenterOf(pos));
		context.getInput().holdKey(options -> options.keyAttack);
		singleplayer.getServer().waitFor(_ -> player(singleplayer).level().getBlockState(pos).isAir(), 100);
		context.getInput().releaseKey(options -> options.keyAttack);
		context.waitFor(client -> client.level.getBlockState(pos).isAir(), 40);
	}

	/** One click on a crop, aimed low enough to hit young wheat too; crops break at once. */
	private static void harvest(ClientGameTestContext context, TestSingleplayerContext singleplayer, BlockPos pos) {
		context.waitFor(client -> client.level.getBlockState(pos).is(Blocks.WHEAT), 40);
		aimAt(context, new Vec3(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5));
		context.getInput().pressKey(options -> options.keyAttack);
		singleplayer.getServer().waitFor(_ -> player(singleplayer).level().getBlockState(pos).isAir(), 40);
		context.waitFor(client -> client.level.getBlockState(pos).isAir(), 40);
	}

	/**
	 * One swing at a mob from the client with a full attack charge, aimed at its chest. Returns the health it lost on
	 * the server.
	 */
	private static double hitFromClient(ClientGameTestContext context, TestSingleplayerContext singleplayer, Mob mob) {
		context.waitFor(client -> client.level.getEntity(mob.getId()) != null, 40);
		context.waitFor(client -> client.player.getAttackStrengthScale(0.0F) >= 1.0F, 40);
		Vec3 chest = singleplayer.getServer().computeOnServer(_ -> mob.position().add(0.0, mob.getBbHeight() * 0.6, 0.0));
		aimAt(context, chest);
		float before = singleplayer.getServer().computeOnServer(_ -> mob.getHealth());
		context.getInput().pressKey(options -> options.keyAttack);
		singleplayer.getServer().waitFor(_ -> mob.getHealth() < before, 20);
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

	/**
	 * Puts this in the player's main hand and waits until the client has it. The item's attribute modifiers (a tool's
	 * Efficiency, say) are applied on each side's next entity tick, which nothing shows, so two more ticks pass.
	 */
	private static void holdInMainHand(ClientGameTestContext context, TestSingleplayerContext singleplayer, ItemStack stack) {
		singleplayer.getServer().runOnServer(_ -> player(singleplayer).setItemInHand(InteractionHand.MAIN_HAND, stack.copy()));
		context.waitFor(client -> ItemStack.isSameItemSameComponents(client.player.getMainHandItem(), stack)
				&& client.player.getMainHandItem().getCount() == stack.getCount(), 40);
		context.waitTicks(2);
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

	private static void setBlock(TestSingleplayerContext singleplayer, BlockPos pos, BlockState state) {
		singleplayer.getServer().runOnServer(_ -> player(singleplayer).level().setBlockAndUpdate(pos, state));
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
	private static double damageDealt(TestSingleplayerContext singleplayer, DamageSource source, float damage) {
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

	// Reading the game

	private static ServerPlayer player(TestSingleplayerContext singleplayer) {
		return singleplayer.getConnection().getServerPlayer();
	}

	/** A tree's XP with the part earned but not yet committed: everything earned, read on the server. */
	private static double earnedXp(TestSingleplayerContext singleplayer, TalentTree tree) {
		return singleplayer.getServer().computeOnServer(_ -> serverEarnedXp(player(singleplayer), tree));
	}

	/** {@link #earnedXp} on the server thread, for use in a server wait. */
	private static double earnedXp0(TestSingleplayerContext singleplayer, TalentTree tree) {
		return serverEarnedXp(player(singleplayer), tree);
	}

	private static double serverEarnedXp(ServerPlayer player, TalentTree tree) {
		return Talents.treeXp(player, tree) + Talents.ledger(player).pending(tree);
	}

	private static long treeXp(TestSingleplayerContext singleplayer, TalentTree tree) {
		return singleplayer.getServer().computeOnServer(_ -> Talents.treeXp(player(singleplayer), tree));
	}

	private static long[] treeXps(ServerPlayer player) {
		long[] xp = new long[TalentTree.values().length];

		for (TalentTree tree : TalentTree.values()) {
			xp[tree.ordinal()] = Talents.treeXp(player, tree);
		}

		return xp;
	}

	private static int serverLevel(TestSingleplayerContext singleplayer, Talent talent) {
		return singleplayer.getServer().computeOnServer(_ -> Talents.level(player(singleplayer), talent));
	}

	private static boolean isDisabled(TestSingleplayerContext singleplayer, Talent talent) {
		return singleplayer.getServer().computeOnServer(_ -> Talents.data(player(singleplayer)).isDisabled(talent));
	}

	private static int serverTick(TestSingleplayerContext singleplayer) {
		return singleplayer.getServer().computeOnServer(MinecraftServer::getTickCount);
	}

	/** Waits until the server's clock has moved on this many ticks: for rules that are about time. */
	private static void waitServerTicks(TestSingleplayerContext singleplayer, int ticks) {
		int start = serverTick(singleplayer);
		singleplayer.getServer().waitFor(server -> server.getTickCount() >= start + ticks, ticks + 40);
	}

	/** Waits until the player has come to a stop on the ground and the server has the client's last position. */
	private static void waitUntilStill(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		context.waitFor(client -> client.player.getDeltaMovement().horizontalDistanceSqr() < 1e-8 && client.player.onGround(), 60);
		Vec3 position = context.computeOnClient(client -> client.player.position());
		singleplayer.getServer().waitFor(_ -> player(singleplayer).position().distanceToSqr(position) < 1e-10, 40);
	}

	private static double serverX(TestSingleplayerContext singleplayer) {
		return singleplayer.getServer().computeOnServer(_ -> player(singleplayer).getX());
	}

	/** The count of one item in the player's inventory and lying within 8 blocks of this spot. Call on the server thread. */
	private static int itemsAround(TestSingleplayerContext singleplayer, BlockPos pos, Item item) {
		return player(singleplayer).getInventory().countItem(item) + itemsOnTheGround(singleplayer, pos, item);
	}

	/** The count of one item lying within 8 blocks of this spot. Call on the server thread. */
	private static int itemsOnTheGround(TestSingleplayerContext singleplayer, BlockPos pos, Item item) {
		return player(singleplayer).level().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(8.0)).stream()
				.filter(entity -> entity.getItem().is(item))
				.mapToInt(entity -> entity.getItem().getCount())
				.sum();
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

	// Files

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

	private static void deleteFile(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// Checks

	/** A count of {@code trials} chances of {@code p} each, within {@link #SIGMAS} standard deviations of its mean. */
	private static void checkBinomial(int actual, int trials, double p, String what) {
		checkWithin(actual, trials * p, Math.sqrt(trials * p * (1.0 - p)), what);
	}

	private static void checkWithin(double actual, double mean, double sd, String what) {
		check(Math.abs(actual - mean) <= SIGMAS * sd, "Expected " + mean + " ± " + SIGMAS * sd + " for " + what + ", got " + actual);
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
