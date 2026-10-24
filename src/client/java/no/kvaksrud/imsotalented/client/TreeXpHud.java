package no.kvaksrud.imsotalented.client;

import java.nio.file.Path;

import no.kvaksrud.imsotalented.ImSoTalented;
import no.kvaksrud.imsotalented.client.talent.TreeXpBar;
import no.kvaksrud.imsotalented.talent.TalentData;
import no.kvaksrud.imsotalented.talent.TalentRules;
import no.kvaksrud.imsotalented.talent.TalentTree;
import no.kvaksrud.imsotalented.talent.Talents;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.StatusBarHeightProvider;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;

/**
 * A tree's XP bar on the HUD: one slot right above the vanilla XP bar, as wide as it, showing one tree at a time in the
 * tree's color with its name and level to the right of the bar. The pinned tree always shows there; earning XP in
 * another tree borrows the slot for {@link #FLASH_TICKS}, and with nothing pinned the slot is only filled for that long
 * after earning. One tree is pinned at a time, from the pin button in the talent screen, and the pin is saved in
 * {@link ImSoTalentedClientConfig} on this computer.
 *
 * <p>Earning is spotted by watching the synced tree XP go up, so the server needs no packet for it.
 *
 * <p>The slot is a Fabric status bar right after the vanilla info bar (the XP bar, or the locator or jump bar in its
 * place), so Fabric moves the health, armor, food, air and mount health rows up by its height; the held item name and
 * the action bar text go up with them ({@link #liftedOverStrip}). The vanilla XP level number stays where it is, in
 * the strip's lower part. It's reserved whenever the game mode has an XP bar, even while the slot is empty, so the
 * rows never jump when a bar appears; in creative and spectator it takes no room and shows nothing, like the XP bar.
 */
public final class TreeXpHud {
	public static final Identifier ID = ImSoTalented.id("tree_xp_bar");
	/**
	 * The strip reserved above the vanilla XP bar: the bar at its top, a pixel of air, and seven rows for the vanilla
	 * XP level number, which stands up out of the XP bar's middle and isn't moved.
	 */
	public static final int SLOT_HEIGHT = 13;
	public static final int BAR_WIDTH = 182;
	/** How long the bar of a tree that just earned XP shows: three seconds. */
	public static final int FLASH_TICKS = 60;
	/**
	 * After the player entity changes (joining, respawning, changing dimension), how long XP changes only set the
	 * baseline: the talents can be synced to the new entity a few ticks after it appears, which would look like earning.
	 */
	private static final int SYNC_GRACE_TICKS = 20;
	/** Between the bar's right end and its label. */
	private static final int LABEL_GAP = 4;
	/** Capital letters are 7 px tall, so this puts them in the middle of the 5 px bar. */
	private static final int LABEL_Y_OFFSET = (TreeXpBar.HEIGHT - 7) / 2;
	/** The vanilla XP level number's black outline. */
	private static final int LABEL_OUTLINE = 0xFF000000;
	/** The held item's name is drawn this far above the screen's bottom; the action bar text 9 px above that. */
	private static final int HELD_ITEM_NAME_TOP = 59;

	private static @Nullable TalentTree pinned;
	private static @Nullable TalentTree flashing;
	private static int flashTicks;
	private static long @Nullable [] lastXp;
	private static @Nullable LocalPlayer lastPlayer;
	private static int graceTicks;

	private TreeXpHud() {
	}

	public static void init() {
		pinned = ImSoTalentedClientConfig.load(configFile()).pinnedTree();

		HudElementRegistry.attachElementAfter(VanillaHudElements.INFO_BAR, ID, TreeXpHud::extract);
		// The bar spans both halves of the hotbar, so both columns of status rows move up.
		StatusBarHeightProvider height = _ -> reservesSlot() ? SLOT_HEIGHT : 0;
		HudStatusBarHeightRegistry.addLeft(ID, height);
		HudStatusBarHeightRegistry.addRight(ID, height);
		HudElementRegistry.replaceElement(VanillaHudElements.HELD_ITEM_TOOLTIP, TreeXpHud::liftedOverStrip);
		HudElementRegistry.replaceElement(VanillaHudElements.OVERLAY_MESSAGE, TreeXpHud::liftedOverStrip);

		ClientTickEvents.END_CLIENT_TICK.register(TreeXpHud::tick);
	}

	/**
	 * The held item's name and the action bar text, raised by the part of the strip Fabric doesn't raise them by. Fabric
	 * only lifts them once the status rows' top passes the name's top; the strip alone gets the rows most of the way
	 * there, so with one row of hearts and some armor the name would sit on the armor. This keeps both texts as far
	 * above the rows as in vanilla (they still meet the rows where vanilla's do, with extra hearts).
	 */
	private static HudElement liftedOverStrip(HudElement text) {
		return (graphics, deltaTracker) -> {
			int lift = textLift();

			if (lift == 0) {
				text.extractRenderState(graphics, deltaTracker);
				return;
			}

			graphics.pose().pushMatrix();
			graphics.pose().translate(0.0F, -lift);
			text.extractRenderState(graphics, deltaTracker);
			graphics.pose().popMatrix();
		};
	}

