package com.bankbis.party;

import com.bankbis.BankBisConfig;
import com.bankbis.bank.OwnedItemsChanged;
import com.bankbis.bank.OwnedItemsService;
import com.bankbis.data.WikiDataService;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;

/**
 * Opt-in sharing of owned equippable items with the player's RuneLite
 * party, so recommendations can draw on gear a group member could lend
 * (e.g. group ironmen planning a raid). Only item ids and quantities of
 * equippable items are shared, and only while the config toggle is on.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class PartyItemsService
{

	private static final long SEND_DEBOUNCE_MS = 10_000;

	private final PartyService partyService;
	private final OwnedItemsService ownedItemsService;
	private final WikiDataService wikiDataService;
	private final BankBisConfig config;
	private final ScheduledExecutorService executor;

	private final Map<Long, Map<Integer, Integer>> memberItems = new HashMap<>();
	private ScheduledFuture<?> pendingSend;

	@Subscribe
	public void onOwnedItemsChanged(OwnedItemsChanged e)
	{
		scheduleSend();
	}

	@Subscribe
	public void onUserJoin(UserJoin e)
	{
		// a joiner (including ourselves) needs everyone's current snapshot
		scheduleSend();
	}

	@Subscribe
	public void onUserPart(UserPart e)
	{
		synchronized (memberItems)
		{
			memberItems.remove(e.getMemberId());
		}
	}

	@Subscribe
	public void onPartyChanged(PartyChanged e)
	{
		synchronized (memberItems)
		{
			memberItems.clear();
		}
		scheduleSend();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!"bank-bis".equals(e.getGroup()))
		{
			return;
		}
		if (config.sharePartyBanks())
		{
			scheduleSend();
		}
		else
		{
			synchronized (memberItems)
			{
				memberItems.clear();
			}
		}
	}

	@Subscribe
	public void onOwnedItemsUpdate(OwnedItemsUpdate e)
	{
		if (!config.sharePartyBanks())
		{
			return;
		}
		PartyMember local = partyService.getLocalMember();
		if (local != null && local.getMemberId() == e.getMemberId())
		{
			return; // our own broadcast echoed back
		}
		Map<Integer, Integer> items = e.getItems();
		if (items == null)
		{
			return;
		}
		synchronized (memberItems)
		{
			memberItems.put(e.getMemberId(), new HashMap<>(items));
		}
		log.debug("Received {} shared items from party member {}", items.size(), e.getMemberId());
	}

	/**
	 * All items shared by other party members, quantities summed.
	 */
	public Map<Integer, Integer> getPartyQuantities()
	{
		if (!config.sharePartyBanks())
		{
			return Collections.emptyMap();
		}
		Map<Integer, Integer> merged = new HashMap<>();
		synchronized (memberItems)
		{
			for (Map<Integer, Integer> items : memberItems.values())
			{
				items.forEach((id, qty) -> merged.merge(id, qty, Integer::sum));
			}
		}
		return merged;
	}

	private synchronized void scheduleSend()
	{
		if (pendingSend != null && !pendingSend.isDone())
		{
			return;
		}
		pendingSend = executor.schedule(this::sendSnapshot, SEND_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
	}

	private void sendSnapshot()
	{
		if (!config.sharePartyBanks() || !partyService.isInParty())
		{
			return;
		}
		Map<Integer, Integer> equippable = equippableOwned();
		if (equippable.isEmpty())
		{
			return;
		}
		partyService.send(new OwnedItemsUpdate(equippable));
		log.debug("Shared {} equippable items with party", equippable.size());
	}

	/**
	 * Only equippable items are worth sharing; this also keeps the party
	 * message small (a maxed bank is a few hundred entries).
	 */
	private Map<Integer, Integer> equippableOwned()
	{
		Map<Integer, ?> known = wikiDataService.getItemStatsById();
		if (known.isEmpty())
		{
			return Collections.emptyMap();
		}
		Map<Integer, Integer> equippable = new HashMap<>();
		ownedItemsService.getOwnedQuantities().forEach((id, qty) ->
		{
			if (known.containsKey(id))
			{
				equippable.put(id, qty);
			}
		});
		return equippable;
	}

}
