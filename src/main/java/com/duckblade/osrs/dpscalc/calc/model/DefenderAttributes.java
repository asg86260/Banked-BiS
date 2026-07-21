package com.duckblade.osrs.dpscalc.calc.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class DefenderAttributes
{

	public static final DefenderAttributes EMPTY = DefenderAttributes.builder().build();

	@Builder.Default
	private final int npcId = -1;

	@Builder.Default
	private final String name = null;

	@Builder.Default
	private final boolean isDemon = false; // demonbane

	@Builder.Default
	private final boolean isDragon = false; // dhl/dhcb

	@Builder.Default
	private final boolean isKalphite = false; // keris

	@Builder.Default
	private final boolean isLeafy = false; // leaf-bladed

	@Builder.Default
	private final boolean isUndead = false; // salve

	@Builder.Default
	private final boolean isFiery = false; // pearl/dragonstone bolt effects

	@Builder.Default
	private final boolean isVampyre1 = false;

	@Builder.Default
	private final boolean isVampyre2 = false;

	@Builder.Default
	private final boolean isVampyre3 = false;

	@Builder.Default
	private final int size = 1; // scythe

	@Builder.Default
	private final int accuracyMagic = 0; // tbow

	// elemental weakness (2024 mechanic): matching-element spells gain
	// severity% of the base roll as accuracy and severity% of the spell's
	// base max hit as damage
	@Builder.Default
	private final String elementalWeakness = null;

	@Builder.Default
	private final int elementalWeaknessSeverity = 0;

	public boolean isVampyre()
	{
		return isVampyre1 ||
			isVampyre2 ||
			isVampyre3;
	}

}
