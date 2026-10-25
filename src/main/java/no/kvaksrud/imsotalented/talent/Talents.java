package no.kvaksrud.imsotalented.talent;

import java.util.List;

import no.kvaksrud.imsotalented.ImSoTalented;
import no.kvaksrud.imsotalented.inventory.Backpacks;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Reading and changing a player's talents and tree XP. Works on both sides for reading; changes happen on the server
 * only. Talents are bought with talent points, and a tree's points come from its levels: every level of a tree's XP
 * is one point to spend in that tree. Vanilla XP plays no part.
 */
public final class Talents {
	/** The talent tree's key mapping, by name: messages show the key the player bound to it, resolved on their client. */
	public static final String TALENT_TREE_KEY = "key.imsotalented.talent_tree";

	private Talents() {
	}

	public static TalentData data(Player player) {
		return player.getAttachedOrElse(ImSoTalented.TALENTS, TalentData.EMPTY);
	}

	/** The level bought: what the screen, prereqs and commands work with. Effects read {@link #effectiveLevel}. */
	public static int level(Player player, Talent talent) {
		return data(player).level(talent);
	}

	/**
	 * The level that applies right now: 0 while the talent is toggled off. Every effect reads this one, so a toggle
	 * works for every talent without each effect knowing about it.
	 */
	public static int effectiveLevel(Player player, Talent talent) {
		return data(player).effectiveLevel(talent);
	}

	/**
	 * Creative for the mod's rules: talents are free and tree XP isn't earned. One check everywhere, the same one
	 * vanilla uses for free items, so the two never disagree.
	 */
	public static boolean isCreative(Player player) {
		return player.hasInfiniteMaterials();
	}

	/** The rules this side plays by: the server's config on the server, the copy the server sent on the client. */
	public static TalentRules rules(Player player) {
		return player.level().isClientSide() ? ImSoTalented.clientRules() : ImSoTalented.config().rules();
	}

	/** The whole tree XP the player has earned in this tree. */
	public static long treeXp(Player player, TalentTree tree) {
		return data(player).xp(tree);
	}

	/** The tree's level, the XP into it and what the next one takes. */
	public static TalentRules.Progress progress(Player player, TalentTree tree) {
		return rules(player).progress(treeXp(player, tree), tree.maxLevel());
	}

	public static int treeLevel(Player player, TalentTree tree) {
		return rules(player).levelFor(treeXp(player, tree), tree.maxLevel());
	}

	/** Talent points left to spend in this tree: its level minus the levels bought in it, never below 0. */
	public static int points(Player player, TalentTree tree) {
		return Math.max(0, treeLevel(player, tree) - data(player).pointsSpent(tree));
	}

	/**
	 * The tree level that brings the next point to spend. Counted from the points already spent too, so it stays right
	 * when a command set talent levels past what the tree's level paid for.
	 */
	public static int nextPointLevel(Player player, TalentTree tree) {
		return Math.max(treeLevel(player, tree), data(player).pointsSpent(tree)) + 1;
	}

	public enum BuyResult {
		/** The next level can be bought right now. */
		BUYABLE,
		MAXED,
		/** The talent has no effect yet ({@link Talent#implemented()}), so it isn't for sale. */
		NOT_YET,
		LOCKED,
		NO_POINTS
	}

	/**
	 * What a locked talent still needs: the unmet prereqs' names, comma-joined, in prereq declaration order. A prereq
	 * above level 1 names the level too, like "Stronger Weapons 5".
	 */
	public static Component unmetPrereqNames(TalentData data, Talent talent) {
		List<TalentPrereq> unmet = data.unmetPrereqs(talent);
		MutableComponent names = Component.empty();

		for (int i = 0; i < unmet.size(); i++) {
			TalentPrereq prereq = unmet.get(i);
			MutableComponent name = Component.translatable(prereq.talent().translationKey());

			if (prereq.minLevel() > 1) {
				name = Component.translatable("message.imsotalented.prereq_level", name, prereq.minLevel());
			}

			names = i == 0 ? name : names.append(", ").append(name);
		}

		return names;
	}

