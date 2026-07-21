package com.bankbis.e2e;

import com.bankbis.data.MonsterJson;
import com.bankbis.data.NpcStats;
import com.bankbis.optimizer.CombatClass;
import com.bankbis.optimizer.Loadout;
import com.bankbis.optimizer.LoadoutOptimizer;
import com.bankbis.optimizer.OptimizeRequest;
import com.bankbis.optimizer.PrayerAssumption;
import com.bankbis.testutil.TestFixtures;
import com.duckblade.osrs.dpscalc.calc.DpsComputeModule;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.duckblade.osrs.dpscalc.calc.model.Spell;
import com.google.inject.Guice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.EquipmentInventorySlot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * End-to-end scenarios: curated banks of real wiki items through the real
 * optimizer and calc engine against real monsters, asserting the setups a
 * player would expect. Equipment fixture: equipment-e2e.json (extracted
 * from live wiki data); monsters reuse the preset validation fixture.
 */
class OptimizerEndToEndTest
{

	private static Map<String, ItemStats> itemsByName;
	private static Map<Integer, NpcStats> npcById;
	private static LoadoutOptimizer optimizer;

	private static final int GRAARDOR = 2215;
	private static final int ABYSSAL_DEMON = 415;
	private static final int KRAKEN = 494;
	private static final int ICE_DEMON = 7584;

	@BeforeAll
	static void setUp()
	{
		itemsByName = TestFixtures.loadItemStats(OptimizerEndToEndTest.class, "equipment-e2e.json").stream()
			.collect(Collectors.toMap(ItemStats::getName, i -> i, (a, b) -> a));
		npcById = new HashMap<>();
		for (MonsterJson m : TestFixtures.loadMonsterJson(OptimizerEndToEndTest.class, "/com/bankbis/content/monsters-all-presets.json"))
		{
			npcById.putIfAbsent(m.getId(), NpcStats.of(m));
		}
		optimizer = Guice.createInjector(new DpsComputeModule()).getInstance(LoadoutOptimizer.class);
	}

	private static List<ItemStats> bank(String... names)
	{
		List<ItemStats> items = new ArrayList<>();
		for (String name : names)
		{
			ItemStats item = itemsByName.get(name);
			if (item == null)
			{
				throw new IllegalArgumentException("Fixture is missing item: " + name);
			}
			items.add(item);
		}
		return items;
	}

	private static OptimizeRequest request(List<ItemStats> bank, int npcId, boolean onTask)
	{
		return OptimizeRequest.builder()
			.ownedEquipment(bank)
			.playerSkills(TestFixtures.maxedWithBoosts())
			.target(npcById.get(npcId))
			.onSlayerTask(onTask)
			.build();
	}

	private static Optional<Loadout> loadoutFor(List<Loadout> loadouts, CombatClass combatClass)
	{
		return loadouts.stream().filter(l -> l.getCombatClass() == combatClass).findFirst();
	}

	private static Set<String> itemNames(Loadout loadout)
	{
		return loadout.getItems().values().stream().map(ItemStats::getName).collect(Collectors.toSet());
	}

	@Test
	void picksBestMeleeProgressionGear()
	{
		List<Loadout> result = optimizer.optimize(request(bank(
			"Abyssal whip", "Dragon scimitar", "Rune scimitar",
			"Dragon defender", "Rune kiteshield",
			"Bandos chestplate", "Bandos tassets", "Rune platebody", "Rune platelegs",
			"Amulet of torture", "Amulet of strength",
			"Barrows gloves", "Dragon boots", "Fire cape", "Berserker ring"), GRAARDOR, false));

		Loadout melee = loadoutFor(result, CombatClass.MELEE).orElseThrow(AssertionError::new);
		Map<EquipmentInventorySlot, ItemStats> items = melee.getItems();
		assertEquals("Abyssal whip", items.get(EquipmentInventorySlot.WEAPON).getName());
		assertEquals("Dragon defender", items.get(EquipmentInventorySlot.SHIELD).getName());
		assertEquals("Bandos chestplate", items.get(EquipmentInventorySlot.BODY).getName());
		assertEquals("Bandos tassets", items.get(EquipmentInventorySlot.LEGS).getName());
		assertEquals("Amulet of torture", items.get(EquipmentInventorySlot.AMULET).getName());
	}

