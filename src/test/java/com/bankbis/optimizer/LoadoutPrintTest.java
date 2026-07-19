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
import java.util.stream.Collectors;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;

// temporary: dumps optimizer output for manual comparison against the wiki calc
class LoadoutPrintTest
{

	@Test
	void printLoadouts() throws Exception
	{
		Gson gson = new Gson();
		Type eqType = new TypeToken<List<EquipmentJson>>()
		{
		}.getType();
		Type monType = new TypeToken<List<MonsterJson>>()
		{
		}.getType();

		List<ItemStats> owned;
		Map<String, NpcStats> monsters;
		try (Reader r = new InputStreamReader(getClass().getResourceAsStream("equipment-owned.json"), StandardCharsets.UTF_8))
		{
			List<EquipmentJson> parsed = gson.fromJson(r, eqType);
			owned = parsed.stream().map(WikiDataMapper::toItemStats).collect(Collectors.toList());
		}
		try (Reader r = new InputStreamReader(getClass().getResourceAsStream("monsters-preset.json"), StandardCharsets.UTF_8))
		{
			List<MonsterJson> parsed = gson.fromJson(r, monType);
			monsters = parsed.stream().collect(Collectors.toMap(WikiDataMapper::displayName, NpcStats::of));
		}

		Skills skills = Skills.builder()
			.level(Skill.ATTACK, 99).boost(Skill.ATTACK, 19)
			.level(Skill.STRENGTH, 99).boost(Skill.STRENGTH, 19)
			.level(Skill.DEFENCE, 99).boost(Skill.DEFENCE, 19)
			.level(Skill.RANGED, 99).boost(Skill.RANGED, 13)
			.level(Skill.MAGIC, 99).boost(Skill.MAGIC, 13)
			.level(Skill.HITPOINTS, 99)
			.level(Skill.PRAYER, 99)
			.build();

		LoadoutOptimizer optimizer = Guice.createInjector(new DpsComputeModule()).getInstance(LoadoutOptimizer.class);

		for (String monster : new String[]{"General Graardor", "Zulrah (Serpentine)", "Vorkath (Post-quest)", "Abyssal demon (Standard)"})
		{
			System.out.println("=== " + monster + " ===");
			OptimizeRequest req = OptimizeRequest.builder()
				.ownedEquipment(owned)
				.playerSkills(skills)
				.target(monsters.get(monster))
				.build();
			for (Loadout l : optimizer.optimize(req))
			{
				System.out.printf("%s: %.2f dps [%s]%n", l.getCombatClass(), l.getDps(), l.getAttackStyle().getDisplayName());
				for (Map.Entry<EquipmentInventorySlot, ItemStats> e : l.getItems().entrySet())
				{
					System.out.printf("  %-7s %s%n", e.getKey(), e.getValue().getName());
				}
			}
		}
	}

}
