package com.bankbis.optimizer;

import com.duckblade.osrs.dpscalc.calc.model.Prayer;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Which offensive prayers recommendations may assume. AUTO matches the old
 * behavior: best prayer the player's level allows, assuming Piety/Rigour/
 * Augury are unlocked. NO_UNLOCKS covers accounts without those unlocks by
 * capping at the best freely-available prayer tier.
 */
@Getter
@RequiredArgsConstructor
public enum PrayerAssumption
{

	AUTO("Best (Piety/Rigour/Augury)"),
	NO_UNLOCKS("Basic prayers only"),
	NONE("No prayers"),
	;

	private final String label;

	public Set<Prayer> prayersFor(CombatClass combatClass, int prayerLevel)
	{
		switch (this)
		{
			case NONE:
				return ImmutableSet.of();

			case NO_UNLOCKS:
				// cap below the unlockable tiers: Chivalry/Piety (60/70),
				// Rigour (74), Augury (77)
				int cap = combatClass == CombatClass.MELEE ? 59
					: combatClass == CombatClass.RANGED ? 73 : 76;
				return combatClass.bestPrayers(Math.min(prayerLevel, cap));

			case AUTO:
			default:
				return combatClass.bestPrayers(prayerLevel);
		}
	}

	@Override
	public String toString()
	{
		return label;
	}

}
