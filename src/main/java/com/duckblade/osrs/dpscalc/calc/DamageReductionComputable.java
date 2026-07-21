package com.duckblade.osrs.dpscalc.calc;

import com.duckblade.osrs.dpscalc.calc.compute.Computable;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeContext;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeInputs;
import com.duckblade.osrs.dpscalc.calc.exceptions.DpsComputeException;
import com.duckblade.osrs.dpscalc.calc.gearbonus.MeleeDemonbaneGearBonus;
import com.duckblade.osrs.dpscalc.calc.maxhit.BaseMaxHitComputable;
import com.duckblade.osrs.dpscalc.calc.model.AttackType;
import com.duckblade.osrs.dpscalc.calc.model.DefenderAttributes;
import com.duckblade.osrs.dpscalc.calc.model.Spell;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

/**
 * Flat monster damage reductions from the wiki calc's hit transforms:
 * some monsters divide every successful hit by a constant (e.g. the
 * Kraken takes 1/7 damage from ranged). Expressed as the expected-value
 * factor over the uniform 0..maxHit roll so DPT can be scaled directly.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class DamageReductionComputable implements Computable<Double>
{

	private static final Set<Integer> TEKTON_IDS = ImmutableSet.of(7540, 7543, 7544, 7545);
	private static final Set<Integer> GLOWING_CRYSTAL_IDS = ImmutableSet.of(7568);
	private static final Set<Integer> OLM_HEAD_IDS = ImmutableSet.of(7551, 7554);
	private static final Set<Integer> OLM_MELEE_HAND_IDS = ImmutableSet.of(7552, 7555);
	private static final Set<Integer> OLM_MAGE_HAND_IDS = ImmutableSet.of(7550, 7553);
	private static final Set<Integer> ICE_DEMON_IDS = ImmutableSet.of(7584, 7585);

	private final BaseMaxHitComputable baseMaxHitComputable;
	private final WeaponComputable weaponComputable;

	@Override
	public Double compute(ComputeContext context)
	{
		DefenderAttributes attrs = context.get(ComputeInputs.DEFENDER_ATTRIBUTES);
		AttackType type = context.get(ComputeInputs.ATTACK_STYLE).getAttackType();
		int npcId = attrs.getNpcId();
		String name = baseName(attrs.getName());

		if (("Kraken".equals(name) || "Cave kraken".equals(name)) && type == AttackType.RANGED)
		{
			return divisionFactor(context, 7, 1);
		}
		if (TEKTON_IDS.contains(npcId) && type == AttackType.MAGIC)
		{
			return divisionFactor(context, 5, 1);
		}
		if (GLOWING_CRYSTAL_IDS.contains(npcId) && type == AttackType.MAGIC)
		{
			return divisionFactor(context, 3, 0);
		}
		if ((OLM_HEAD_IDS.contains(npcId) || OLM_MELEE_HAND_IDS.contains(npcId)) && type == AttackType.MAGIC)
		{
			return divisionFactor(context, 3, 0);
		}
		if ((OLM_MAGE_HAND_IDS.contains(npcId) || OLM_MELEE_HAND_IDS.contains(npcId)) && type == AttackType.RANGED)
		{
			return divisionFactor(context, 3, 0);
		}
		if (ICE_DEMON_IDS.contains(npcId) && !"fire".equals(spellElement(context)) && !usingMeleeDemonbane(context))
		{
			return divisionFactor(context, 3, 0);
		}
		return 1.0;
	}

	/**
	 * Expected value of max(floor(h / divisor), minimum) over the uniform
	 * hit roll 0..maxHit, relative to the untransformed expectation.
	 */
	private double divisionFactor(ComputeContext context, int divisor, int minimum)
	{
		int maxHit = context.get(baseMaxHitComputable);
		if (maxHit <= 0)
		{
			return 1.0;
		}
		long sum = 0;
		for (int h = 0; h <= maxHit; h++)
		{
			sum += Math.max(h / divisor, minimum);
		}
		double transformed = (double) sum / (maxHit + 1);
		double original = maxHit / 2.0;
		return transformed / original;
	}

	private static String baseName(String displayName)
	{
		if (displayName == null)
		{
			return "";
		}
		int variantIdx = displayName.indexOf(" (");
		return variantIdx > 0 ? displayName.substring(0, variantIdx) : displayName;
	}

	private static String spellElement(ComputeContext context)
	{
		try
		{
			Spell spell = context.get(ComputeInputs.SPELL);
			return spell == null ? null : spell.getElement();
		}
		catch (DpsComputeException e)
		{
			return null;
		}
	}

	private boolean usingMeleeDemonbane(ComputeContext context)
	{
		int weaponId = context.get(weaponComputable).getItemId();
		return MeleeDemonbaneGearBonus.DEMONBANE_L1.contains(weaponId)
			|| MeleeDemonbaneGearBonus.DEMONBANE_L2.contains(weaponId);
	}

}
