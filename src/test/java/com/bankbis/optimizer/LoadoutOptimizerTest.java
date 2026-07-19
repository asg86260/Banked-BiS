package com.bankbis.optimizer;

import com.bankbis.data.EquipmentJson;
import com.bankbis.data.MonsterJson;
import com.bankbis.data.NpcStats;
import com.bankbis.data.WikiDataMapper;
import com.duckblade.osrs.dpscalc.calc.DpsComputeModule;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.duckblade.osrs.dpscalc.calc.model.Skills;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Guice;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LoadoutOptimizerTest
{

	private static List<ItemStats> owned;
	private static Map<String, NpcStats> monsters;
	private static LoadoutOptimizer optimizer;

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

		try (Reader r = open("equipment-owned.json"))
		{
			List<EquipmentJson> parsed = gson.fromJson(r, eqType);
			owned = parsed.stream().map(WikiDataMapper::toItemStats).collect(Collectors.toList());
		}
		try (Reader r = open("monsters-preset.json"))
		{
			List<MonsterJson> parsed = gson.fromJson(r, monType);
			monsters = parsed.stream().collect(Collectors.toMap(WikiDataMapper::displayName, NpcStats::of));
		}

		optimizer = Guice.createInjector(new DpsComputeModule()).getInstance(LoadoutOptimizer.class);
	}

	private static Reader open(String name)
	{
		return new InputStreamReader(
			LoadoutOptimizerTest.class.getResourceAsStream(name), StandardCharsets.UTF_8);
	}

	private static Skills maxedWithBoosts()
	{
		return Skills.builder()
			.level(Skill.ATTACK, 99).boost(Skill.ATTACK, 19)
			.level(Skill.STRENGTH, 99).boost(Skill.STRENGTH, 19)
			.level(Skill.DEFENCE, 99).boost(Skill.DEFENCE, 19)
			.level(Skill.RANGED, 99).boost(Skill.RANGED, 13)
			.level(Skill.MAGIC, 99).boost(Skill.MAGIC, 13)
			.level(Skill.HITPOINTS, 99)
			.level(Skill.PRAYER, 99)
			.build();
	}

	private static OptimizeRequest request(String monster)
	{
		return OptimizeRequest.builder()
			.ownedEquipment(owned)
			.playerSkills(maxedWithBoosts())
			.target(monsters.get(monster))
			.build();
	}

	@Test
	void meleeVsGraardorPicksTopTierGear()
	{
		Optional<Loadout> result = optimizer.optimizeClass(request("General Graardor"), CombatClass.MELEE);
		assertTrue(result.isPresent());
		Loadout loadout = result.get();

		String weapon = loadout.getItems().get(EquipmentInventorySlot.WEAPON).getName();
		assertTrue(weapon.equals("Scythe of vitur") || weapon.equals("Osmumten's fang"),
			"expected scythe or fang, got " + weapon);
		assertTrue(loadout.getDps() > 4, "unexpectedly low dps: " + loadout.getDps());

		// big general is 5x5: scythe hits three times; it should beat fang here
		assertEquals("Scythe of vitur", weapon);
	}

	@Test
	void rangedVsZulrahPicksTbowOrBlowpipe()
	{
		Optional<Loadout> result = optimizer.optimizeClass(request("Zulrah (Serpentine)"), CombatClass.RANGED);
		assertTrue(result.isPresent());
		Loadout loadout = result.get();

		String weapon = loadout.getItems().get(EquipmentInventorySlot.WEAPON).getName();
		// zulrah has high magic level (tbow scaling) but blowpipe with dragon darts is the classic answer;
		// either is acceptable, magic shortbow is not
		assertFalse(weapon.equals("Magic shortbow"), "msb should never beat tbow/blowpipe");
		assertTrue(loadout.getDps() > 3, "unexpectedly low dps: " + loadout.getDps());
	}

	@Test
	void bowTakesArrowsNotBolts()
	{
		Optional<Loadout> result = optimizer.optimizeClass(request("Zulrah (Serpentine)"), CombatClass.RANGED);
		Loadout loadout = result.orElseThrow(IllegalStateException::new);
		ItemStats ammo = loadout.getItems().get(EquipmentInventorySlot.AMMO);
		if (loadout.getItems().get(EquipmentInventorySlot.WEAPON).getName().equals("Twisted bow"))
		{
			assertEquals("Dragon arrow", ammo.getName());
		}
	}

	@Test
	void magicVsVorkathPicksShadow()
	{
		Optional<Loadout> result = optimizer.optimizeClass(request("Vorkath (Post-quest)"), CombatClass.MAGIC);
		assertTrue(result.isPresent());
		Loadout loadout = result.get();

		assertEquals("Tumeken's shadow", loadout.getItems().get(EquipmentInventorySlot.WEAPON).getName());
		assertTrue(loadout.getDps() > 3, "unexpectedly low dps: " + loadout.getDps());
	}

	@Test
	void twoHandedWeaponLeavesShieldEmpty()
	{
		Optional<Loadout> result = optimizer.optimizeClass(request("General Graardor"), CombatClass.MELEE);
		Loadout loadout = result.orElseThrow(IllegalStateException::new);
		if (loadout.getItems().get(EquipmentInventorySlot.WEAPON).is2h())
		{
			assertFalse(loadout.getItems().containsKey(EquipmentInventorySlot.SHIELD));
		}
	}

	@Test
	void slayerHelmNotInFixtureButTaskFlagDoesNotBreak()
	{
		OptimizeRequest onTask = OptimizeRequest.builder()
			.ownedEquipment(owned)
			.playerSkills(maxedWithBoosts())
			.target(monsters.get("Abyssal demon (Standard)"))
			.onSlayerTask(true)
			.build();
		Optional<Loadout> result = optimizer.optimizeClass(onTask, CombatClass.MELEE);
		assertTrue(result.isPresent());
		assertTrue(result.get().getDps() > 0);
	}

	@Test
	void fullLoadoutBeatsWeaponOnly()
	{
		Optional<Loadout> result = optimizer.optimizeClass(request("General Graardor"), CombatClass.MELEE);
		Loadout loadout = result.orElseThrow(IllegalStateException::new);
		// beam search should have filled most armor slots with something
		assertTrue(loadout.getItems().size() >= 8, "expected a mostly full loadout, got " + loadout.getItems().keySet());
	}

	@Test
	void optimizeReturnsAllThreeClasses()
	{
		List<Loadout> all = optimizer.optimize(request("General Graardor"));
		assertEquals(3, all.size());
		assertTrue(all.get(0).getDps() >= all.get(1).getDps());
		assertTrue(all.get(1).getDps() >= all.get(2).getDps());
	}

}
