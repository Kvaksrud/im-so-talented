package no.kvaksrud.imsotalented.talent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

class TalentDataTest {
	@Test
	void levelsAreClampedAndZeroRemoves() {
		TalentData data = TalentData.EMPTY.with(Talent.MORE_HEALTH, 3).with(Talent.FASTER_MINING, 99);
		assertEquals(3, data.level(Talent.MORE_HEALTH));
		assertEquals(5, data.level(Talent.FASTER_MINING), "clamped to the max level");
		assertEquals(8, data.pointsSpent());

		TalentData removed = data.with(Talent.MORE_HEALTH, 0);
		assertFalse(removed.levels().containsKey(Talent.MORE_HEALTH.id()));
		assertEquals(5, removed.pointsSpent());
	}

	@Test
	void pointsSpentCountsEachTreeOnItsOwn() {
		TalentData data = TalentData.EMPTY.with(Talent.FASTER_MINING, 2).with(Talent.GATHERER_XP, 1)
				.with(Talent.STRONGER_WEAPONS, 4).with(Talent.MORE_HEALTH, 5);
		assertEquals(3, data.pointsSpent(TalentTree.GATHERER));
		assertEquals(4, data.pointsSpent(TalentTree.WARRIOR));
		assertEquals(5, data.pointsSpent(TalentTree.GUARDIAN));
		assertEquals(0, data.pointsSpent(TalentTree.EXPLORER));
		assertEquals(12, data.pointsSpent());
	}

	@Test
	void unknownTalentsAndTreesAreKeptButDontCount() {
		TalentData data = new TalentData(Map.of("some_future_talent", 4, Talent.MORE_LUCK.id(), 2), Set.of(),
				Map.of("some_future_tree", 500L, TalentTree.EXPLORER.id(), 40L));
		assertEquals(2, data.pointsSpent());
		assertEquals(4, data.with(Talent.MORE_LUCK, 3).levels().get("some_future_talent"));
		assertEquals(500L, data.withXp(TalentTree.EXPLORER, 41L).xp().get("some_future_tree"));
		assertEquals(40L, data.xp(TalentTree.EXPLORER));
	}

	@Test
	void treeXpStartsAtZeroAndZeroRemoves() {
		assertEquals(0L, TalentData.EMPTY.xp(TalentTree.GATHERER));

		TalentData data = TalentData.EMPTY.withXp(TalentTree.GATHERER, 120L).withXp(TalentTree.WARRIOR, 7L);
		assertEquals(120L, data.xp(TalentTree.GATHERER));
		assertEquals(7L, data.xp(TalentTree.WARRIOR));
		assertEquals(0L, data.xp(TalentTree.GUARDIAN));

		TalentData cleared = data.withXp(TalentTree.GATHERER, 0L).withXp(TalentTree.WARRIOR, -3L);
		assertEquals(Map.of(), cleared.xp(), "0 or less removes the entry");
		assertEquals(0L, new TalentData(Map.of(), Set.of(), Map.of(TalentTree.GUARDIAN.id(), -9L)).xp(TalentTree.GUARDIAN),
				"a negative saved value reads as 0");
	}

	@Test
	void xpAndLevelsDontTouchEachOther() {
		TalentData data = TalentData.EMPTY.with(Talent.FASTER_RUNNING, 2).withDisabled(Talent.FASTER_RUNNING, true)
				.withXp(TalentTree.EXPLORER, 90L);
		assertEquals(90L, data.with(Talent.SWIM_SPEED, 1).xp(TalentTree.EXPLORER), "buying keeps the XP: points come from levels");
		assertTrue(data.withXp(TalentTree.EXPLORER, 1L).isDisabled(Talent.FASTER_RUNNING), "XP keeps the toggles");
		assertEquals(2, data.withXp(TalentTree.EXPLORER, 0L).level(Talent.FASTER_RUNNING), "XP keeps the levels");
	}

	@Test
	void rootsAreOpenAndChildrenNeedTheirRoot() {
		for (TalentTree tree : TalentTree.values()) {
			assertTrue(TalentData.EMPTY.isUnlocked(tree.root()), tree.root().id() + " is open from the start");
		}

		assertFalse(TalentData.EMPTY.isUnlocked(Talent.SWORD_SPECIALIST));
		assertTrue(TalentData.EMPTY.with(Talent.STRONGER_WEAPONS, 1).isUnlocked(Talent.SWORD_SPECIALIST));
	}

