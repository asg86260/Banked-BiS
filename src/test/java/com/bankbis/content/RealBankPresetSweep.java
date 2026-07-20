package com.bankbis.content;

import com.bankbis.data.EquipmentJson;
import com.bankbis.data.MonsterJson;
import com.bankbis.data.NpcStats;
import com.bankbis.data.WikiDataMapper;
import com.bankbis.optimizer.Loadout;
import com.bankbis.optimizer.LoadoutOptimizer;
import com.bankbis.optimizer.OptimizeRequest;
import com.bankbis.testutil.TestFixtures;
import com.duckblade.osrs.dpscalc.calc.DpsComputeModule;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Guice;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Local-only pre-flight sweep: runs every ContentPreset against the real
 * on-disk bank snapshot and live wiki data cache. Not committed to CI -
 * depends on local .runelite state.
 */
class RealBankPresetSweep
{

	private static final Path RUNELITE_DATA = Paths.get(System.getProperty("user.home"), ".runelite", "bank-bis");

	@Test
	void sweepAllPresetsAgainstRealBank() throws Exception
	{
		Assumptions.assumeTrue(Files.exists(RUNELITE_DATA.resolve("equipment.json")),
			"no local wiki data cache; sweep only runs on a machine that has used the plugin");

		Gson gson = new Gson();
		Type eqType = new TypeToken<List<EquipmentJson>>()
		{
		}.getType();
		Type monType = new TypeToken<List<MonsterJson>>()
		{
		}.getType();
		Type ownedType = new TypeToken<Map<String, Map<Integer, Integer>>>()
		{
		}.getType();

		Map<Integer, ItemStats> wikiItems = new HashMap<>();
		try (Reader r = Files.newBufferedReader(RUNELITE_DATA.resolve("equipment.json"), StandardCharsets.UTF_8))
		{
			List<EquipmentJson> parsed = gson.fromJson(r, eqType);
			for (EquipmentJson e : parsed)
			{
				wikiItems.put(e.getId(), WikiDataMapper.toItemStats(e));
			}
		}

		Map<Integer, MonsterJson> monsters = new HashMap<>();
		try (Reader r = Files.newBufferedReader(RUNELITE_DATA.resolve("monsters.json"), StandardCharsets.UTF_8))
		{
			List<MonsterJson> parsed = gson.fromJson(r, monType);
			for (MonsterJson m : parsed)
			{
				monsters.merge(m.getId(), m, (a, b) -> a); // sweep: first variant is fine
			}
		}

		Path owned = Files.list(RUNELITE_DATA)
			.filter(p -> p.getFileName().toString().startsWith("owned-"))
			.findFirst()
			.orElse(null);
		Assumptions.assumeTrue(owned != null, "no owned-items snapshot; open the bank in-game once first");
		Map<String, Map<Integer, Integer>> sources;
		try (Reader r = Files.newBufferedReader(owned, StandardCharsets.UTF_8))
		{
			sources = gson.fromJson(r, ownedType);
		}
		Map<Integer, Integer> quantities = new HashMap<>();
		sources.values().forEach(m -> m.forEach((id, q) -> quantities.merge(id, q, Integer::sum)));

		List<ItemStats> ownedEquipment = new ArrayList<>();
		quantities.keySet().forEach(id ->
		{
			ItemStats stats = wikiItems.get(id);
			if (stats != null)
			{
				ownedEquipment.add(stats);
			}
		});

		System.out.printf("bank snapshot: %d items, %d equippable%n", quantities.size(), ownedEquipment.size());

		LoadoutOptimizer optimizer = Guice.createInjector(new DpsComputeModule()).getInstance(LoadoutOptimizer.class);
		int failures = 0;
		for (ContentPreset preset : ContentPreset.values())
		{
			MonsterJson monster = monsters.get(preset.getPrimaryMonsterId());
			if (monster == null)
			{
				System.out.printf("%-35s MISSING MONSTER id=%d%n", preset.getLabel(), preset.getPrimaryMonsterId());
				failures++;
				continue;
			}

			long start = System.nanoTime();
			List<Loadout> loadouts;
			try
			{
				loadouts = optimizer.optimize(OptimizeRequest.builder()
					.ownedEquipment(ownedEquipment)
					.playerSkills(TestFixtures.maxedWithBoosts())
					.target(NpcStats.of(monster))
					.onSlayerTask(preset.isOnSlayerTask())
					.raidPartySize(preset.getRaidPartySize())
					.build());
			}
			catch (Exception e)
			{
				System.out.printf("%-35s THREW %s%n", preset.getLabel(), e);
				failures++;
				continue;
			}
			long ms = (System.nanoTime() - start) / 1_000_000;

			if (loadouts.isEmpty())
			{
				System.out.printf("%-35s NO LOADOUTS (%d ms)%n", preset.getLabel(), ms);
				failures++;
				continue;
			}

			StringBuilder sb = new StringBuilder();
			for (Loadout l : loadouts)
			{
				if (l.getDps() > 40 || !Double.isFinite(l.getDps()))
				{
					sb.append(" [SUSPICIOUS DPS]");
					failures++;
				}
				sb.append(String.format(" %s=%.2f(%s)", l.getCombatClass(), l.getDps(),
					l.getItems().get(EquipmentInventorySlot.WEAPON).getName()));
			}
			System.out.printf("%-35s %4dms%s%n", preset.getLabel(), ms, sb);
		}

		System.out.println("failures: " + failures);
		if (failures > 0)
		{
			throw new AssertionError(failures + " preset(s) failed the sweep");
		}
	}

}
