package no.kvaksrud.imsotalented.talent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The server's per-player tree XP bookkeeping: pending XP, the hourly full-rate allowance, and the AFK check. */
class TreeXpLedgerTest {
	@Test
	void theDesignsLimits() {
		assertEquals(3000.0, TalentValues.TREE_XP_FULL_RATE_PER_HOUR, "full rate for 3,000 XP an hour per tree");
		assertEquals(0.1, TalentValues.TREE_XP_OVER_CAP_SHARE, "then a tenth");
		assertEquals(72_000L, TalentValues.TREE_XP_WINDOW_TICKS, "an hour of ticks");
		assertEquals(6_000L, TalentValues.AFK_TICKS, "five minutes without turning the camera");
	}

	@Test
	void creditedXpWaitsUntilItIsTaken() {
		TreeXpLedger ledger = new TreeXpLedger();
		assertEquals(0.0, ledger.pending(TalentTree.GATHERER));
		assertEquals(5.0, ledger.credit(TalentTree.GATHERER, 5.0, 100L), 1e-9);
		assertEquals(0.5, ledger.credit(TalentTree.GATHERER, 0.5, 100L), 1e-9);
		assertEquals(5.5, ledger.pending(TalentTree.GATHERER), 1e-9);
		assertEquals(0.0, ledger.pending(TalentTree.WARRIOR), "each tree has its own");
	}

	@Test
	void takingKeepsTheFraction() {
		TreeXpLedger ledger = new TreeXpLedger();
		ledger.credit(TalentTree.EXPLORER, 0.6, 1L);
		assertEquals(0L, ledger.takeWhole(TalentTree.EXPLORER), "no whole point yet");
		ledger.credit(TalentTree.EXPLORER, 0.6, 2L);
		assertEquals(1L, ledger.takeWhole(TalentTree.EXPLORER));
		assertEquals(0.2, ledger.pending(TalentTree.EXPLORER), 1e-9, "the fraction waits for the next point");

		ledger.credit(TalentTree.EXPLORER, 25.0, 3L);
		assertEquals(25L, ledger.takeWhole(TalentTree.EXPLORER), "a quarry's whole vein comes out in one go");
		assertEquals(0.2, ledger.pending(TalentTree.EXPLORER), 1e-9);
		assertEquals(0L, ledger.takeWhole(TalentTree.GUARDIAN));
	}

	@Test
	void pastTheHourlyAllowanceOnlyATenthCounts() {
		TreeXpLedger ledger = new TreeXpLedger();
		assertEquals(2995.0, ledger.credit(TalentTree.WARRIOR, 2995.0, 0L), 1e-9);
		assertEquals(5.0 + 0.5, ledger.credit(TalentTree.WARRIOR, 10.0, 10L), 1e-9, "5 left at the full rate, the other 5 at a tenth");
		assertEquals(1.0, ledger.credit(TalentTree.WARRIOR, 10.0, 20L), 1e-9, "all of it past the allowance");
		assertEquals(3001.5, ledger.pending(TalentTree.WARRIOR), 1e-9);
		assertEquals(10.0, ledger.credit(TalentTree.GUARDIAN, 10.0, 20L), 1e-9, "another tree has its own allowance");
	}

	@Test
	void anHourAfterItStartedTheAllowanceIsBack() {
		TreeXpLedger ledger = new TreeXpLedger();
		ledger.credit(TalentTree.WARRIOR, 3000.0, 1000L);
		assertEquals(1.0, ledger.credit(TalentTree.WARRIOR, 10.0, 1000L + 71_999L), 1e-9, "a tick short of the hour");
		assertEquals(10.0, ledger.credit(TalentTree.WARRIOR, 10.0, 1000L + 72_000L), 1e-9, "the hour started at the first XP");
		assertEquals(2990.0 + 0.5, ledger.credit(TalentTree.WARRIOR, 2995.0, 1000L + 72_001L), 1e-9,
				"the new hour counts from its own first XP: 2,990 more at the full rate, the other 5 at a tenth");
	}

	@Test
	void theHourStartsAtTheFirstXpEarnedInIt() {
		TreeXpLedger ledger = new TreeXpLedger();
		// Nothing earned for a long time, then the allowance runs from the first XP, not from tick 0.
		ledger.credit(TalentTree.GATHERER, 3000.0, 500_000L);
		assertEquals(0.5, ledger.credit(TalentTree.GATHERER, 5.0, 500_000L + 72_000L - 1L), 1e-9);
	}

	@Test
	void clearingForgetsThePendingXpAndTheAllowance() {
		TreeXpLedger ledger = new TreeXpLedger();
		ledger.credit(TalentTree.GATHERER, 3000.5, 0L);
		ledger.clear();

		for (TalentTree tree : TalentTree.values()) {
			assertEquals(0.0, ledger.pending(tree), tree.id());
		}

		assertEquals(10.0, ledger.credit(TalentTree.GATHERER, 10.0, 5L), 1e-9, "the full rate again, as after /talents reset");
	}

	@Test
	void aPlayerWhoDoesntTurnTheCameraGoesIdleAfterFiveMinutes() {
		TreeXpLedger ledger = new TreeXpLedger();
		assertFalse(ledger.idle(1_000_000L), "never looked yet: a player who just joined isn't idle");

		ledger.look(90.0F, 10.0F, 1000L);
		assertFalse(ledger.idle(1000L + TalentValues.AFK_TICKS), "exactly five minutes");
		assertTrue(ledger.idle(1000L + TalentValues.AFK_TICKS + 1L), "a tick more");

		// Holding a key doesn't turn the camera: the same angles don't count as activity.
		ledger.look(90.0F, 10.0F, 4000L);
		assertTrue(ledger.idle(1000L + TalentValues.AFK_TICKS + 1L));
	}

	@Test
	void anyTurnOfTheCameraCountsAsActivity() {
		TreeXpLedger ledger = new TreeXpLedger();
		ledger.look(90.0F, 10.0F, 1000L);
		ledger.look(90.5F, 10.0F, 4000L);
		assertFalse(ledger.idle(4000L + TalentValues.AFK_TICKS), "turning sideways");
		assertTrue(ledger.idle(4000L + TalentValues.AFK_TICKS + 1L));

		ledger.look(90.5F, 11.0F, 9000L);
		assertFalse(ledger.idle(9000L + TalentValues.AFK_TICKS), "looking up or down");
	}
}