	@Test
	void singlePrereqBoundary() {
		// Higher Jumping's only prereq is Faster Running at level 1: 0 locks, 1 unlocks.
		assertFalse(TalentData.EMPTY.isUnlocked(Talent.HIGHER_JUMPING), "prereq at level 0 must lock");
		assertTrue(TalentData.EMPTY.with(Talent.FASTER_RUNNING, 1).isUnlocked(Talent.HIGHER_JUMPING), "prereq at level 1 must unlock");
	}

	@Test
	void higherGateNeedsItsLevel() {
		TalentPrereq gate = new TalentPrereq(Talent.STRONGER_WEAPONS, 5);
		TalentData four = TalentData.EMPTY.with(Talent.STRONGER_WEAPONS, 4);
		assertFalse(four.isUnlocked(Talent.LIFESTEAL), "Lifesteal needs Stronger Weapons 5, not 4");
		assertFalse(four.isMet(gate));
		assertEquals(List.of(gate), four.unmetPrereqs(Talent.LIFESTEAL), "the gate is reported with its level");
		assertTrue(four.with(Talent.STRONGER_WEAPONS, 5).isUnlocked(Talent.LIFESTEAL));
	}

	@Test
	void multiPrereqUnlocking() {
		// More Luck, the Gatherer capstone, needs Reach on the mining side and Fast Growth on the farming side.
		TalentData none = TalentData.EMPTY;
		assertFalse(none.isUnlocked(Talent.MORE_LUCK), "no prereqs met must lock");
		assertEquals(List.of(new TalentPrereq(Talent.REACH, 1), new TalentPrereq(Talent.FAST_GROWTH, 1)), none.unmetPrereqs(Talent.MORE_LUCK),
				"unmet prereqs are reported in prereq declaration order");

		TalentData both = TalentData.EMPTY.with(Talent.REACH, 1).with(Talent.FAST_GROWTH, 1);
		assertTrue(both.isUnlocked(Talent.MORE_LUCK), "every prereq met must unlock");
		assertEquals(List.of(), both.unmetPrereqs(Talent.MORE_LUCK));

		TalentData oneMissing = both.with(Talent.REACH, 0);
		assertFalse(oneMissing.isUnlocked(Talent.MORE_LUCK), "one unmet prereq must lock");
		assertEquals(List.of(new TalentPrereq(Talent.REACH, 1)), oneMissing.unmetPrereqs(Talent.MORE_LUCK), "only the missing prereq is reported");
	}

