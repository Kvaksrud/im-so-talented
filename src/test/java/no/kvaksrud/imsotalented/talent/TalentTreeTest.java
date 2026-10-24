package no.kvaksrud.imsotalented.talent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The shape of the four trees: what the talent screen draws and what buying checks, so a typo in {@link Talent} shows here. */
class TalentTreeTest {
	@Test
	void thereAre34TalentsWith117Levels() {
		assertEquals(34, Talent.values().length);
		assertEquals(117, Talent.totalLevels());
	}

	@Test
	void eachTreeTopsOutAtItsTalentLevels() {
		assertEquals(27, TalentTree.GATHERER.maxLevel());
		assertEquals(29, TalentTree.WARRIOR.maxLevel());
		assertEquals(31, TalentTree.GUARDIAN.maxLevel());
		assertEquals(30, TalentTree.EXPLORER.maxLevel());

		int total = 0;
		int talents = 0;

		for (TalentTree tree : TalentTree.values()) {
			total += tree.maxLevel();
			talents += tree.talents().size();
		}

		assertEquals(Talent.totalLevels(), total, "every talent level belongs to exactly one tree");
		assertEquals(Talent.values().length, talents, "every talent belongs to exactly one tree");
	}

	@Test
	void eachTreeHasOneRootAtTheTop() {
		assertSame(Talent.FASTER_MINING, TalentTree.GATHERER.root());
		assertSame(Talent.STRONGER_WEAPONS, TalentTree.WARRIOR.root());
		assertSame(Talent.MORE_HEALTH, TalentTree.GUARDIAN.root());
		assertSame(Talent.FASTER_RUNNING, TalentTree.EXPLORER.root());

		for (TalentTree tree : TalentTree.values()) {
			int roots = 0;

			for (Talent talent : tree.talents()) {
				roots += talent.prereqs().length == 0 ? 1 : 0;
			}

			assertEquals(1, roots, tree.id() + " must have exactly one talent without prereqs");
			assertEquals(0, tree.root().row(), tree.id() + "'s root sits in row 0");
			assertSame(tree.root(), tree.talents().getFirst(), tree.id() + "'s root is declared first");
		}
	}

	@Test
	void prereqsAreDeclaredBeforeUseInTheSameTreeAndAbove() {
		for (Talent talent : Talent.values()) {
			for (TalentPrereq prereq : talent.prereqs()) {
				Talent needed = prereq.talent();
				assertTrue(needed.ordinal() < talent.ordinal(), talent.id() + " lists " + needed.id() + ", which is declared later");
				assertSame(talent.tree(), needed.tree(), talent.id() + " needs " + needed.id() + " from another tree, whose points it can't use");
				assertTrue(needed.row() < talent.row(), talent.id() + " must sit below its prereq " + needed.id());
				assertTrue(prereq.minLevel() >= 1 && prereq.minLevel() <= needed.maxLevel(),
						talent.id() + " asks for " + needed.id() + " at level " + prereq.minLevel() + ", which can't be bought");
			}
		}
	}

	@Test
	void noTwoTalentsShareASpotInATree() {
		for (TalentTree tree : TalentTree.values()) {
			Set<String> spots = new HashSet<>();

			for (Talent talent : tree.talents()) {
				assertTrue(talent.column() >= 0.0 && talent.column() * 2.0 == Math.rint(talent.column() * 2.0),
						talent.id() + " must be on a whole or half column");
				assertTrue(spots.add(talent.row() + "/" + talent.column()),
						talent.id() + " sits on the same spot as another " + tree.id() + " talent");
			}
		}
	}

	@Test
	void eachTreeHasItsXpTalentRightUnderTheRoot() {
		for (TalentTree tree : TalentTree.values()) {
			Talent xp = tree.xpTalent();
			assertSame(tree, xp.tree(), xp.id() + " must be in " + tree.id());
			assertEquals(3, xp.maxLevel(), xp.id());
			assertEquals(List.of(new TalentPrereq(tree.root(), 1)), List.of(xp.prereqs()), xp.id() + " hangs off the root");
			assertEquals(tree.id() + "_xp", xp.id());
		}
	}

	@Test
	void lifestealIsGatedOnStrongerWeaponsFive() {
		assertEquals(List.of(new TalentPrereq(Talent.STRONGER_WEAPONS, 5)), List.of(Talent.LIFESTEAL.prereqs()));
		assertEquals(Talent.STRONGER_WEAPONS.maxLevel(), Talent.LIFESTEAL.prereqs()[0].minLevel(), "every weapon build reaches it");
	}

	@Test
	void idsAndIconsAreWellFormed() {
		Set<String> ids = new HashSet<>();

		for (Talent talent : Talent.values()) {
			assertTrue(ids.add(talent.id()), "duplicate id " + talent.id());
			assertTrue(talent.id().matches("[a-z0-9_]+"), talent.id());
			assertTrue(talent.icon().matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"), talent.icon());
			assertSame(talent, Talent.byId(talent.id()).orElseThrow());
		}

		for (TalentTree tree : TalentTree.values()) {
			assertSame(tree, TalentTree.byId(tree.id()).orElseThrow());
			assertEquals(0xFF, tree.color() >>> 24, tree.id() + "'s color must be opaque");
		}

		assertTrue(Talent.byId("extra_inventory").isEmpty(), "Extra Inventory is the Backpack now");
		assertTrue(TalentTree.byId("combat").isEmpty());
	}
}
