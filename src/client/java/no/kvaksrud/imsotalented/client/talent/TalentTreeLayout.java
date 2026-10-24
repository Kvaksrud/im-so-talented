package no.kvaksrud.imsotalented.client.talent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import no.kvaksrud.imsotalented.talent.Talent;
import no.kvaksrud.imsotalented.talent.TalentPrereq;
import no.kvaksrud.imsotalented.talent.TalentTree;
import org.joml.Vector2i;
import org.jspecify.annotations.Nullable;

/**
 * Where everything in the talent screen goes, worked out once from the {@link Talent} enum and the screen size, so a
 * talent shows up in its tree at its {@link Talent#row()} and {@link Talent#column()} without touching the screen. Pure
 * geometry, no rendering, so a client game test can ask where a node or a tab is and which node is under a point.
 *
 * <p>The screen is the vanilla advancements window: a column of tabs down its left side, one per {@link TalentTree},
 * and the window beside them with a light header (the active tree's name, level, XP bar and pin button) over a dark area that
 * holds the active tree. Every tree is laid out in the same window, so all talents have a position at once; only the
 * active tab's are drawn and clickable.
 *
 * <p>The window is {@link #COLUMNS} columns wide whatever the tab, and as tall as the deepest tree, so switching tabs
 * never moves the window. A tree is centered on its columns: the middle of its leftmost and rightmost column sits in
 * the middle of the window, so the roots land in the middle, and half columns (1.5) fall between two others. Rows
 * start at the top, so a tab's root is always in the same place. Rows are a node plus a gap for the connector lines;
 * the gap shrinks from {@link #MAX_ROW_GAP} towards {@link #MIN_ROW_GAP} when the screen is short. The tabs sit on the
 * left rather than on top because height is what runs out: the automatic GUI scale leaves a 854x480 window (the game's
 * default) 427x240, and with the tabs beside it the whole window fits that with a 7 px row gap.
 *
 * <p>Each prereq gets its own connector, an elbow: down from the parent, across in the gap right above the child,
 * and down into the child. A prereq two rows up runs straight down past the row between, through the gap between two
 * nodes (Lifesteal's line from Stronger Weapons passes between Spear and Mace).
 */
public final class TalentTreeLayout {
	/** The vanilla advancement frame sprites are this size. */
	public static final int NODE_SIZE = 26;
	public static final int ICON_SIZE = 16;
	public static final int ICON_OFFSET = (NODE_SIZE - ICON_SIZE) / 2;
	/** The level badge starts just under the icon and overlaps the frame's bottom border. */
	public static final int BADGE_TOP = ICON_OFFSET + ICON_SIZE;
	public static final int BADGE_HEIGHT = 11;
	public static final int NODE_FOOTPRINT = BADGE_TOP + BADGE_HEIGHT;

	/** Columns in every tab: the widest tree (Warrior) is seven across, which leaves half a column of air each side. */
	public static final int COLUMNS = 8;
	/** Room between nodes in a row, wide enough for a connector (3 px with its outline) to pass between two of them. */
	private static final int COLUMN_GAP = 8;
	private static final int COLUMN_PITCH = NODE_SIZE + COLUMN_GAP;
	/** Room between rows for the connector lines and some air, when the screen allows. */
	public static final int MAX_ROW_GAP = 8;
	/** The least room between rows: the lines then touch the next row's frames, which are drawn over them. */
	public static final int MIN_ROW_GAP = 2;
	/** Rows in every tab: as many as the deepest tree has. */
	public static final int ROWS = rowCount();

	/** The advancements window texture's left, right and bottom border, its one-pixel bevel included. */
	public static final int BORDER = 9;
	/**
	 * From the window's top to the dark area: two lines of header, where the vanilla window has room for one (18).
	 * The screen stretches the texture's header to fit.
	 */
	public static final int HEADER_HEIGHT = 30;
	/** Between the dark area's edge and the nodes; the window's inner shadow is 6 px deep. */
	private static final int TREE_PADDING = 6;
	/** The first row of nodes starts this far below the window's top. */
	private static final int TREE_TOP = HEADER_HEIGHT + TREE_PADDING;

	/** Header text starts this far in from the window's left and right edges, like the vanilla window title. */
	public static final int HEADER_X = 8;
	/** The first header line (name and level, points), at the vanilla window title's height. */
	public static final int TITLE_Y = 6;
	/** The second header line (XP bar, XP numbers, pin button) is centered on this height. */
	private static final int XP_ROW_CENTER_Y = 21;
	/** Capital letters are 7 px tall from the text's top. */
	private static final int CAP_HEIGHT = 7;
	public static final int XP_TEXT_Y = XP_ROW_CENTER_Y - CAP_HEIGHT / 2;
	/** Short enough that the longest English XP line ("3584/3585 XP to the next level") fits before the pin. */
	public static final int XP_BAR_WIDTH = 90;
	public static final int XP_BAR_HEIGHT = 5;
	public static final int XP_BAR_Y = XP_ROW_CENTER_Y - XP_BAR_HEIGHT / 2;
	/** Between the XP bar and its numbers, and between the numbers and the pin button. */
	public static final int XP_TEXT_GAP = 6;
	public static final int PIN_SIZE = 13;
	public static final int PIN_Y = XP_ROW_CENTER_Y - PIN_SIZE / 2;

