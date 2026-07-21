package com.bankbis.optimizer;

import com.duckblade.osrs.dpscalc.calc.model.AttackStyle;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.duckblade.osrs.dpscalc.calc.model.Spell;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import net.runelite.api.EquipmentInventorySlot;

/**
 * A concrete gear recommendation: what to wear, how to attack, and the
 * resulting DPS against the requested target.
 */
@Value
@Builder(toBuilder = true)
public class Loadout
{

	private final CombatClass combatClass;
	private final Map<EquipmentInventorySlot, ItemStats> items;
	private final AttackStyle attackStyle;

	/** The spell being cast, or null for non-casting loadouts. */
	private final Spell spell;

	private final double dps;

	/**
	 * This same gear re-evaluated under fixed scenarios, for display.
	 * Null when the scenario evaluations failed.
	 */
	private final DpsBreakdown breakdown;

	@Value
	public static class DpsBreakdown
	{
		double base;
		double prayed;
		double potted;
	}

}
