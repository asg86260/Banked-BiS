package com.bankbis.optimizer;

import com.duckblade.osrs.dpscalc.calc.model.Prayer;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Which offensive prayers recommendations may assume. AUTO uses the best
 * prayer the player's level allows among prayers they have actually
 * unlocked (detected from varbits). NO_UNLOCKS forces the freely-available
 * tier regardless; NONE disables prayers entirely.
 */
@Getter
@RequiredArgsConstructor
public enum PrayerAssumption
{

	AUTO("Best unlocked"),
	NO_UNLOCKS("Basic prayers only"),
	NONE("No prayers"),
	;

	// caps just below the unlockable tiers: Chivalry/Piety (60/70),
	// Rigour (74), Augury (77)
	private static final int MELEE_BASIC_CAP = 59;
	private static final int RANGED_BASIC_CAP = 73;
	private static final int MAGIC_BASIC_CAP = 76;

	private final String label;

	public Set<Prayer> prayersFor(CombatClass combatClass, int prayerLevel, PrayerUnlocks unlocks)
	{
		switch (this)
		{
			case NONE:
				return ImmutableSet.of();

			case NO_UNLOCKS:
				return combatClass.bestPrayers(Math.min(prayerLevel, basicCap(combatClass)));

			case AUTO:
			default:
				boolean unlocked = combatClass == CombatClass.MELEE ? unlocks.isPietyChivalry()
					: combatClass == CombatClass.RANGED ? unlocks.isRigour() : unlocks.isAugury();
				int cap = unlocked ? Integer.MAX_VALUE : basicCap(combatClass);
				return combatClass.bestPrayers(Math.min(prayerLevel, cap));
		}
	}

	private static int basicCap(CombatClass combatClass)
	{
		return combatClass == CombatClass.MELEE ? MELEE_BASIC_CAP
			: combatClass == CombatClass.RANGED ? RANGED_BASIC_CAP : MAGIC_BASIC_CAP;
	}

	@Override
	public String toString()
	{
		return label;
	}

}