	/** The vanilla left-side tab sprites ({@code advancements/tab_left_*}) are this size, stacked with no gap. */
	public static final int TAB_WIDTH = 32;
	public static final int TAB_HEIGHT = 28;
	/** A tab's right columns lie over the window's left border, so the selected one opens into the window. */
	public static final int TAB_OVERLAP = 4;
	/** Where the tab's icon goes inside it, as in the advancements screen. */
	public static final int TAB_ICON_X = 10;
	public static final int TAB_ICON_Y = 5;

	private final int left;
	private final int top;
	private final int width;
	private final int height;
	private final int rowGap;
	private final Map<Talent, Vector2i> nodes = new EnumMap<>(Talent.class);
	private final Map<TalentTree, List<Connector>> connectors = new EnumMap<>(TalentTree.class);

	private TalentTreeLayout(int screenWidth, int screenHeight) {
		int tabsLeft = TAB_WIDTH - TAB_OVERLAP;
		int fixedHeight = TREE_TOP + ROWS * NODE_FOOTPRINT + TREE_PADDING + BORDER;
		int spare = (screenHeight - fixedHeight) / Math.max(1, ROWS - 1);
		this.rowGap = Math.clamp(spare, MIN_ROW_GAP, MAX_ROW_GAP);

		this.width = 2 * BORDER + 2 * TREE_PADDING + COLUMNS * COLUMN_PITCH - COLUMN_GAP;
		this.height = fixedHeight + (ROWS - 1) * this.rowGap;
		// Centered with the tabs; if the screen is too small, keep the tabs and header on screen rather than the bottom.
		this.left = Math.max(0, (screenWidth - tabsLeft - this.width) / 2) + tabsLeft;
		this.top = Math.max(0, (screenHeight - this.height) / 2);

		for (TalentTree tree : TalentTree.values()) {
			this.placeTree(tree);
		}

		for (TalentTree tree : TalentTree.values()) {
			List<Connector> lines = new ArrayList<>();

			for (Talent child : tree.talents()) {
				for (TalentPrereq prereq : child.prereqs()) {
					lines.add(this.elbow(prereq, child));
				}
			}

			this.connectors.put(tree, List.copyOf(lines));
		}
	}

	public static TalentTreeLayout forScreen(int screenWidth, int screenHeight) {
		return new TalentTreeLayout(screenWidth, screenHeight);
	}

	private static int rowCount() {
		int deepest = 0;

		for (Talent talent : Talent.values()) {
			deepest = Math.max(deepest, talent.row());
		}

		return deepest + 1;
	}

	/** Puts the tree's nodes in their rows and columns, the tree centered on its leftmost and rightmost columns. */
	private void placeTree(TalentTree tree) {
		double minColumn = Double.MAX_VALUE;
		double maxColumn = -Double.MAX_VALUE;

		for (Talent talent : tree.talents()) {
			minColumn = Math.min(minColumn, talent.column());
			maxColumn = Math.max(maxColumn, talent.column());
		}

		double middleColumn = (minColumn + maxColumn) / 2.0;
		int middleX = this.left + this.width / 2;

		for (Talent talent : tree.talents()) {
			int centerX = middleX + (int) Math.round((talent.column() - middleColumn) * COLUMN_PITCH);
			int nodeTop = this.top + TREE_TOP + talent.row() * (NODE_FOOTPRINT + this.rowGap);
			this.nodes.put(talent, new Vector2i(centerX - NODE_SIZE / 2, nodeTop));
		}
	}

	/**
	 * The line for one prereq: from the parent's middle straight down to the gap above the child, across to the
	 * child's column and down into its middle. The frames and badges are drawn over both ends. The across part sits in
	 * the middle of the gap, so with the full gap there is air on both sides of its outline.
	 */
	private Connector elbow(TalentPrereq prereq, Talent child) {
		Vector2i from = this.nodeCenter(prereq.talent());
		Vector2i to = this.nodeCenter(child);
		int across = this.nodes.get(child).y - (this.rowGap + 1) / 2;
		List<Segment> segments = new ArrayList<>();

		if (from.x == to.x) {
			segments.add(Segment.of(from.x, from.y, to.x, to.y));
		} else {
			segments.add(Segment.of(from.x, from.y, from.x, across));
			segments.add(Segment.of(from.x, across, to.x, across));
			segments.add(Segment.of(to.x, across, to.x, to.y));
		}

		return new Connector(prereq, child, List.copyOf(segments));
	}