	@Test
	void savesAndLoadsEveryPart() {
		TalentData data = TalentData.EMPTY.with(Talent.SAFE_FALLING, 2).with(Talent.BOW_SPECIALIST, 1)
				.with(Talent.HIGHER_JUMPING, 3).withDisabled(Talent.HIGHER_JUMPING, true)
				.withXp(TalentTree.GATHERER, 11_992L).withXp(TalentTree.WARRIOR, 5_000_000_000L);

		// NBT: the save format. XP above an int must survive as a long.
		Tag saved = TalentData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		assertTrue(saved instanceof CompoundTag);
		assertEquals(data, TalentData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow());
		assertEquals(data, TalentData.fromNbt(data.toNbt()));

		// JSON: the codec must stay usable outside NBT too.
		var json = TalentData.CODEC.encodeStart(JsonOps.INSTANCE, data.withXp(TalentTree.WARRIOR, 300L)).getOrThrow();
		assertEquals(data.withXp(TalentTree.WARRIOR, 300L), TalentData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
	}

	@Test
	void theXpIsSavedAsAMapOfLongsByTreeId() {
		CompoundTag saved = TalentData.EMPTY.withXp(TalentTree.EXPLORER, 42L).with(Talent.FASTER_RUNNING, 1).toNbt();
		CompoundTag xp = (CompoundTag) saved.get("xp");
		assertEquals(LongTag.valueOf(42L), xp.get(TalentTree.EXPLORER.id()));
		assertEquals(1, xp.size());
		assertEquals(IntTag.valueOf(1), ((CompoundTag) saved.get("levels")).get(Talent.FASTER_RUNNING.id()));
	}

	@Test
	void syncsEveryPart() {
		TalentData data = TalentData.EMPTY.with(Talent.MORE_HEALTH, 5).with(Talent.STEP_ASSIST, 1)
				.withDisabled(Talent.STEP_ASSIST, true).withXp(TalentTree.GUARDIAN, 23_372L);
		ByteBuf buffer = Unpooled.buffer();

		try {
			TalentData.STREAM_CODEC.encode(buffer, data);
			assertEquals(data, TalentData.STREAM_CODEC.decode(buffer));
			assertEquals(0, buffer.readableBytes(), "the whole message is read");
		} finally {
			buffer.release();
		}
	}

	@Test
	void missingPartsReadAsEmpty() {
		assertEquals(TalentData.EMPTY, TalentData.fromNbt(new CompoundTag()));

		CompoundTag onlyXp = new CompoundTag();
		CompoundTag xp = new CompoundTag();
		xp.putLong(TalentTree.GUARDIAN.id(), 77L);
		onlyXp.put("xp", xp);
		assertEquals(TalentData.EMPTY.withXp(TalentTree.GUARDIAN, 77L), TalentData.fromNbt(onlyXp));
	}

	@Test
	void anyNumberTypeReadsAsXp() {
		CompoundTag tag = new CompoundTag();
		CompoundTag xp = new CompoundTag();
		xp.putInt(TalentTree.GATHERER.id(), 12);
		xp.putDouble(TalentTree.WARRIOR.id(), 34.0);
		tag.put("xp", xp);

		TalentData data = TalentData.fromNbt(tag);
		assertEquals(12L, data.xp(TalentTree.GATHERER));
		assertEquals(34L, data.xp(TalentTree.WARRIOR));
	}

	@Test
	void partsOfTheWrongTypeFailTheRead() {
		CompoundTag xpNotACompound = new CompoundTag();
		xpNotACompound.putInt("xp", 5);
		assertThrows(IllegalArgumentException.class, () -> TalentData.fromNbt(xpNotACompound));

		CompoundTag xpNotANumber = new CompoundTag();
		CompoundTag xp = new CompoundTag();
		xp.put(TalentTree.GATHERER.id(), StringTag.valueOf("lots"));
		xpNotANumber.put("xp", xp);
		assertThrows(IllegalArgumentException.class, () -> TalentData.fromNbt(xpNotANumber));

		CompoundTag disabledNotAList = new CompoundTag();
		disabledNotAList.putString("disabled", Talent.STEP_ASSIST.id());
		assertThrows(IllegalArgumentException.class, () -> TalentData.fromNbt(disabledNotAList));
	}

	@Test
	void effectiveLevelFollowsTheToggle() {
		TalentData on = TalentData.EMPTY.with(Talent.HIGHER_JUMPING, 5);
		assertFalse(on.isDisabled(Talent.HIGHER_JUMPING));
		assertEquals(5, on.effectiveLevel(Talent.HIGHER_JUMPING));

		TalentData off = on.withDisabled(Talent.HIGHER_JUMPING, true);
		assertTrue(off.isDisabled(Talent.HIGHER_JUMPING));
		assertEquals(0, off.effectiveLevel(Talent.HIGHER_JUMPING));
		assertEquals(5, off.level(Talent.HIGHER_JUMPING), "the purchased level is untouched by the toggle");
		assertEquals(5, off.pointsSpent(), "toggling off costs nothing");

		assertEquals(on, off.withDisabled(Talent.HIGHER_JUMPING, false), "toggling back on restores it");
	}

	@Test
	void withDisabledOnLevelZeroIsANoOp() {
		assertEquals(TalentData.EMPTY, TalentData.EMPTY.withDisabled(Talent.HIGHER_JUMPING, true),
				"a level-0 talent must not enter the disabled set");
		assertEquals(TalentData.EMPTY, TalentData.EMPTY.withDisabled(Talent.HIGHER_JUMPING, false));
	}

	@Test
	void levelChangesPreserveTheDisabledSet() {
		TalentData off = TalentData.EMPTY.with(Talent.HIGHER_JUMPING, 5).with(Talent.FASTER_RUNNING, 2)
				.withDisabled(Talent.HIGHER_JUMPING, true);

		TalentData lowered = off.with(Talent.HIGHER_JUMPING, 3);
		assertEquals(3, lowered.level(Talent.HIGHER_JUMPING));
		assertTrue(lowered.isDisabled(Talent.HIGHER_JUMPING), "a level change keeps the toggle");
		assertEquals(0, lowered.effectiveLevel(Talent.HIGHER_JUMPING), "still off after the level change");

		assertTrue(off.with(Talent.FASTER_RUNNING, 4).isDisabled(Talent.HIGHER_JUMPING),
				"another talent's level change keeps it too");
	}
}
