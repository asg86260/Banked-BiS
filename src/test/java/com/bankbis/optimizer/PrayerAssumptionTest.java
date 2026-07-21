package com.bankbis.optimizer;

import com.duckblade.osrs.dpscalc.calc.model.Prayer;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PrayerAssumptionTest
{

	@Test
	void autoUsesBestByLevel()
	{
		assertEquals(ImmutableSet.of(Prayer.PIETY), PrayerAssumption.AUTO.prayersFor(CombatClass.MELEE, 99));
		assertEquals(ImmutableSet.of(Prayer.RIGOUR), PrayerAssumption.AUTO.prayersFor(CombatClass.RANGED, 99));
		assertEquals(ImmutableSet.of(Prayer.AUGURY), PrayerAssumption.AUTO.prayersFor(CombatClass.MAGIC, 99));
	}

	@Test
	void noUnlocksCapsAtFreelyAvailablePrayers()
	{
		assertEquals(ImmutableSet.of(Prayer.ULTIMATE_STRENGTH, Prayer.INCREDIBLE_REFLEXES),
			PrayerAssumption.NO_UNLOCKS.prayersFor(CombatClass.MELEE, 99));
		assertEquals(ImmutableSet.of(Prayer.EAGLE_EYE),
			PrayerAssumption.NO_UNLOCKS.prayersFor(CombatClass.RANGED, 99));
		assertEquals(ImmutableSet.of(Prayer.MYSTIC_MIGHT),
			PrayerAssumption.NO_UNLOCKS.prayersFor(CombatClass.MAGIC, 99));
	}

	@Test
	void noUnlocksStillRespectsLowPrayerLevel()
	{
		Set<Prayer> prayers = PrayerAssumption.NO_UNLOCKS.prayersFor(CombatClass.RANGED, 30);
		assertEquals(ImmutableSet.of(Prayer.HAWK_EYE), prayers);
	}

	@Test
	void noneIsEmpty()
	{
		assertTrue(PrayerAssumption.NONE.prayersFor(CombatClass.MELEE, 99).isEmpty());
	}

}
