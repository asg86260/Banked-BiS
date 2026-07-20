package com.bankbis.data;

import com.bankbis.testutil.TestFixtures;
import com.duckblade.osrs.dpscalc.calc.model.DefenderAttributes;
import com.duckblade.osrs.dpscalc.calc.model.DefensiveBonuses;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.duckblade.osrs.dpscalc.calc.model.WeaponCategory;
import java.util.Map;
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
	static void loadFixtures()
	{
		equipment = TestFixtures.equipmentById(WikiDataMapperTest.class, "equipment-sample.json");
		monsters = TestFixtures.monstersByDisplayName(WikiDataMapperTest.class, "monsters-sample.json");
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
	void convertsMagicStrengthFromTenthsToWholePercent()
	{
		// fang has 0 magic str; use any fixture item with known magic damage if added later.
		// The unit contract: wiki "magic_str": 50 means 5%.
		EquipmentJson synthetic = equipment.get(26219);
		synthetic.getBonuses().setMagicStr(50);
		assertEquals(5, WikiDataMapper.toItemStats(synthetic).getStrengthMagic());
		synthetic.getBonuses().setMagicStr(0);
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
