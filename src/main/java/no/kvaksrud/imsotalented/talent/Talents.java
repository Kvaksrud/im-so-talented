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
	/** The keybind named in the level-up message: the client resolves it to the key the player bound. */
	private static final String TALENT_TREE_KEY = "key.imsotalented.open_talents";

	private Talents() {
	}

	public static TalentData data(Player player) {
		return player.getAttachedOrElse(ImSoTalented.TALENTS, TalentData.EMPTY);
	}

	public static int level(Player player, Talent talent) {
		return data(player).level(talent);
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
		BOUGHT,
		MAXED,
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

		if (!data.isUnlocked(talent)) {
			return BuyResult.LOCKED;
		}

		if (!player.hasInfiniteMaterials() && points(player, talent.tree()) <= 0) {
			return BuyResult.NO_POINTS;
		}

		return BuyResult.BOUGHT;
	}

	/**
	 * Buys the next level of a talent with a point of its tree, if the player can, and tells them how it went. Creative
	 * players get it for free, like enchanting.
	 */
	public static BuyResult tryBuy(ServerPlayer player, Talent talent) {
		BuyResult result = canBuy(player, talent);
		TalentData data = data(player);
		Component name = Component.translatable(talent.translationKey());

		switch (result) {
			case MAXED -> player.sendOverlayMessage(Component.translatable("message.imsotalented.maxed", name));
			case LOCKED -> player.sendOverlayMessage(Component.translatable("message.imsotalented.locked", name, unmetPrereqNames(data, talent)));
			case NO_POINTS -> player.sendOverlayMessage(Component.translatable("message.imsotalented.no_points",
					name, treeName(talent.tree()), nextPointLevel(player, talent.tree())));
			case BOUGHT -> {
				int level = data.level(talent) + 1;
				setLevel(player, talent, level);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.75F, 1.2F);
				player.sendOverlayMessage(Component.translatable("message.imsotalented.bought", name, level, talent.maxLevel()));
				ImSoTalented.LOGGER.debug("{} bought {} level {}", player.getName().getString(), talent.id(), level);
			}
		}

		return result;
	}

	/**
	 * Tree XP from an activity: creative and spectator players earn none, the tree's XP talent adds its share, and
	 * fractions are carried over per player until they make a whole point (mining gives half a point per block). The
	 * whole points go through {@link #addXp}.
	 */
	public static void earn(ServerPlayer player, TalentTree tree, double amount) {
		if (!(amount > 0.0) || player.isCreative() || player.isSpectator()) {
			return;
		}

		double boosted = amount * TalentValues.treeXpMultiplier(data(player).effectiveLevel(tree.xpTalent()));
		double[] fractions = player.getAttachedOrCreate(ImSoTalented.TREE_XP_FRACTIONS);
		double total = fractions[tree.ordinal()] + boosted;
		long whole = (long) total;
		fractions[tree.ordinal()] = total - whole;

		if (whole > 0L) {
			addXp(player, tree, whole);
		}
	}

	/**
	 * Adds whole tree XP, as earned or from a command. A tree at its top level takes no more. Each new level posts a
	 * chat line (only this player sees it) with the points there are to spend in the tree.
	 */
	public static void addXp(ServerPlayer player, TalentTree tree, long amount) {
		if (amount <= 0L) {
			return;
		}

		TalentRules rules = rules(player);
		long before = treeXp(player, tree);
		long cap = rules.xpForLevel(tree.maxLevel());

		if (before >= cap) {
			return;
		}

		setXp(player, tree, Math.min(cap, before + amount));
	}

	/** Sets a tree's XP outright (a command, a reset), with the level-up message when it crosses into a new level. */
	public static void setXp(ServerPlayer player, TalentTree tree, long xp) {
		TalentRules rules = rules(player);
		int levelBefore = treeLevel(player, tree);
		player.setAttached(ImSoTalented.TALENTS, data(player).withXp(tree, Math.max(0L, xp)));
		int levelAfter = rules.levelFor(treeXp(player, tree), tree.maxLevel());

		if (levelAfter > levelBefore) {
			player.sendSystemMessage(Component.translatable("message.imsotalented.level_up",
					treeName(tree), levelAfter, points(player, tree), Component.keybind(TALENT_TREE_KEY)));
		}
	}

	/** The tree's name in its own color, for chat and overlay messages. */
	public static MutableComponent treeName(TalentTree tree) {
		return Component.translatable(tree.translationKey()).withColor(tree.color() & 0xFFFFFF);
	}

	/** The state of a server-side toggle: the new state, or rejected (the reason is shown as an overlay). */
	public enum ToggleResult {
		ON,
		OFF,
		REJECTED
	}

	/**
	 * Toggles a talent off/on, if the player may: the talent must be toggleable, owned (level 1 or more),
	 * and the player must not be in creative (talents are always on there). Toggling costs nothing, is
	 * instant and never changes a purchased level or the points spent; it re-applies the effects (off removes
	 * the attribute modifiers, on re-adds them) and the toggle persists with the player like the levels do.
	 */
	public static ToggleResult toggle(ServerPlayer player, Talent talent, boolean on) {
		if (!talent.toggleable()) {
			player.sendOverlayMessage(Component.translatable("message.imsotalented.toggle_not_toggleable", Component.translatable(talent.translationKey())));
			return ToggleResult.REJECTED;
		}

		TalentData data = data(player);

		if (data.level(talent) <= 0) {
			player.sendOverlayMessage(Component.translatable("message.imsotalented.toggle_not_owned", Component.translatable(talent.translationKey())));
			return ToggleResult.REJECTED;
		}

		if (player.hasInfiniteMaterials()) {
			player.sendOverlayMessage(Component.translatable("message.imsotalented.toggle_creative"));
			return ToggleResult.REJECTED;
		}

		ToggleResult result = on ? ToggleResult.ON : ToggleResult.OFF;
		setData(player, data.withDisabled(talent, !on));
		player.sendOverlayMessage(Component.translatable(on ? "message.imsotalented.toggled_on" : "message.imsotalented.toggled_off", Component.translatable(talent.translationKey())));
		ImSoTalented.LOGGER.debug("{} toggled {} {}", player.getName().getString(), talent.id(), on ? "on" : "off");

		return result;
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
