package no.kvaksrud.imsotalented.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
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
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationTrigger;
import net.minecraft.client.gui.narration.ScreenNarrationCollector;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.BelowOrAboveWidgetTooltipPositioner;
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
 * The talent trees, in the vanilla advancements window: a tab per {@link TalentTree} along the left, showing its root's
 * icon, and beside them the active tree. Its header has the tree's name and level, its XP bar in the tree's color with
 * the XP into the level, the points left to spend, and a pin button for the HUD bar ({@link TreeXpHud}). Each talent
 * is a vanilla advancement frame with its icon and a level badge, at its row and column ({@link TalentTreeLayout}).
 * Left-clicking a node asks the server to buy its next level with a point of the tree; right-clicking a toggleable
 * talent the player owns flips its on/off toggle, in every game mode. The server checks everything and updates the
 * synced talent attachment, and since this screen draws from {@link Talents#data} every frame it simply shows the new
 * state once it arrives.
 *
 * <p>Frames say where a talent stands at a glance: grey while nothing is bought, gold once it has a level, the rounded
 * goal frame for a talent that unlocks others, and the spiky challenge frame once it is maxed. A talent whose
 * prereqs aren't met yet is dimmed, and a toggled-off talent is dimmed to a cool grey with an OFF badge. The badge
 * color says what a click would do: green buys, grey has no point to spend, gold maxed, red toggled off. A talent
 * that isn't in the game yet ({@link Talent#implemented()}) has a muted SOON badge instead, whatever its level, and
 * can't be bought or toggled. A connector lights up once its prereq is met.
 *
 * <p>The keyboard and the narrator can use it too: the tabs, the pin and the active tree's nodes are focusable widgets.
 * Tab and Shift+Tab step through them (the tabs top to bottom, the pin, then the nodes row by row), the arrow keys move
 * to the nearest one that way, and Enter or Space does what a left click does: shows the tab, buys the node's next
 * level, or pins the tree. Shift+Enter (or Shift+Space) on a node is its right click: it flips the toggle. While the
 * keyboard is in use the focused element is outlined in white (the pin gets its highlighted button) and shows its
 * tooltip beside it. The narrator reads a tab's tree, level and points, a node's tooltip, and the pin's tooltip. The
 * mouse works as it always has and never moves the focus.
 *
 * <p>On a server without the mod, or with another version of it ({@link ImSoTalentedClient#serverHasMod}), the header
 * says so where the tree's level and XP would be, no badge or tooltip offers a buy or a toggle, and clicks only play
 * the fail sound.
 *
 * <p>It doesn't pause the game, like the inventory: in singleplayer a paused server would handle the buy but not send
 * the new talents until the game resumes, so the screen would show stale levels. The last tab stays selected until the
 * game closes.
 */
public class TalentScreen extends Screen {
	private static final Component TITLE = Component.translatable("screen.imsotalented.talents");
	private static final Component NO_SERVER_MOD = Component.translatable("screen.imsotalented.no_server_mod");

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

	private static final Identifier TAB_FIRST = Identifier.withDefaultNamespace("advancements/tab_left_top");
	private static final Identifier TAB_FIRST_SELECTED = Identifier.withDefaultNamespace("advancements/tab_left_top_selected");
	private static final Identifier TAB = Identifier.withDefaultNamespace("advancements/tab_left_middle");
	private static final Identifier TAB_SELECTED = Identifier.withDefaultNamespace("advancements/tab_left_middle_selected");

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
	/** Puts the digits in the middle of the badge. */
	private static final int BADGE_TEXT_Y = (TalentTreeLayout.BADGE_HEIGHT - TalentTreeLayout.CAP_HEIGHT) / 2;
	/** Lines are 1 px wide with a 1 px dark outline all around, like the advancement screen's. */
	private static final int LINE_OUTLINE = 1;
	/** Between the header's title and the points text on the same line. */
	private static final int TITLE_GAP = 8;
	/** The header's text runs between the window's left and right header margins. */
	private static final int HEADER_TEXT_WIDTH = TalentTreeLayout.WIDTH - 2 * TalentTreeLayout.HEADER_X;
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
	/** Around the keyboard focus, like the white border of a focused vanilla button. */
	private static final int FOCUS_OUTLINE = 0xFFFFFFFF;
	private static final int BADGE_BACKGROUND = 0xFF181818;

	/** The tab the screen opens on: the one last shown, until the game closes. */
	private static TalentTree lastTree = TalentTree.GATHERER;

	private final boolean fromInventory;
	private final Map<Talent, ItemStack> icons = new EnumMap<>(Talent.class);
	private final Set<Talent> parents = EnumSet.noneOf(Talent.class);
	private final Map<TalentTree, TabWidget> tabWidgets = new EnumMap<>(TalentTree.class);
	private final Map<Talent, NodeWidget> nodeWidgets = new EnumMap<>(Talent.class);
	private @Nullable PinWidget pinWidget;
	private TalentTree tree;
	private TalentTreeLayout layout = TalentTreeLayout.forScreen(0, 0);

	/** What the cached texts below were worked out from; they're thrown away as soon as any of it changes. */
	private @Nullable Basis basis;
	private final Map<Talent, NodeText> nodeTexts = new EnumMap<>(Talent.class);
	private final Map<TalentTree, TabText> tabTexts = new EnumMap<>(TalentTree.class);
	private final Map<TalentTree, Header> headers = new EnumMap<>(TalentTree.class);
	private @Nullable List<FormattedCharSequence> noServerModLines;

	/**
	 * Opens on the tab last shown.
	 *
	 * @param fromInventory whether the survival inventory's button opened it: leaving then opens a fresh inventory
	 *                      (the button closed the old one's menu on the way here), otherwise it goes back to the game
	 */
	public TalentScreen(boolean fromInventory) {
		super(TITLE);
		this.fromInventory = fromInventory;
		this.tree = lastTree;

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
	 * For client game tests only: the tab the open talent screen shows, or the one it will open on. Call on the client
	 * thread.
	 */
	@VisibleForTesting
	public static TalentTree activeTree() {
		return Minecraft.getInstance().gui.screen() instanceof TalentScreen screen ? screen.tree : lastTree;
	}

	/**
	 * For client game tests only, to hover or click a node: the middle of a talent's node, in GUI-scaled coordinates,
	 * from the open talent screen, or where it would be at the window's current size. Only the active tab's nodes are
	 * on screen, so show the talent's tree first. Call on the client thread.
	 */
	@VisibleForTesting
	public static Vector2i nodeCenter(Talent talent) {
		return currentLayout().nodeCenter(talent);
	}

	/** For client game tests only: the middle of a tree's tab, in GUI-scaled coordinates, like {@link #nodeCenter}. */
	@VisibleForTesting
	public static Vector2i tabCenter(TalentTree tree) {
		return currentLayout().tabCenter(tree);
	}

	/**
	 * For client game tests only: the middle of the pin button, in GUI-scaled coordinates, like {@link #nodeCenter}. It
	 * pins the active tab's tree.
	 */
	@VisibleForTesting
	public static Vector2i pinCenter() {
		return currentLayout().pinCenter();
	}

	/**
	 * For client game tests only: the talent whose node has the keyboard focus in the open talent screen, or null (no
	 * screen, nothing focused, or a tab or the pin). Call on the client thread.
	 */
	@VisibleForTesting
	public static @Nullable Talent focusedTalent() {
		return focusedWidget() instanceof NodeWidget node ? node.talent : null;
	}

	/** For client game tests only: the tab with the keyboard focus in the open talent screen, or null, like {@link #focusedTalent}. */
	@VisibleForTesting
	public static @Nullable TalentTree focusedTab() {
		return focusedWidget() instanceof TabWidget tab ? tab.tab : null;
	}

	/** For client game tests only: whether the pin button has the keyboard focus in the open talent screen. */
	@VisibleForTesting
	public static boolean isPinFocused() {
		return focusedWidget() instanceof PinWidget;
	}

	/**
	 * For client game tests only: what the narrator says for the element with the keyboard focus in the open talent
	 * screen (its own part, without the screen's title and position), or an empty string. Call on the client thread.
	 */
	@VisibleForTesting
	public static String focusedNarration() {
		TalentWidget widget = focusedWidget();

		if (widget == null) {
			return "";
		}

		ScreenNarrationCollector collector = new ScreenNarrationCollector();
		collector.update(widget::updateNarration, NarrationTrigger.KEYBOARD);
		return collector.collectNarrationText(true);
	}

	private static @Nullable TalentScreen openScreen() {
		return Minecraft.getInstance().gui.screen() instanceof TalentScreen screen ? screen : null;
	}

	private static @Nullable TalentWidget focusedWidget() {
		TalentScreen screen = openScreen();
		return screen != null && screen.getFocused() instanceof TalentWidget widget ? widget : null;
	}

	private static TalentTreeLayout currentLayout() {
		TalentScreen screen = openScreen();

		if (screen != null) {
			return screen.layout;
		}

		Window window = Minecraft.getInstance().getWindow();
		return TalentTreeLayout.forScreen(window.getGuiScaledWidth(), window.getGuiScaledHeight());
	}

	@Override
	protected void init() {
		this.layout = TalentTreeLayout.forScreen(this.width, this.height);
		this.tabWidgets.clear();
		this.nodeWidgets.clear();

		// Children in focus order: the tabs top to bottom, the pin, then the nodes row by row, left to right. Every
		// tree's nodes are here; only the active tree's are visible, and so focusable and narrated.
		for (TalentTree tab : TalentTree.values()) {
			this.tabWidgets.put(tab, this.addWidget(new TabWidget(tab)));
		}

		this.pinWidget = this.addWidget(new PinWidget());
		List<Talent> talents = new ArrayList<>(List.of(Talent.values()));
		talents.sort(Comparator.comparingInt(Talent::row).thenComparingDouble(Talent::column));

		for (Talent talent : talents) {
			this.nodeWidgets.put(talent, this.addWidget(new NodeWidget(talent)));
		}

		this.placeWidgets();
	}

	/** On a resize: the window stays centered, and the widgets, with the keyboard focus, stay as they are. */
	@Override
	protected void repositionElements() {
		this.layout = TalentTreeLayout.forScreen(this.width, this.height);
		this.placeWidgets();
	}

	private void placeWidgets() {
		for (TabWidget widget : this.tabWidgets.values()) {
			Vector2i position = this.layout.tabPosition(widget.tab);
			widget.setRectangle(TalentTreeLayout.TAB_WIDTH, TalentTreeLayout.TAB_HEIGHT, position.x, position.y);
		}

		if (this.pinWidget != null) {
			Vector2i position = this.layout.pinPosition();
			this.pinWidget.setRectangle(TalentTreeLayout.PIN_SIZE, TalentTreeLayout.PIN_SIZE, position.x, position.y);
		}

		for (NodeWidget widget : this.nodeWidgets.values()) {
			Vector2i position = this.layout.nodePosition(widget.talent);
			widget.setRectangle(TalentTreeLayout.NODE_SIZE, TalentTreeLayout.NODE_FOOTPRINT, position.x, position.y);
		}

		this.showActiveNodes();
	}

	/** Only the active tree's nodes take part; the focus leaves a node whose tree was just hidden. */
	private void showActiveNodes() {
		for (NodeWidget widget : this.nodeWidgets.values()) {
			widget.visible = widget.talent.tree() == this.tree;
		}

		if (this.getFocused() instanceof NodeWidget node && !node.visible) {
			this.setFocused(null);
		}
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
		LocalPlayer player = this.minecraft.player;
		this.minecraft.gui.setScreen(this.fromInventory && player != null ? new InventoryScreen(player) : null);
	}

	/** Shows this tree's tab, and opens on it next time. */
	private void select(TalentTree tree) {
		this.tree = tree;
		lastTree = tree;
		this.showActiveNodes();
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
				this.togglePin();
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

	private void togglePin() {
		TreeXpHud.togglePin(this.tree);
		this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, CLICK_PITCH));
	}

	/**
	 * Asks the server for the next level if the client thinks it'll work; the server decides either way. Nothing is
	 * asked of a server without the mod.
	 */
	private void buy(LocalPlayer player, Talent talent) {
		if (ImSoTalentedClient.serverHasMod() && Talents.canBuy(player, talent) == Talents.BuyResult.BUYABLE
				&& ClientPlayNetworking.canSend(BuyTalentPayload.TYPE)) {
			ClientPlayNetworking.send(new BuyTalentPayload(talent.id()));
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, CLICK_PITCH));
		} else {
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, FAIL_PITCH));
		}
	}

	/**
	 * Asks the server to switch the talent to the other state than the one shown, if the player may toggle it; the
	 * server decides either way. The state goes with the request, so a double click asks for the same state twice
	 * rather than flipping it back.
	 */
	private void toggle(LocalPlayer player, Talent talent) {
		if (ImSoTalentedClient.serverHasMod() && Talents.canToggle(player, talent) && ClientPlayNetworking.canSend(ToggleTalentPayload.TYPE)) {
			boolean on = Talents.data(player).isDisabled(talent);
			ClientPlayNetworking.send(new ToggleTalentPayload(talent.id(), on));
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, CLICK_PITCH));
		} else {
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, FAIL_PITCH));
		}
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
		this.updateHovered(hovered, hoveredTab, pinHovered);
		// The focus only shows while the keyboard is in use, so the mouse sees the screen as it always was.
		TalentWidget focused = this.minecraft.getLastInputType().isKeyboard() && this.getFocused() instanceof TalentWidget widget
				&& widget.isActive() ? widget : null;

		// As in the advancements screen: the tree, then the window over it (its inner shadow falls on the tree's edges).
		graphics.nextStratum();
		this.extractTree(graphics, player, data, hovered, focused instanceof NodeWidget node ? node.talent : null);
		graphics.nextStratum();
		this.extractWindow(graphics);
		this.extractTabs(graphics, focused instanceof TabWidget tab ? tab.tab : null);
		this.extractHeader(graphics, player, pinHovered || focused instanceof PinWidget);

		if (hoveredTab != null && hoveredTab != this.tree || pinHovered) {
			graphics.requestCursor(CursorTypes.POINTING_HAND);
		}

		if (hovered != null) {
			graphics.setTooltipForNextFrame(this.font, this.nodeText(player, hovered).lines(), mouseX, mouseY);
		} else if (hoveredTab != null) {
			graphics.setTooltipForNextFrame(this.font, this.tabText(player, hoveredTab).lines(), mouseX, mouseY);
		} else if (pinHovered) {
			graphics.setTooltipForNextFrame(this.font, this.pinTooltip(), mouseX, mouseY);
		} else if (focused != null) {
			// Beside the focused element rather than at the mouse, like a focused vanilla widget's tooltip.
			graphics.setTooltipForNextFrame(this.font, focused.tooltip(player), new BelowOrAboveWidgetTooltipPositioner(focused.getRectangle()),
					mouseX, mouseY, true);
		}
	}

	/** Tells the widgets what the mouse is over, which the narrator reads when nothing has the focus. */
	private void updateHovered(@Nullable Talent hovered, @Nullable TalentTree hoveredTab, boolean pinHovered) {
		for (TabWidget widget : this.tabWidgets.values()) {
			widget.setHovered(widget.tab == hoveredTab);
		}

		for (NodeWidget widget : this.nodeWidgets.values()) {
			widget.setHovered(widget.talent == hovered);
		}

		if (this.pinWidget != null) {
			this.pinWidget.setHovered(pinHovered);
		}
	}

	/** The dark area, then every connector of the active tree, then its nodes over their ends. */
	private void extractTree(GuiGraphicsExtractor graphics, LocalPlayer player, TalentData data, @Nullable Talent hovered, @Nullable Talent focused) {
		int insideLeft = this.layout.insideLeft();
		int insideTop = this.layout.insideTop();
		graphics.fill(insideLeft, insideTop, insideLeft + this.layout.insideWidth(), insideTop + this.layout.insideHeight(), TREE_BACKGROUND);
		this.extractConnectors(graphics, data);

		for (Talent talent : this.tree.talents()) {
			this.extractNode(graphics, player, data, talent, talent == hovered || talent == focused, talent == focused);
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

	private void extractNode(GuiGraphicsExtractor graphics, LocalPlayer player, TalentData data, Talent talent, boolean highlighted, boolean focused) {
		Vector2i position = this.layout.nodePosition(talent);
		int x = position.x;
		int y = position.y;
		int level = data.level(talent);
		boolean locked = !data.isUnlocked(talent);
		// Owned but toggled off: a level-0 talent can never be in the set, so it always reads locked or plain. A talent
		// that isn't in the game yet has no toggle, so it never reads off.
		boolean off = talent.implemented() && !locked && level > 0 && data.isDisabled(talent);
		int iconX = x + TalentTreeLayout.ICON_OFFSET;
		int iconY = y + TalentTreeLayout.ICON_OFFSET;
		int size = TalentTreeLayout.NODE_SIZE;

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.frameSprite(talent, level), x, y, size, size, locked ? LOCKED_FRAME_TINT : off ? TOGGLED_OFF_FRAME_TINT : NO_TINT);
		graphics.fakeItem(this.icons.get(talent), iconX, iconY);

		if (locked) {
			graphics.fill(iconX, iconY, iconX + TalentTreeLayout.ICON_SIZE, iconY + TalentTreeLayout.ICON_SIZE, LOCKED_ICON_SHADE);
		} else if (off) {
			graphics.fill(iconX, iconY, iconX + TalentTreeLayout.ICON_SIZE, iconY + TalentTreeLayout.ICON_SIZE, TOGGLED_OFF_ICON_SHADE);
		} else if (highlighted) {
			graphics.fill(iconX, iconY, iconX + TalentTreeLayout.ICON_SIZE, iconY + TalentTreeLayout.ICON_SIZE, HOVER_HIGHLIGHT);
		}

		if (focused) {
			// Just outside the frame; the badge is drawn over its bottom edge.
			graphics.outline(x - 1, y - 1, size + 2, size + 2, FOCUS_OUTLINE);
		}

		int badgeX = x + size / 2;
		int badgeY = y + TalentTreeLayout.BADGE_TOP;

		// The badges' display strings; the badge mechanism takes a plain string like the level plate.
		if (!talent.implemented()) {
			this.extractBadge(graphics, badgeX, badgeY, Component.translatable("screen.imsotalented.not_yet_badge").getString(), BadgeStyle.NOT_YET);
		} else if (off) {
			this.extractBadge(graphics, badgeX, badgeY, Component.translatable("screen.imsotalented.off_badge").getString(), BadgeStyle.TOGGLED_OFF);
		} else {
			BadgeStyle style = BadgeStyle.of(Talents.canBuy(player, talent), ImSoTalentedClient.serverHasMod());
			this.extractBadge(graphics, badgeX, badgeY, level + "/" + talent.maxLevel(), style);
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
	 * The vanilla left-side tabs, over the window's border: the first tab's sprite lines up with the window's top edge,
	 * and the selected one opens into the window. Each shows its tree's root icon; the focused one has it outlined.
	 */
	private void extractTabs(GuiGraphicsExtractor graphics, @Nullable TalentTree focused) {
		for (TalentTree tab : TalentTree.values()) {
			Vector2i position = this.layout.tabPosition(tab);
			boolean selected = tab == this.tree;
			boolean first = tab.ordinal() == 0;
			Identifier sprite = first ? (selected ? TAB_FIRST_SELECTED : TAB_FIRST) : (selected ? TAB_SELECTED : TAB);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, position.x, position.y, TalentTreeLayout.TAB_WIDTH, TalentTreeLayout.TAB_HEIGHT);
		}

		for (TalentTree tab : TalentTree.values()) {
			Vector2i position = this.layout.tabPosition(tab);
			int iconX = position.x + TalentTreeLayout.TAB_ICON_X;
			int iconY = position.y + TalentTreeLayout.TAB_ICON_Y;
			graphics.fakeItem(this.icons.get(tab.root()), iconX, iconY);

			if (tab == focused) {
				graphics.outline(iconX - 1, iconY - 1, TalentTreeLayout.ICON_SIZE + 2, TalentTreeLayout.ICON_SIZE + 2, FOCUS_OUTLINE);
			}
		}
	}

	/**
	 * Two lines on the light header. The first: the tree's name and level, and on the right its points to spend (or why
	 * points don't matter). The second: the XP bar, the XP into the level and what the level takes, and the pin button.
	 * On a server without the mod, the two lines say so instead.
	 */
	private void extractHeader(GuiGraphicsExtractor graphics, LocalPlayer player, boolean pinHighlighted) {
		int left = this.layout.left() + TalentTreeLayout.HEADER_X;
		int right = left + HEADER_TEXT_WIDTH;
		int top = this.layout.top();

		if (!ImSoTalentedClient.serverHasMod()) {
			List<FormattedCharSequence> lines = this.noServerModLines(player);
			graphics.text(this.font, lines.getFirst(), left, top + TalentTreeLayout.TITLE_Y, TEXT_HEADER, false);

			if (lines.size() > 1) {
				graphics.text(this.font, lines.get(1), left, top + TalentTreeLayout.XP_TEXT_Y, TEXT_HEADER, false);
			}

			this.extractPin(graphics, pinHighlighted);
			return;
		}

		Header header = this.header(player, this.tree);
		graphics.text(this.font, header.title(), left, top + TalentTreeLayout.TITLE_Y, TEXT_HEADER, false);
		graphics.text(this.font, header.status(), right - this.font.width(header.status()), top + TalentTreeLayout.TITLE_Y, header.statusColor(), false);

		TreeXpBar.extract(graphics, left, top + TalentTreeLayout.XP_BAR_Y, TalentTreeLayout.XP_BAR_WIDTH, header.fraction(), this.tree.color());
		int xpX = left + TalentTreeLayout.XP_BAR_WIDTH + TalentTreeLayout.XP_TEXT_GAP;
		graphics.text(this.font, header.xp(), xpX, top + TalentTreeLayout.XP_TEXT_Y, TEXT_HEADER, false);

		this.extractPin(graphics, pinHighlighted);
	}

	/** A small vanilla button with a push pin, grey while this tree isn't pinned and in the tree's color once it is. */
	private void extractPin(GuiGraphicsExtractor graphics, boolean highlighted) {
		Vector2i position = this.layout.pinPosition();
		int size = TalentTreeLayout.PIN_SIZE;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, highlighted ? PIN_BUTTON_HOVERED : PIN_BUTTON, position.x, position.y, size, size);

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

	/** What the pin button does for the active tree: pin it, or unpin it if it's the pinned one. */
	private Component pinText() {
		return Component.translatable(TreeXpHud.pinned() == this.tree ? "screen.imsotalented.unpin" : "screen.imsotalented.pin");
	}

	private List<FormattedCharSequence> pinTooltip() {
		return this.font.split(this.pinText(), TalentTooltip.MAX_WIDTH);
	}

	/** The text as it is if it fits in {@code maxWidth}, else cut short with an ellipsis (a long translation). */
	private FormattedCharSequence fit(Component text, int maxWidth) {
		if (this.font.width(text) <= maxWidth) {
			return text.getVisualOrderText();
		}

		FormattedText cut = this.font.substrByWidth(text, Math.max(0, maxWidth - this.font.width(CommonComponents.ELLIPSIS)));
		return Language.getInstance().getVisualOrder(FormattedText.composite(cut, CommonComponents.ELLIPSIS));
	}

	/**
	 * Throws the cached texts away if anything they were worked out from changed since they were. Every text getter
	 * calls it first, so a sync, a game mode change or a language change shows on the next frame.
	 */
	private void refreshTexts(LocalPlayer player) {
		Basis now = new Basis(Talents.data(player), Talents.rules(player), Talents.isCreative(player), ImSoTalentedClient.serverHasMod(), Language.getInstance());

		if (this.basis != null && this.basis.sameAs(now)) {
			return;
		}

		this.basis = now;
		this.nodeTexts.clear();
		this.tabTexts.clear();
		this.headers.clear();
		this.noServerModLines = null;
	}

	/** A node's tooltip and narration. */
	private NodeText nodeText(LocalPlayer player, Talent talent) {
		this.refreshTexts(player);
		return this.nodeTexts.computeIfAbsent(talent, _ -> {
			List<Component> text = TalentTooltip.text(player, talent, ImSoTalentedClient.serverHasMod());
			return new NodeText(TalentTooltip.lines(this.font, text), TalentTooltip.narration(text));
		});
	}

	/** A tab's tooltip, and the same as the narrator's title and hint: the tree's name, level and points, and what earns its XP. */
	private TabText tabText(LocalPlayer player, TalentTree tab) {
		this.refreshTexts(player);
		return this.tabTexts.computeIfAbsent(tab, _ -> {
			Component name = Component.translatable(tab.translationKey());
			Component title = Component.translatable("screen.imsotalented.tab", name, Talents.treeLevel(player, tab), Talents.points(player, tab));
			Component hint = Component.translatable("screen.imsotalented.earn." + tab.id()).withStyle(ChatFormatting.GRAY);
			List<FormattedCharSequence> lines = new ArrayList<>(this.font.split(title, TalentTooltip.MAX_WIDTH));
			lines.addAll(this.font.split(hint, TalentTooltip.MAX_WIDTH));
			return new TabText(List.copyOf(lines), title, hint);
		});
	}

	/** The header's texts and XP bar fill for a tree. */
	private Header header(LocalPlayer player, TalentTree tree) {
		this.refreshTexts(player);
		return this.headers.computeIfAbsent(tree, _ -> {
			TalentRules.Progress progress = Talents.progress(player, tree);
			Component title = Component.translatable("screen.imsotalented.tree_level", Component.translatable(tree.translationKey()), progress.level());

			int points = Talents.points(player, tree);
			boolean creative = Talents.isCreative(player);
			Component status = creative ? Component.translatable("screen.imsotalented.free")
					: Talents.data(player).pointsSpent(tree) >= tree.maxLevel() ? Component.translatable("screen.imsotalented.all_maxed")
					: Component.translatable("screen.imsotalented.points", points);
			int statusColor = !creative && points > 0 ? TEXT_POINTS : TEXT_HEADER;
			FormattedCharSequence statusLine = this.fit(status, HEADER_TEXT_WIDTH - this.font.width(title) - TITLE_GAP);

			Component xp = progress.maxed() ? Component.translatable("screen.imsotalented.tree_maxed")
					: Component.translatable("screen.imsotalented.tree_xp", progress.into(), progress.needed());
			return new Header(title, statusLine, statusColor, progress.fraction(), this.fit(xp, this.layout.xpTextMaxWidth()));
		});
	}

	/**
	 * The header's two lines on a server without the mod: the explanation wrapped to stay clear of the pin, the second
	 * line cut short with an ellipsis if a translation needs a third.
	 */
	private List<FormattedCharSequence> noServerModLines(LocalPlayer player) {
		this.refreshTexts(player);

		if (this.noServerModLines == null) {
			int width = HEADER_TEXT_WIDTH - TalentTreeLayout.PIN_SIZE - TalentTreeLayout.XP_TEXT_GAP;
			List<FormattedCharSequence> lines = this.font.split(NO_SERVER_MOD, width - this.font.width(CommonComponents.ELLIPSIS));

			if (lines.size() > 2) {
				lines = List.of(lines.get(0), FormattedCharSequence.composite(lines.get(1), CommonComponents.ELLIPSIS.getVisualOrderText()));
			}

			this.noServerModLines = lines.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : List.copyOf(lines);
		}

		return this.noServerModLines;
	}

	/**
	 * What the cached texts were worked out from. The synced talent data is an immutable record that's replaced on every
	 * sync, and the rules are replaced when the server sends them, so the same instances mean the same texts; the game
	 * mode, whether the server has the mod and the language can change without either.
	 */
	private record Basis(TalentData data, TalentRules rules, boolean creative, boolean live, Language language) {
		private boolean sameAs(Basis other) {
			return this.data == other.data && this.rules == other.rules && this.creative == other.creative && this.live == other.live
					&& this.language == other.language;
		}
	}

	private record NodeText(List<FormattedCharSequence> lines, Component narration) {
	}

	private record TabText(List<FormattedCharSequence> lines, Component title, Component hint) {
	}

	private record Header(Component title, FormattedCharSequence status, int statusColor, float fraction, FormattedCharSequence xp) {
	}

	/** Badge colors by what a click would do. */
	private enum BadgeStyle {
		BUYABLE(0xFF4E9A1E, 0xFF80FF20),
		NO_POINTS(0xFF6A6A6A, 0xFFE0E0E0),
		LOCKED(0xFF3C3C3C, 0xFF7A7A7A),
		MAXED(0xFFC89B2C, 0xFFFFD84A),
		TOGGLED_OFF(0xFF7A3B3B, 0xFFFF9A8A),
		/** Not in the game yet: muted, a little cooler than locked. */
		NOT_YET(0xFF44464E, 0xFF9497A3);

		private final int border;
		private final int text;

		BadgeStyle(int border, int text) {
			this.border = border;
			this.text = text;
		}

		/** On a server without the mod nothing can be bought, so nothing looks buyable. */
		static BadgeStyle of(Talents.BuyResult result, boolean live) {
			return switch (result) {
				case BUYABLE -> live ? BUYABLE : NO_POINTS;
				case NO_POINTS -> NO_POINTS;
				case LOCKED -> LOCKED;
				case MAXED -> MAXED;
				case NOT_YET -> NOT_YET;
			};
		}
	}

	/**
	 * A tab, a node or the pin as a widget, for the keyboard and the narrator: focusable while visible, activated with
	 * Enter or Space. The screen draws it in its own layers and handles the mouse on it, so the widget draws nothing
	 * and ignores clicks; it only learns from the screen whether the mouse is over it, for the narrator.
	 */
	private abstract static class TalentWidget extends AbstractWidget {
		private TalentWidget(Component message) {
			super(0, 0, 0, 0, message);
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		}

		void setHovered(boolean hovered) {
			this.isHovered = hovered;
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			return false;
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			if (!this.isActive() || !event.isSelection()) {
				return false;
			}

			this.activate(event);
			return true;
		}

		/** What Enter or Space (with {@code event}'s modifiers) does. */
		abstract void activate(KeyEvent event);

		/** The tooltip shown beside it while it has the keyboard focus, the same as on hover. */
		abstract List<FormattedCharSequence> tooltip(LocalPlayer player);

		/** Vanilla's "Press Enter to activate" while focused, "Click to activate" while hovered. */
		void addUsage(NarrationElementOutput output) {
			output.add(NarratedElementType.USAGE, Component.translatable(this.isFocused() ? "narration.button.usage.focused" : "narration.button.usage.hovered"));
		}
	}

	/** A tree's tab: Enter or Space shows it. Narrated like its tooltip: the tree's name, level and points, then what earns its XP. */
	private final class TabWidget extends TalentWidget {
		private final TalentTree tab;

		private TabWidget(TalentTree tab) {
			super(Component.translatable(tab.translationKey()));
			this.tab = tab;
		}

		@Override
		void activate(KeyEvent event) {
			TalentScreen.this.select(this.tab);
		}

		@Override
		List<FormattedCharSequence> tooltip(LocalPlayer player) {
			return TalentScreen.this.tabText(player, this.tab).lines();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			LocalPlayer player = Minecraft.getInstance().player;

			if (player == null) {
				return;
			}

			TabText text = TalentScreen.this.tabText(player, this.tab);
			output.add(NarratedElementType.TITLE, text.title());
			output.add(NarratedElementType.HINT, text.hint());

			if (this.tab != TalentScreen.this.tree) {
				this.addUsage(output);
			}
		}
	}

	/**
	 * A talent's node: Enter or Space buys its next level, like a left click; Shift+Enter or Shift+Space flips its
	 * toggle, like a right click. Narrated as its tooltip reads.
	 */
	private final class NodeWidget extends TalentWidget {
		private final Talent talent;

		private NodeWidget(Talent talent) {
			super(Component.translatable(talent.translationKey()));
			this.talent = talent;
		}

		@Override
		void activate(KeyEvent event) {
			LocalPlayer player = Minecraft.getInstance().player;

			if (player == null) {
				return;
			}

			if (event.hasShiftDown()) {
				TalentScreen.this.toggle(player, this.talent);
			} else {
				TalentScreen.this.buy(player, this.talent);
			}
		}

		@Override
		List<FormattedCharSequence> tooltip(LocalPlayer player) {
			return TalentScreen.this.nodeText(player, this.talent).lines();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			LocalPlayer player = Minecraft.getInstance().player;

			if (player == null) {
				return;
			}

			output.add(NarratedElementType.TITLE, TalentScreen.this.nodeText(player, this.talent).narration());

			if (!this.isFocused() || !ImSoTalentedClient.serverHasMod()) {
				return;
			}

			List<Component> usage = new ArrayList<>();

			if (Talents.canBuy(player, this.talent) == Talents.BuyResult.BUYABLE) {
				usage.add(Component.translatable("narration.button.usage.focused"));
			}

			// The tooltip says "Right-click to toggle"; the keyboard's way is Shift with Enter.
			if (Talents.canToggle(player, this.talent)) {
				usage.add(Component.translatableWithFallback("narration.imsotalented.toggle_usage", "Press Shift and Enter to toggle"));
			}

			if (!usage.isEmpty()) {
				output.add(NarratedElementType.USAGE, CommonComponents.joinForNarration(usage.toArray(Component[]::new)));
			}
		}
	}

	/** The pin button: Enter or Space pins or unpins the active tree. Narrated as a button with its tooltip. */
	private final class PinWidget extends TalentWidget {
		private PinWidget() {
			super(CommonComponents.EMPTY);
		}

		@Override
		public Component getMessage() {
			return TalentScreen.this.pinText();
		}

		@Override
		void activate(KeyEvent event) {
			TalentScreen.this.togglePin();
		}

		@Override
		List<FormattedCharSequence> tooltip(LocalPlayer player) {
			return TalentScreen.this.pinTooltip();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
