package com.bankbis.ui;

import java.util.Collections;
import java.util.Set;
import javax.inject.Singleton;

/**
 * The set of item ids currently being highlighted in the bank, driven by
 * the per-loadout toggle buttons in the panel. Empty means no highlight.
 * Written from the Swing thread, read from the render thread.
 */
@Singleton
public class BankHighlightState
{

	private volatile Set<Integer> highlightedItemIds = Collections.emptySet();

	public Set<Integer> getHighlightedItemIds()
	{
		return highlightedItemIds;
	}

	public void set(Set<Integer> itemIds)
	{
		highlightedItemIds = Collections.unmodifiableSet(itemIds);
	}

	public void clear()
	{
		highlightedItemIds = Collections.emptySet();
	}

}
