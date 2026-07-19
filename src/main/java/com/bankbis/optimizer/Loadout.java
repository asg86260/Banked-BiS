package com.bankbis.optimizer;

import com.duckblade.osrs.dpscalc.calc.model.AttackStyle;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.EquipmentInventorySlot;

/**
 * A concrete gear recommendation: what to wear, how to attack, and the
 * resulting DPS against the requested target.
 */
@Value
@Builder
public class Loadout
{

	private final CombatClass combatClass;
	private final Map<EquipmentInventorySlot, ItemStats> items;
	private final AttackStyle attackStyle;
	private final double dps;

}
