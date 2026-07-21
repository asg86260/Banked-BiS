package com.bankbis.content;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PresetCategory
{

	COX("Chambers of Xeric", RaidType.COX),
	TOB("Theatre of Blood", RaidType.TOB),
	TOA("Tombs of Amascut", RaidType.TOA),
	GOD_WARS("God Wars", RaidType.NONE),
	WILDERNESS("Wilderness", RaidType.NONE),
	SLAYER("Slayer", RaidType.NONE),
	DESERT_TREASURE_2("Desert Treasure II", RaidType.NONE),
	BOSSES("Other bosses", RaidType.NONE),
	;

	private final String label;
	private final RaidType raidType;

	@Override
	public String toString()
	{
		return label;
	}

}
