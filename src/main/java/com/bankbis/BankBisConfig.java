package com.bankbis;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("bank-bis")
public interface BankBisConfig extends Config
{

	@ConfigItem(
		keyName = "includeGroupStorage",
		name = "Include group storage",
		description = "Count items in group ironman storage as owned when recommending gear"
	)
	default boolean includeGroupStorage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sharePartyBanks",
		name = "Share banks with party",
		description = "Share your equippable items (ids and quantities only) with your RuneLite party via the "
			+ "RuneLite party service, and include party members' shared items in recommendations. "
			+ "Items a party member owns are marked '(party)'."
	)
	default boolean sharePartyBanks()
	{
		return false;
	}

}
