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
import com.duckblade.osrs.dpscalc.calc.model.WeaponCategory;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;

/**
 * Enchanted crossbow bolt effects (ruby/diamond/onyx/dragonstone/opal/pearl),
 * modelled as expected-value modifications of the base ranged hit. Mirrors the
 * wiki calc's bolt hit-distribution transforms (src/lib/dists/bolts.ts): each
 * proc chance and effect matches, converted from a distribution transform to an
 * expected-value form since this engine works in expected damage per tick.
 *
 * Kandarin diary (x1.1 proc chance) and the Zaryte crossbow spec are not
 * modelled; the passive Zaryte bonus (higher chances/caps) is.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class EnchantedBoltDptComputable implements MultiHitDptComputable
{

	private static final Set<Integer> RUBY = ImmutableSet.of(
		ItemID.RUBY_BOLTS_E, ItemID.RUBY_DRAGON_BOLTS_E);
	private static final Set<Integer> DIAMOND = ImmutableSet.of(
		ItemID.DIAMOND_BOLTS_E, ItemID.DIAMOND_BOLTS_E_23649, ItemID.DIAMOND_DRAGON_BOLTS_E);
	private static final Set<Integer> ONYX = ImmutableSet.of(
		ItemID.ONYX_BOLTS_E, ItemID.ONYX_DRAGON_BOLTS_E);
	private static final Set<Integer> DRAGONSTONE = ImmutableSet.of(
		ItemID.DRAGONSTONE_BOLTS_E, ItemID.DRAGONSTONE_DRAGON_BOLTS_E);
	private static final Set<Integer> OPAL = ImmutableSet.of(
		ItemID.OPAL_BOLTS_E, ItemID.OPAL_DRAGON_BOLTS_E, ItemID.OPAL_DRAGON_BOLTS_E_27192);
	private static final Set<Integer> PEARL = ImmutableSet.of(
		ItemID.PEARL_BOLTS_E, ItemID.PEARL_DRAGON_BOLTS_E);

	private static final Set<Integer> ENCHANTED = ImmutableSet.<Integer>builder()
		.addAll(RUBY).addAll(DIAMOND).addAll(ONYX).addAll(DRAGONSTONE).addAll(OPAL).addAll(PEARL)
		.build();

	private static final Set<Integer> ZCB = ImmutableSet.of(
		ItemID.ZARYTE_CROSSBOW, ItemID.ZARYTE_CROSSBOW_27186);

	// monsters with functionally infinite HP cap ruby's %-current-hp effect lower
	private static final Set<Integer> INFINITE_HP = ImmutableSet.of(
		14779 // Gemstone crab
	);

	private final BaseHitDptComputable baseHitDptComputable;
	private final HitChanceComputable hitChanceComputable;
	private final BaseMaxHitComputable baseMaxHitComputable;
	private final AttackSpeedComputable attackSpeedComputable;
	private final WeaponComputable weaponComputable;
	private final EquipmentItemIdsComputable equipmentItemIdsComputable;

	@Override
	public boolean isApplicable(ComputeContext context)
	{
		if (context.get(ComputeInputs.ATTACK_STYLE).getAttackType() != AttackType.RANGED)
		{
			return false;
		}
		if (context.get(weaponComputable).getWeaponCategory() != WeaponCategory.CROSSBOW)
		{
			return false;
		}
		Integer ammo = context.get(equipmentItemIdsComputable).get(EquipmentInventorySlot.AMMO);
		return ammo != null && ENCHANTED.contains(ammo);
	}

	@Override
	public Double compute(ComputeContext context)
	{
		double baseDpt = context.get(baseHitDptComputable);
		double hitChance = context.get(hitChanceComputable);
		int maxHit = context.get(baseMaxHitComputable);
		int speed = context.get(attackSpeedComputable);

		Map<EquipmentInventorySlot, Integer> equipment = context.get(equipmentItemIdsComputable);
		int ammo = equipment.get(EquipmentInventorySlot.AMMO);
		boolean zcb = ZCB.contains(equipment.get(EquipmentInventorySlot.WEAPON));

		DefenderAttributes attrs = context.get(ComputeInputs.DEFENDER_ATTRIBUTES);
		int rangedLevel = context.get(ComputeInputs.ATTACKER_SKILLS).getTotals().getOrDefault(Skill.RANGED, 0);
		int currentHp = context.get(ComputeInputs.DEFENDER_SKILLS).getTotals().getOrDefault(Skill.HITPOINTS, 0);

		if (RUBY.contains(ammo))
		{
			// 6% chance to deal capped %-of-current-hp, replacing the hit
			int cap = INFINITE_HP.contains(attrs.getNpcId()) ? (zcb ? 66 : 60) : (zcb ? 110 : 100);
			int effectDmg = Math.min(cap, currentHp * (zcb ? 22 : 20) / 100);
			return replacesHit(baseDpt, 0.06, effectDmg / (double) speed);
		}
		if (DIAMOND.contains(ammo))
		{
			// 10% chance to deal 0..effectMax, ignoring defence (procs on any attack)
			int effectMax = maxHit * (zcb ? 126 : 115) / 100;
			return replacesHit(baseDpt, 0.10, (effectMax / 2.0) / speed);
		}
		if (ONYX.contains(ammo))
		{
			if (attrs.isUndead())
			{
				return baseDpt;
			}
			// 11% chance to deal 0..effectMax on an accurate hit
			int effectMax = maxHit * (zcb ? 132 : 120) / 100;
			return replacesHit(baseDpt, 0.11, hitChance * (effectMax / 2.0) / speed);
		}
		if (DRAGONSTONE.contains(ammo))
		{
			if (attrs.isDragon() || attrs.isFiery())
			{
				return baseDpt;
			}
			// 6% chance to add bonus damage on an accurate hit
			int bonusDmg = rangedLevel * 2 / (zcb ? 9 : 10);
			return baseDpt + hitChance * 0.06 * bonusDmg / speed;
		}
		if (OPAL.contains(ammo))
		{
			// 5% chance to add bonus damage (procs on any attack)
			int bonusDmg = rangedLevel / (zcb ? 9 : 10);
			return baseDpt + 0.05 * bonusDmg / speed;
		}
		if (PEARL.contains(ammo))
		{
			// 6% chance to add bonus damage (procs on any attack); stronger vs fiery
			int divisor = attrs.isFiery() ? 15 : 20;
			int bonusDmg = rangedLevel / (zcb ? divisor - 2 : divisor);
			return baseDpt + 0.06 * bonusDmg / speed;
		}
		return baseDpt;
	}

	// proc replaces the normal hit: chance * effect + (1 - chance) * base
	private static double replacesHit(double baseDpt, double chance, double effectDpt)
	{
		return (1 - chance) * baseDpt + chance * effectDpt;
	}

}
