package com.bankbis.data;

import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;

class WikiDataServiceTest
{

	private static MonsterJson variant(String version)
	{
		MonsterJson m = new MonsterJson();
		m.setId(12191);
		m.setName("Duke Sucellus");
		m.setVersion(version);
		return m;
	}

	@Test
	void prefersPostQuestOverAwakened()
	{
		MonsterJson awakened = variant("Awakened, Awake");
		MonsterJson postQuest = variant("Post-quest, Awake");
		assertSame(postQuest, WikiDataService.preferredVariant(awakened, postQuest));
		assertSame(postQuest, WikiDataService.preferredVariant(postQuest, awakened));
	}

	@Test
	void prefersPostQuestOverDuringQuest()
	{
		MonsterJson quest = variant("Quest, Awake");
		MonsterJson postQuest = variant("Post-quest, Awake");
		assertSame(postQuest, WikiDataService.preferredVariant(quest, postQuest));
		assertSame(postQuest, WikiDataService.preferredVariant(postQuest, quest));
	}

	@Test
	void keepsFirstOnTie()
	{
		MonsterJson a = variant("");
		MonsterJson b = variant("");
		assertSame(a, WikiDataService.preferredVariant(a, b));
	}

}
