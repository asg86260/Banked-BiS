package com.bankbis.optimizer;

import com.duckblade.osrs.dpscalc.calc.model.Spell;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SpellSelectorTest
{

	@Test
	void maxedPlayerGetsAllSurges()
	{
		List<Spell> spells = SpellSelector.bestCastable(99);
		assertEquals(List.of(Spell.WIND_SURGE, Spell.WATER_SURGE, Spell.EARTH_SURGE, Spell.FIRE_SURGE), spells);
	}

	@Test
	void midLevelGetsBestTierPerElement()
	{
		// 60 magic: fire blast (59), earth blast (53), water blast (47), wind blast (41)
		List<Spell> spells = SpellSelector.bestCastable(60);
		assertEquals(List.of(Spell.WIND_BLAST, Spell.WATER_BLAST, Spell.EARTH_BLAST, Spell.FIRE_BLAST), spells);
	}

	@Test
	void surgeCutoffsAreExact()
	{
		// 94 magic: everything surges except fire (95)
		List<Spell> spells = SpellSelector.bestCastable(94);
		assertTrue(spells.contains(Spell.EARTH_SURGE));
		assertTrue(spells.contains(Spell.FIRE_WAVE));
		assertTrue(!spells.contains(Spell.FIRE_SURGE));
	}

	@Test
	void lowLevelOnlyGetsCastableElements()
	{
		// level 3: only wind strike castable
		assertEquals(List.of(Spell.WIND_STRIKE), SpellSelector.bestCastable(3));
	}

}
