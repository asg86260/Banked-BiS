package com.bankbis.optimizer;

import com.duckblade.osrs.dpscalc.calc.DpsComputable;
import com.duckblade.osrs.dpscalc.calc.HitChanceComputable;
import com.duckblade.osrs.dpscalc.calc.VoidLevelComputable;
import com.duckblade.osrs.dpscalc.calc.ammo.BlowpipeDartsItemStatsComputable;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeContext;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeInputs;
import com.duckblade.osrs.dpscalc.calc.exceptions.DpsComputeException;
import com.duckblade.osrs.dpscalc.calc.gearbonus.BlackMaskGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.CrystalGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.InquisitorsGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.LeafyGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.SalveAmuletGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.TomesGearBonus;
import com.duckblade.osrs.dpscalc.calc.maxhit.TrueMaxHitComputable;
import com.duckblade.osrs.dpscalc.calc.maxhit.magic.PoweredStaffMaxHitComputable;
import com.duckblade.osrs.dpscalc.calc.model.AttackStyle;
import com.duckblade.osrs.dpscalc.calc.model.CombatStyle;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.duckblade.osrs.dpscalc.calc.model.Prayer;
import com.duckblade.osrs.dpscalc.calc.model.Skills;
import com.duckblade.osrs.dpscalc.calc.model.Spell;
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
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;

