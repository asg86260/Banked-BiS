package com.bankbis.content;

import com.bankbis.data.NpcStats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Value;

/**
 * Name index over the wiki monster list backing the panel's type-ahead
 * search: exact and unique-substring resolution, plus ranked suggestions
 * (prefix matches before contains matches).
 */
public class MonsterSearchIndex
{

	/** How a query resolved: a unique match, nothing, or several monsters. */
	@Value
	public static class Resolution
	{
		Integer npcId;
		String displayName;
		boolean ambiguous;

		public boolean isMatch()
		{
			return npcId != null;
		}
	}

	private static final Resolution NONE = new Resolution(null, null, false);
	private static final Resolution AMBIGUOUS = new Resolution(null, null, true);

	private final Map<String, Integer> idByLower = new HashMap<>();
	private final Map<String, String> displayByLower = new HashMap<>();
	private final List<String> sortedNames = new ArrayList<>();

	public boolean isEmpty()
	{
		return idByLower.isEmpty();
	}

	/**
	 * Builds the index once; duplicate display names keep their first id.
	 * No-op when already built or when stats are empty.
	 */
	public void build(Map<Integer, NpcStats> statsById)
	{
		if (!isEmpty() || statsById.isEmpty())
		{
			return;
		}
		statsById.forEach((id, npc) ->
		{
			String display = npc.getDisplayName();
			if (display == null || display.isEmpty())
			{
				return;
			}
			String key = display.toLowerCase(Locale.ROOT);
			if (idByLower.putIfAbsent(key, id) == null)
			{
				displayByLower.put(key, display);
				sortedNames.add(display);
			}
		});
		sortedNames.sort(String.CASE_INSENSITIVE_ORDER);
	}

	public boolean hasExact(String query)
	{
		return idByLower.containsKey(normalize(query));
	}

	public Resolution resolve(String query)
	{
		String key = normalize(query);
		if (key.isEmpty())
		{
			return NONE;
		}
		Integer exact = idByLower.get(key);
		if (exact != null)
		{
			return new Resolution(exact, displayByLower.get(key), false);
		}
		String single = null;
		for (String name : idByLower.keySet())
		{
			if (name.contains(key))
			{
				if (single != null)
				{
					return AMBIGUOUS;
				}
				single = name;
			}
		}
		if (single == null)
		{
			return NONE;
		}
		return new Resolution(idByLower.get(single), displayByLower.get(single), false);
	}

	/** Display names matching the query: prefix matches first, capped. */
	public List<String> suggest(String query, int limit)
	{
		String key = normalize(query);
		List<String> matches = new ArrayList<>();
		if (key.isEmpty())
		{
			return matches;
		}
		for (String name : sortedNames)
		{
			if (name.toLowerCase(Locale.ROOT).startsWith(key))
			{
				matches.add(name);
			}
		}
		for (String name : sortedNames)
		{
			if (matches.size() >= limit)
			{
				break;
			}
			String lower = name.toLowerCase(Locale.ROOT);
			if (!lower.startsWith(key) && lower.contains(key))
			{
				matches.add(name);
			}
		}
		return matches.size() > limit ? new ArrayList<>(matches.subList(0, limit)) : matches;
	}

	/**
	 * Display name for a live NPC name: exact match, else first prefix
	 * match; null when unknown. Used by the in-game picker fallback.
	 */
	public String displayForName(String name)
	{
		String key = normalize(name);
		if (key.isEmpty())
		{
			return null;
		}
		String exact = displayByLower.get(key);
		if (exact != null)
		{
			return exact;
		}
		for (Map.Entry<String, String> entry : displayByLower.entrySet())
		{
			if (entry.getKey().startsWith(key))
			{
				return entry.getValue();
			}
		}
		return null;
	}

	public Integer idFor(String displayName)
	{
		return idByLower.get(normalize(displayName));
	}

	private static String normalize(String s)
	{
		return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
	}

}
