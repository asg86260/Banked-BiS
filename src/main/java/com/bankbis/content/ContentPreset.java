package com.bankbis.content;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Maps an activity the player wants to do onto concrete calc targets.
 * Monster ids reference the wiki data (monsters.json). Multi-phase content
 * lists each relevant form; the first id is the primary/most representative.
 */
@Getter
@RequiredArgsConstructor
public enum ContentPreset
{

	ZULRAH("Zulrah", ImmutableList.of(2042, 2043, 2044), false, 1),
	VORKATH("Vorkath", ImmutableList.of(8059), false, 1),
	GENERAL_GRAARDOR("General Graardor", ImmutableList.of(2215), false, 1),
	ABYSSAL_DEMONS_SLAYER("Abyssal demons (slayer task)", ImmutableList.of(415), true, 1),
	;

	private final String label;
	private final List<Integer> monsterIds;
	private final boolean onSlayerTask;
	private final int raidPartySize;

	public int getPrimaryMonsterId()
	{
		return monsterIds.get(0);
	}

}
