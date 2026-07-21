package com.bankbis.content;

import lombok.Builder;
import lombok.Value;

/**
 * What to optimize against: a monster plus fight context. Built either
 * from a curated {@link ContentPreset} or from the monster search/picker.
 */
@Value
@Builder(toBuilder = true)
public class Target
{

	int npcId;
	String label;

	@Builder.Default
	boolean onSlayerTask = false;

	@Builder.Default
	int raidPartySize = 1;

	@Builder.Default
	RaidType raid = RaidType.NONE;

	@Builder.Default
	boolean coxChallengeMode = false;

	/**
	 * Stored for future time-to-kill work; invocation level scales monster
	 * HP only, so it does not affect gear ranking.
	 */
	@Builder.Default
	int toaInvocationLevel = 150;

	public static Target ofPreset(ContentPreset preset)
	{
		return Target.builder()
			.npcId(preset.getPrimaryMonsterId())
			.label(preset.getLabel())
			.onSlayerTask(preset.isOnSlayerTask())
			.raidPartySize(preset.getRaidPartySize())
			.raid(preset.getCategory().getRaidType())
			.build();
	}

}
