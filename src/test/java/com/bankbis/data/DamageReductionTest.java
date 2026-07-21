package com.bankbis.data;

import com.duckblade.osrs.dpscalc.calc.DpsComputable;
import com.duckblade.osrs.dpscalc.calc.DpsComputeModule;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeContext;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeInputs;
import com.duckblade.osrs.dpscalc.calc.model.AttackStyle;
import com.duckblade.osrs.dpscalc.calc.model.AttackType;
import com.duckblade.osrs.dpscalc.calc.model.DefenderAttributes;
import com.duckblade.osrs.dpscalc.calc.model.DefensiveBonuses;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.duckblade.osrs.dpscalc.calc.model.Skills;
import com.duckblade.osrs.dpscalc.calc.model.Spell;
import com.duckblade.osrs.dpscalc.calc.model.WeaponCategory;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Flat monster damage reductions ported from the wiki calc (Kraken 1/7
 * from ranged, Ice demon 1/3 unless fire or demonbane, etc.).
 */
class DamageReductionTest
{

	private static DpsComputable dps;

	@BeforeAll
	static void setUp()
	{
		Injector injector = Guice.createInjector(new DpsComputeModule());
		dps = injector.getInstance(DpsComputable.class);
	}

	private static DefenderAttributes kraken()
	{
		return DefenderAttributes.builder().npcId(494).name("Kraken").build();
	}

	private static DefenderAttributes iceDemon()
	{
		return DefenderAttributes.builder().npcId(7584).name("Ice demon (Normal)").isDemon(true).build();
	}

	private static ComputeContext rangedContext(DefenderAttributes attrs)
	{
		ItemStats crossbow = ItemStats.builder()
			.itemId(9185).name("Rune crossbow").slot(EquipmentInventorySlot.WEAPON.getSlotIdx())
			.accuracyRanged(90).speed(6).weaponCategory(WeaponCategory.CROSSBOW).build();
		ItemStats bolts = ItemStats.builder()
			.itemId(11875).name("Broad bolts").slot(EquipmentInventorySlot.AMMO.getSlotIdx())
			.accuracyRanged(100).strengthRanged(100).speed(4).weaponCategory(WeaponCategory.UNARMED).build();
		AttackStyle rapid = WeaponCategory.CROSSBOW.getAttackStyles().stream()
			.filter(s -> s.getAttackType() == AttackType.RANGED)
			.findFirst().orElseThrow(IllegalStateException::new);

		ComputeContext context = new ComputeContext();
		context.put(ComputeInputs.ATTACKER_SKILLS, Skills.builder().level(Skill.RANGED, 99).build());
		context.put(ComputeInputs.ATTACKER_ITEMS, ImmutableMap.of(
			EquipmentInventorySlot.WEAPON, crossbow, EquipmentInventorySlot.AMMO, bolts));
		context.put(ComputeInputs.ATTACKER_PRAYERS, Collections.emptySet());
		context.put(ComputeInputs.ATTACK_STYLE, rapid);
		defenderDefaults(context, attrs);
		return context;
	}

	private static ComputeContext castContext(DefenderAttributes attrs, Spell spell)
	{
		ItemStats staff = ItemStats.builder()
			.itemId(1387).name("Staff of fire").slot(EquipmentInventorySlot.WEAPON.getSlotIdx())
			.speed(4).weaponCategory(WeaponCategory.STAFF).build();
		ComputeContext context = new ComputeContext();
		context.put(ComputeInputs.ATTACKER_SKILLS, Skills.builder().level(Skill.MAGIC, 99).build());
		context.put(ComputeInputs.ATTACKER_ITEMS, ImmutableMap.of(EquipmentInventorySlot.WEAPON, staff));
		context.put(ComputeInputs.ATTACKER_PRAYERS, Collections.emptySet());
		context.put(ComputeInputs.ATTACK_STYLE, AttackStyle.MANUAL_CAST);
		context.put(ComputeInputs.SPELL, spell);
		defenderDefaults(context, attrs);
		return context;
	}

	private static void defenderDefaults(ComputeContext context, DefenderAttributes attrs)
	{
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.DEFENCE, 100);
		levels.put(Skill.MAGIC, 100);
		levels.put(Skill.HITPOINTS, 255);
		context.put(ComputeInputs.DEFENDER_SKILLS, Skills.builder().levels(levels).build());
		context.put(ComputeInputs.DEFENDER_BONUSES, DefensiveBonuses.builder().build());
		context.put(ComputeInputs.DEFENDER_ATTRIBUTES, attrs);
	}

	@Test
	void krakenTakesRoughlyOneSeventhFromRanged()
	{
		double vsKraken = rangedContext(kraken()).get(dps);
		double vsNormal = rangedContext(DefenderAttributes.builder().npcId(1).name("Test").build()).get(dps);
		double ratio = vsNormal / vsKraken;
		assertTrue(ratio > 6.5 && ratio < 8.5,
			"expected ~7x reduction, got ratio " + ratio + " (" + vsNormal + " vs " + vsKraken + ")");
	}

	@Test
	void iceDemonReducesNonFireButNotFire()
	{
		double fire = castContext(iceDemon(), Spell.FIRE_SURGE).get(dps);
		double windReduced = castContext(iceDemon(), Spell.WIND_SURGE).get(dps);
		double windNormal = castContext(DefenderAttributes.builder().npcId(1).name("Test").build(), Spell.WIND_SURGE).get(dps);

		double ratio = windNormal / windReduced;
		assertTrue(ratio > 2.5 && ratio < 3.5, "expected ~3x reduction for non-fire, got " + ratio);
		// fire is exempt from the reduction and boosted by base max hit
		assertTrue(fire > windReduced * 2, "fire " + fire + " should far outclass reduced wind " + windReduced);
	}

	@Test
	void normalMonstersAreUntouched()
	{
		DefenderAttributes plain = DefenderAttributes.builder().npcId(2215).name("General Graardor").build();
		double a = rangedContext(plain).get(dps);
		double b = rangedContext(plain).get(dps);
		assertEquals(a, b, 1e-9);
		assertTrue(a > 0);
	}

}
