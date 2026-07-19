package com.bankbis.data;

import com.duckblade.osrs.dpscalc.calc.model.DefenderAttributes;
import com.duckblade.osrs.dpscalc.calc.model.DefensiveBonuses;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.duckblade.osrs.dpscalc.calc.model.WeaponCategory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.runelite.api.Skill;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WikiDataMapperTest
{

	private static Map<Integer, EquipmentJson> equipment;
	private static Map<String, MonsterJson> monsters;

	@BeforeAll
	static void loadFixtures() throws Exception
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
	}

	private static Reader open(String name)
	{
		return new InputStreamReader(
			WikiDataMapperTest.class.getResourceAsStream(name), StandardCharsets.UTF_8);
	}

	@Test
	void mapsScytheOfVitur()
	{
		ItemStats scythe = WikiDataMapper.toItemStats(equipment.get(22325));
		assertEquals("Scythe of vitur", scythe.getName());
		assertEquals(125, scythe.getAccuracySlash());
		assertEquals(75, scythe.getStrengthMelee());
		assertEquals(5, scythe.getSpeed());
		assertTrue(scythe.is2h());
		assertEquals(WeaponCategory.SCYTHE, scythe.getWeaponCategory());
	}

	@Test
	void mapsTwistedBow()
	{
		ItemStats tbow = WikiDataMapper.toItemStats(equipment.get(20997));
		assertEquals(70, tbow.getAccuracyRanged());
		assertEquals(20, tbow.getStrengthRanged());
		assertEquals(WeaponCategory.BOW, tbow.getWeaponCategory());
		assertTrue(tbow.is2h());
	}

	@Test
	void mapsNonWeaponWithoutCategory()
	{
		ItemStats helm = WikiDataMapper.toItemStats(equipment.get(26382));
		assertEquals("Torva full helm", helm.getName());
		assertEquals(WeaponCategory.UNARMED, helm.getWeaponCategory());
		assertEquals(4, helm.getSpeed());
	}

	@Test
	void mapsGraardorSkillsAndDefence()
	{
		MonsterJson graardor = monsters.get("General Graardor");
		NpcStats stats = NpcStats.of(graardor);

		assertEquals(255, (int) stats.getSkills().getLevels().get(Skill.HITPOINTS));
		DefensiveBonuses def = stats.getDefensiveBonuses();
		assertEquals(graardor.getDefensive().getStab(), def.getDefenseStab());
		assertEquals(graardor.getDefensive().getStandard(), def.getDefenseRanged());
	}

	@Test
	void mapsVorkathAttributes()
	{
		DefenderAttributes vorkath = NpcStats.of(monsters.get("Vorkath (Post-quest)")).getAttributes();
		assertTrue(vorkath.isDragon());
		assertTrue(vorkath.isUndead());
		assertFalse(vorkath.isDemon());
	}

	@Test
	void mapsKalphiteAttribute()
	{
		DefenderAttributes kq = NpcStats.of(monsters.get("Kalphite Queen (Airborne)")).getAttributes();
		assertTrue(kq.isKalphite());
		assertEquals(965, kq.getNpcId());
	}

}
