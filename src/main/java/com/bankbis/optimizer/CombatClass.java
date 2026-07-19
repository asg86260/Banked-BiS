package com.bankbis.optimizer;

import com.duckblade.osrs.dpscalc.calc.model.AttackType;
import com.duckblade.osrs.dpscalc.calc.model.Prayer;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CombatClass
{

	MELEE(ImmutableSet.of(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH), Prayer.PIETY),
	RANGED(ImmutableSet.of(AttackType.RANGED), Prayer.RIGOUR),
	MAGIC(ImmutableSet.of(AttackType.MAGIC), Prayer.AUGURY),
	;

	private final Set<AttackType> attackTypes;
	private final Prayer bestPrayer;

	public boolean includes(AttackType type)
	{
		return attackTypes.contains(type);
	}

}