/**
 * Finds the highest-DPS loadout the player can assemble from owned items,
 * per combat class: beam search over equipment slots with the full calc
 * engine as the scoring function, plus forced "set templates" (Void,
 * Inquisitor's, crystal armour) whose value only materializes when several
 * pieces are worn together.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class LoadoutOptimizer
{

	private static final int MAX_CANDIDATES_PER_SLOT = 8;
	private static final int MAX_WEAPON_STYLES = 10;
	private static final int BEAM_WIDTH = 20;
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

	private static final EquipmentInventorySlot[] SLOT_BY_IDX = buildSlotIndex();

	// requires herb "ammo" the engine models separately; not worth supporting yet
	private static final Set<WeaponCategory> EXCLUDED_CATEGORIES = ImmutableSet.of(WeaponCategory.SALAMANDER);

	/**
	 * Items whose power comes from engine special cases rather than raw
	 * stats (slayer helms, salve, tomes, broad ammo, set pieces). They must
	 * survive dominance pruning or the engine never gets to score them.
	 */
	private static final Set<Integer> NEVER_PRUNE = ImmutableSet.<Integer>builder()
		.addAll(BlackMaskGearBonus.BLACK_MASKS_MELEE)
		.addAll(BlackMaskGearBonus.BLACK_MASKS_MAGE_RANGED)
		.addAll(SalveAmuletGearBonus.SALVE_ALL)
		.addAll(LeafyGearBonus.LEAF_BLADED_AMMO)
		.addAll(TomesGearBonus.TOME_OF_FIRE)
		.addAll(TomesGearBonus.TOME_OF_WATER)
		.addAll(InquisitorsGearBonus.INQ_HELM_IDS)
		.addAll(InquisitorsGearBonus.INQ_BODY_IDS)
		.addAll(InquisitorsGearBonus.INQ_LEGS_IDS)
		.addAll(CrystalGearBonus.CRYSTAL_HELM_IDS)
		.addAll(CrystalGearBonus.CRYSTAL_BODY_IDS)
		.addAll(CrystalGearBonus.CRYSTAL_LEGS_IDS)
		.build();

	private final DpsComputable dpsComputable;
	private final TrueMaxHitComputable trueMaxHitComputable;
	private final HitChanceComputable hitChanceComputable;

	/**
	 * Best loadout per combat class, ordered best-first. Classes with no
	 * usable weapon (or that the target is immune to) are omitted.
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
		Set<Prayer> prayers = request.getPrayerAssumption().prayersFor(combatClass, prayerLevel(request), request.getPrayerUnlocks());

		// weapon prefilter: rank weapon+style(+spell) combos by weapon-only
		// dps and keep the top few before paying for full beam searches
		List<ScoredLoadout> seeds = new ArrayList<>();
		for (ItemStats weapon : weaponCandidates(request, combatClass, darts))
		{
			if (combatClass == CombatClass.MAGIC && isCastingStaff(weapon))
			{
				// casting staves attack by spell, not by weapon style; try
				// the best castable spell of each element (weakness makes
				// the element matter, not just the tier)
				for (Spell spell : SpellSelector.bestCastable(magicLevel(request)))
				{
					Map<EquipmentInventorySlot, ItemStats> seed = new EnumMap<>(EquipmentInventorySlot.class);
					seed.put(EquipmentInventorySlot.WEAPON, weapon);
					double dps = evaluate(request, seed, AttackStyle.MANUAL_CAST, spell, prayers, darts);
					if (dps > 0)
					{
						seeds.add(new ScoredLoadout(seed, AttackStyle.MANUAL_CAST, spell, dps));
					}
				}
				continue;
			}
			for (AttackStyle style : usableStyles(weapon, combatClass))
			{
				Map<EquipmentInventorySlot, ItemStats> seed = new EnumMap<>(EquipmentInventorySlot.class);
				seed.put(EquipmentInventorySlot.WEAPON, weapon);
				double dps = evaluate(request, seed, style, null, prayers, darts);
				if (dps > 0)
				{
					seeds.add(new ScoredLoadout(seed, style, null, dps));
				}
			}
		}
		seeds.sort(Comparator.comparingDouble((ScoredLoadout s) -> s.dps).reversed());
		if (seeds.size() > MAX_WEAPON_STYLES)
		{
			seeds = seeds.subList(0, MAX_WEAPON_STYLES);
		}

		// weapon-independent candidates built and pruned once per class
		Map<EquipmentInventorySlot, List<ItemStats>> baseCandidates = baseArmorCandidates(request, combatClass);
		List<ItemStats> shields = baseCandidates.remove(EquipmentInventorySlot.SHIELD);

		ScoredLoadout best = null;
		for (ScoredLoadout seed : seeds)
		{
			ItemStats weapon = seed.items.get(EquipmentInventorySlot.WEAPON);

			Map<EquipmentInventorySlot, List<ItemStats>> candidates = new EnumMap<>(baseCandidates);
			if (!weapon.is2h() && shields != null)
			{
				candidates.put(EquipmentInventorySlot.SHIELD, shields);
			}
			List<ItemStats> ammo = ammoFor(request, weapon, combatClass);
			if (!ammo.isEmpty())
			{
				candidates.put(EquipmentInventorySlot.AMMO, ammo);
			}

			ScoredLoadout searched = beamSearch(request, seed, candidates, prayers, darts);
			best = better(best, searched);

			for (Map<EquipmentInventorySlot, ItemStats> template : setTemplates(request, combatClass, weapon))
			{
				Map<EquipmentInventorySlot, ItemStats> templatedSeed = new EnumMap<>(seed.items);
				templatedSeed.putAll(template);
				Map<EquipmentInventorySlot, List<ItemStats>> remaining = new EnumMap<>(candidates);
				remaining.keySet().removeAll(template.keySet());
				ScoredLoadout templated = beamSearch(request,
					new ScoredLoadout(templatedSeed, seed.style, seed.spell, 0), remaining, prayers, darts);
				best = better(best, templated);
			}
		}

		if (best == null || best.dps <= 0)
		{
			return Optional.empty();
		}

		int maxHit = 0;
		double accuracy = 0;
		try
		{
			ComputeContext context = buildContext(request, best.items, best.style, best.spell, prayers, darts);
			maxHit = context.get(trueMaxHitComputable);
			accuracy = context.get(hitChanceComputable);
		}
		catch (DpsComputeException e)
		{
			// display-only; the loadout itself already evaluated fine
		}

		return Optional.of(Loadout.builder()
			.combatClass(combatClass)
			.items(best.items)
			.attackStyle(best.style)
			.spell(best.spell)
			.dps(best.dps)
			.maxHit(maxHit)
			.accuracy(accuracy)
			.build());
	}

	/**
	 * Re-evaluates an already-chosen loadout under the request's skills and
	 * the given prayers, for scenario comparisons (base/prayed/potted).
	 *
	 * @return dps, or -1 if the combination cannot be computed
	 */
	public double evaluateLoadout(OptimizeRequest request, Loadout loadout, Set<Prayer> prayers)
	{
		return evaluate(request, loadout.getItems(), loadout.getAttackStyle(), loadout.getSpell(), prayers, bestDarts(request));
	}

	private static ScoredLoadout better(ScoredLoadout a, ScoredLoadout b)
	{
		if (a == null)
		{
			return b;
		}
		if (b == null)
		{
			return a;
		}
		return b.dps > a.dps ? b : a;
	}

	private ScoredLoadout beamSearch(
		OptimizeRequest request,
		ScoredLoadout seed,
		Map<EquipmentInventorySlot, List<ItemStats>> candidates,
		Set<Prayer> prayers,
		ItemStats darts)
	{
		double seedScore = evaluate(request, seed.items, seed.style, seed.spell, prayers, darts);
		if (seedScore < 0)
		{
			return null;
		}

		List<ScoredLoadout> beam = new ArrayList<>();
		beam.add(new ScoredLoadout(seed.items, seed.style, seed.spell, seedScore));

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
					double score = evaluate(request, extended, seed.style, seed.spell, prayers, darts);
					if (score >= 0)
					{
						next.add(new ScoredLoadout(extended, seed.style, seed.spell, score));
					}
				}
			}
			next.sort(Comparator.comparingDouble((ScoredLoadout s) -> s.dps).reversed());
			beam = next.size() > BEAM_WIDTH ? new ArrayList<>(next.subList(0, BEAM_WIDTH)) : next;
		}

		return beam.get(0);
	}

	/**
	 * @return dps, or -1 if this combination cannot be computed (missing
	 * inputs, unsupported weapon, etc.)
	 */
	private double evaluate(
		OptimizeRequest request,
		Map<EquipmentInventorySlot, ItemStats> items,
		AttackStyle style,
		Spell spell,
		Set<Prayer> prayers,
		ItemStats darts)
	{
		try
		{
			ComputeContext context = buildContext(request, items, style, spell, prayers, darts);
			double dps = context.get(dpsComputable);
			return Double.isFinite(dps) && dps >= 0 ? dps : -1;
		}
		catch (DpsComputeException e)
		{
			return -1;
		}
	}

	private ComputeContext buildContext(
		OptimizeRequest request,
		Map<EquipmentInventorySlot, ItemStats> items,
		AttackStyle style,
		Spell spell,
		Set<Prayer> prayers,
		ItemStats darts)
	{
		ComputeContext context = new ComputeContext();
		context.put(ComputeInputs.ATTACKER_SKILLS, request.getPlayerSkills());
		context.put(ComputeInputs.ATTACKER_ITEMS, items);
		context.put(ComputeInputs.ATTACKER_PRAYERS, prayers);
		context.put(ComputeInputs.ATTACK_STYLE, style);
		if (spell != null)
		{
			context.put(ComputeInputs.SPELL, spell);
		}
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
		return context;
	}

	private int prayerLevel(OptimizeRequest request)
	{
		Skills skills = request.getPlayerSkills();
		Integer level = skills.getLevels() == null ? null : skills.getLevels().get(Skill.PRAYER);
		return level == null ? 0 : level;
	}

	private int magicLevel(OptimizeRequest request)
	{
		Skills skills = request.getPlayerSkills();
		Integer level = skills.getLevels() == null ? null : skills.getLevels().get(Skill.MAGIC);
		return level == null ? 0 : level;
	}

	private static boolean isCastingStaff(ItemStats weapon)
	{
		WeaponCategory category = weapon.getWeaponCategory();
		return category == WeaponCategory.STAFF || category == WeaponCategory.BLADED_STAFF;
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
			// UNARMED as a weapon's category means the wiki category is
			// unknown to the engine; recommending it would use punch styles
			if (category == null || category == WeaponCategory.UNARMED || EXCLUDED_CATEGORIES.contains(category))
			{
				continue;
			}
			if (combatClass == CombatClass.MAGIC
				&& !(category == WeaponCategory.POWERED_STAFF
				&& PoweredStaffMaxHitComputable.SUPPORTED_STAFF_IDS.contains(item.getItemId()))
				&& !isCastingStaff(item))
			{
				continue;
			}
			if (BlowpipeDartsItemStatsComputable.BLOWPIPE_IDS.contains(item.getItemId()) && darts == null)
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

	private Map<EquipmentInventorySlot, List<ItemStats>> baseArmorCandidates(OptimizeRequest request, CombatClass combatClass)
	{
		Map<EquipmentInventorySlot, List<ItemStats>> candidates = new EnumMap<>(EquipmentInventorySlot.class);
		for (ItemStats item : request.getOwnedEquipment())
		{
			EquipmentInventorySlot slot = slotOf(item);
			if (slot == null || slot == EquipmentInventorySlot.WEAPON || slot == EquipmentInventorySlot.AMMO)
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
	 * engine reads for this combat class, then caps the list. Items with
	 * engine special cases ({@link #NEVER_PRUNE}) always survive.
	 */
	List<ItemStats> prune(List<ItemStats> items, CombatClass combatClass)
	{
		List<ItemStats> specials = new ArrayList<>();
		List<ItemStats> normals = new ArrayList<>();
		for (ItemStats item : items)
		{
			(NEVER_PRUNE.contains(item.getItemId()) ? specials : normals).add(item);
		}

		int n = normals.size();
		int[][] vectors = new int[n][];
		for (int i = 0; i < n; i++)
		{
			vectors[i] = combatClass.statsVector(normals.get(i));
		}

		List<ItemStats> kept = new ArrayList<>();
		for (int i = 0; i < n; i++)
		{
			boolean dominated = false;
			for (int j = 0; j < n && !dominated; j++)
			{
				dominated = j != i && dominates(vectors[j], vectors[i]);
			}
			if (!dominated)
			{
				kept.add(normals.get(i));
			}
		}
		kept.sort(Comparator.comparingInt((ItemStats i) -> combatClass.strengthOf(i)).reversed()
			.thenComparing(Comparator.comparingInt((ItemStats i) -> combatClass.accuracySumOf(i)).reversed()));
		if (kept.size() > MAX_CANDIDATES_PER_SLOT)
		{
			kept = new ArrayList<>(kept.subList(0, MAX_CANDIDATES_PER_SLOT));
		}
		kept.addAll(specials);
		return kept;
	}

	private static boolean dominates(int[] a, int[] b)
	{
		boolean strictlyBetter = false;
		for (int i = 0; i < a.length; i++)
		{
			if (a[i] < b[i])
			{
				return false;
			}
			if (a[i] > b[i])
			{
				strictlyBetter = true;
			}
		}
		return strictlyBetter;
	}

	// ---- set templates ------------------------------------------------

	/**
	 * Piece combinations whose bonus only exists when worn together, so
	 * incremental beam search would never assemble them on its own.
	 */
	private List<Map<EquipmentInventorySlot, ItemStats>> setTemplates(OptimizeRequest request, CombatClass combatClass, ItemStats weapon)
	{
		List<Map<EquipmentInventorySlot, ItemStats>> templates = new ArrayList<>();

		Set<Integer> voidHelms = combatClass == CombatClass.MELEE ? VoidLevelComputable.VOID_MELEE_HELMS
			: combatClass == CombatClass.RANGED ? VoidLevelComputable.VOID_RANGER_HELMS
			: VoidLevelComputable.VOID_MAGE_HELMS;
		ItemStats helm = firstOwned(request, voidHelms);
		ItemStats gloves = firstOwned(request, VoidLevelComputable.VOID_KNIGHT_GLOVES);
		if (helm != null && gloves != null)
		{
			ItemStats eliteTop = firstOwned(request, VoidLevelComputable.ELITE_VOID_TOPS);
			ItemStats eliteRobe = firstOwned(request, VoidLevelComputable.ELITE_VOID_ROBES);
			ItemStats top = eliteTop != null ? eliteTop : firstOwned(request, VoidLevelComputable.VOID_KNIGHT_TOPS);
			ItemStats robe = eliteRobe != null ? eliteRobe : firstOwned(request, VoidLevelComputable.VOID_KNIGHT_ROBES);
			if (top != null && robe != null)
			{
				Map<EquipmentInventorySlot, ItemStats> voidSet = new EnumMap<>(EquipmentInventorySlot.class);
				voidSet.put(EquipmentInventorySlot.HEAD, helm);
				voidSet.put(EquipmentInventorySlot.BODY, top);
				voidSet.put(EquipmentInventorySlot.LEGS, robe);
				voidSet.put(EquipmentInventorySlot.GLOVES, gloves);
				templates.add(voidSet);
			}
		}

		if (combatClass == CombatClass.MELEE)
		{
			ItemStats inqHelm = firstOwned(request, InquisitorsGearBonus.INQ_HELM_IDS);
			ItemStats inqBody = firstOwned(request, InquisitorsGearBonus.INQ_BODY_IDS);
			ItemStats inqLegs = firstOwned(request, InquisitorsGearBonus.INQ_LEGS_IDS);
			if (inqHelm != null && inqBody != null && inqLegs != null)
			{
				Map<EquipmentInventorySlot, ItemStats> inq = new EnumMap<>(EquipmentInventorySlot.class);
				inq.put(EquipmentInventorySlot.HEAD, inqHelm);
				inq.put(EquipmentInventorySlot.BODY, inqBody);
				inq.put(EquipmentInventorySlot.LEGS, inqLegs);
				templates.add(inq);
			}
		}

		if (combatClass == CombatClass.RANGED && CrystalGearBonus.CRYSTAL_BOWS.contains(weapon.getItemId()))
		{
			// crystal bonus scales per piece; force whatever is owned
			Map<EquipmentInventorySlot, ItemStats> crystal = new EnumMap<>(EquipmentInventorySlot.class);
			putIfOwned(crystal, EquipmentInventorySlot.HEAD, request, CrystalGearBonus.CRYSTAL_HELM_IDS);
			putIfOwned(crystal, EquipmentInventorySlot.BODY, request, CrystalGearBonus.CRYSTAL_BODY_IDS);
			putIfOwned(crystal, EquipmentInventorySlot.LEGS, request, CrystalGearBonus.CRYSTAL_LEGS_IDS);
			if (!crystal.isEmpty())
			{
				templates.add(crystal);
			}
		}

		return templates;
	}

	private static void putIfOwned(Map<EquipmentInventorySlot, ItemStats> map, EquipmentInventorySlot slot, OptimizeRequest request, Set<Integer> ids)
	{
		ItemStats item = firstOwned(request, ids);
		if (item != null)
		{
			map.put(slot, item);
		}
	}

	private static ItemStats firstOwned(OptimizeRequest request, Set<Integer> ids)
	{
		for (ItemStats item : request.getOwnedEquipment())
		{
			if (ids.contains(item.getItemId()))
			{
				return item;
			}
		}
		return null;
	}

	// ---- ammo ---------------------------------------------------------

	private static final List<String> ARROW_TIERS = ImmutableList.of(
		"bronze arrow", "iron arrow", "steel arrow", "mithril arrow",
		"adamant arrow", "broad arrow", "rune arrow", "amethyst arrow", "dragon arrow");

	private List<ItemStats> ammoFor(OptimizeRequest request, ItemStats weapon, CombatClass combatClass)
	{
		if (combatClass != CombatClass.RANGED
			|| BlowpipeDartsItemStatsComputable.BLOWPIPE_IDS.contains(weapon.getItemId())
			|| CrystalGearBonus.CRYSTAL_BOWS.contains(weapon.getItemId()))
		{
			return ImmutableList.of();
		}

		String weaponName = lowerName(weapon);
		List<ItemStats> matches = new ArrayList<>();
		for (ItemStats item : request.getOwnedEquipment())
		{
			if (item.getSlot() != EquipmentInventorySlot.AMMO.getSlotIdx())
			{
				continue;
			}
			if (ammoCompatible(weapon, weaponName, item))
			{
				matches.add(item);
			}
		}
		return prune(matches, combatClass);
	}

	private boolean ammoCompatible(ItemStats weapon, String weaponName, ItemStats ammo)
	{
		String name = lowerName(ammo);
		switch (weapon.getWeaponCategory())
		{
			case BOW:
				int tier = arrowTier(name);
				if (tier < 0 || name.contains("ogre") || name.contains("brutal") || name.contains("training"))
				{
					return false;
				}
				return tier <= maxArrowTier(weaponName);

			case CROSSBOW:
				if (weaponName.contains("karil"))
				{
					return name.contains("bolt rack");
				}
				if (weaponName.contains("ballista"))
				{
					return name.contains("javelin");
				}
				if (!name.contains("bolt") || name.contains("bolt rack") || name.contains("kebbit"))
				{
					return false;
				}
				// dragon bolts need a dragon-tier crossbow
				if (name.contains("dragon bolts") && !weaponName.contains("dragon") && !weaponName.contains("armadyl") && !weaponName.contains("zaryte"))
				{
					return false;
				}
				return true;

			default:
				return false; // other weapons gain nothing from ammo in the engine
		}
	}

	private static int arrowTier(String ammoName)
	{
		for (int i = 0; i < ARROW_TIERS.size(); i++)
		{
			if (ammoName.contains(ARROW_TIERS.get(i)))
			{
				return i;
			}
		}
		return -1;
	}

	private static int maxArrowTier(String weaponName)
	{
		if (weaponName.contains("twisted bow") || weaponName.contains("dark bow") || weaponName.contains("venator"))
		{
			return ARROW_TIERS.indexOf("dragon arrow");
		}
		if (weaponName.contains("magic shortbow") || weaponName.contains("magic comp"))
		{
			return ARROW_TIERS.indexOf("amethyst arrow");
		}
		return ARROW_TIERS.indexOf("rune arrow");
	}

	private ItemStats bestDarts(OptimizeRequest request)
	{
		ItemStats best = null;
		for (ItemStats item : request.getOwnedEquipment())
		{
			// darts are weapon-slot thrown weapons; ammo-slot lookalikes
			// (atlatl darts) are excluded by the slot check
			if (item.getSlot() != EquipmentInventorySlot.WEAPON.getSlotIdx()
				|| item.getWeaponCategory() != WeaponCategory.THROWN
				|| !lowerName(item).contains("dart"))
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

	// ---- misc ---------------------------------------------------------

	private static String lowerName(ItemStats item)
	{
		return item.getName() == null ? "" : item.getName().toLowerCase(Locale.ROOT);
	}

	private static EquipmentInventorySlot[] buildSlotIndex()
	{
		int max = 0;
		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			max = Math.max(max, slot.getSlotIdx());
		}
		EquipmentInventorySlot[] byIdx = new EquipmentInventorySlot[max + 1];
		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			byIdx[slot.getSlotIdx()] = slot;
		}
		return byIdx;
	}

	static EquipmentInventorySlot slotOf(ItemStats item)
	{
		int idx = item.getSlot();
		return idx >= 0 && idx < SLOT_BY_IDX.length ? SLOT_BY_IDX[idx] : null;
	}

	@Value
	private static class ScoredLoadout
	{
		Map<EquipmentInventorySlot, ItemStats> items;
		AttackStyle style;
		Spell spell;
		double dps;
	}

}
