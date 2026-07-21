package com.bankbis.content;

import com.bankbis.data.NpcStats;
import com.duckblade.osrs.dpscalc.calc.model.Skills;
import com.google.common.collect.ImmutableSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;

/**
 * Ports the wiki DPS calc's raid stat scaling, limited to what actually
 * changes gear ranking: Chambers of Xeric scales Defence (and Magic, i.e.
 * magic defence) with party size and challenge mode. ToB party sizing and
 * ToA invocations scale monster HP only, which never changes which gear
 * hits hardest, so they intentionally do not scale stats here.
 *
 * <p>The base stat factor (party's highest HP level / 99) is assumed 1,
 * i.e. a maxed-stats party - the standard assumption for BiS planning.
 */
public final class RaidScaling
{

	/**
	 * CoX monsters whose magic level counts as a defensive stat for
	 * scaling (Olm hands, Tekton, Vespula and portal, deathly rangers);
	 * everything else scales magic with the offensive multiplier.
	 */
	private static final Set<Integer> COX_MAGIC_IS_DEFENSIVE = ImmutableSet.of(
		7540, 7541, 7542, 7543, 7544, 7545, // Tekton variants
		7550, 7553, // Olm melee hand
		7552, 7555, // Olm mage hand
		7530, 7531, 7532, // Vespula
		7533, // Abyssal portal
		7526 // Deathly ranger
	);

	private RaidScaling()
	{
	}

	public static NpcStats scale(NpcStats stats, Target target)
	{
		if (target.getRaid() != RaidType.COX)
		{
			return stats;
		}
		int partySize = Math.max(1, target.getRaidPartySize());
		boolean cm = target.isCoxChallengeMode();
		if (partySize == 1 && !cm)
		{
			return stats;
		}

		int m1 = partySize - 1;
		int defensivePct = 100 + iSqrt(m1) + m1 * 7 / 10;
		int offensivePct = 100 + iSqrt(m1) * 7 + m1;
		int magicPct = COX_MAGIC_IS_DEFENSIVE.contains(target.getNpcId()) ? defensivePct : offensivePct;

		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		Map<Skill, Integer> source = stats.getSkills().getLevels() == null
			? new HashMap<>() : stats.getSkills().getLevels();
		levels.putAll(source);

		int def = source.getOrDefault(Skill.DEFENCE, 1) * defensivePct / 100;
		int magic = source.getOrDefault(Skill.MAGIC, 1) * magicPct / 100;
		if (cm)
		{
			// challenge mode: +50% defence (Tekton is 20/35% in the wiki
			// calc; acceptable approximation until Tekton is a preset)
			def = def * 3 / 2;
			magic = magic * 3 / 2;
		}
		levels.put(Skill.DEFENCE, def);
		levels.put(Skill.MAGIC, magic);

		return NpcStats.builder()
			.displayName(stats.getDisplayName())
			.speed(stats.getSpeed())
			.skills(Skills.builder().levels(levels).boosts(stats.getSkills().getBoosts()).build())
			.defensiveBonuses(stats.getDefensiveBonuses())
			.attributes(stats.getAttributes())
			.build();
	}

	static int iSqrt(int n)
	{
		return (int) Math.sqrt(n);
	}

}
