package com.duckblade.osrs.dpscalc.calc.maxhit.magic;

import com.duckblade.osrs.dpscalc.calc.attack.AttackRollComputable;
import com.duckblade.osrs.dpscalc.calc.compute.Computable;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeContext;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeInputs;
import com.duckblade.osrs.dpscalc.calc.gearbonus.AggregateGearBonusesComputable;
import com.duckblade.osrs.dpscalc.calc.maxhit.StrengthBonusComputable;
import com.duckblade.osrs.dpscalc.calc.model.Prayer;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class MageMaxHitComputable implements Computable<Integer>
{

	private final Set<MagicMaxHitComputable> maxHitComputables;
	private final StrengthBonusComputable strengthBonusComputable;
	private final AggregateGearBonusesComputable aggregateGearBonusesComputable;

	@Override
	public Integer compute(ComputeContext context)
	{
		int base = maxHitComputables.stream()
			.filter(mmhc -> mmhc.isApplicable(context))
			.mapToInt(context::get)
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("No magic max hit provider for inputs"));

		// Each stage truncates independently, matching the wiki calc: the magic
		// damage bonus first (additive), then gear multipliers like the imbued
		// black mask (x23/20). Folding them into one multiply rounds differently.
		int magDmgBonus = context.get(strengthBonusComputable)
			+ prayerMagicDamage(context.get(ComputeInputs.ATTACKER_PRAYERS));
		int maxHit = base + base * magDmgBonus / 100;

		double gearBonus = context.get(aggregateGearBonusesComputable).getStrengthBonus();
		maxHit = (int) (maxHit * gearBonus);

		// elemental weakness: matching-element spells add severity% of the
		// (pre-bonus) base max hit, after all multipliers (wiki calc parity)
		int severity = AttackRollComputable.elementalWeaknessSeverity(context);
		if (severity > 0)
		{
			maxHit += base * severity / 100;
		}
		return maxHit;
	}

	// Magic damage % from prayers (wiki calc: magicDamageBonus, added to the
	// magic damage bonus). Augury gained +4% in a 2024 update.
	private static int prayerMagicDamage(Set<Prayer> prayers)
	{
		int bonus = 0;
		for (Prayer p : prayers)
		{
			switch (p)
			{
				case AUGURY:
					bonus += 4;
					break;
				case MYSTIC_MIGHT:
					bonus += 2;
					break;
				case MYSTIC_LORE:
					bonus += 1;
					break;
				default:
					break;
			}
		}
		return bonus;
	}
}
