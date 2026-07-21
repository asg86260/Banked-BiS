package com.bankbis;

import com.bankbis.bank.OwnedItemsService;
import com.bankbis.content.RaidScaling;
import com.bankbis.content.Target;
import com.bankbis.data.NpcStats;
import com.bankbis.data.WikiDataService;
import com.bankbis.party.PartyItemsService;
import com.bankbis.optimizer.Loadout;
import com.bankbis.optimizer.LoadoutOptimizer;
import com.bankbis.optimizer.OptimizeRequest;
import com.bankbis.optimizer.PotionBoost;
import com.bankbis.optimizer.PrayerAssumption;
import com.bankbis.optimizer.PrayerUnlocks;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.duckblade.osrs.dpscalc.calc.model.Prayer;
import com.duckblade.osrs.dpscalc.calc.model.Skills;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;

/**
 * Glue between the live client (skills, owned items) and the optimizer.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class RecommendationService
{

	private final Client client;
	private final ClientThread clientThread;
	private final OwnedItemsService ownedItemsService;
	private final WikiDataService wikiDataService;
	private final PartyItemsService partyItemsService;
	private final LoadoutOptimizer optimizer;
	private final ScheduledExecutorService executor;
	private final BankBisConfig config;

	@Value
	public static class Result
	{
		List<Loadout> loadouts;
		List<String> warnings;

		/**
		 * Item ids only available via a party member's shared bank.
		 */
		Set<Integer> partyItemIds;

		/**
		 * Item ids that live only in group storage (not bank/inventory/worn),
		 * so the player has to fetch them before use.
		 */
		Set<Integer> groupStorageItemIds;
	}

	/**
	 * Skills and prayer unlocks captured together on the client thread.
	 * Null when not logged in.
	 */
	@Value
	private static class ClientSnapshot
	{
		Skills skills;
		PrayerUnlocks prayerUnlocks;
	}

	public CompletableFuture<Result> recommend(Target target, PotionBoost potionBoost, PrayerAssumption prayerAssumption)
	{
		CompletableFuture<ClientSnapshot> snapshotFuture = new CompletableFuture<>();
		clientThread.invoke(() ->
		{
			try
			{
				snapshotFuture.complete(snapshotClient());
			}
			catch (Exception e)
			{
				snapshotFuture.completeExceptionally(e);
			}
		});
		return snapshotFuture
			.orTimeout(15, TimeUnit.SECONDS) // never leave the panel wedged on "Computing..."
			.thenApplyAsync(snapshot -> compute(
				target,
				snapshot == null ? null : applyBoosts(snapshot.getSkills(), potionBoost),
				snapshot == null ? PrayerUnlocks.ALL : snapshot.getPrayerUnlocks(),
				potionBoost,
				prayerAssumption), executor);
	}

	private static Skills applyBoosts(Skills skills, PotionBoost potionBoost)
	{
		if (skills == null)
		{
			return null;
		}
		Map<Skill, Integer> boosts = potionBoost.boostsFor(skills.getLevels());
		if (boosts == null)
		{
			return skills; // CURRENT: keep live boosts
		}
		return Skills.builder().levels(skills.getLevels()).boosts(boosts).build();
	}

	private ClientSnapshot snapshotClient()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		Map<Skill, Integer> boosts = new EnumMap<>(Skill.class);
		for (Skill s : Skill.values())
		{
			int real = client.getRealSkillLevel(s);
			levels.put(s, real);
			boosts.put(s, client.getBoostedSkillLevel(s) - real);
		}
		Skills skills = Skills.builder().levels(levels).boosts(boosts).build();

		// Knight Waves completion (state 8) gates both Piety and Chivalry,
		// and can only be done after King's Ransom
		PrayerUnlocks unlocks = new PrayerUnlocks(
			client.getVarbitValue(VarbitID.KR_KNIGHTWAVES_STATE) >= 8,
			client.getVarbitValue(VarbitID.PRAYER_RIGOUR_UNLOCKED) == 1,
			client.getVarbitValue(VarbitID.PRAYER_AUGURY_UNLOCKED) == 1);

		return new ClientSnapshot(skills, unlocks);
	}

	private Result compute(Target target, Skills skills, PrayerUnlocks prayerUnlocks, PotionBoost potionBoost, PrayerAssumption prayerAssumption)
	{
		List<String> warnings = new ArrayList<>();

		if (skills == null)
		{
			return new Result(Collections.emptyList(), List.of("Log in to get recommendations."), Collections.emptySet(), Collections.emptySet());
		}

		Map<Integer, ItemStats> wikiItems = wikiDataService.getItemStatsById();
		if (wikiItems.isEmpty())
		{
			return new Result(Collections.emptyList(), List.of("Equipment data is still loading; try again shortly."), Collections.emptySet(), Collections.emptySet());
		}

		NpcStats npcStats = wikiDataService.getNpcStatsById().get(target.getNpcId());
		if (npcStats == null)
		{
			return new Result(Collections.emptyList(), List.of("No monster data for " + target.getLabel() + "."), Collections.emptySet(), Collections.emptySet());
		}
		npcStats = RaidScaling.scale(npcStats, target);

		if (!ownedItemsService.hasBankSnapshot())
		{
			warnings.add("Bank not scanned yet - open your bank once so all your items are known.");
		}

		Map<Integer, Integer> ownedQuantities = config.includeGroupStorage()
			? ownedItemsService.getOwnedQuantities()
			: ownedItemsService.getOwnedQuantitiesExcluding(OwnedItemsService.Source.GROUP_STORAGE);

		Set<Integer> partyItemIds = new HashSet<>();
		if (config.sharePartyBanks())
		{
			partyItemsService.getPartyQuantities().forEach((id, qty) ->
			{
				if (!ownedQuantities.containsKey(id))
				{
					partyItemIds.add(id);
				}
				ownedQuantities.merge(id, qty, Integer::sum);
			});
		}

		List<ItemStats> ownedEquipment = new ArrayList<>();
		for (Integer itemId : ownedQuantities.keySet())
		{
			ItemStats stats = wikiItems.get(itemId);
			if (stats != null)
			{
				ownedEquipment.add(stats);
			}
		}

		if (ownedEquipment.isEmpty())
		{
			warnings.add("No equippable items found yet.");
			return new Result(Collections.emptyList(), warnings, Collections.emptySet(), Collections.emptySet());
		}

		OptimizeRequest request = OptimizeRequest.builder()
			.ownedEquipment(ownedEquipment)
			.playerSkills(skills)
			.target(npcStats)
			.onSlayerTask(target.isOnSlayerTask())
			.raidPartySize(target.getRaidPartySize())
			.prayerAssumption(prayerAssumption)
			.prayerUnlocks(prayerUnlocks)
			.build();

		Set<Integer> groupStorageItemIds = config.includeGroupStorage()
			? ownedItemsService.getGroupOnlyItemIds()
			: Collections.emptySet();

		List<Loadout> loadouts = new ArrayList<>();
		for (Loadout loadout : optimizer.optimize(request))
		{
			loadouts.add(withBreakdown(loadout, request, potionBoost, prayerAssumption));
		}
		return new Result(loadouts, warnings, partyItemIds, groupStorageItemIds);
	}

	/**
	 * Re-evaluates the chosen gear under fixed scenarios so the panel can
	 * show what potting/praying is worth: base (nothing), prayed (prayers
	 * only), potted (prayers + potion). "Potted" uses the selected potion,
	 * or a super set when the selection is CURRENT/NONE, so the line is
	 * meaningful even when optimizing from live unpotted stats.
	 */
	private Loadout withBreakdown(Loadout loadout, OptimizeRequest request, PotionBoost potionBoost, PrayerAssumption prayerAssumption)
	{
		Skills skills = request.getPlayerSkills();
		PotionBoost pottedBoost = potionBoost == PotionBoost.CURRENT || potionBoost == PotionBoost.NONE
			? PotionBoost.SUPER_SET
			: potionBoost;

		Skills unboosted = Skills.builder().levels(skills.getLevels()).boosts(PotionBoost.NONE.boostsFor(skills.getLevels())).build();
		Skills potted = Skills.builder().levels(skills.getLevels()).boosts(pottedBoost.boostsFor(skills.getLevels())).build();

		Integer prayerLevel = skills.getLevels().get(Skill.PRAYER);
		Set<Prayer> prayers = prayerAssumption.prayersFor(loadout.getCombatClass(),
			prayerLevel == null ? 0 : prayerLevel, request.getPrayerUnlocks());

		double base = optimizer.evaluateLoadout(request.toBuilder().playerSkills(unboosted).build(), loadout, Collections.emptySet());
		double prayed = optimizer.evaluateLoadout(request.toBuilder().playerSkills(unboosted).build(), loadout, prayers);
		double pottedDps = optimizer.evaluateLoadout(request.toBuilder().playerSkills(potted).build(), loadout, prayers);

		if (base < 0 || prayed < 0 || pottedDps < 0)
		{
			return loadout;
		}
		return loadout.toBuilder().breakdown(new Loadout.DpsBreakdown(base, prayed, pottedDps)).build();
	}

}
