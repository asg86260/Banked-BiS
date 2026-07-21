package com.bankbis.ui;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines the items of the loadout the player chose to highlight, so they
 * can pull their setup without hunting through tabs. Only active while a
 * loadout's "Highlight in bank" toggle is on; cleared when the bank closes.
 */
@Singleton
public class BankHighlightOverlay extends WidgetItemOverlay
{

	private final ItemManager itemManager;
	private final BankHighlightState highlightState;

	@Inject
	BankHighlightOverlay(ItemManager itemManager, BankHighlightState highlightState)
	{
		this.itemManager = itemManager;
		this.highlightState = highlightState;
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		Set<Integer> highlighted = highlightState.getHighlightedItemIds();
		if (highlighted.isEmpty() || !highlighted.contains(itemManager.canonicalize(itemId)))
		{
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		graphics.setColor(ColorScheme.BRAND_ORANGE);
		graphics.draw(bounds);
	}

}