	@Test
	void slayerHelmOnlyHelpsOnTask()
	{
		List<ItemStats> gear = bank("Abyssal whip", "Black mask", "Rune full helm");

		Loadout onTask = loadoutFor(optimizer.optimize(request(gear, ABYSSAL_DEMON, true)), CombatClass.MELEE)
			.orElseThrow(AssertionError::new);
		Loadout offTask = loadoutFor(optimizer.optimize(request(gear, ABYSSAL_DEMON, false)), CombatClass.MELEE)
			.orElseThrow(AssertionError::new);

		assertTrue(itemNames(onTask).contains("Black mask"), "black mask should be worn on task");
		assertTrue(onTask.getDps() > offTask.getDps(),
			"on-task dps " + onTask.getDps() + " should beat off-task " + offTask.getDps());
	}

	@Test
	void meleeImmuneMonsterOmitsMeleeLoadout()
	{
		List<Loadout> result = optimizer.optimize(request(bank(
			"Abyssal whip", "Rune crossbow", "Broad bolts"), KRAKEN, true));

		assertFalse(loadoutFor(result, CombatClass.MELEE).isPresent(), "Kraken is immune to melee");
		assertTrue(loadoutFor(result, CombatClass.RANGED).isPresent());
	}

	@Test
	void fireWeakMonsterGetsFireSpell()
	{
		List<Loadout> result = optimizer.optimize(request(bank(
			"Kodai wand", "Trident of the swamp", "Staff of fire", "Occult necklace"), ICE_DEMON, false));

		Loadout magic = loadoutFor(result, CombatClass.MAGIC).orElseThrow(AssertionError::new);
		assertEquals(Spell.FIRE_SURGE, magic.getSpell(),
			"Ice demon is weak to fire; expected Fire Surge, got " + magic.getSpell());
	}

	@Test
	void ammoMustMatchTheCrossbow()
	{
		// rune crossbow cannot fire dragon bolts; broad bolts are the best legal ammo
		Loadout rcb = loadoutFor(optimizer.optimize(request(bank(
			"Rune crossbow", "Broad bolts", "Dragon bolts"), GRAARDOR, false)), CombatClass.RANGED)
			.orElseThrow(AssertionError::new);
		assertEquals("Broad bolts", rcb.getItems().get(EquipmentInventorySlot.AMMO).getName());

		// with a dragon crossbow available, dragon bolts become usable and win
		Loadout dcb = loadoutFor(optimizer.optimize(request(bank(
			"Rune crossbow", "Dragon crossbow", "Broad bolts", "Dragon bolts"), GRAARDOR, false)), CombatClass.RANGED)
			.orElseThrow(AssertionError::new);
		assertEquals("Dragon crossbow", dcb.getItems().get(EquipmentInventorySlot.WEAPON).getName());
		assertEquals("Dragon bolts", dcb.getItems().get(EquipmentInventorySlot.AMMO).getName());
	}

	@Test
	void voidSetIsAssembledWhenOwned()
	{
		List<Loadout> result = optimizer.optimize(request(bank(
			"Magic shortbow", "Amethyst arrow",
			"Void ranger helm", "Void knight top", "Void knight robe", "Void knight gloves"), GRAARDOR, false));

		Loadout ranged = loadoutFor(result, CombatClass.RANGED).orElseThrow(AssertionError::new);
		Set<String> names = itemNames(ranged);
		assertTrue(names.containsAll(Set.of(
			"Void ranger helm", "Void knight top", "Void knight robe", "Void knight gloves")),
			"expected the full void set, got " + names);
	}

	@Test
	void prayerAssumptionChangesDps()
	{
		List<ItemStats> gear = bank("Abyssal whip", "Dragon defender");

		Loadout auto = loadoutFor(optimizer.optimize(request(gear, GRAARDOR, false)), CombatClass.MELEE)
			.orElseThrow(AssertionError::new);
		Loadout none = loadoutFor(optimizer.optimize(request(gear, GRAARDOR, false).toBuilder()
			.prayerAssumption(PrayerAssumption.NONE).build()), CombatClass.MELEE)
			.orElseThrow(AssertionError::new);

		assertTrue(auto.getDps() > none.getDps(),
			"piety dps " + auto.getDps() + " should beat prayerless " + none.getDps());
	}

}
