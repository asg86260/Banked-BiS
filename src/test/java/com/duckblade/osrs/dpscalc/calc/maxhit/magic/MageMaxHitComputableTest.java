package com.duckblade.osrs.dpscalc.calc.maxhit.magic;

import com.duckblade.osrs.dpscalc.calc.compute.ComputeContext;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeInputs;
import com.duckblade.osrs.dpscalc.calc.gearbonus.AggregateGearBonusesComputable;
import com.duckblade.osrs.dpscalc.calc.maxhit.StrengthBonusComputable;
import com.duckblade.osrs.dpscalc.calc.model.AttackStyle;
import com.duckblade.osrs.dpscalc.calc.model.GearBonuses;
import com.google.common.collect.ImmutableSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MageMaxHitComputableTest
{

	@Mock
	private MagicMaxHitComputable magicMaxHitComputable1, magicMaxHitComputable2;

	@Mock
	private StrengthBonusComputable strengthBonusComputable;

	@Mock
	private AggregateGearBonusesComputable aggregateGearBonusesComputable;

	@Mock
	private ComputeContext context;

	private MageMaxHitComputable mageMaxHitComputable;

	@BeforeEach
	void setUp()
	{
		mageMaxHitComputable = new MageMaxHitComputable(
			ImmutableSet.of(magicMaxHitComputable1, magicMaxHitComputable2),
			strengthBonusComputable,
			aggregateGearBonusesComputable
		);
	}

	@Test
	void throwsWhenNoProvidersApplicable()
	{
		when(magicMaxHitComputable1.isApplicable(context)).thenReturn(false);
		when(magicMaxHitComputable2.isApplicable(context)).thenReturn(false);

		assertThrows(IllegalArgumentException.class, () -> mageMaxHitComputable.compute(context));
	}

	@Test
	void selectsApplicableMaxHit()
	{
		when(magicMaxHitComputable1.isApplicable(context)).thenReturn(true, false);
		when(magicMaxHitComputable2.isApplicable(context)).thenReturn(true);
		when(context.get(magicMaxHitComputable1)).thenReturn(5);
		when(context.get(magicMaxHitComputable2)).thenReturn(10);
		when(context.get(strengthBonusComputable)).thenReturn(0);
		when(context.get(ComputeInputs.ATTACKER_PRAYERS)).thenReturn(java.util.Collections.emptySet());
		when(context.get(aggregateGearBonusesComputable)).thenReturn(GearBonuses.EMPTY);
		when(context.get(ComputeInputs.ATTACK_STYLE)).thenReturn(AttackStyle.MANUAL_CAST);
		when(context.get(ComputeInputs.SPELL)).thenReturn(null);

		assertEquals(5, mageMaxHitComputable.compute(context));
		assertEquals(10, mageMaxHitComputable.compute(context));
	}

	@Test
	void correctlyAppliesBonuses()
	{
		when(magicMaxHitComputable1.isApplicable(context)).thenReturn(true);
		when(context.get(magicMaxHitComputable1)).thenReturn(20);
		when(context.get(strengthBonusComputable)).thenReturn(10);
		when(context.get(ComputeInputs.ATTACKER_PRAYERS)).thenReturn(java.util.Collections.emptySet());
		when(context.get(aggregateGearBonusesComputable)).thenReturn(GearBonuses.of(1.2, 1.5));
		when(context.get(ComputeInputs.ATTACK_STYLE)).thenReturn(AttackStyle.MANUAL_CAST);
		when(context.get(ComputeInputs.SPELL)).thenReturn(null);

		// staged truncation: base + magic dmg bonus (truncated), then gear
		// multiplier (truncated) — not one combined multiply
		int afterMagicDmg = 20 + 20 * 10 / 100;
		assertEquals((int) (afterMagicDmg * 1.5), mageMaxHitComputable.compute(context));
	}

}