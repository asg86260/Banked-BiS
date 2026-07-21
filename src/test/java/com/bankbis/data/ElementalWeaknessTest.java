package com.bankbis.data;

import com.duckblade.osrs.dpscalc.calc.DpsComputable;
import com.duckblade.osrs.dpscalc.calc.DpsComputeModule;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeContext;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeInputs;
import com.duckblade.osrs.dpscalc.calc.maxhit.magic.MageMaxHitComputable;
import com.duckblade.osrs.dpscalc.calc.model.AttackStyle;
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
 * Verifies the elemental weakness port: matching-element spells gain
 * severity% of the base roll as accuracy and severity% of the spell's base
 * max hit as damage, mirroring the wiki calc.
 */
class ElementalWeaknessTest
{

	private static Injector injector;

	@BeforeAll
	static void setUp()
	{
		injector = Guice.createInjector(new DpsComputeModule());
	}

	private static ComputeContext castContext(Spell spell, DefenderAttributes attributes)
	{
		ItemStats staff = ItemStats.builder()
			.itemId(1387)
			.name("Staff of fire")
			.slot(EquipmentInventorySlot.WEAPON.getSlotIdx())
			.speed(4)
			.weaponCategory(WeaponCategory.STAFF)
			.build();

		Map<Skill, Integer> npcLevels = new EnumMap<>(Skill.class);
		npcLevels.put(Skill.DEFENCE, 100);
		npcLevels.put(Skill.MAGIC, 100);
		npcLevels.put(Skill.HITPOINTS, 200);

		ComputeContext context = new ComputeContext();
		context.put(ComputeInputs.ATTACKER_SKILLS, Skills.builder().level(Skill.MAGIC, 99).build());
		context.put(ComputeInputs.ATTACKER_ITEMS, ImmutableMap.of(EquipmentInventorySlot.WEAPON, staff));
		context.put(ComputeInputs.ATTACKER_PRAYERS, Collections.emptySet());
		context.put(ComputeInputs.ATTACK_STYLE, AttackStyle.MANUAL_CAST);
		context.put(ComputeInputs.SPELL, spell);
		context.put(ComputeInputs.DEFENDER_SKILLS, Skills.builder().levels(npcLevels).build());
		context.put(ComputeInputs.DEFENDER_BONUSES, DefensiveBonuses.builder().defenseMagic(50).build());
		context.put(ComputeInputs.DEFENDER_ATTRIBUTES, attributes);
		return context;
	}

	private static DefenderAttributes fireWeak(int severity)
	{
		return DefenderAttributes.builder()
			.elementalWeakness("fire")
			.elementalWeaknessSeverity(severity)
			.build();
	}

	@Test
	void matchingElementBeatsNonMatching()
	{
		DpsComputable dps = injector.getInstance(DpsComputable.class);
		double fire = castContext(Spell.FIRE_SURGE, fireWeak(50)).get(dps);
		double wind = castContext(Spell.WIND_SURGE, fireWeak(50)).get(dps);
		// fire surge base 24 vs wind surge 21 plus the weakness bonus
		assertTrue(fire > wind, "fire " + fire + " should beat wind " + wind + " vs fire-weak monster");
	}

	@Test
	void weaknessAddsSeverityPercentOfSpellBaseToMaxHit()
	{
		MageMaxHitComputable maxHit = injector.getInstance(MageMaxHitComputable.class);
		int with = castContext(Spell.FIRE_SURGE, fireWeak(50)).get(maxHit);
		int without = castContext(Spell.FIRE_SURGE, DefenderAttributes.EMPTY).get(maxHit);
		// no magic damage gear: bonus = trunc(24 * 50/100) = 12
		assertEquals(12, with - without);
	}

	@Test
	void noBonusForWrongElementOrNoWeakness()
	{
		MageMaxHitComputable maxHit = injector.getInstance(MageMaxHitComputable.class);
		int wrongElement = castContext(Spell.WIND_SURGE, fireWeak(50)).get(maxHit);
		int noWeakness = castContext(Spell.WIND_SURGE, DefenderAttributes.EMPTY).get(maxHit);
		assertEquals(noWeakness, wrongElement);
	}

	@Test
	void weaknessImprovesDps()
	{
		DpsComputable dps = injector.getInstance(DpsComputable.class);
		double with = castContext(Spell.FIRE_SURGE, fireWeak(50)).get(dps);
		double without = castContext(Spell.FIRE_SURGE, DefenderAttributes.EMPTY).get(dps);
		assertTrue(with > without, "weakness should raise dps: " + with + " vs " + without);
	}

}
