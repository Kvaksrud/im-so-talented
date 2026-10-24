package no.kvaksrud.imsotalented.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import no.kvaksrud.imsotalented.client.talent.TalentTooltip;
import no.kvaksrud.imsotalented.client.talent.TalentTreeLayout;
import no.kvaksrud.imsotalented.client.talent.TreeXpBar;
import no.kvaksrud.imsotalented.network.BuyTalentPayload;
import no.kvaksrud.imsotalented.network.ToggleTalentPayload;
import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentData;
import no.kvaksrud.imsotalented.talent.TalentPrereq;
import no.kvaksrud.imsotalented.talent.TalentRules;
import no.kvaksrud.imsotalented.talent.TalentTree;
import no.kvaksrud.imsotalented.talent.Talents;
import org.joml.Vector2i;
import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * The talent trees, in the vanilla advancements window: a tab per {@link TalentTree} along the top, showing its root's
 * icon, and under them the active tree. Its header has the tree's name and level, its XP bar in the tree's color with
 * the XP into the level, the points left to spend, and a pin button for the HUD bar ({@link TreeXpHud}). Each talent
 * is a vanilla advancement frame with its icon and a level badge, at its row and column ({@link TalentTreeLayout}).
 * Left-clicking a node asks the server to buy its next level with a point of the tree; right-clicking a toggleable
 * talent the player owns (outside creative) flips its on/off toggle. The server checks everything and updates the
 * synced talent attachment, and since this screen draws from {@link Talents#data} every frame it simply shows the new
 * state once it arrives.
 *
 * <p>Frames say where a talent stands at a glance: grey while nothing is bought, gold once it has a level, the rounded
 * goal frame for a talent that unlocks others, and the spiky challenge frame once it is maxed. A talent whose
 * prereqs aren't met yet is dimmed, and a toggled-off talent is dimmed to a cool grey with an OFF badge. The badge
 * color says what a click would do: green buys, grey has no point to spend, gold maxed, red toggled off. A connector
 * lights up once its prereq is met.
 *
 * <p>It doesn't pause the game, like the inventory: in singleplayer a paused server would handle the buy but not send
 * the new talents until the game resumes, so the screen would show stale levels. The last tab stays selected until the
 * game closes.
 */
public class TalentScreen extends Screen {
	private static final Component TITLE = Component.translatable("screen.imsotalented.talents");

	/** The vanilla advancements window, 252x140, stretched to this screen's size by cutting it into bands. */
	private static final Identifier WINDOW_TEXTURE = Identifier.withDefaultNamespace("textures/gui/advancements/window.png");
	private static final int WINDOW_TEXTURE_SIZE = 256;
	/**
	 * Where the window texture is cut across: the left border with the dark area's inner shadow, the middle (stretched),
	 * and the right border with its shadow.
	 */
	private static final int[] WINDOW_COLUMNS = {0, 17, 235, 252};
	/**
	 * Where the window texture is cut down: the top border, the header's plain middle (stretched to the taller header),
	 * the header's last row with the bevel and the inner shadow, the dark area's plain middle (stretched), and the inner
	 * shadow with the bottom border.
	 */
	private static final int[] WINDOW_ROWS = {0, 4, 16, 26, 123, 140};
	/** The vanilla window's header and dark area, which the stretched bands grow from. */
	private static final int VANILLA_HEADER_HEIGHT = 18;
	private static final int VANILLA_INSIDE_WIDTH = 234;
	private static final int VANILLA_INSIDE_HEIGHT = 113;

	private static final Identifier TAB_FIRST = Identifier.withDefaultNamespace("advancements/tab_above_left");
	private static final Identifier TAB_FIRST_SELECTED = Identifier.withDefaultNamespace("advancements/tab_above_left_selected");
	private static final Identifier TAB = Identifier.withDefaultNamespace("advancements/tab_above_middle");
	private static final Identifier TAB_SELECTED = Identifier.withDefaultNamespace("advancements/tab_above_middle_selected");

	private static final Identifier TASK_FRAME = Identifier.withDefaultNamespace("advancements/task_frame_unobtained");
	private static final Identifier TASK_FRAME_BOUGHT = Identifier.withDefaultNamespace("advancements/task_frame_obtained");
	private static final Identifier PARENT_FRAME = Identifier.withDefaultNamespace("advancements/goal_frame_unobtained");
	private static final Identifier PARENT_FRAME_BOUGHT = Identifier.withDefaultNamespace("advancements/goal_frame_obtained");
	private static final Identifier MAXED_FRAME = Identifier.withDefaultNamespace("advancements/challenge_frame_obtained");

	private static final Identifier PIN_BUTTON = Identifier.withDefaultNamespace("widget/button");
	private static final Identifier PIN_BUTTON_HOVERED = Identifier.withDefaultNamespace("widget/button_highlighted");
	/** A push pin, drawn a pixel at a time inside the button's 3 px border. */
	private static final String[] PIN_GLYPH = {
			".#####.",
			"..###..",
			"..###..",
			"#######",
			"...#...",
			"...#...",
			"...#...",
	};
	private static final int PIN_GLYPH_OFFSET = (TalentTreeLayout.PIN_SIZE - PIN_GLYPH.length) / 2;

	/** Space between the level text and the badge's border. */
	private static final int BADGE_PADDING_X = 2;
	private static final int BADGE_BORDER = 1;
	/** Puts the 7 px tall digits in the middle of the badge. */
	private static final int BADGE_TEXT_Y = 2;
	/** Lines are 1 px wide with a 1 px dark outline all around, like the advancement screen's. */
	private static final int LINE_OUTLINE = 1;
	/** Between the header's title and the points text on the same line. */
	private static final int TITLE_GAP = 8;
	/** The tab tooltip's lines wrap at this width, like a talent's. */
	private static final int TOOLTIP_WIDTH = 170;
	private static final float CLICK_PITCH = 1.0F;
	private static final float FAIL_PITCH = 1.0F;

	/** The vanilla window title's dark grey, for the header on the light window. */
	private static final int TEXT_HEADER = 0xFF404040;
	/** Points waiting to be spent stand out in dark green. */
	private static final int TEXT_POINTS = 0xFF2A7A1A;
	/** The dark area behind the tree. */
	private static final int TREE_BACKGROUND = 0xFF303030;
	private static final int LINE_OUTLINE_COLOR = 0xFF000000;
	private static final int LINE_MET = 0xFFFFFFFF;
	private static final int LINE_UNMET = 0xFF5A5A5A;
	/** The pin while its tree isn't pinned; once pinned, it takes the tree's color. */
	private static final int PIN_UNPINNED = 0xFFA0A0A0;
	/** Multiplied into a locked talent's frame, and laid over its icon, to grey it out. */
	private static final int LOCKED_FRAME_TINT = 0xFF707070;
	private static final int LOCKED_ICON_SHADE = 0xB0282828;
	/** Multiplied into a toggled-off talent's frame: a cool grey distinct from locked, so it reads as owned-but-off. */
	private static final int TOGGLED_OFF_FRAME_TINT = 0xFF5A6B7C;
	/** Laid over a toggled-off talent's icon: shaded, but lighter than a locked one — the level is still there. */
	private static final int TOGGLED_OFF_ICON_SHADE = 0x80303030;
	private static final int NO_TINT = 0xFFFFFFFF;
	/** Like a hovered inventory slot. */
	private static final int HOVER_HIGHLIGHT = 0x50FFFFFF;
	private static final int BADGE_BACKGROUND = 0xFF181818;

	/** The tab the screen opens on: the one last shown, until the game closes. */
	private static TalentTree lastTree = TalentTree.GATHERER;

	private final @Nullable Screen lastScreen;
	private final Map<Talent, ItemStack> icons = new EnumMap<>(Talent.class);
	private final Set<Talent> parents = EnumSet.noneOf(Talent.class);
	private TalentTree tree;
	private TalentTreeLayout layout = TalentTreeLayout.forScreen(0, 0);

	/** @param lastScreen where Esc goes back to: the inventory when opened from its button, otherwise null. */
	public TalentScreen(@Nullable Screen lastScreen) {
		this(lastScreen, lastTree);
	}

	/** Opens on this tree's tab. */
	public TalentScreen(@Nullable Screen lastScreen, TalentTree tree) {
		super(TITLE);
		this.lastScreen = lastScreen;
		this.tree = tree;
		lastTree = tree;

		for (Talent talent : Talent.values()) {
			this.icons.put(talent, icon(talent));

			for (TalentPrereq prereq : talent.prereqs()) {
				this.parents.add(prereq.talent());
			}
		}
	}

	/** The talent's item, or a barrier if its id names no item (a typo, or an item from a newer version). */
	private static ItemStack icon(Talent talent) {
		Identifier id = Identifier.tryParse(talent.icon());
		// The item registry is a defaulted one: unknown ids give air.
		Item item = id != null ? BuiltInRegistries.ITEM.getValue(id) : Items.AIR;
		return new ItemStack(item == Items.AIR ? Items.BARRIER : item);
	}

	/**
	 * Shows this tree's tab, for client game tests: switches the open talent screen to it, or opens the screen on it.
	 * Call on the client thread.
	 */
	public static void openTab(TalentTree tree) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.gui.screen() instanceof TalentScreen screen) {
			screen.select(tree);
		} else {
			minecraft.gui.setScreen(new TalentScreen(null, tree));
		}
	}

	/** The tab the open talent screen shows, or the one it will open on. */
	public static TalentTree activeTree() {
		return Minecraft.getInstance().gui.screen() instanceof TalentScreen screen ? screen.tree : lastTree;
	}

	/**
	 * Middle of a talent's node, in GUI-scaled coordinates, for client game tests that hover or click one: from the
	 * open talent screen, or where it would be at the window's current size. Only the active tab's nodes are on
	 * screen, so open the talent's tree first ({@link #openTab}). Call on the client thread.
	 */
	public static Vector2i nodeCenter(Talent talent) {
		return currentLayout().nodeCenter(talent);
	}

	/** Middle of a tree's tab, in GUI-scaled coordinates, like {@link #nodeCenter}. */
	public static Vector2i tabCenter(TalentTree tree) {
		return currentLayout().tabCenter(tree);
	}

	/** Middle of the pin button, in GUI-scaled coordinates, like {@link #nodeCenter}. It pins the active tab's tree. */
	public static Vector2i pinCenter() {
		return currentLayout().pinCenter();
	}

	private static TalentTreeLayout currentLayout() {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.gui.screen() instanceof TalentScreen screen) {
			return screen.layout;
		}

		Window window = minecraft.getWindow();
		return TalentTreeLayout.forScreen(window.getGuiScaledWidth(), window.getGuiScaledHeight());
	}

	@Override
	protected void init() {
		// Also runs on every resize, so the window stays centered.
		this.layout = TalentTreeLayout.forScreen(this.width, this.height);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		// The dimmed world behind it, like the inventory, rather than the menu blur.
		return true;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.lastScreen);
	}

	/** Shows this tree's tab, and opens on it next time. */
	private void select(TalentTree tree) {
		this.tree = tree;
		lastTree = tree;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// The key that opens the tree also closes it, like the inventory key.
		if (TalentClient.OPEN_TALENTS.matches(event)) {
			this.onClose();
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		LocalPlayer player = this.minecraft.player;

		if (player == null) {
			return super.mouseClicked(event, doubleClick);
		}

		// Not a literal 0: 26.3 moved from GLFW to SDL, where the left button is 1.
		boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;

		if (left) {
			TalentTree tab = this.layout.tabAt(event.x(), event.y());

			if (tab != null) {
				this.select(tab);
				return true;
			}

			if (this.layout.isOnPin(event.x(), event.y())) {
				TreeXpHud.togglePin(this.tree);
				this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, CLICK_PITCH));
				return true;
			}
		}

		Talent talent = this.layout.talentAt(this.tree, event.x(), event.y());

		if (talent != null && left) {
			this.buy(player, talent);
			return true;
		}

		if (talent != null && event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			this.toggle(player, talent);
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	/** Asks the server for the next level if the client thinks it'll work; the server decides either way. */
	private void buy(LocalPlayer player, Talent talent) {
		if (Talents.canBuy(player, talent) == Talents.BuyResult.BOUGHT && ClientPlayNetworking.canSend(BuyTalentPayload.TYPE)) {
			ClientPlayNetworking.send(new BuyTalentPayload(talent.id()));
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, CLICK_PITCH));
		} else {
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, FAIL_PITCH));
		}
	}

	/** Asks the server to flip the toggle if the client offers the control at all; the server decides either way. */
	private void toggle(LocalPlayer player, Talent talent) {
		if (canToggle(player, talent) && ClientPlayNetworking.canSend(ToggleTalentPayload.TYPE)) {
			ClientPlayNetworking.send(new ToggleTalentPayload(talent.id()));
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, CLICK_PITCH));
		} else {
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, FAIL_PITCH));
		}
	}

	/** The client's side of the toggle control: a toggleable talent with a bought level, outside creative (always on there). */
	private static boolean canToggle(LocalPlayer player, Talent talent) {
		return talent.toggleable() && !player.hasInfiniteMaterials() && Talents.data(player).level(talent) >= 1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		LocalPlayer player = this.minecraft.player;

		if (player == null) {
			return;
		}

		TalentData data = Talents.data(player);
		Talent hovered = this.layout.talentAt(this.tree, mouseX, mouseY);
		TalentTree hoveredTab = this.layout.tabAt(mouseX, mouseY);
		boolean pinHovered = this.layout.isOnPin(mouseX, mouseY);

		// As in the advancements screen: the tree, then the window over it (its inner shadow falls on the tree's edges).
		graphics.nextStratum();
		this.extractTree(graphics, player, data, hovered);
		graphics.nextStratum();
		this.extractWindow(graphics);
		this.extractTabs(graphics);
		this.extractHeader(graphics, player, data, pinHovered);

		if (hoveredTab != null && hoveredTab != this.tree || pinHovered) {
			graphics.requestCursor(CursorTypes.POINTING_HAND);
		}

		if (hovered != null) {
			graphics.setTooltipForNextFrame(this.font, TalentTooltip.lines(this.font, player, hovered), mouseX, mouseY);
		} else if (hoveredTab != null) {
			graphics.setTooltipForNextFrame(this.font, this.tabTooltip(player, hoveredTab), mouseX, mouseY);
		} else if (pinHovered) {
			String key = TreeXpHud.pinned() == this.tree ? "screen.imsotalented.unpin" : "screen.imsotalented.pin";
			graphics.setTooltipForNextFrame(this.font, this.font.split(Component.translatable(key), TOOLTIP_WIDTH), mouseX, mouseY);
		}
	}

	/** The dark area, then every connector of the active tree, then its nodes over their ends. */
	private void extractTree(GuiGraphicsExtractor graphics, LocalPlayer player, TalentData data, @Nullable Talent hovered) {
		int insideLeft = this.layout.insideLeft();
		int insideTop = this.layout.insideTop();
		graphics.fill(insideLeft, insideTop, insideLeft + this.layout.insideWidth(), insideTop + this.layout.insideHeight(), TREE_BACKGROUND);
		this.extractConnectors(graphics, data);

		for (Talent talent : this.tree.talents()) {
			this.extractNode(graphics, player, data, talent, talent == hovered);
		}
	}

	/**
	 * All outlines first, then the unmet lines, then the met ones, so an outline never cuts through a line where two
	 * meet, and where a met line and an unmet one share a stretch (Lifesteal's line runs down Stronger Weapons' stem),
	 * the stretch shows met.
	 */
	private void extractConnectors(GuiGraphicsExtractor graphics, TalentData data) {
		List<TalentTreeLayout.Connector> connectors = this.layout.connectors(this.tree);

		for (TalentTreeLayout.Connector connector : connectors) {
			for (TalentTreeLayout.Segment segment : connector.segments()) {
				graphics.fill(segment.x0() - LINE_OUTLINE, segment.y0() - LINE_OUTLINE, segment.x1() + 1 + LINE_OUTLINE, segment.y1() + 1 + LINE_OUTLINE, LINE_OUTLINE_COLOR);
			}
		}

		for (boolean met : new boolean[] {false, true}) {
			for (TalentTreeLayout.Connector connector : connectors) {
				if (data.isMet(connector.prereq()) != met) {
					continue;
				}

				for (TalentTreeLayout.Segment segment : connector.segments()) {
					graphics.fill(segment.x0(), segment.y0(), segment.x1() + 1, segment.y1() + 1, met ? LINE_MET : LINE_UNMET);
				}
			}
		}
	}

	private void extractNode(GuiGraphicsExtractor graphics, LocalPlayer player, TalentData data, Talent talent, boolean hovered) {
		Vector2i position = this.layout.nodePosition(talent);
		int x = position.x;
		int y = position.y;
		int level = data.level(talent);
		boolean locked = !data.isUnlocked(talent);
		// Owned but toggled off: a level-0 talent can never be in the set, so it always reads locked or plain.
		boolean off = !locked && level > 0 && data.isDisabled(talent);
		int iconX = x + TalentTreeLayout.ICON_OFFSET;
		int iconY = y + TalentTreeLayout.ICON_OFFSET;
		int size = TalentTreeLayout.NODE_SIZE;

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.frameSprite(talent, level), x, y, size, size, locked ? LOCKED_FRAME_TINT : off ? TOGGLED_OFF_FRAME_TINT : NO_TINT);
		graphics.fakeItem(this.icons.get(talent), iconX, iconY);

		if (locked) {
			graphics.fill(iconX, iconY, iconX + TalentTreeLayout.ICON_SIZE, iconY + TalentTreeLayout.ICON_SIZE, LOCKED_ICON_SHADE);
		} else if (off) {
			graphics.fill(iconX, iconY, iconX + TalentTreeLayout.ICON_SIZE, iconY + TalentTreeLayout.ICON_SIZE, TOGGLED_OFF_ICON_SHADE);
		} else if (hovered) {
			graphics.fill(iconX, iconY, iconX + TalentTreeLayout.ICON_SIZE, iconY + TalentTreeLayout.ICON_SIZE, HOVER_HIGHLIGHT);
		}

		if (off) {
			// The display string of the OFF badge; the badge mechanism takes a plain string like the level plate.
			this.extractBadge(graphics, x + size / 2, y + TalentTreeLayout.BADGE_TOP, Component.translatable("screen.imsotalented.off_badge").getString(), BadgeStyle.TOGGLED_OFF);
		} else {
			this.extractBadge(graphics, x + size / 2, y + TalentTreeLayout.BADGE_TOP, level + "/" + talent.maxLevel(), BadgeStyle.of(Talents.canBuy(player, talent)));
		}
	}

	/**
	 * Grey until bought, gold once it has a level, spiky gold when maxed. Talents that unlock others get the rounded
	 * frame so the tree's structure shows even before anything is bought.
	 */
	private Identifier frameSprite(Talent talent, int level) {
		if (level >= talent.maxLevel()) {
			return MAXED_FRAME;
		}

		if (this.parents.contains(talent)) {
			return level > 0 ? PARENT_FRAME_BOUGHT : PARENT_FRAME;
		}

		return level > 0 ? TASK_FRAME_BOUGHT : TASK_FRAME;
	}

	/** A small "level/max" plate, centered under the icon and overlapping the frame's bottom edge. */
	private void extractBadge(GuiGraphicsExtractor graphics, int centerX, int top, String text, BadgeStyle style) {
		int width = this.font.width(text) + 2 * (BADGE_PADDING_X + BADGE_BORDER);
		int left = centerX - width / 2;
		int bottom = top + TalentTreeLayout.BADGE_HEIGHT;

		graphics.fill(left, top, left + width, bottom, style.border);
		graphics.fill(left + BADGE_BORDER, top + BADGE_BORDER, left + width - BADGE_BORDER, bottom - BADGE_BORDER, BADGE_BACKGROUND);
		graphics.text(this.font, text, left + BADGE_BORDER + BADGE_PADDING_X, top + BADGE_TEXT_Y, style.text, false);
	}

	/**
	 * The advancements window's texture at this screen's size: borders and corners as they are, the header's and the
	 * dark area's plain middles stretched. The dark area is see-through apart from its inner shadow.
	 */
	private void extractWindow(GuiGraphicsExtractor graphics) {
		int insideWidth = this.layout.insideWidth();
		int insideHeight = this.layout.insideHeight();
		int[] columnWidths = {
				WINDOW_COLUMNS[1] - WINDOW_COLUMNS[0],
				WINDOW_COLUMNS[2] - WINDOW_COLUMNS[1] + insideWidth - VANILLA_INSIDE_WIDTH,
				WINDOW_COLUMNS[3] - WINDOW_COLUMNS[2]};
		int[] rowHeights = {
				WINDOW_ROWS[1] - WINDOW_ROWS[0],
				WINDOW_ROWS[2] - WINDOW_ROWS[1] + TalentTreeLayout.HEADER_HEIGHT - VANILLA_HEADER_HEIGHT,
				WINDOW_ROWS[3] - WINDOW_ROWS[2],
				WINDOW_ROWS[4] - WINDOW_ROWS[3] + insideHeight - VANILLA_INSIDE_HEIGHT,
				WINDOW_ROWS[5] - WINDOW_ROWS[4]};
		int y = this.layout.top();

		for (int row = 0; row < rowHeights.length; row++) {
			int x = this.layout.left();

			for (int column = 0; column < columnWidths.length; column++) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, WINDOW_TEXTURE, x, y, WINDOW_COLUMNS[column], WINDOW_ROWS[row],
						columnWidths[column], rowHeights[row],
						WINDOW_COLUMNS[column + 1] - WINDOW_COLUMNS[column], WINDOW_ROWS[row + 1] - WINDOW_ROWS[row],
						WINDOW_TEXTURE_SIZE, WINDOW_TEXTURE_SIZE);
				x += columnWidths[column];
			}

			y += rowHeights[row];
		}
	}

	/**
	 * The vanilla top-row tabs, over the window's border: the first tab's sprite lines up with the window's left edge,
	 * and the selected one opens into the header. Each shows its tree's root icon.
	 */
	private void extractTabs(GuiGraphicsExtractor graphics) {
		for (TalentTree tab : TalentTree.values()) {
			Vector2i position = this.layout.tabPosition(tab);
			boolean selected = tab == this.tree;
			boolean first = tab.ordinal() == 0;
			Identifier sprite = first ? (selected ? TAB_FIRST_SELECTED : TAB_FIRST) : (selected ? TAB_SELECTED : TAB);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, position.x, position.y, TalentTreeLayout.TAB_WIDTH, TalentTreeLayout.TAB_HEIGHT);
		}

		for (TalentTree tab : TalentTree.values()) {
			Vector2i position = this.layout.tabPosition(tab);
			graphics.fakeItem(this.icons.get(tab.root()), position.x + TalentTreeLayout.TAB_ICON_X, position.y + TalentTreeLayout.TAB_ICON_Y);
		}
	}

	/**
	 * Two lines on the light header. The first: the tree's name and level, and on the right its points to spend (or why
	 * points don't matter). The second: the XP bar, the XP into the level and what the level takes, and the pin button.
	 */
	private void extractHeader(GuiGraphicsExtractor graphics, LocalPlayer player, TalentData data, boolean pinHovered) {
		int left = this.layout.left() + TalentTreeLayout.HEADER_X;
		int right = this.layout.left() + this.layout.width() - TalentTreeLayout.HEADER_X;
		int top = this.layout.top();
		TalentRules.Progress progress = Talents.progress(player, this.tree);

		Component title = Component.translatable("screen.imsotalented.tree_level", Component.translatable(this.tree.translationKey()), progress.level());
		graphics.text(this.font, title, left, top + TalentTreeLayout.TITLE_Y, TEXT_HEADER, false);

		int points = Talents.points(player, this.tree);
		Component status = player.hasInfiniteMaterials() ? Component.translatable("screen.imsotalented.free")
				: data.pointsSpent(this.tree) >= this.tree.maxLevel() ? Component.translatable("screen.imsotalented.all_maxed")
				: Component.translatable("screen.imsotalented.points", points);
		int statusColor = !player.hasInfiniteMaterials() && points > 0 ? TEXT_POINTS : TEXT_HEADER;
		FormattedCharSequence statusLine = this.fit(status, right - left - this.font.width(title) - TITLE_GAP);
		graphics.text(this.font, statusLine, right - this.font.width(statusLine), top + TalentTreeLayout.TITLE_Y, statusColor, false);

		TreeXpBar.extract(graphics, left, top + TalentTreeLayout.XP_BAR_Y, TalentTreeLayout.XP_BAR_WIDTH, progress.fraction(), this.tree.color());
		Component xp = progress.maxed() ? Component.translatable("screen.imsotalented.tree_maxed")
				: Component.translatable("screen.imsotalented.tree_xp", progress.into(), progress.needed());
		int xpX = left + TalentTreeLayout.XP_BAR_WIDTH + TalentTreeLayout.XP_TEXT_GAP;
		graphics.text(this.font, this.fit(xp, this.layout.xpTextMaxWidth()), xpX, top + TalentTreeLayout.XP_TEXT_Y, TEXT_HEADER, false);

		this.extractPin(graphics, pinHovered);
	}

	/** A small vanilla button with a push pin, grey while this tree isn't pinned and in the tree's color once it is. */
	private void extractPin(GuiGraphicsExtractor graphics, boolean hovered) {
		Vector2i position = this.layout.pinPosition();
		int size = TalentTreeLayout.PIN_SIZE;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? PIN_BUTTON_HOVERED : PIN_BUTTON, position.x, position.y, size, size);

		int color = TreeXpHud.pinned() == this.tree ? this.tree.color() : PIN_UNPINNED;
		int glyphX = position.x + PIN_GLYPH_OFFSET;
		int glyphY = position.y + PIN_GLYPH_OFFSET;

		for (int row = 0; row < PIN_GLYPH.length; row++) {
			for (int column = 0; column < PIN_GLYPH[row].length(); column++) {
				if (PIN_GLYPH[row].charAt(column) == '#') {
					graphics.fill(glyphX + column, glyphY + row, glyphX + column + 1, glyphY + row + 1, color);
				}
			}
		}
	}

	/** The tree's name, level and points, and what earns its XP. */
	private List<FormattedCharSequence> tabTooltip(LocalPlayer player, TalentTree tab) {
		List<FormattedCharSequence> lines = new ArrayList<>();
		Component name = Component.translatable(tab.translationKey());
		lines.addAll(this.font.split(Component.translatable("screen.imsotalented.tab", name, Talents.treeLevel(player, tab), Talents.points(player, tab)), TOOLTIP_WIDTH));
		lines.addAll(this.font.split(Component.translatable("screen.imsotalented.earn." + tab.id()).withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
		return lines;
	}

	/** The text as it is if it fits in {@code maxWidth}, else cut short with an ellipsis (a long translation). */
	private FormattedCharSequence fit(Component text, int maxWidth) {
		if (this.font.width(text) <= maxWidth) {
			return text.getVisualOrderText();
		}

		FormattedText cut = this.font.substrByWidth(text, Math.max(0, maxWidth - this.font.width(CommonComponents.ELLIPSIS)));
		return Language.getInstance().getVisualOrder(FormattedText.composite(cut, CommonComponents.ELLIPSIS));
	}

	/** Badge colors by what a click would do. */
	private enum BadgeStyle {
		BUYABLE(0xFF4E9A1E, 0xFF80FF20),
		NO_POINTS(0xFF6A6A6A, 0xFFE0E0E0),
		LOCKED(0xFF3C3C3C, 0xFF7A7A7A),
		MAXED(0xFFC89B2C, 0xFFFFD84A),
		TOGGLED_OFF(0xFF7A3B3B, 0xFFFF9A8A);

		private final int border;
		private final int text;

		BadgeStyle(int border, int text) {
			this.border = border;
			this.text = text;
		}

		static BadgeStyle of(Talents.BuyResult result) {
			return switch (result) {
				case BOUGHT -> BUYABLE;
				case NO_POINTS -> NO_POINTS;
				case LOCKED -> LOCKED;
				case MAXED -> MAXED;
			};
		}
	}
}
