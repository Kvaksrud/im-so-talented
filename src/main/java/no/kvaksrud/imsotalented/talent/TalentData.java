package no.kvaksrud.imsotalented.talent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import io.netty.buffer.ByteBuf;

/**
 * A player's talent levels by talent id, the set of talents toggled off, and the whole tree XP earned in each tree
 * by tree id. Immutable: change it with {@link #with}, {@link #withDisabled} or {@link #withXp} and attach the result
 * again, so the attachment syncs. Ids this version doesn't know (from a newer version, or a removed talent) are kept as
 * they are.
 *
 * <p>A tree's level isn't stored: it follows from the tree's XP and the server's prices ({@link TalentRules#levelFor}),
 * and every level is one talent point to spend in that tree. Buying spends the point, never the XP.
 *
 * <p>The saved compound holds the levels under {@value #LEVELS_KEY}, the toggled-off ids as a string list under
 * {@value #DISABLED_KEY} and the tree XP under {@value #XP_KEY}. Each part may be missing and then reads as empty.
 */
public record TalentData(Map<String, Integer> levels, Set<String> disabled, Map<String, Long> xp) {
	public static final TalentData EMPTY = new TalentData(Map.of(), Set.of(), Map.of());

	private static final String LEVELS_KEY = "levels";
	private static final String DISABLED_KEY = "disabled";
	private static final String XP_KEY = "xp";

	public static final Codec<TalentData> CODEC = CompoundTag.CODEC
			.xmap(TalentData::fromNbt, TalentData::toNbt);

