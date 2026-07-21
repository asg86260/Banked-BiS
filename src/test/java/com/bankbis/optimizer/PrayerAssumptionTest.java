package com.bankbis.optimizer;

import com.duckblade.osrs.dpscalc.calc.model.Prayer;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PrayerAssumptionTest
{

	private static final PrayerUnlocks NOTHING_UNLOCKED = new PrayerUnlocks(false, false, false);

	@Test
	void autoUsesBestByLevelWhenUnlocked()
	{
		assertEquals(ImmutableSet.of(Prayer.PIETY), PrayerAssumption.AUTO.prayersFor(CombatClass.MELEE, 99, PrayerUnlocks.ALL));
		assertEquals(ImmutableSet.of(Prayer.RIGOUR), PrayerAssumption.AUTO.prayersFor(CombatClass.RANGED, 99, PrayerUnlocks.ALL));
		assertEquals(ImmutableSet.of(Prayer.AUGURY), PrayerAssumption.AUTO.prayersFor(CombatClass.MAGIC, 99, PrayerUnlocks.ALL));
	}

	@Test
	void autoRespectsMissingUnlocks()
	{
		assertEquals(ImmutableSet.of(Prayer.ULTIMATE_STRENGTH, Prayer.INCREDIBLE_REFLEXES),
			PrayerAssumption.AUTO.prayersFor(CombatClass.MELEE, 99, NOTHING_UNLOCKED));
		assertEquals(ImmutableSet.of(Prayer.EAGLE_EYE),
			PrayerAssumption.AUTO.prayersFor(CombatClass.RANGED, 99, NOTHING_UNLOCKED));
		assertEquals(ImmutableSet.of(Prayer.MYSTIC_MIGHT),
			PrayerAssumption.AUTO.prayersFor(CombatClass.MAGIC, 99, NOTHING_UNLOCKED));
	}

	@Test
	void autoAppliesUnlocksPerClass()
	{
		// rigour unlocked but knight waves not done: ranged gets Rigour,
		// melee stays on basic prayers
		PrayerUnlocks rigourOnly = new PrayerUnlocks(false, true, false);
		assertEquals(ImmutableSet.of(Prayer.RIGOUR),
			PrayerAssumption.AUTO.prayersFor(CombatClass.RANGED, 99, rigourOnly));
		assertEquals(ImmutableSet.of(Prayer.ULTIMATE_STRENGTH, Prayer.INCREDIBLE_REFLEXES),
			PrayerAssumption.AUTO.prayersFor(CombatClass.MELEE, 99, rigourOnly));
	}

	@Test
	void noUnlocksCapsEvenWhenUnlocked()
	{
		assertEquals(ImmutableSet.of(Prayer.ULTIMATE_STRENGTH, Prayer.INCREDIBLE_REFLEXES),
			PrayerAssumption.NO_UNLOCKS.prayersFor(CombatClass.MELEE, 99, PrayerUnlocks.ALL));
		assertEquals(ImmutableSet.of(Prayer.EAGLE_EYE),
			PrayerAssumption.NO_UNLOCKS.prayersFor(CombatClass.RANGED, 99, PrayerUnlocks.ALL));
		assertEquals(ImmutableSet.of(Prayer.MYSTIC_MIGHT),
			PrayerAssumption.NO_UNLOCKS.prayersFor(CombatClass.MAGIC, 99, PrayerUnlocks.ALL));
	}

	@Test
	void noUnlocksStillRespectsLowPrayerLevel()
	{
		Set<Prayer> prayers = PrayerAssumption.NO_UNLOCKS.prayersFor(CombatClass.RANGED, 30, PrayerUnlocks.ALL);
		assertEquals(ImmutableSet.of(Prayer.HAWK_EYE), prayers);
	}

	@Test
	void noneIsEmpty()
	{
		assertTrue(PrayerAssumption.NONE.prayersFor(CombatClass.MELEE, 99, PrayerUnlocks.ALL).isEmpty());
	}

}