	/**
	 * Fabric lifts both texts by {@code statusTop - HELD_ITEM_NAME_TOP} once that's positive, {@code statusTop} being
	 * how far above the screen's bottom the highest status row reaches. Without the strip the rows, and so Fabric's
	 * lift, would be a strip lower; the texts should end up a strip higher than that, like the rows. This is the rest.
	 */
	private static int textLift() {
		if (!reservesSlot() || !(Minecraft.getInstance().getCameraEntity() instanceof Player)) {
			return 0;
		}

		int statusTop = Math.max(HudStatusBarHeightRegistry.getHeight(VanillaHudElements.ARMOR_BAR),
				HudStatusBarHeightRegistry.getHeight(VanillaHudElements.AIR_BAR));
		return Math.clamp(HELD_ITEM_NAME_TOP + SLOT_HEIGHT - statusTop, 0, SLOT_HEIGHT);
	}

	/** The tree pinned to the HUD, or null. */
	public static @Nullable TalentTree pinned() {
		return pinned;
	}

	/** Pins this tree (replacing the pinned one) or, with null, unpins; saved right away. */
	public static void setPinned(@Nullable TalentTree tree) {
		pinned = tree;
		new ImSoTalentedClientConfig(tree).save(configFile());
	}

	/** The pin button: pins this tree, or unpins it if it's the pinned one. */
	public static void togglePin(TalentTree tree) {
		setPinned(pinned == tree ? null : tree);
	}

	/** The tree whose bar the slot shows right now: one that just earned XP, else the pinned one, else none. */
	public static @Nullable TalentTree shownTree() {
		return flashing != null ? flashing : pinned;
	}

	private static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(ImSoTalentedClientConfig.FILE_NAME);
	}

	/** Whether the game mode shows the vanilla XP bar (survival and adventure), which is when the slot takes room. */
	private static boolean reservesSlot() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.gameMode != null && minecraft.gameMode.hasExperience();
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;

		if (player == null) {
			lastPlayer = null;
			lastXp = null;
			flashing = null;
			flashTicks = 0;
			return;
		}

		if (player != lastPlayer) {
			lastPlayer = player;
			graceTicks = SYNC_GRACE_TICKS;
		}

		if (flashTicks > 0 && --flashTicks == 0) {
			flashing = null;
		}

		TalentData data = Talents.data(player);
		long[] xp = new long[TalentTree.values().length];

		for (TalentTree tree : TalentTree.values()) {
			xp[tree.ordinal()] = data.xp(tree);
		}

		if (graceTicks > 0) {
			graceTicks--;
		} else if (lastXp != null) {
			TalentTree earned = earnedTree(lastXp, xp);

			if (earned != null) {
				flashing = earned;
				flashTicks = FLASH_TICKS;
			}
		}

		lastXp = xp;
	}

	/**
	 * The tree to show for this tick's earnings: the one already showing if it earned again, so a fight that fills
	 * Warrior and Guardian together doesn't flicker; otherwise the first that isn't pinned, since the pinned one shows
	 * anyway; otherwise the pinned one. Null if nothing went up.
	 */
	private static @Nullable TalentTree earnedTree(long[] before, long[] after) {
		TalentTree earned = null;

		for (TalentTree tree : TalentTree.values()) {
			if (after[tree.ordinal()] <= before[tree.ordinal()]) {
				continue;
			}

			if (tree == flashing) {
				return tree;
			}

			if (earned == null || earned == pinned) {
				earned = tree;
			}
		}

		return earned;
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		TalentTree tree = shownTree();

		// Fabric only knows where status bars go while the camera is a player.
		if (tree == null || minecraft.player == null || !reservesSlot() || !(minecraft.getCameraEntity() instanceof Player)) {
			return;
		}

		int top = graphics.guiHeight() - HudStatusBarHeightRegistry.getHeight(ID);
		int left = (graphics.guiWidth() - BAR_WIDTH) / 2;
		TalentRules.Progress progress = Talents.progress(minecraft.player, tree);
		TreeXpBar.extract(graphics, left, top, BAR_WIDTH, progress.fraction(), tree.color());

		Component name = Component.translatable(tree.translationKey());
		Component label = progress.maxed()
				? Component.translatable("hud.imsotalented.bar_maxed", name)
				: Component.translatable("hud.imsotalented.bar", name, progress.level());
		Font font = minecraft.font;
		int labelX = left + BAR_WIDTH + LABEL_GAP;

		// Right of the bar: the status rows stay inside the bar's width, and the chat grows from the left edge, so on
		// the left the label would run under the chat's last line. A window too narrow for the label still gets the bar.
		if (labelX + font.width(label) + 1 <= graphics.guiWidth()) {
			extractOutlined(graphics, font, label, labelX, top + LABEL_Y_OFFSET, tree.color());
		}
	}

	/** Text with a black outline all around, like the vanilla XP level number, so it reads over any world. */
	private static void extractOutlined(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
		graphics.text(font, text, x + 1, y, LABEL_OUTLINE, false);
		graphics.text(font, text, x - 1, y, LABEL_OUTLINE, false);
		graphics.text(font, text, x, y + 1, LABEL_OUTLINE, false);
		graphics.text(font, text, x, y - 1, LABEL_OUTLINE, false);
		graphics.text(font, text, x, y, color, false);
	}
}
