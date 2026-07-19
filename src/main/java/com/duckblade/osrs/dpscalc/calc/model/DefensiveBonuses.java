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
	private final int defenseRanged;
	private final int defenseMagic;

}