	/** Whether the player could buy the next level of this talent right now, and if not, why. Both sides. */
	public static BuyResult canBuy(Player player, Talent talent) {
		TalentData data = data(player);

		if (data.level(talent) >= talent.maxLevel()) {
			return BuyResult.MAXED;
		}

		if (!talent.implemented()) {
			return BuyResult.NOT_YET;
		}

		if (!data.isUnlocked(talent)) {
			return BuyResult.LOCKED;
		}

		if (!isCreative(player) && points(player, talent.tree()) <= 0) {
			return BuyResult.NO_POINTS;
		}

		return BuyResult.BUYABLE;
	}

	/**
	 * Buys the next level of a talent with a point of its tree, if the player can, and tells them how it went. Creative
	 * players get it for free, like enchanting. A rejection means a stale or modified client (the screen only asks for
	 * what it thinks will work), so it's logged at debug.
	 */
	public static BuyResult tryBuy(ServerPlayer player, Talent talent) {
		BuyResult result = canBuy(player, talent);
		TalentData data = data(player);
		Component name = Component.translatable(talent.translationKey());

		switch (result) {
			case MAXED -> player.sendOverlayMessage(Component.translatable("message.imsotalented.maxed", name));
			case NOT_YET -> player.sendOverlayMessage(Component.translatable("message.imsotalented.not_yet", name));
			case LOCKED -> player.sendOverlayMessage(Component.translatable("message.imsotalented.locked", name, unmetPrereqNames(data, talent)));
			case NO_POINTS -> player.sendOverlayMessage(Component.translatable("message.imsotalented.no_points",
					name, treeName(talent.tree()), nextPointLevel(player, talent.tree())));
			case BUYABLE -> {
				int level = data.level(talent) + 1;
				setLevel(player, talent, level);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.75F, 1.2F);
				player.sendOverlayMessage(Component.translatable("message.imsotalented.bought", name, level, talent.maxLevel()));
				ImSoTalented.LOGGER.info("{} ({}) bought {} level {}; {} {} points left", player.getName().getString(), player.getUUID(),
						talent.id(), level, points(player, talent.tree()), talent.tree().id());
			}
		}

		if (result != BuyResult.BUYABLE) {
			ImSoTalented.LOGGER.debug("Rejected buying {} for {} ({}): {}", talent.id(), player.getName().getString(), player.getUUID(), result);
		}

