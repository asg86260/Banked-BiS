package com.bankbis.optimizer;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PotionBoostTest
{

	private static Map<Skill, Integer> levels(int level)
	{
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill s : Skill.values())
		{
			levels.put(s, level);
		}
		return levels;
	}

	@Test
	void currentReturnsNullToKeepLiveBoosts()
	{
		assertNull(PotionBoost.CURRENT.boostsFor(levels(99)));
	}

	@Test
	void noneReturnsNoBoosts()
	{
		assertTrue(PotionBoost.NONE.boostsFor(levels(99)).isEmpty());
	}

	@Test
	void superSetAt99()
	{
		Map<Skill, Integer> boosts = PotionBoost.SUPER_SET.boostsFor(levels(99));
		// super combat at 99: 5 + floor(0.15 * 99) = 19
		assertEquals(19, (int) boosts.get(Skill.ATTACK));
		assertEquals(19, (int) boosts.get(Skill.STRENGTH));
		// ranging pot / saturated heart at 99: 4 + floor(0.10 * 99) = 13
		assertEquals(13, (int) boosts.get(Skill.RANGED));
		assertEquals(13, (int) boosts.get(Skill.MAGIC));
	}

	@Test
	void overloadAt99()
	{
		Map<Skill, Integer> boosts = PotionBoost.OVERLOAD.boostsFor(levels(99));
		// overload at 99: 6 + floor(0.16 * 99) = 21
		assertEquals(21, (int) boosts.get(Skill.ATTACK));
		assertEquals(21, (int) boosts.get(Skill.RANGED));
		assertEquals(21, (int) boosts.get(Skill.MAGIC));
	}

	@Test
	void smellingSaltsAt99()
	{
		Map<Skill, Integer> boosts = PotionBoost.SMELLING_SALTS.boostsFor(levels(99));
		// salts at 99: 11 + floor(0.16 * 99) = 26
		assertEquals(26, (int) boosts.get(Skill.ATTACK));
		assertEquals(26, (int) boosts.get(Skill.MAGIC));
	}

}
