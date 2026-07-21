package com.bankbis;

import com.bankbis.bank.OwnedItemsService;
import com.bankbis.content.RaidType;
import com.bankbis.content.Target;
import com.bankbis.data.NpcStats;
import com.bankbis.data.WikiDataService;
import com.bankbis.optimizer.CombatClass;
import com.bankbis.optimizer.Loadout;
import com.bankbis.optimizer.LoadoutOptimizer;
import com.bankbis.optimizer.OptimizeRequest;
import com.bankbis.optimizer.PotionBoost;
import com.bankbis.optimizer.PrayerAssumption;
import com.bankbis.party.PartyItemsService;
import com.duckblade.osrs.dpscalc.calc.model.AttackStyle;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.duckblade.osrs.dpscalc.calc.model.Skills;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationServiceTest
{

	@Mock
	private Client client;

	@Mock
	private ClientThread clientThread;

	@Mock
	private OwnedItemsService ownedItemsService;

	@Mock
	private WikiDataService wikiDataService;

	@Mock
	private PartyItemsService partyItemsService;

	@Mock
	private LoadoutOptimizer optimizer;

	@Mock
	private BankBisConfig config;

	private ScheduledExecutorService executor;
	private RecommendationService service;

	@BeforeEach
	void setUp()
	{
		executor = new ScheduledThreadPoolExecutor(1);
		doAnswer(inv ->
		{
			((Runnable) inv.getArgument(0)).run();
			return null;
		}).when(clientThread).invoke(any(Runnable.class));

		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		for (Skill s : Skill.values())
		{
			when(client.getRealSkillLevel(s)).thenReturn(99);
			when(client.getBoostedSkillLevel(s)).thenReturn(99);
		}
		when(client.getVarbitValue(VarbitID.PRAYER_RIGOUR_UNLOCKED)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.PRAYER_AUGURY_UNLOCKED)).thenReturn(0);
		when(client.getVarbitValue(VarbitID.KR_KNIGHTWAVES_STATE)).thenReturn(8);

		when(config.includeGroupStorage()).thenReturn(true);
		when(config.sharePartyBanks()).thenReturn(false);

		when(wikiDataService.getItemStatsById()).thenReturn(Map.of(4151, item(4151)));
		when(wikiDataService.getNpcStatsById()).thenReturn(Map.of(494, npc(200, 200)));
		// production returns a fresh mutable merge; the party path mutates it
		when(ownedItemsService.getOwnedQuantities()).thenAnswer(inv -> new java.util.HashMap<>(Map.of(4151, 1)));
		when(ownedItemsService.hasBankSnapshot()).thenReturn(true);
		when(ownedItemsService.getGroupOnlyItemIds()).thenReturn(Set.of(4151));

		service = new RecommendationService(client, clientThread, ownedItemsService,
			wikiDataService, partyItemsService, optimizer, executor, config);
	}

	@AfterEach
	void tearDown()
	{
		executor.shutdownNow();
	}

	private static ItemStats item(int id)
	{
		return ItemStats.builder().itemId(id).name("Item " + id).build();
	}

	private static NpcStats npc(int def, int magic)
	{
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.DEFENCE, def);
		levels.put(Skill.MAGIC, magic);
		return NpcStats.builder()
			.displayName("Test monster")
			.speed(4)
			.skills(Skills.builder().levels(levels).build())
			.build();
	}

	private static Loadout loadout()
	{
		Map<EquipmentInventorySlot, ItemStats> items = new EnumMap<>(EquipmentInventorySlot.class);
		items.put(EquipmentInventorySlot.WEAPON, item(4151));
		return Loadout.builder()
			.combatClass(CombatClass.MELEE)
			.items(items)
			.attackStyle(AttackStyle.builder().displayName("Aggressive").build())
			.dps(7.5)
			.build();
	}

	private static Target target()
	{
		return Target.builder().npcId(494).label("Kraken").build();
	}

	private RecommendationService.Result recommend(Target target) throws Exception
	{
		return service.recommend(target, PotionBoost.NONE, PrayerAssumption.AUTO).get(5, TimeUnit.SECONDS);
	}

	@Test
	void notLoggedInWarns() throws Exception
	{
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		RecommendationService.Result result = recommend(target());
		assertTrue(result.getLoadouts().isEmpty());
		assertEquals(List.of("Log in to get recommendations."), result.getWarnings());
	}

	@Test
	void missingWikiDataWarns() throws Exception
	{
		when(wikiDataService.getItemStatsById()).thenReturn(Map.of());
		RecommendationService.Result result = recommend(target());
		assertTrue(result.getLoadouts().isEmpty());
		assertTrue(result.getWarnings().get(0).contains("still loading"));
	}

	@Test
	void unknownMonsterWarns() throws Exception
	{
		RecommendationService.Result result = recommend(Target.builder().npcId(123456).label("Mystery").build());
		assertTrue(result.getWarnings().get(0).contains("No monster data for Mystery"));
	}

	@Test
	void attachesBreakdownFromScenarioEvaluations() throws Exception
	{
		when(optimizer.optimize(any())).thenReturn(List.of(loadout()));
		when(optimizer.evaluateLoadout(any(), any(), any())).thenReturn(5.0, 6.0, 7.0);

		RecommendationService.Result result = recommend(target());
		Loadout.DpsBreakdown breakdown = result.getLoadouts().get(0).getBreakdown();
		assertNotNull(breakdown);
		assertEquals(5.0, breakdown.getBase());
		assertEquals(6.0, breakdown.getPrayed());
		assertEquals(7.0, breakdown.getPotted());
		assertEquals(Set.of(4151), result.getGroupStorageItemIds());
	}

	@Test
	void omitsBreakdownWhenScenarioEvaluationFails() throws Exception
	{
		when(optimizer.optimize(any())).thenReturn(List.of(loadout()));
		when(optimizer.evaluateLoadout(any(), any(), any())).thenReturn(-1.0);

		RecommendationService.Result result = recommend(target());
		assertTrue(result.getLoadouts().get(0).getBreakdown() == null);
	}

	@Test
	void appliesCoxScalingToTargetStats() throws Exception
	{
		when(optimizer.optimize(any())).thenReturn(List.of());
		Target cox = Target.builder().npcId(494).label("Test").raid(RaidType.COX).raidPartySize(5).build();
		recommend(cox);

		ArgumentCaptor<OptimizeRequest> request = ArgumentCaptor.forClass(OptimizeRequest.class);
		org.mockito.Mockito.verify(optimizer).optimize(request.capture());
		// party 5: def 200 -> 208 (defensive pct 104)
		assertEquals(208, (int) request.getValue().getTarget().getSkills().getLevels().get(Skill.DEFENCE));
	}

	@Test
	void snapshotsPrayerUnlocksFromVarbits() throws Exception
	{
		when(optimizer.optimize(any())).thenReturn(List.of());
		recommend(target());

		ArgumentCaptor<OptimizeRequest> request = ArgumentCaptor.forClass(OptimizeRequest.class);
		org.mockito.Mockito.verify(optimizer).optimize(request.capture());
		assertTrue(request.getValue().getPrayerUnlocks().isRigour());
		assertFalse(request.getValue().getPrayerUnlocks().isAugury());
		assertTrue(request.getValue().getPrayerUnlocks().isPietyChivalry());
	}

	@Test
	void potionAssumptionReplacesLiveBoosts() throws Exception
	{
		// live stats are boosted +10, but NONE must zero the boosts
		when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(109);
		when(optimizer.optimize(any())).thenReturn(List.of());
		recommend(target());

		ArgumentCaptor<OptimizeRequest> request = ArgumentCaptor.forClass(OptimizeRequest.class);
		org.mockito.Mockito.verify(optimizer).optimize(request.capture());
		Map<Skill, Integer> boosts = request.getValue().getPlayerSkills().getBoosts();
		assertTrue(boosts == null || boosts.getOrDefault(Skill.STRENGTH, 0) == 0);
	}

	@Test
	void partyItemsMergeWhenSharingEnabled() throws Exception
	{
		when(config.sharePartyBanks()).thenReturn(true);
		when(partyItemsService.getPartyQuantities()).thenReturn(Map.of(9999, 1));
		when(wikiDataService.getItemStatsById()).thenReturn(Map.of(4151, item(4151), 9999, item(9999)));
		when(optimizer.optimize(any())).thenReturn(List.of());

		RecommendationService.Result result = recommend(target());
		assertEquals(Set.of(9999), result.getPartyItemIds());

		ArgumentCaptor<OptimizeRequest> request = ArgumentCaptor.forClass(OptimizeRequest.class);
		org.mockito.Mockito.verify(optimizer).optimize(request.capture());
		assertEquals(2, request.getValue().getOwnedEquipment().size());
	}

}
