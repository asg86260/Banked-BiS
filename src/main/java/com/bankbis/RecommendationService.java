package com.bankbis;

import com.bankbis.bank.OwnedItemsService;
import com.bankbis.content.ContentPreset;
import com.bankbis.data.NpcStats;
import com.bankbis.data.WikiDataService;
import com.bankbis.party.PartyItemsService;
import com.bankbis.optimizer.Loadout;
import com.bankbis.optimizer.LoadoutOptimizer;
import com.bankbis.optimizer.OptimizeRequest;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
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
	}

	public CompletableFuture<Result> recommend(ContentPreset preset)
	{
		CompletableFuture<Skills> skillsFuture = new CompletableFuture<>();
		clientThread.invoke(() ->
		{
			try
			{
				skillsFuture.complete(snapshotSkills());
			}
			catch (Exception e)
			{
				skillsFuture.completeExceptionally(e);
			}
		});
		return skillsFuture
			.orTimeout(15, TimeUnit.SECONDS) // never leave the panel wedged on "Computing..."
			.thenApplyAsync(skills -> compute(preset, skills), executor);
	}

	private Skills snapshotSkills()
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
		return Skills.builder().levels(levels).boosts(boosts).build();
	}

	private Result compute(ContentPreset preset, Skills skills)
	{
		List<String> warnings = new ArrayList<>();

		if (skills == null)
		{
			return new Result(Collections.emptyList(), List.of("Log in to get recommendations."), Collections.emptySet());
		}

		Map<Integer, ItemStats> wikiItems = wikiDataService.getItemStatsById();
		if (wikiItems.isEmpty())
		{
			return new Result(Collections.emptyList(), List.of("Equipment data is still loading; try again shortly."), Collections.emptySet());
		}

		NpcStats target = wikiDataService.getNpcStatsById().get(preset.getPrimaryMonsterId());
		if (target == null)
		{
			return new Result(Collections.emptyList(), List.of("No monster data for " + preset.getLabel() + "."), Collections.emptySet());
		}

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
			return new Result(Collections.emptyList(), warnings, Collections.emptySet());
		}

		OptimizeRequest request = OptimizeRequest.builder()
			.ownedEquipment(ownedEquipment)
			.playerSkills(skills)
			.target(target)
			.onSlayerTask(preset.isOnSlayerTask())
			.raidPartySize(preset.getRaidPartySize())
			.build();

		return new Result(optimizer.optimize(request), warnings, partyItemIds);
	}

}
