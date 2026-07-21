package com.duckblade.osrs.dpscalc.calc.defender;

import com.duckblade.osrs.dpscalc.calc.WeaponComputable;
import com.duckblade.osrs.dpscalc.calc.compute.Computable;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeContext;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeInputs;
import com.duckblade.osrs.dpscalc.calc.model.DefensiveBonuses;
import com.duckblade.osrs.dpscalc.calc.model.Skills;
import com.duckblade.osrs.dpscalc.calc.model.AttackStyle;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class DefenseRollComputable implements Computable<Integer>
{

	// NPCs whose magic defence rolls off their Defence level instead of Magic
	// (mirrors USES_DEFENCE_LEVEL_FOR_MAGIC_DEFENCE_NPC_IDS in the wiki calc)
	private static final Set<Integer> DEFENCE_LEVEL_FOR_MAGIC = ImmutableSet.of(
		7584, 7585, // ice demon
		10830, 10831, 10832, 8369, 8370, 8371, 10847, 10848, 10849, // verzik p1
		10833, 10834, 10835, 8372, 8373, 8374, 10850, 10851, 10852, // verzik p2/p3
		8917, 8918, 8919, 8920, // fragment of seren
		11709, 11712 // baboon brawler
	);

	private final DefenderSkillsComputable defenderSkillsComputable;
	private final WeaponComputable weaponComputable;

	@Override
	public Integer compute(ComputeContext context)
	{
		Skills skills = context.get(defenderSkillsComputable);
		DefensiveBonuses defensiveBonuses = context.get(ComputeInputs.DEFENDER_BONUSES); // todo scaling bonuses

		int defenseLevel;
		int defenseBonus;

		AttackStyle attackStyle = context.get(ComputeInputs.ATTACK_STYLE);
		switch (attackStyle.getAttackType())
		{
			case MAGIC:
				int npcId = context.get(ComputeInputs.DEFENDER_ATTRIBUTES).getNpcId();
				defenseLevel = DEFENCE_LEVEL_FOR_MAGIC.contains(npcId)
					? skills.getTotals().get(Skill.DEFENCE)
					: skills.getTotals().get(Skill.MAGIC);
				defenseBonus = defensiveBonuses.getDefenseMagic();
				break;

			case RANGED:
				defenseLevel = skills.getTotals().get(Skill.DEFENCE);
				defenseBonus = rangedDefenceBonus(context, defensiveBonuses);
				break;

			case STAB:
				defenseLevel = skills.getTotals().get(Skill.DEFENCE);
				defenseBonus = defensiveBonuses.getDefenseStab();
				break;

			case SLASH:
				defenseLevel = skills.getTotals().get(Skill.DEFENCE);
				defenseBonus = defensiveBonuses.getDefenseSlash();
				break;

			default:
				defenseLevel = skills.getTotals().get(Skill.DEFENCE);
				defenseBonus = defensiveBonuses.getDefenseCrush();
				break;
		}

		return (defenseLevel + 9) * (defenseBonus + 64);
	}

	// Select the ranged defence type by weapon (wiki calc getRangedDamageType):
	// thrown = light, crossbows/chinchompas = heavy, salamanders = mixed
	// (average), everything else (bows) = standard.
	private int rangedDefenceBonus(ComputeContext context, DefensiveBonuses d)
	{
		switch (context.get(weaponComputable).getWeaponCategory())
		{
			case THROWN:
				return d.getDefenseRangedLight();
			case CROSSBOW:
			case CHINCHOMPAS:
				return d.getDefenseRangedHeavy();
			case SALAMANDER:
				return (d.getDefenseRangedLight() + d.getDefenseRanged() + d.getDefenseRangedHeavy()) / 3;
			default:
				return d.getDefenseRanged();
		}
	}
}