	public static final StreamCodec<ByteBuf, TalentData> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.<ByteBuf, String, Integer, Map<String, Integer>>map(
					HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), TalentData::levels,
			ByteBufCodecs.<ByteBuf, String>list().apply(ByteBufCodecs.STRING_UTF8), TalentData::disabledIds,
			ByteBufCodecs.<ByteBuf, String, Long, Map<String, Long>>map(
					HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG), TalentData::xp,
			TalentData::from);

	public TalentData {
		levels = Map.copyOf(levels);
		disabled = Set.copyOf(disabled);
		xp = Map.copyOf(xp);
	}

	/** This talent's level, 0 if never bought, never above its max (in case the max was lowered). */
	public int level(Talent talent) {
		return Math.clamp(this.levels.getOrDefault(talent.id(), 0), 0, talent.maxLevel());
	}

	/** Whether the talent is toggled off; only talents with a level can ever be in the set. */
	public boolean isDisabled(Talent talent) {
		return this.disabled.contains(talent.id());
	}

	/** The level that applies right now: 0 while the talent is toggled off, the purchased level otherwise. */
	public int effectiveLevel(Talent talent) {
		return this.isDisabled(talent) ? 0 : this.level(talent);
	}

	/** The whole tree XP earned in this tree so far; never negative. */
	public long xp(TalentTree tree) {
		return Math.max(0L, this.xp.getOrDefault(tree.id(), 0L));
	}

	/** A copy with this talent at this level (clamped to 0..max); level 0 removes it. The rest is kept. */
	public TalentData with(Talent talent, int level) {
		Map<String, Integer> copy = new HashMap<>(this.levels);
		int clamped = Math.clamp(level, 0, talent.maxLevel());

		if (clamped == 0) {
			copy.remove(talent.id());
		} else {
			copy.put(talent.id(), clamped);
		}

		return new TalentData(copy, this.disabled, this.xp);
	}

	/** A copy with this tree's XP set to {@code xp} (0 or more); 0 removes the entry. The rest is kept. */
	public TalentData withXp(TalentTree tree, long xp) {
		Map<String, Long> copy = new HashMap<>(this.xp);

		if (xp <= 0L) {
			copy.remove(tree.id());
		} else {
			copy.put(tree.id(), xp);
		}

		return new TalentData(this.levels, this.disabled, copy);
	}

	/**
	 * A copy with this talent toggled off/on. Toggling costs nothing and never changes a level: a level-0
	 * talent cannot enter the set, and removing an absent id is a no-op.
	 */
	public TalentData withDisabled(Talent talent, boolean disabled) {
		if (disabled) {
			if (this.isDisabled(talent) || this.level(talent) <= 0) {
				return this;
			}

			Set<String> copy = new HashSet<>(this.disabled);
			copy.add(talent.id());
			return new TalentData(this.levels, copy, this.xp);
		}

		if (!this.isDisabled(talent)) {
			return this;
		}

		Set<String> copy = new HashSet<>(this.disabled);
		copy.remove(talent.id());
		return new TalentData(this.levels, copy, this.xp);
	}

	/** Talent points spent in this tree: the levels bought of its talents. */
	public int pointsSpent(TalentTree tree) {
		int total = 0;

		for (Talent talent : tree.talents()) {
			total += this.level(talent);
		}

		return total;
	}

	/** Talent points spent in every tree together, over the talents this version knows. */
	public int pointsSpent() {
		int total = 0;

		for (Talent talent : Talent.values()) {
			total += this.level(talent);
		}

		return total;
	}

	/** Whether every prereq this talent needs is met: an empty list is unlocked from the start. */
	public boolean isUnlocked(Talent talent) {
		for (TalentPrereq prereq : talent.prereqs()) {
			if (!this.isMet(prereq)) {
				return false;
			}
		}

		return true;
	}

	/** Whether this one prereq is met: its talent has at least the level it asks for. */
	public boolean isMet(TalentPrereq prereq) {
		return this.level(prereq.talent()) >= prereq.minLevel();
	}

	/** The prereqs this talent still needs, in declaration order; empty once it's unlocked. */
	public List<TalentPrereq> unmetPrereqs(Talent talent) {
		List<TalentPrereq> unmet = new ArrayList<>();

		for (TalentPrereq prereq : talent.prereqs()) {
			if (!this.isMet(prereq)) {
				unmet.add(prereq);
			}
		}

		return Collections.unmodifiableList(unmet);
	}

	/** The toggled-off ids in a stable order, for the stream codec's list. */
	public List<String> disabledIds() {
		return this.disabled.stream().sorted().toList();
	}

	/** The stream codec's constructor: a levels map, a disabled id list and the tree XP map. */
	public static TalentData from(Map<String, Integer> levels, List<String> disabled, Map<String, Long> xp) {
		return new TalentData(levels, new HashSet<>(disabled), xp);
	}

	/** The saved compound: levels, toggled-off ids and tree XP, each under its own key. */
	public CompoundTag toNbt() {
		CompoundTag tag = new CompoundTag();

		CompoundTag levels = new CompoundTag();
		this.levels.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> levels.putInt(entry.getKey(), entry.getValue()));
		tag.put(LEVELS_KEY, levels);

		ListTag list = new ListTag();
		for (String id : this.disabledIds()) {
			list.addAndUnwrap(StringTag.valueOf(id));
		}
		tag.put(DISABLED_KEY, list);

		CompoundTag xp = new CompoundTag();
		this.xp.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> xp.putLong(entry.getKey(), entry.getValue()));
		tag.put(XP_KEY, xp);

		return tag;
	}

	/** Reads a saved compound; a missing part reads as empty, a part of the wrong type fails the read. */
	public static TalentData fromNbt(CompoundTag tag) {
		Map<String, Integer> levels = new HashMap<>();
		Set<String> disabled = new HashSet<>();
		Map<String, Long> xp = new HashMap<>();

		for (Map.Entry<String, Tag> entry : compound(tag, LEVELS_KEY).entrySet()) {
			levels.put(entry.getKey(), number(LEVELS_KEY, entry).intValue());
		}

		Tag disabledTag = tag.get(DISABLED_KEY);

		if (disabledTag != null) {
			if (!(disabledTag instanceof ListTag list)) {
				throw new IllegalArgumentException(DISABLED_KEY + " must be a string list, got " + disabledTag.getClass().getSimpleName());
			}

			for (Tag element : list) {
				if (!(element instanceof StringTag string)) {
					throw new IllegalArgumentException(DISABLED_KEY + " must hold strings, got " + element.getClass().getSimpleName());
				}

				disabled.add(string.value());
			}
		}

		for (Map.Entry<String, Tag> entry : compound(tag, XP_KEY).entrySet()) {
			xp.put(entry.getKey(), number(XP_KEY, entry).longValue());
		}

		return new TalentData(levels, disabled, xp);
	}

	private static CompoundTag compound(CompoundTag tag, String key) {
		Tag part = tag.get(key);

		if (part == null) {
			return new CompoundTag();
		}

		if (!(part instanceof CompoundTag compound)) {
			throw new IllegalArgumentException(key + " must be a compound, got " + part.getClass().getSimpleName());
		}

		return compound;
	}

	private static NumericTag number(String key, Map.Entry<String, Tag> entry) {
		if (!(entry.getValue() instanceof NumericTag numeric)) {
			throw new IllegalArgumentException(key + " entry " + entry.getKey() + " must be a number, got " + entry.getValue().getClass().getSimpleName());
		}

		return numeric;
	}
}
