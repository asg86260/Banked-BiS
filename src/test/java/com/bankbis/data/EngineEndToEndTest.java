package com.bankbis.data;

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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
	static void setUp() throws Exception
	{
		Gson gson = new Gson();
		Type eqType = new TypeToken<List<EquipmentJson>>()
		{
		}.getType();
		Type monType = new TypeToken<List<MonsterJson>>()
		{
		}.getType();

		try (Reader r = open("equipment-sample.json"))
		{
			List<EquipmentJson> parsed = gson.fromJson(r, eqType);
			equipment = parsed.stream().collect(Collectors.toMap(EquipmentJson::getId, Function.identity()));
		}
		try (Reader r = open("monsters-sample.json"))
		{
			List<MonsterJson> parsed = gson.fromJson(r, monType);
			monsters = parsed.stream().collect(Collectors.toMap(WikiDataMapper::displayName, Function.identity()));
		}

		Injector injector = Guice.createInjector(new DpsComputeModule());
		dpsComputable = injector.getInstance(DpsComputable.class);
	}

	private static Reader open(String name)
	{
		return new InputStreamReader(
			EngineEndToEndTest.class.getResourceAsStream(name), StandardCharsets.UTF_8);
	}

	private ComputeContext scytheVsGraardor(boolean piety)
	{
		ItemStats scythe = WikiDataMapper.toItemStats(equipment.get(22325));
		NpcStats graardor = NpcStats.of(monsters.get("General Graardor"));

		AttackStyle aggressiveSlash = scythe.getWeaponCategory().getAttackStyles().stream()
			.filter(as -> as.getAttackType() == AttackType.SLASH)
			.findFirst()
			.orElseThrow(IllegalStateException::new);

		Skills maxed = Skills.builder()
			.level(Skill.ATTACK, 99)
			.level(Skill.STRENGTH, 99)
			.level(Skill.DEFENCE, 99)
			.level(Skill.RANGED, 99)
			.level(Skill.MAGIC, 99)
			.level(Skill.HITPOINTS, 99)
			// super combat potion
			.boost(Skill.ATTACK, 19)
			.boost(Skill.STRENGTH, 19)
			.boost(Skill.DEFENCE, 19)
			.build();

		ComputeContext context = new ComputeContext();
		context.put(ComputeInputs.ATTACKER_SKILLS, maxed);
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
