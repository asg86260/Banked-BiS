package com.bankbis.ui;

import com.bankbis.optimizer.CombatClass;
import com.bankbis.optimizer.Loadout;
import com.duckblade.osrs.dpscalc.calc.model.AttackStyle;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class InventorySetupExportTest
{

	@Test
	void producesImportableStructure()
	{
		Map<EquipmentInventorySlot, ItemStats> items = new EnumMap<>(EquipmentInventorySlot.class);
		items.put(EquipmentInventorySlot.WEAPON, ItemStats.builder().itemId(4151).name("Abyssal whip").build());
		items.put(EquipmentInventorySlot.HEAD, ItemStats.builder().itemId(10828).name("Helm of neitiznot").build());
		Loadout loadout = Loadout.builder()
			.combatClass(CombatClass.MELEE)
			.items(items)
			.attackStyle(AttackStyle.builder().displayName("Aggressive").build())
			.dps(7.5)
			.build();

		Gson gson = new Gson();
		String json = InventorySetupExport.toJson(gson, loadout, "Kraken");
		JsonObject root = gson.fromJson(json, JsonObject.class);

		// layout is required by the importer: non-null, padded to rows of 8
		JsonArray layout = root.getAsJsonArray("layout");
		assertEquals(0, layout.size() % 8);
		assertEquals(4151, findInLayout(layout, 4151));

		JsonObject setup = root.getAsJsonObject("setup");
		assertEquals(28, setup.getAsJsonArray("inv").size());

		JsonArray eq = setup.getAsJsonArray("eq");
		assertEquals(14, eq.size());
		// items land at their equipment slot index
		assertEquals(10828, eq.get(EquipmentInventorySlot.HEAD.getSlotIdx()).getAsJsonObject().get("id").getAsInt());
		assertEquals(4151, eq.get(EquipmentInventorySlot.WEAPON.getSlotIdx()).getAsJsonObject().get("id").getAsInt());
		// empty slots serialize as null
		assertTrue(eq.get(EquipmentInventorySlot.SHIELD.getSlotIdx()).isJsonNull());

		assertEquals("Kraken - Melee", setup.get("name").getAsString());
		assertTrue(setup.get("notes").getAsString().contains("7.50"));
		assertTrue(setup.getAsJsonObject("hc").has("value"));
	}

	private static int findInLayout(JsonArray layout, int itemId)
	{
		for (int i = 0; i < layout.size(); i++)
		{
			if (!layout.get(i).isJsonNull() && layout.get(i).getAsInt() == itemId)
			{
				return itemId;
			}
		}
		return -1;
	}

}
