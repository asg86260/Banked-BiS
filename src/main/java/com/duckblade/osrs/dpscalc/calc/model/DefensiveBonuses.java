package com.duckblade.osrs.dpscalc.calc.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class DefensiveBonuses
{

	public static final DefensiveBonuses EMPTY = DefensiveBonuses.builder().build();

	private final int defenseStab;
	private final int defenseSlash;
	private final int defenseCrush;
	private final int defenseRanged; // standard ranged defence (bows)
	private final int defenseMagic;

	// split ranged defence (2024 mechanic): light = thrown, heavy = crossbows /
	// chinchompas; defenseRanged above is the standard (bow) value.
	private final int defenseRangedLight;
	private final int defenseRangedHeavy;

}
