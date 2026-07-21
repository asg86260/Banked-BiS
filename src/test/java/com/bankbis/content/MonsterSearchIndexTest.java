package com.bankbis.content;

import com.bankbis.data.NpcStats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonsterSearchIndexTest
{

	private MonsterSearchIndex index;

	@BeforeEach
	void setUp()
	{
		Map<Integer, NpcStats> stats = new LinkedHashMap<>();
		stats.put(2042, npc("Zulrah (Serpentine)"));
		stats.put(2043, npc("Zulrah (Magma)"));
		stats.put(494, npc("Kraken"));
		stats.put(3021, npc("Man"));
		stats.put(3022, npc("Woman"));
		stats.put(6766, npc("Lizardman shaman"));
		stats.put(8059, npc("Vorkath"));
		index = new MonsterSearchIndex();
		index.build(stats);
	}

	private static NpcStats npc(String name)
	{
		return NpcStats.builder().displayName(name).build();
	}

	@Test
	void exactMatchResolvesCaseInsensitively()
	{
		MonsterSearchIndex.Resolution r = index.resolve("KRAKEN");
		assertTrue(r.isMatch());
		assertEquals(494, (int) r.getNpcId());
		assertEquals("Kraken", r.getDisplayName());
	}

	@Test
	void exactMatchWinsEvenWhenOthersContainIt()
	{
		// "man" is contained in Woman, Lizardman shaman - exact must win
		MonsterSearchIndex.Resolution r = index.resolve("man");
		assertTrue(r.isMatch());
		assertEquals(3021, (int) r.getNpcId());
	}

	@Test
	void uniqueSubstringResolves()
	{
		MonsterSearchIndex.Resolution r = index.resolve("vork");
		assertTrue(r.isMatch());
		assertEquals(8059, (int) r.getNpcId());
	}

	@Test
	void multipleMatchesAreAmbiguous()
	{
		MonsterSearchIndex.Resolution r = index.resolve("zulrah");
		assertFalse(r.isMatch());
		assertTrue(r.isAmbiguous());
	}

	@Test
	void noMatchesIsNone()
	{
		MonsterSearchIndex.Resolution r = index.resolve("tzkal-zuk");
		assertFalse(r.isMatch());
		assertFalse(r.isAmbiguous());
	}

	@Test
	void suggestionsRankPrefixBeforeContains()
	{
		List<String> suggestions = index.suggest("man", 10);
		assertEquals("Man", suggestions.get(0)); // prefix match first
		assertTrue(suggestions.contains("Woman"));
		assertTrue(suggestions.contains("Lizardman shaman"));
	}

	@Test
	void suggestionsRespectLimit()
	{
		assertEquals(1, index.suggest("man", 1).size());
	}

	@Test
	void exactPickerNameResolvesToDisplay()
	{
		assertEquals("Kraken", index.displayForName("kraken"));
		// live NPC named "Zulrah" prefix-matches one of the variant entries
		String zulrah = index.displayForName("Zulrah");
		assertTrue(zulrah != null && zulrah.startsWith("Zulrah ("));
		assertNull(index.displayForName("Nonexistent"));
	}

	@Test
	void emptyIndexResolvesNothing()
	{
		MonsterSearchIndex empty = new MonsterSearchIndex();
		assertTrue(empty.isEmpty());
		assertFalse(empty.resolve("kraken").isMatch());
		assertTrue(empty.suggest("kraken", 10).isEmpty());
	}

}
