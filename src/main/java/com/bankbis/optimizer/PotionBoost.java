package com.bankbis.optimizer;

import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;

/**
 * Boost assumption for recommendations. CURRENT uses the player's live
 * boosted stats; the rest replace live boosts with the chosen potion's
 * effect computed from base levels, so you can plan a setup while standing
 * unpotted at the bank.
 */
@Getter
@RequiredArgsConstructor
public enum PotionBoost
{

	CURRENT("Current stats"),
	NONE("No boosts"),
	SUPER_SET("Super combat + ranging + heart"),
	OVERLOAD("Overload (raids)"),
	SMELLING_SALTS("Smelling salts"),
	;

	private final String label;

	/**
	 * @return boost deltas per skill, or null for CURRENT (keep live boosts)
	 */
	public Map<Skill, Integer> boostsFor(Map<Skill, Integer> levels)
	{
		Map<Skill, Integer> boosts = new EnumMap<>(Skill.class);
		switch (this)
		{
			case CURRENT:
				return null;

			case NONE:
				return boosts;

			case SUPER_SET:
				// super combat: +5 + 15%; ranging potion / saturated heart: +4 + 10%
				put(boosts, levels, Skill.ATTACK, 5, 0.15);
				put(boosts, levels, Skill.STRENGTH, 5, 0.15);
				put(boosts, levels, Skill.DEFENCE, 5, 0.15);
				put(boosts, levels, Skill.RANGED, 4, 0.10);
				put(boosts, levels, Skill.MAGIC, 4, 0.10);
				return boosts;

			case OVERLOAD:
				put(boosts, levels, Skill.ATTACK, 6, 0.16);
				put(boosts, levels, Skill.STRENGTH, 6, 0.16);
				put(boosts, levels, Skill.DEFENCE, 6, 0.16);
				put(boosts, levels, Skill.RANGED, 6, 0.16);
				put(boosts, levels, Skill.MAGIC, 6, 0.16);
				return boosts;

			case SMELLING_SALTS:
			default:
				put(boosts, levels, Skill.ATTACK, 11, 0.16);
				put(boosts, levels, Skill.STRENGTH, 11, 0.16);
				put(boosts, levels, Skill.DEFENCE, 11, 0.16);
				put(boosts, levels, Skill.RANGED, 11, 0.16);
				put(boosts, levels, Skill.MAGIC, 11, 0.16);
				return boosts;
		}
	}

	private static void put(Map<Skill, Integer> boosts, Map<Skill, Integer> levels, Skill skill, int base, double percent)
	{
		int level = levels.getOrDefault(skill, 1);
		boosts.put(skill, base + (int) (percent * level));
	}

	@Override
	public String toString()
	{
		return label;
	}

}
