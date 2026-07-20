package com.bankbis.content;

import com.bankbis.data.MonsterJson;
import com.bankbis.testutil.TestFixtures;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ContentPresetTest
{

	private static Set<Integer> knownMonsterIds;

	@BeforeAll
	static void loadFixture()
	{
		knownMonsterIds = TestFixtures.loadMonsterJson(ContentPresetTest.class, "monsters-all-presets.json")
			.stream().map(MonsterJson::getId).collect(Collectors.toSet());
	}

	@Test
	void everyPresetMonsterIdExistsInWikiData()
	{
		for (ContentPreset preset : ContentPreset.values())
		{
			for (int id : preset.getMonsterIds())
			{
				assertTrue(knownMonsterIds.contains(id),
					preset.name() + " references unknown monster id " + id);
			}
		}
	}

	@Test
	void presetLabelsAreUnique()
	{
		Set<String> labels = new HashSet<>();
		for (ContentPreset preset : ContentPreset.values())
		{
			assertTrue(labels.add(preset.getLabel()), "duplicate label " + preset.getLabel());
		}
	}

	@Test
	void everyCategoryHasAtLeastOnePreset()
	{
		Set<PresetCategory> used = new HashSet<>();
		for (ContentPreset preset : ContentPreset.values())
		{
			used.add(preset.getCategory());
		}
		assertEquals(PresetCategory.values().length, used.size());
	}

}
