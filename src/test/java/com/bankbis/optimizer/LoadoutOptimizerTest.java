package com.bankbis.optimizer;

import com.bankbis.data.NpcStats;
import com.bankbis.testutil.TestFixtures;
import com.duckblade.osrs.dpscalc.calc.DpsComputeModule;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.google.inject.Guice;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.runelite.api.EquipmentInventorySlot;
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
	static void setUp()
	{
		owned = TestFixtures.loadItemStats(LoadoutOptimizerTest.class, "equipment-owned.json");
		monsters = TestFixtures.npcStatsByDisplayName(LoadoutOptimizerTest.class, "monsters-preset.json");
		optimizer = Guice.createInjector(new DpsComputeModule()).getInstance(LoadoutOptimizer.class);
	}

	private static OptimizeRequest request(String monster)
	{
		return OptimizeRequest.builder()
			.ownedEquipment(owned)
			.playerSkills(TestFixtures.maxedWithBoosts())
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
	void slayerHelmetChosenOnTaskDespiteZeroStats()
	{
		OptimizeRequest onTask = OptimizeRequest.builder()
			.ownedEquipment(owned)
			.playerSkills(TestFixtures.maxedWithBoosts())
			.target(monsters.get("Abyssal demon (Standard)"))
			.onSlayerTask(true)
			.build();
		Loadout loadout = optimizer.optimizeClass(onTask, CombatClass.MELEE).orElseThrow(IllegalStateException::new);
		assertEquals("Slayer helmet (i)", loadout.getItems().get(EquipmentInventorySlot.HEAD).getName());
	}

	@Test
	void salveBeatsTortureAgainstUndeadVorkath()
	{
		Loadout loadout = optimizer.optimizeClass(request("Vorkath (Post-quest)"), CombatClass.MELEE)
			.orElseThrow(IllegalStateException::new);
		assertTrue(loadout.getItems().get(EquipmentInventorySlot.AMULET).getName().startsWith("Salve amulet"),
			"expected salve vs undead, got " + loadout.getItems().get(EquipmentInventorySlot.AMULET).getName());
	}

	@Test
	void pruneKeepsZeroStatSpecialItems()
	{
		ItemStats slayerHelm = owned.stream()
			.filter(i -> "Slayer helmet (i)".equals(i.getName())).findFirst().orElseThrow(IllegalStateException::new);
		ItemStats torvaHelm = owned.stream()
			.filter(i -> "Torva full helm".equals(i.getName())).findFirst().orElseThrow(IllegalStateException::new);

		List<ItemStats> kept = optimizer.prune(List.of(slayerHelm, torvaHelm), CombatClass.MELEE);
		assertTrue(kept.contains(slayerHelm), "slayer helmet must survive dominance pruning");
		assertTrue(kept.contains(torvaHelm));
	}

	@Test
	void dragonBoltsNotPairedWithRuneCrossbow()
	{
		Optional<Loadout> result = optimizer.optimizeClass(request("General Graardor"), CombatClass.RANGED);
		Loadout loadout = result.orElseThrow(IllegalStateException::new);
		String weapon = loadout.getItems().get(EquipmentInventorySlot.WEAPON).getName();
		ItemStats ammo = loadout.getItems().get(EquipmentInventorySlot.AMMO);
		if ("Rune crossbow".equals(weapon) && ammo != null)
		{
			assertFalse(ammo.getName().contains("dragon bolts"), "rune crossbow cannot fire dragon bolts");
		}
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
