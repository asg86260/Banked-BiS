package com.duckblade.osrs.dpscalc.calc.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Elemental spells' base max hit scales with magic level, not the element cast:
 * once you clear a tier's threshold you hit as hard as the next element up.
 * Regression coverage for the Kraken parity fix (Earth Surge at 99 was hitting
 * for its static 23 instead of Fire Surge's 24). Reference: wiki calc
 * getSpellMaxHit.
 */
class SpellTest
{

	@Test
	void surgesScaleToFireAt95()
	{
		// at 99 magic every surge hits for Fire Surge's 24
		assertEquals(24, Spell.WIND_SURGE.getBaseMaxHit(99));
		assertEquals(24, Spell.WATER_SURGE.getBaseMaxHit(99));
		assertEquals(24, Spell.EARTH_SURGE.getBaseMaxHit(99));
		assertEquals(24, Spell.FIRE_SURGE.getBaseMaxHit(99));
	}

	@Test
	void surgesStepDownBelowThresholds()
	{
		// Earth Surge: fire@95, earth@90, water@85, else wind
		assertEquals(24, Spell.EARTH_SURGE.getBaseMaxHit(95));
		assertEquals(23, Spell.EARTH_SURGE.getBaseMaxHit(90));
		assertEquals(22, Spell.EARTH_SURGE.getBaseMaxHit(89));
		assertEquals(21, Spell.EARTH_SURGE.getBaseMaxHit(84));
	}

	@Test
	void lowerTiersScaleToo()
	{
		// bolts: fire@35, earth@29, water@23, else wind
		assertEquals(12, Spell.WIND_BOLT.getBaseMaxHit(35));
		assertEquals(11, Spell.WIND_BOLT.getBaseMaxHit(34));
		assertEquals(9, Spell.WIND_BOLT.getBaseMaxHit(22));
	}

	@Test
	void nonElementalSpellsIgnoreLevel()
	{
		// ancient / god / demonbane spells keep their static base at any level
		assertEquals(30, Spell.ICE_BARRAGE.getBaseMaxHit(1));
		assertEquals(30, Spell.ICE_BARRAGE.getBaseMaxHit(99));
		assertEquals(20, Spell.SARADOMIN_STRIKE.getBaseMaxHit(99));
		assertEquals(30, Spell.DARK_DEMONBANE.getBaseMaxHit(99));
	}

}