	public int left() {
		return this.left;
	}

	public int top() {
		return this.top;
	}

	public int width() {
		return this.width;
	}

	public int height() {
		return this.height;
	}

	/** The room between two rows of nodes at this screen size, from {@link #MIN_ROW_GAP} to {@link #MAX_ROW_GAP}. */
	public int rowGap() {
		return this.rowGap;
	}

	/** Left edge of the dark area that holds the tree. */
	public int insideLeft() {
		return this.left + BORDER;
	}

	/** Top edge of the dark area that holds the tree, right under the header. */
	public int insideTop() {
		return this.top + HEADER_HEIGHT;
	}

	public int insideWidth() {
		return this.width - 2 * BORDER;
	}

	public int insideHeight() {
		return this.height - HEADER_HEIGHT - BORDER;
	}

	/** Top left corner of the talent's frame, in GUI coordinates (where it is when its tab is active). */
	public Vector2i nodePosition(Talent talent) {
		return new Vector2i(this.nodes.get(talent));
	}

	/** Middle of the talent's frame (and its icon), in GUI coordinates (where it is when its tab is active). */
	public Vector2i nodeCenter(Talent talent) {
		Vector2i position = this.nodes.get(talent);
		return new Vector2i(position.x + NODE_SIZE / 2, position.y + NODE_SIZE / 2);
	}

	/** The talent of this tree whose frame or level badge is under this point, if any. */
	public @Nullable Talent talentAt(TalentTree tree, double x, double y) {
		for (Talent talent : tree.talents()) {
			Vector2i position = this.nodes.get(talent);

			if (x >= position.x && x < position.x + NODE_SIZE && y >= position.y && y < position.y + NODE_FOOTPRINT) {
				return talent;
			}
		}

		return null;
	}

	/** The lines of this tree's tab, one per prereq, in talent declaration order. */
	public List<Connector> connectors(TalentTree tree) {
		return Collections.unmodifiableList(this.connectors.get(tree));
	}

	/**
	 * Top left corner of the tree's tab sprite: top to bottom in {@link TalentTree} order from the window's top edge,
	 * against the window's left border, as the advancements screen places its left-side tabs.
	 */
	public Vector2i tabPosition(TalentTree tree) {
		return new Vector2i(this.left - TAB_WIDTH + TAB_OVERLAP, this.top + tree.ordinal() * TAB_HEIGHT);
	}

	/** Middle of the tree's tab icon, which is inside the tab whether it's selected or not. */
	public Vector2i tabCenter(TalentTree tree) {
		Vector2i position = this.tabPosition(tree);
		return new Vector2i(position.x + TAB_ICON_X + ICON_SIZE / 2, position.y + TAB_ICON_Y + ICON_SIZE / 2);
	}

	/** The tab under this point, if any: the whole sprite counts, as in the advancements screen. */
	public @Nullable TalentTree tabAt(double x, double y) {
		for (TalentTree tree : TalentTree.values()) {
			Vector2i position = this.tabPosition(tree);

			if (x >= position.x && x < position.x + TAB_WIDTH && y >= position.y && y < position.y + TAB_HEIGHT) {
				return tree;
			}
		}

		return null;
	}

	/** Top left corner of the pin button, at the right end of the header's XP line. */
	public Vector2i pinPosition() {
		return new Vector2i(this.left + this.width - HEADER_X - PIN_SIZE, this.top + PIN_Y);
	}

	/** Middle of the pin button. */
	public Vector2i pinCenter() {
		Vector2i position = this.pinPosition();
		return new Vector2i(position.x + PIN_SIZE / 2, position.y + PIN_SIZE / 2);
	}

	/** Whether this point is on the pin button. */
	public boolean isOnPin(double x, double y) {
		Vector2i position = this.pinPosition();
		return x >= position.x && x < position.x + PIN_SIZE && y >= position.y && y < position.y + PIN_SIZE;
	}

	/** The widest the XP numbers after the bar can be before they reach the pin button. */
	public int xpTextMaxWidth() {
		return this.width - 2 * HEADER_X - XP_BAR_WIDTH - XP_TEXT_GAP - PIN_SIZE - XP_TEXT_GAP;
	}

	/** The line for one prereq into one node. It lights up when that prereq is met. */
	public record Connector(TalentPrereq prereq, Talent child, List<Segment> segments) {
	}

	/** A horizontal or vertical line, both ends included, with {@code x0 <= x1} and {@code y0 <= y1}. */
	public record Segment(int x0, int y0, int x1, int y1) {
		static Segment of(int xa, int ya, int xb, int yb) {
			return new Segment(Math.min(xa, xb), Math.min(ya, yb), Math.max(xa, xb), Math.max(ya, yb));
		}
	}
}