		return result;
	}

	/** Whether the player can switch this talent off or on: a toggleable talent that works and that they own. */
	public static boolean canToggle(Player player, Talent talent) {
		return talent.toggleable() && talent.implemented() && level(player, talent) >= 1;
	}

	/** The state of a server-side toggle: the new state, or rejected (the reason is shown as an overlay). */
	public enum ToggleResult {
		ON,
		OFF,
		REJECTED
	}

	/**
	 * Switches a talent on or off, if the player may ({@link #canToggle}), in every game mode. Toggling costs nothing,
	 * is instant and never changes a bought level or the points spent; it re-applies the effects (off removes the
	 * attribute modifiers, on re-adds them) and the toggle is saved with the player like the levels are. {@code
	 * notify} sends the result to the player's action bar; a command handles its own feedback instead.
	 */
	public static ToggleResult toggle(ServerPlayer player, Talent talent, boolean on, boolean notify) {
		Component name = Component.translatable(talent.translationKey());

		if (!talent.toggleable() || !talent.implemented()) {
			reject(player, notify, Component.translatable("message.imsotalented.toggle_not_toggleable", name), talent, "not toggleable");
			return ToggleResult.REJECTED;
		}

		TalentData data = data(player);

		if (data.level(talent) <= 0) {
			reject(player, notify, Component.translatable("message.imsotalented.toggle_not_owned", name), talent, "not owned");
			return ToggleResult.REJECTED;
		}

		setData(player, data.withDisabled(talent, !on));

		if (notify) {
			player.sendOverlayMessage(Component.translatable(on ? "message.imsotalented.toggled_on" : "message.imsotalented.toggled_off", name));
		}

		ImSoTalented.LOGGER.debug("{} ({}) toggled {} {}", player.getName().getString(), player.getUUID(), talent.id(), on ? "on" : "off");
		return on ? ToggleResult.ON : ToggleResult.OFF;
	}

	private static void reject(ServerPlayer player, boolean notify, Component message, Talent talent, String reason) {
		if (notify) {
			player.sendOverlayMessage(message);
		}

		ImSoTalented.LOGGER.debug("Rejected toggling {} for {} ({}): {}", talent.id(), player.getName().getString(), player.getUUID(), reason);
	}

	/** The server's tree XP bookkeeping for this player: pending XP, the hourly allowance, the last camera turn. */
	public static TreeXpLedger ledger(ServerPlayer player) {
		return player.getAttachedOrCreate(ImSoTalented.TREE_XP_LEDGER);
	}

	/**
	 * Tree XP from an activity. Creative and spectator players earn none, and neither does a player who hasn't turned
	 * the camera for {@link TalentValues#AFK_TICKS} (a held key, a fishing farm). The tree's XP talent adds its share,
	 * and past the tree's hourly allowance only {@link TalentValues#TREE_XP_OVER_CAP_SHARE} of it counts. The result
	 * waits in the player's {@link TreeXpLedger} and is committed once per tick by {@link #commitPending}, so a quarry
	 * that breaks a whole vein sends one update, not one per ore.
	 */
	public static void earn(ServerPlayer player, TalentTree tree, double amount) {
		if (!(amount > 0.0) || isCreative(player) || player.isSpectator()) {
			return;
		}

		TreeXpLedger ledger = ledger(player);
		long tick = player.level().getServer().getTickCount();

		if (ledger.idle(tick)) {
			return;
		}

		ledger.credit(tree, amount * TalentValues.treeXpMultiplier(effectiveLevel(player, tree.xpTalent())), tick);
	}

	/**
	 * Once per player per server tick: notes where the player looks (the AFK check) and commits the whole points
	 * earned since the last tick, keeping fractions for later.
	 */
	public static void commitPending(ServerPlayer player) {
		TreeXpLedger ledger = ledger(player);
		ledger.look(player.getYRot(), player.getXRot(), player.level().getServer().getTickCount());

		for (TalentTree tree : TalentTree.values()) {
			long whole = ledger.takeWhole(tree);

			if (whole > 0L) {
				addXp(player, tree, whole);
			}
		}
	}

	/**
	 * Adds whole tree XP, as earned or from a command, and says how much went in. A tree at its top level takes no
	 * more, and an addition past the top stops there (it never wraps around, however large).
	 */
	public static long addXp(ServerPlayer player, TalentTree tree, long amount) {
		if (amount <= 0L) {
			return 0L;
		}

		long before = treeXp(player, tree);
		long cap = rules(player).xpForLevel(tree.maxLevel());

		if (before >= cap) {
			return 0L;
		}

		long after = amount >= cap - before ? cap : before + amount;
		setXp(player, tree, after);
		return after - before;
	}

	/**
	 * Sets a tree's XP outright (a command, a reset). Each new level posts a chat line (only this player sees it) with
	 * the points to spend, plays the level-up sound and is logged; the top level says the tree earns no more.
	 */
	public static void setXp(ServerPlayer player, TalentTree tree, long xp) {
		TalentRules rules = rules(player);
		int levelBefore = treeLevel(player, tree);
		player.setAttached(ImSoTalented.TALENTS, data(player).withXp(tree, Math.max(0L, xp)));
		int levelAfter = rules.levelFor(treeXp(player, tree), tree.maxLevel());

		if (levelAfter > levelBefore) {
			boolean top = levelAfter >= tree.maxLevel();
			player.sendSystemMessage(Component.translatable(top ? "message.imsotalented.level_up_top" : "message.imsotalented.level_up",
					treeName(tree), levelAfter, points(player, tree), Component.keybind(TALENT_TREE_KEY)));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.0F);
			ImSoTalented.LOGGER.info("{} ({}) reached {} level {}{}", player.getName().getString(), player.getUUID(), tree.id(), levelAfter,
					top ? " (top level)" : "");
		}
	}

	/** The tree's name in its own color, for chat and overlay messages. */
	public static MutableComponent treeName(TalentTree tree) {
		return Component.translatable(tree.translationKey()).withColor(tree.color() & 0xFFFFFF);
	}

	/** Sets a talent's level (no points involved) and applies what changed. */
	public static void setLevel(ServerPlayer player, Talent talent, int level) {
		setData(player, data(player).with(talent, level));
	}

	/** Replaces all of a player's talents and tree XP (no points involved) and applies what changed. */
	public static void setData(ServerPlayer player, TalentData data) {
		player.setAttached(ImSoTalented.TALENTS, data);
		applyAll(player);
	}

	/** Brings everything that isn't checked on the fly up to date: attributes and the backpack's size. */
	public static void applyAll(ServerPlayer player) {
		TalentAttributes.apply(player);
		Backpacks.onTalentsChanged(player);
	}
}
