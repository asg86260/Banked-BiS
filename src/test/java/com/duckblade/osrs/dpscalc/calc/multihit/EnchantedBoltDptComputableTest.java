package com.duckblade.osrs.dpscalc.calc.multihit;

import com.duckblade.osrs.dpscalc.calc.AttackSpeedComputable;
import com.duckblade.osrs.dpscalc.calc.BaseHitDptComputable;
import com.duckblade.osrs.dpscalc.calc.EquipmentItemIdsComputable;
import com.duckblade.osrs.dpscalc.calc.HitChanceComputable;
import com.duckblade.osrs.dpscalc.calc.WeaponComputable;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeContext;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeInputs;
import com.duckblade.osrs.dpscalc.calc.maxhit.BaseMaxHitComputable;
import com.duckblade.osrs.dpscalc.calc.model.AttackType;
import com.duckblade.osrs.dpscalc.calc.model.DefenderAttributes;
import static com.duckblade.osrs.dpscalc.calc.testutil.AttackStyleUtil.ofAttackType;
import static com.duckblade.osrs.dpscalc.calc.testutil.ItemStatsUtil.ofWeaponCategory;
import static com.duckblade.osrs.dpscalc.calc.testutil.SkillsUtil.ofSkill;
import com.duckblade.osrs.dpscalc.calc.model.WeaponCategory;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnchantedBoltDptComputableTest
{

	@Mock
	private BaseHitDptComputable baseHitDptComputable;
	@Mock
	private HitChanceComputable hitChanceComputable;
	@Mock
	private BaseMaxHitComputable baseMaxHitComputable;
	@Mock
	private AttackSpeedComputable attackSpeedComputable;
	@Mock
	private WeaponComputable weaponComputable;
	@Mock
	private EquipmentItemIdsComputable equipmentItemIdsComputable;
	@Mock
	private ComputeContext context;

	@InjectMocks
	private EnchantedBoltDptComputable computable;

	private void stubCompute(int ammoId, int weaponId)
	{
		Map<EquipmentInventorySlot, Integer> equipment = new EnumMap<>(EquipmentInventorySlot.class);
		equipment.put(EquipmentInventorySlot.WEAPON, weaponId);
		equipment.put(EquipmentInventorySlot.AMMO, ammoId);

		when(context.get(baseHitDptComputable)).thenReturn(1.0);
		when(context.get(hitChanceComputable)).thenReturn(0.6);
		when(context.get(baseMaxHitComputable)).thenReturn(30);
		when(context.get(attackSpeedComputable)).thenReturn(5);
		when(context.get(equipmentItemIdsComputable)).thenReturn(equipment);
		when(context.get(ComputeInputs.DEFENDER_ATTRIBUTES)).thenReturn(DefenderAttributes.EMPTY);
		when(context.get(ComputeInputs.ATTACKER_SKILLS)).thenReturn(ofSkill(Skill.RANGED, 99));
		when(context.get(ComputeInputs.DEFENDER_SKILLS)).thenReturn(ofSkill(Skill.HITPOINTS, 150));
	}

	@Test
	void isApplicableForEnchantedBoltsOnCrossbow()
	{
		Map<EquipmentInventorySlot, Integer> equipment = new EnumMap<>(EquipmentInventorySlot.class);
		equipment.put(EquipmentInventorySlot.AMMO, ItemID.RUBY_DRAGON_BOLTS_E);
		when(context.get(ComputeInputs.ATTACK_STYLE)).thenReturn(ofAttackType(AttackType.RANGED));
		when(context.get(weaponComputable)).thenReturn(ofWeaponCategory(WeaponCategory.CROSSBOW));
		when(context.get(equipmentItemIdsComputable)).thenReturn(equipment);

		assertTrue(computable.isApplicable(context));
	}

	@Test
	void notApplicableForPlainBolts()
	{
		Map<EquipmentInventorySlot, Integer> equipment = new EnumMap<>(EquipmentInventorySlot.class);
		equipment.put(EquipmentInventorySlot.AMMO, ItemID.RUNITE_BOLTS);
		when(context.get(ComputeInputs.ATTACK_STYLE)).thenReturn(ofAttackType(AttackType.RANGED));
		when(context.get(weaponComputable)).thenReturn(ofWeaponCategory(WeaponCategory.CROSSBOW));
		when(context.get(equipmentItemIdsComputable)).thenReturn(equipment);

		assertFalse(computable.isApplicable(context));
	}

	@Test
	void rubyDealsCappedPercentCurrentHp()
	{
		stubCompute(ItemID.RUBY_DRAGON_BOLTS_E, ItemID.ARMADYL_CROSSBOW);
		// 6% chance to deal trunc(150 * 20/100) = 30, replacing the hit
		// 0.94 * 1.0 + 0.06 * 30/5
		assertEquals(0.94 + 0.06 * 30 / 5, computable.compute(context), 1e-9);
	}

	@Test
	void diamondDealsUniformBonusIgnoringDefence()
	{
		stubCompute(ItemID.DIAMOND_DRAGON_BOLTS_E, ItemID.ARMADYL_CROSSBOW);
		// 10% chance to deal 0..trunc(30 * 115/100)=34, mean 17
		// 0.90 * 1.0 + 0.10 * 17/5
		assertEquals(0.90 + 0.10 * (34 / 2.0) / 5, computable.compute(context), 1e-9);
	}

	@Test
	void opalAddsFlatBonusFromRangedLevel()
	{
		stubCompute(ItemID.OPAL_DRAGON_BOLTS_E, ItemID.ARMADYL_CROSSBOW);
		// 5% chance to add trunc(99/10)=9 damage
		// 1.0 + 0.05 * 9/5
		assertEquals(1.0 + 0.05 * 9 / 5, computable.compute(context), 1e-9);
	}

	@Test
	void zcbBoostsProcMagnitude()
	{
		stubCompute(ItemID.RUBY_DRAGON_BOLTS_E, ItemID.ZARYTE_CROSSBOW);
		// zcb: 22% of current hp = trunc(150*22/100)=33
		assertEquals(0.94 + 0.06 * 33 / 5, computable.compute(context), 1e-9);
	}

}
