package com.bankbis.optimizer;

import com.duckblade.osrs.dpscalc.calc.model.Spell;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Picks the spells worth evaluating for a caster: the highest castable
 * standard elemental spell per element at the player's magic level. All
 * four elements are kept because elemental weaknesses make the element
 * itself matter, not just the tier. Ancients and Arceuus are out of scope
 * (autocast and unlock state are not detectable).
 */
public final class SpellSelector
{

	private static final Map<Spell, Integer> LEVEL_REQS = ImmutableMap.<Spell, Integer>builder()
		.put(Spell.WIND_STRIKE, 1).put(Spell.WATER_STRIKE, 5).put(Spell.EARTH_STRIKE, 9).put(Spell.FIRE_STRIKE, 13)
		.put(Spell.WIND_BOLT, 17).put(Spell.WATER_BOLT, 23).put(Spell.EARTH_BOLT, 29).put(Spell.FIRE_BOLT, 35)
		.put(Spell.WIND_BLAST, 41).put(Spell.WATER_BLAST, 47).put(Spell.EARTH_BLAST, 53).put(Spell.FIRE_BLAST, 59)
		.put(Spell.WIND_WAVE, 62).put(Spell.WATER_WAVE, 65).put(Spell.EARTH_WAVE, 70).put(Spell.FIRE_WAVE, 75)
		.put(Spell.WIND_SURGE, 81).put(Spell.WATER_SURGE, 85).put(Spell.EARTH_SURGE, 90).put(Spell.FIRE_SURGE, 95)
		.build();

	// best-first per element
	private static final List<List<Spell>> ELEMENT_TIERS = ImmutableList.of(
		ImmutableList.of(Spell.WIND_SURGE, Spell.WIND_WAVE, Spell.WIND_BLAST, Spell.WIND_BOLT, Spell.WIND_STRIKE),
		ImmutableList.of(Spell.WATER_SURGE, Spell.WATER_WAVE, Spell.WATER_BLAST, Spell.WATER_BOLT, Spell.WATER_STRIKE),
		ImmutableList.of(Spell.EARTH_SURGE, Spell.EARTH_WAVE, Spell.EARTH_BLAST, Spell.EARTH_BOLT, Spell.EARTH_STRIKE),
		ImmutableList.of(Spell.FIRE_SURGE, Spell.FIRE_WAVE, Spell.FIRE_BLAST, Spell.FIRE_BOLT, Spell.FIRE_STRIKE));

	private SpellSelector()
	{
	}

	public static List<Spell> bestCastable(int magicLevel)
	{
		List<Spell> spells = new ArrayList<>();
		for (List<Spell> tiers : ELEMENT_TIERS)
		{
			for (Spell spell : tiers)
			{
				if (magicLevel >= LEVEL_REQS.get(spell))
				{
					spells.add(spell);
					break;
				}
			}
		}
		return spells;
	}

}
