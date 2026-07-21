package com.bankbis.content;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PresetCategory
{

	RAIDS("Raids"),
	GOD_WARS("God Wars"),
	WILDERNESS("Wilderness"),
	SLAYER("Slayer"),
	DESERT_TREASURE_2("Desert Treasure II"),
	BOSSES("Other bosses"),
	;

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}

}
