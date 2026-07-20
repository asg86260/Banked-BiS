package com.bankbis.optimizer;

import com.bankbis.data.NpcStats;
import com.bankbis.testutil.TestFixtures;
import com.duckblade.osrs.dpscalc.calc.DpsComputeModule;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.google.inject.Guice;
import java.util.List;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Manual harness: dumps optimizer output for comparison against the wiki
 * calculator (dps.osrs.wiki). Run on demand with
 * {@code gradlew test --tests "*.LoadoutPrintTest" -i} after removing the
 * Disabled annotation, or via IDE.
 */
@Disabled("manual validation harness, not an automated test")
class LoadoutPrintTest
{

	@Test
	void printLoadouts()
	{
		List<ItemStats> owned = TestFixtures.loadItemStats(LoadoutPrintTest.class, "equipment-owned.json");
		Map<String, NpcStats> monsters = TestFixtures.npcStatsByDisplayName(LoadoutPrintTest.class, "monsters-preset.json");
		LoadoutOptimizer optimizer = Guice.createInjector(new DpsComputeModule()).getInstance(LoadoutOptimizer.class);

		for (String monster : new String[]{"General Graardor", "Zulrah (Serpentine)", "Vorkath (Post-quest)", "Abyssal demon (Standard)"})
		{
			System.out.println("=== " + monster + " ===");
			OptimizeRequest req = OptimizeRequest.builder()
				.ownedEquipment(owned)
				.playerSkills(TestFixtures.maxedWithBoosts())
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
