package com.bankbis.optimizer;

import com.duckblade.osrs.dpscalc.calc.model.AttackType;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.duckblade.osrs.dpscalc.calc.model.Prayer;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CombatClass
{

	MELEE(ImmutableSet.of(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH)),
	RANGED(ImmutableSet.of(AttackType.RANGED)),
	MAGIC(ImmutableSet.of(AttackType.MAGIC)),
	;

	private final Set<AttackType> attackTypes;

	public boolean includes(AttackType type)
	{
		return attackTypes.contains(type);
	}

	public static CombatClass of(AttackType type)
	{
		for (CombatClass c : values())
		{
			if (c.includes(type))
			{
				return c;
			}
		}
		throw new IllegalArgumentException("No combat class for attack type " + type);
	}

	/**
	 * Best offensive prayers the player can actually activate at their
	 * prayer level. (Rigour/Augury unlock states are not detectable here;
	 * they are assumed unlocked at level - documented limitation.)
	 */
	public Set<Prayer> bestPrayers(int prayerLevel)
	{
		switch (this)
		{
			case MELEE:
				if (prayerLevel >= 70)
				{
					return ImmutableSet.of(Prayer.PIETY);
				}
				if (prayerLevel >= 60)
				{
					return ImmutableSet.of(Prayer.CHIVALRY);
				}
				if (prayerLevel >= 34)
				{
					return ImmutableSet.of(Prayer.ULTIMATE_STRENGTH, Prayer.INCREDIBLE_REFLEXES);
				}
				return ImmutableSet.of();

			case RANGED:
				if (prayerLevel >= 74)
				{
					return ImmutableSet.of(Prayer.RIGOUR);
				}
				if (prayerLevel >= 44)
				{
					return ImmutableSet.of(Prayer.EAGLE_EYE);
				}
				if (prayerLevel >= 26)
				{
					return ImmutableSet.of(Prayer.HAWK_EYE);
				}
				return ImmutableSet.of();

			case MAGIC:
			default:
				if (prayerLevel >= 77)
				{
					return ImmutableSet.of(Prayer.AUGURY);
				}
				if (prayerLevel >= 45)
				{
					return ImmutableSet.of(Prayer.MYSTIC_MIGHT);
				}
				if (prayerLevel >= 27)
				{
					return ImmutableSet.of(Prayer.MYSTIC_LORE);
				}
				return ImmutableSet.of();
		}
	}

	/**
	 * The stats the engine reads for this class, as a vector for dominance
	 * comparison. Order: accuracies..., strength, prayer.
	 */
	public int[] statsVector(ItemStats i)
	{
		switch (this)
		{
			case MELEE:
				return new int[]{i.getAccuracyStab(), i.getAccuracySlash(), i.getAccuracyCrush(), i.getStrengthMelee(), i.getPrayer()};
			case RANGED:
				return new int[]{i.getAccuracyRanged(), i.getStrengthRanged(), i.getPrayer()};
			case MAGIC:
			default:
				return new int[]{i.getAccuracyMagic(), i.getStrengthMagic(), i.getPrayer()};
		}
	}

	public int strengthOf(ItemStats i)
	{
		switch (this)
		{
			case MELEE:
				return i.getStrengthMelee();
			case RANGED:
				return i.getStrengthRanged();
			case MAGIC:
			default:
				return i.getStrengthMagic();
		}
	}

	public int accuracySumOf(ItemStats i)
	{
		switch (this)
		{
			case MELEE:
				return i.getAccuracyStab() + i.getAccuracySlash() + i.getAccuracyCrush();
			case RANGED:
				return i.getAccuracyRanged();
			case MAGIC:
			default:
				return i.getAccuracyMagic();
		}
	}

}
