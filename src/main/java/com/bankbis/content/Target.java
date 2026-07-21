package com.bankbis.content;

import lombok.Value;

/**
 * What to optimize against: a monster plus fight context. Built either
 * from a curated {@link ContentPreset} or from the monster search.
 */
@Value
public class Target
{

	int npcId;
	String label;
	boolean onSlayerTask;
	int raidPartySize;

	public static Target ofPreset(ContentPreset preset)
	{
		return new Target(preset.getPrimaryMonsterId(), preset.getLabel(), preset.isOnSlayerTask(), preset.getRaidPartySize());
	}

	public Target withPartySize(int partySize)
	{
		return new Target(npcId, label, onSlayerTask, partySize);
	}

}
