package com.bankbis.content;

import java.util.Locale;

/**
 * Heuristic match between the Slayer plugin's tracked task category
 * ("Abyssal demons") and a wiki monster display name ("Abyssal demon
 * (variant)"): variant suffix stripped, task singularized, then either
 * containing the other counts as on-task.
 */
public final class SlayerTaskMatcher
{

	private SlayerTaskMatcher()
	{
	}

	public static boolean matches(String taskName, String monsterDisplayName)
	{
		if (taskName == null || taskName.isEmpty() || monsterDisplayName == null || monsterDisplayName.isEmpty())
		{
			return false;
		}

		String monster = monsterDisplayName.toLowerCase(Locale.ROOT);
		int variantIdx = monster.indexOf(" (");
		if (variantIdx > 0)
		{
			monster = monster.substring(0, variantIdx);
		}

		String task = taskName.toLowerCase(Locale.ROOT);
		if (task.endsWith("s"))
		{
			task = task.substring(0, task.length() - 1);
		}

		return monster.contains(task) || task.contains(monster);
	}

}
