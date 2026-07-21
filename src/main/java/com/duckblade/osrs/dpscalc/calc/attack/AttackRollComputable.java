package com.duckblade.osrs.dpscalc.calc.attack;

import com.duckblade.osrs.dpscalc.calc.compute.Computable;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeContext;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeInputs;
import com.duckblade.osrs.dpscalc.calc.exceptions.DpsComputeException;
import com.duckblade.osrs.dpscalc.calc.gearbonus.AggregateGearBonusesComputable;
import com.duckblade.osrs.dpscalc.calc.model.AttackType;
import com.duckblade.osrs.dpscalc.calc.model.DefenderAttributes;
import com.duckblade.osrs.dpscalc.calc.model.Spell;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class AttackRollComputable implements Computable<Integer>
{

	private final EffectiveAttackLevelComputable effectiveAttackLevelComputable;
	private final AttackBonusComputable attackBonusComputable;
	private final AggregateGearBonusesComputable aggregateGearBonusesComputable;

	@Override
	public Integer compute(ComputeContext context)
	{
		int effectiveAttack = context.get(effectiveAttackLevelComputable);
		int attackBonus = context.get(attackBonusComputable);
		double accuracyGearBonus = context.get(aggregateGearBonusesComputable).getAccuracyBonus();

		int baseRoll = effectiveAttack * (attackBonus + 64);
		int roll = (int) (baseRoll * accuracyGearBonus);

		// elemental weakness: matching-element spells add severity% of the
		// BASE roll (pre gear multipliers), after other modifiers - matches
		// the wiki calc's PLAYER_ACCURACY_SPELLEMENT step
		int severity = elementalWeaknessSeverity(context);
		if (severity > 0)
		{
			roll += baseRoll * severity / 100;
		}
		return roll;
	}

	public static int elementalWeaknessSeverity(ComputeContext context)
	{
		if (context.get(ComputeInputs.ATTACK_STYLE).getAttackType() != AttackType.MAGIC)
		{
			return 0;
		}
		Spell spell;
		try
		{
			spell = context.get(ComputeInputs.SPELL);
		}
		catch (DpsComputeException e)
		{
			return 0; // not casting (e.g. powered staff)
		}
		if (spell == null || spell.getElement() == null)
		{
			return 0;
		}
		DefenderAttributes attributes = context.get(ComputeInputs.DEFENDER_ATTRIBUTES);
		if (!spell.getElement().equals(attributes.getElementalWeakness()))
		{
			return 0;
		}
		return attributes.getElementalWeaknessSeverity();
	}
}
