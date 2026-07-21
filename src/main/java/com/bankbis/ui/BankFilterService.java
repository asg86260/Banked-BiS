package com.bankbis.ui;

import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;

/**
 * Filters the open bank down to a loadout via the core Bank Tags plugin's
 * public API: a virtual tag is registered (never persisted to the user's
 * saved tags) and opened as the active bank view. Falls back silently when
 * Bank Tags is unavailable - the highlight overlay still outlines items.
 */
@Slf4j
@Singleton
public class BankFilterService
{

	private static final String TAG = "banked-bis";

	private final ClientThread clientThread;
	private final TagManager tagManager;
	private final BankTagsService bankTagsService;
	private final ItemManager itemManager;

	@Inject
	BankFilterService(ClientThread clientThread, TagManager tagManager, BankTagsService bankTagsService, ItemManager itemManager)
	{
		this.clientThread = clientThread;
		this.tagManager = tagManager;
		this.bankTagsService = bankTagsService;
		this.itemManager = itemManager;
	}

	public void show(Set<Integer> itemIds)
	{
		clientThread.invoke(() ->
		{
			try
			{
				tagManager.registerTag(TAG, itemId -> itemIds.contains(itemManager.canonicalize(itemId)));
				bankTagsService.openBankTag(TAG, BankTagsService.OPTION_NO_LAYOUT | BankTagsService.OPTION_HIDE_TAG_NAME);
			}
			catch (Exception e)
			{
				log.debug("Bank Tags unavailable; falling back to outline overlay", e);
			}
		});
	}

	public void clear()
	{
		clientThread.invoke(() ->
		{
			try
			{
				if (TAG.equals(bankTagsService.getActiveTag()))
				{
					bankTagsService.closeBankTag();
				}
				tagManager.unregisterTag(TAG);
			}
			catch (Exception e)
			{
				log.debug("Failed to clear bank tag filter", e);
			}
		});
	}

}
