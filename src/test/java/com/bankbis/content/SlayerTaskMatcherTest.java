package com.bankbis.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SlayerTaskMatcherTest
{

	@Test
	void pluralTaskMatchesSingularMonster()
	{
		assertTrue(SlayerTaskMatcher.matches("Abyssal demons", "Abyssal demon"));
		assertTrue(SlayerTaskMatcher.matches("Gargoyles", "Gargoyle"));
	}

	@Test
	void variantSuffixIsIgnored()
	{
		assertTrue(SlayerTaskMatcher.matches("Abyssal demons", "Abyssal demon (Abyssal Sire)"));
	}

	@Test
	void bossVariantOfTaskCategoryMatches()
	{
		// task "Cave kraken" covers the Kraken boss: task contains monster
		assertTrue(SlayerTaskMatcher.matches("Cave krakens", "Kraken"));
	}

	@Test
	void caseInsensitive()
	{
		assertTrue(SlayerTaskMatcher.matches("ABYSSAL DEMONS", "abyssal demon"));
	}

	@Test
	void unrelatedMonsterDoesNotMatch()
	{
		assertFalse(SlayerTaskMatcher.matches("Abyssal demons", "Zulrah (Serpentine)"));
		assertFalse(SlayerTaskMatcher.matches("Gargoyles", "General Graardor"));
	}

	@Test
	void missingTaskNeverMatches()
	{
		assertFalse(SlayerTaskMatcher.matches(null, "Kraken"));
		assertFalse(SlayerTaskMatcher.matches("", "Kraken"));
		assertFalse(SlayerTaskMatcher.matches("Kraken", null));
	}

}
