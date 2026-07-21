package com.duckblade.osrs.dpscalc.calc.gearbonus;

import com.duckblade.osrs.dpscalc.calc.WeaponComputable;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeContext;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeInputs;
import com.duckblade.osrs.dpscalc.calc.defender.DefenderSkillsComputable;
import com.duckblade.osrs.dpscalc.calc.model.GearBonuses;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class TbowGearBonus implements GearBonusComputable
{

	private static final Set<Integer> TBOW_IDS = ImmutableSet.of(
		ItemID.TWISTED_BOW
	);

	private static final int LOW_MAGIC_WARN_THRESHOLD = 100;

	private final WeaponComputable weaponComputable;
	private final DefenderSkillsComputable defenderSkillsComputable;

	@Override
	public boolean isApplicable(ComputeContext context)
	{
		return TBOW_IDS.contains(context.get(weaponComputable).getItemId());
	}

	@Override
	public GearBonuses compute(ComputeContext context)
	{
		int defenderMagicLevel = context.get(defenderSkillsComputable).getTotals().getOrDefault(Skill.MAGIC, 0);
		int defenderMagicAccuracy = context.get(ComputeInputs.DEFENDER_ATTRIBUTES).getAccuracyMagic();

		int magic = Math.min(250, Math.max(defenderMagicLevel, defenderMagicAccuracy));
		if (magic < LOW_MAGIC_WARN_THRESHOLD)
		{
			context.warn("Using the twisted bow against low-magic targets incurs negative bonuses.");
		}

		return GearBonuses.of(tbowFormula(magic, true), tbowFormula(magic, false));
	}

	// formulas are the same shape with different static values. Each term is
	// truncated to an integer (wiki calc parity — tbowScaling); keeping them as
	// doubles drifts the factor by ~1% and shifts max hit by a point.
	@VisibleForTesting
	static double tbowFormula(int magic, boolean accuracy)
	{
		int base = accuracy ? 140 : 250;
		int factor = accuracy ? 10 : 14;

		int t2 = (3 * magic - factor) / 100;
		int inner = (3 * magic / 10) - (10 * factor);
		int t3 = (inner * inner) / 100;
		return (base + t2 - t3) / 100.0;
	}

}
