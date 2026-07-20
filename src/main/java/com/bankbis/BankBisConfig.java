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

}
