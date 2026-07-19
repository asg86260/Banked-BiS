package com.bankbis.data;

import com.duckblade.osrs.dpscalc.calc.model.DefenderAttributes;
import com.duckblade.osrs.dpscalc.calc.model.DefensiveBonuses;
import com.duckblade.osrs.dpscalc.calc.model.Skills;
import lombok.Builder;
import lombok.Value;

/**
 * Aggregate of the calc engine's three NPC-side inputs for one monster (variant).
 */
@Value
@Builder
public class NpcStats
{

	private final String displayName;
	private final int speed;
	private final Skills skills;
	private final DefensiveBonuses defensiveBonuses;
	private final DefenderAttributes attributes;

	public static NpcStats of(MonsterJson m)
	{
		return NpcStats.builder()
			.displayName(WikiDataMapper.displayName(m))
			.speed(m.getSpeed())
			.skills(WikiDataMapper.toNpcSkills(m))
			.defensiveBonuses(WikiDataMapper.toDefensiveBonuses(m))
			.attributes(WikiDataMapper.toDefenderAttributes(m))
			.build();
	}

}
