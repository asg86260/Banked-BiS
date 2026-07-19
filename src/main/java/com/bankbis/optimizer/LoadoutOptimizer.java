package com.bankbis.optimizer;

import com.duckblade.osrs.dpscalc.calc.DpsComputable;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeContext;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeInputs;
import com.duckblade.osrs.dpscalc.calc.exceptions.DpsComputeException;
import com.duckblade.osrs.dpscalc.calc.model.AttackStyle;
import com.duckblade.osrs.dpscalc.calc.model.CombatStyle;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.duckblade.osrs.dpscalc.calc.model.WeaponCategory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;

/**
 * Finds the highest-DPS loadout the player can assemble from owned items,
 * per combat class, using beam search over equipment slots with the full
 * calc engine as the scoring function.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class LoadoutOptimizer
{

	private static final int MAX_CANDIDATES_PER_SLOT = 8;
	private static final int BEAM_WIDTH = 25;
	private static final int DEFAULT_ATTACK_DISTANCE = 5;

	private static final List<EquipmentInventorySlot> ARMOR_SLOTS = ImmutableList.of(
		EquipmentInventorySlot.HEAD,
		EquipmentInventorySlot.CAPE,
		EquipmentInventorySlot.AMULET,
		EquipmentInventorySlot.AMMO,
		EquipmentInventorySlot.BODY,
		EquipmentInventorySlot.SHIELD,
		EquipmentInventorySlot.LEGS,
		EquipmentInventorySlot.GLOVES,
		EquipmentInventorySlot.BOOTS,
		EquipmentInventorySlot.RING
	);

	// v1: magic is limited to powered staves; autocast spell selection comes later
	private static final ImmutableSet<WeaponCategory> MAGIC_CATEGORIES = ImmutableSet.of(WeaponCategory.POWERED_STAFF);

	// requires herb "ammo" the engine models separately; not worth supporting yet
	private static final ImmutableSet<WeaponCategory> EXCLUDED_CATEGORIES = ImmutableSet.of(WeaponCategory.SALAMANDER);

	private final DpsComputable dpsComputable;

	/**
	 * Best loadout per combat class, ordered best-first. Classes with no
	 * usable weapon are omitted.
	 */
	public List<Loadout> optimize(OptimizeRequest request)
	{
		List<Loadout> results = new ArrayList<>();
		for (CombatClass combatClass : CombatClass.values())
		{
			optimizeClass(request, combatClass).ifPresent(results::add);
		}
		results.sort(Comparator.comparingDouble(Loadout::getDps).reversed());
		return results;
	}

	public Optional<Loadout> optimizeClass(OptimizeRequest request, CombatClass combatClass)
	{
		ItemStats darts = bestDarts(request);
		Loadout best = null;

		for (ItemStats weapon : weaponCandidates(request, combatClass, darts))
		{
			for (AttackStyle style : usableStyles(weapon, combatClass))
			{
				Loadout candidate = beamSearch(request, combatClass, weapon, style, darts);
				if (candidate != null && (best == null || candidate.getDps() > best.getDps()))
				{
					best = candidate;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	private Loadout beamSearch(OptimizeRequest request, CombatClass combatClass, ItemStats weapon, AttackStyle style, ItemStats darts)
	{
		Map<EquipmentInventorySlot, List<ItemStats>> candidates = armorCandidates(request, combatClass, weapon);

		List<ScoredLoadout> beam = new ArrayList<>();
		Map<EquipmentInventorySlot, ItemStats> seed = new EnumMap<>(EquipmentInventorySlot.class);
		seed.put(EquipmentInventorySlot.WEAPON, weapon);
		double seedScore = evaluate(request, seed, style, darts);
		if (seedScore < 0)
		{
			return null; // weapon itself doesn't produce a computable dps
		}
		beam.add(new ScoredLoadout(seed, seedScore));

		for (EquipmentInventorySlot slot : ARMOR_SLOTS)
		{
			List<ItemStats> slotCandidates = candidates.get(slot);
			if (slotCandidates == null || slotCandidates.isEmpty())
			{
				continue;
			}

			List<ScoredLoadout> next = new ArrayList<>(beam); // keeping the slot empty is always an option
			for (ScoredLoadout partial : beam)
			{
				for (ItemStats item : slotCandidates)
				{
					Map<EquipmentInventorySlot, ItemStats> extended = new EnumMap<>(partial.items);
					extended.put(slot, item);
					double score = evaluate(request, extended, style, darts);
					if (score >= 0)
					{
						next.add(new ScoredLoadout(extended, score));
					}
				}
			}
			next.sort(Comparator.comparingDouble((ScoredLoadout s) -> s.dps).reversed());
			beam = next.size() > BEAM_WIDTH ? new ArrayList<>(next.subList(0, BEAM_WIDTH)) : next;
		}

		ScoredLoadout top = beam.get(0);
		if (top.dps <= 0)
		{
			return null; // target is immune to this combat class (e.g. melee at Zulrah)
		}
		return Loadout.builder()
			.combatClass(combatClass)
			.items(top.items)
			.attackStyle(style)
			.dps(top.dps)
			.build();
	}

	/**
	 * @return dps, or -1 if this combination cannot be computed (missing
	 * inputs, immune target, etc.)
	 */
	private double evaluate(OptimizeRequest request, Map<EquipmentInventorySlot, ItemStats> items, AttackStyle style, ItemStats darts)
	{
		try
		{
			ComputeContext context = new ComputeContext();
			context.put(ComputeInputs.ATTACKER_SKILLS, request.getPlayerSkills());
			context.put(ComputeInputs.ATTACKER_ITEMS, items);
			context.put(ComputeInputs.ATTACKER_PRAYERS, ImmutableSet.of(styleClass(style).getBestPrayer()));
			context.put(ComputeInputs.ATTACK_STYLE, style);
			context.put(ComputeInputs.DEFENDER_SKILLS, request.getTarget().getSkills());
			context.put(ComputeInputs.DEFENDER_BONUSES, request.getTarget().getDefensiveBonuses());
			context.put(ComputeInputs.DEFENDER_ATTRIBUTES, request.getTarget().getAttributes());
			context.put(ComputeInputs.ON_SLAYER_TASK, request.isOnSlayerTask());
			context.put(ComputeInputs.RAID_PARTY_SIZE, request.getRaidPartySize());
			context.put(ComputeInputs.ATTACK_DISTANCE, DEFAULT_ATTACK_DISTANCE);
			if (darts != null)
			{
				context.put(ComputeInputs.BLOWPIPE_DARTS, darts);
			}
			double dps = context.get(dpsComputable);
			return Double.isFinite(dps) && dps >= 0 ? dps : -1;
		}
		catch (DpsComputeException e)
		{
			return -1;
		}
	}

	private CombatClass styleClass(AttackStyle style)
	{
		for (CombatClass c : CombatClass.values())
		{
			if (c.includes(style.getAttackType()))
			{
				return c;
			}
		}
		return CombatClass.MELEE;
	}

	private List<ItemStats> weaponCandidates(OptimizeRequest request, CombatClass combatClass, ItemStats darts)
	{
		List<ItemStats> weapons = new ArrayList<>();
		for (ItemStats item : request.getOwnedEquipment())
		{
			if (item.getSlot() != EquipmentInventorySlot.WEAPON.getSlotIdx())
			{
				continue;
			}
			WeaponCategory category = item.getWeaponCategory();
			if (category == null || EXCLUDED_CATEGORIES.contains(category))
			{
				continue;
			}
			if (combatClass == CombatClass.MAGIC && !MAGIC_CATEGORIES.contains(category))
			{
				continue;
			}
			if (isBlowpipe(item) && darts == null)
			{
				continue;
			}
			boolean attacksClass = category.getAttackStyles().stream()
				.anyMatch(s -> s.getAttackType() != null && combatClass.includes(s.getAttackType()));
			if (attacksClass)
			{
				weapons.add(item);
			}
		}
		return weapons;
	}

	private List<AttackStyle> usableStyles(ItemStats weapon, CombatClass combatClass)
	{
		List<AttackStyle> styles = new ArrayList<>();
		for (AttackStyle style : weapon.getWeaponCategory().getAttackStyles())
		{
			if (style.getAttackType() == null || !combatClass.includes(style.getAttackType()))
			{
				continue;
			}
			if (style.getCombatStyle() == CombatStyle.DEFENSIVE || style.getCombatStyle() == CombatStyle.AUTOCAST)
			{
				continue;
			}
			styles.add(style);
		}
		return styles;
	}

	private Map<EquipmentInventorySlot, List<ItemStats>> armorCandidates(OptimizeRequest request, CombatClass combatClass, ItemStats weapon)
	{
		Map<EquipmentInventorySlot, List<ItemStats>> candidates = new EnumMap<>(EquipmentInventorySlot.class);
		for (ItemStats item : request.getOwnedEquipment())
		{
			EquipmentInventorySlot slot = slotOf(item);
			if (slot == null || slot == EquipmentInventorySlot.WEAPON)
			{
				continue;
			}
			if (slot == EquipmentInventorySlot.SHIELD && weapon.is2h())
			{
				continue;
			}
			if (slot == EquipmentInventorySlot.AMMO && !ammoMatches(weapon, item))
			{
				continue;
			}
			candidates.computeIfAbsent(slot, s -> new ArrayList<>()).add(item);
		}

		candidates.replaceAll((slot, items) -> prune(items, combatClass));
		return candidates;
	}

	/**
	 * Drops items strictly dominated by another candidate in the stats the
	 * engine actually reads for this combat class, then caps list size.
	 */
	List<ItemStats> prune(List<ItemStats> items, CombatClass combatClass)
	{
		List<ItemStats> kept = new ArrayList<>();
		for (ItemStats item : items)
		{
			boolean dominated = items.stream()
				.anyMatch(other -> other != item && dominates(other, item, combatClass));
			if (!dominated)
			{
				kept.add(item);
			}
		}
		kept.sort(Comparator.comparingInt((ItemStats i) -> strengthOf(i, combatClass)).reversed()
			.thenComparing(Comparator.comparingInt((ItemStats i) -> accuracySumOf(i, combatClass)).reversed()));
		return kept.size() > MAX_CANDIDATES_PER_SLOT ? kept.subList(0, MAX_CANDIDATES_PER_SLOT) : kept;
	}

	private boolean dominates(ItemStats a, ItemStats b, CombatClass combatClass)
	{
		int[] va = statsVector(a, combatClass);
		int[] vb = statsVector(b, combatClass);
		boolean strictlyBetter = false;
		for (int i = 0; i < va.length; i++)
		{
			if (va[i] < vb[i])
			{
				return false;
			}
			if (va[i] > vb[i])
			{
				strictlyBetter = true;
			}
		}
		return strictlyBetter;
	}

	private int[] statsVector(ItemStats i, CombatClass combatClass)
	{
		switch (combatClass)
		{
			case MELEE:
				return new int[]{i.getAccuracyStab(), i.getAccuracySlash(), i.getAccuracyCrush(), i.getStrengthMelee(), i.getPrayer()};
			case RANGED:
				return new int[]{i.getAccuracyRanged(), i.getStrengthRanged(), i.getPrayer()};
			case MAGIC:
			default:
				return new int[]{i.getAccuracyMagic(), i.getStrengthMagic(), i.getPrayer()};
		}
	}

	private int strengthOf(ItemStats i, CombatClass combatClass)
	{
		switch (combatClass)
		{
			case MELEE:
				return i.getStrengthMelee();
			case RANGED:
				return i.getStrengthRanged();
			case MAGIC:
			default:
				return i.getStrengthMagic();
		}
	}

	private int accuracySumOf(ItemStats i, CombatClass combatClass)
	{
		switch (combatClass)
		{
			case MELEE:
				return i.getAccuracyStab() + i.getAccuracySlash() + i.getAccuracyCrush();
			case RANGED:
				return i.getAccuracyRanged();
			case MAGIC:
			default:
				return i.getAccuracyMagic();
		}
	}

	private EquipmentInventorySlot slotOf(ItemStats item)
	{
		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			if (slot.getSlotIdx() == item.getSlot())
			{
				return slot;
			}
		}
		return null;
	}

	private boolean ammoMatches(ItemStats weapon, ItemStats ammo)
	{
		String name = ammo.getName() == null ? "" : ammo.getName().toLowerCase(Locale.ROOT);
		switch (weapon.getWeaponCategory())
		{
			case BOW:
				return name.contains("arrow") && !name.contains("gloves");
			case CROSSBOW:
				return name.contains("bolt");
			default:
				return false; // other weapons gain nothing from ammo in the engine
		}
	}

	private boolean isBlowpipe(ItemStats item)
	{
		return item.getName() != null && item.getName().toLowerCase(Locale.ROOT).contains("blowpipe");
	}

	private ItemStats bestDarts(OptimizeRequest request)
	{
		ItemStats best = null;
		for (ItemStats item : request.getOwnedEquipment())
		{
			String name = item.getName() == null ? "" : item.getName().toLowerCase(Locale.ROOT);
			if (!name.endsWith("dart"))
			{
				continue;
			}
			if (best == null || item.getStrengthRanged() > best.getStrengthRanged())
			{
				best = item;
			}
		}
		return best;
	}

	@Value
	private static class ScoredLoadout
	{
		Map<EquipmentInventorySlot, ItemStats> items;
		double dps;
	}

}
