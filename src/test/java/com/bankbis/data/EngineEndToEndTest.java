package com.bankbis.data;

import com.bankbis.testutil.TestFixtures;
import com.duckblade.osrs.dpscalc.calc.DpsComputable;
import com.duckblade.osrs.dpscalc.calc.DpsComputeModule;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeContext;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeInputs;
import com.duckblade.osrs.dpscalc.calc.model.AttackStyle;
import com.duckblade.osrs.dpscalc.calc.model.AttackType;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.duckblade.osrs.dpscalc.calc.model.Prayer;
import com.duckblade.osrs.dpscalc.calc.model.Skills;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import java.util.Collections;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Full-pipeline smoke test: wiki JSON fixtures through the mapper into the
 * vendored calc engine, producing a plausible DPS number.
 */
class EngineEndToEndTest
{

	private static Map<Integer, EquipmentJson> equipment;
	private static Map<String, MonsterJson> monsters;
	private static DpsComputable dpsComputable;

	@BeforeAll
	static void setUp()
	{
		equipment = TestFixtures.equipmentById(EngineEndToEndTest.class, "equipment-sample.json");
		monsters = TestFixtures.monstersByDisplayName(EngineEndToEndTest.class, "monsters-sample.json");
		dpsComputable = Guice.createInjector(new DpsComputeModule()).getInstance(DpsComputable.class);
	}

	private ComputeContext scytheVsGraardor(boolean piety)
	{
		ItemStats scythe = WikiDataMapper.toItemStats(equipment.get(22325));
		NpcStats graardor = NpcStats.of(monsters.get("General Graardor"));

		AttackStyle aggressiveSlash = scythe.getWeaponCategory().getAttackStyles().stream()
			.filter(as -> as.getAttackType() == AttackType.SLASH)
			.findFirst()
			.orElseThrow(IllegalStateException::new);

		ComputeContext context = new ComputeContext();
		context.put(ComputeInputs.ATTACKER_SKILLS, TestFixtures.maxedWithBoosts());
		context.put(ComputeInputs.ATTACKER_ITEMS, ImmutableMap.of(EquipmentInventorySlot.WEAPON, scythe));
		context.put(ComputeInputs.ATTACKER_PRAYERS, piety ? ImmutableSet.of(Prayer.PIETY) : Collections.emptySet());
		context.put(ComputeInputs.ATTACK_STYLE, aggressiveSlash);
		context.put(ComputeInputs.DEFENDER_SKILLS, graardor.getSkills());
		context.put(ComputeInputs.DEFENDER_BONUSES, graardor.getDefensiveBonuses());
		context.put(ComputeInputs.DEFENDER_ATTRIBUTES, graardor.getAttributes());
		return context;
	}

	@Test
	void computesPlausibleScytheDpsAgainstGraardor()
	{
		double dps = scytheVsGraardor(true).get(dpsComputable);
		assertTrue(dps > 3 && dps < 15, "expected plausible scythe dps, got " + dps);
	}

	@Test
	void pietyImprovesDps()
	{
		double with = scytheVsGraardor(true).get(dpsComputable);
		double without = scytheVsGraardor(false).get(dpsComputable);
		assertTrue(with > without, "piety should increase dps: " + with + " vs " + without);
	}

}
