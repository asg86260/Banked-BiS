package com.bankbis;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("bank-bis")
public interface BankBisConfig extends Config
{

	enum HeaderDps
	{
		SETTINGS("Optimizer settings"),
		BASE("Base"),
		PRAYER("Prayer"),
		POTTED("Potted"),
		;

		private final String label;

		HeaderDps(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "headerDps",
		name = "Title DPS",
		description = "Which DPS number each loadout's title shows: the value the optimizer "
			+ "maximized under your panel settings, or a fixed scenario (base / prayed / potted)"
	)
	default HeaderDps headerDps()
	{
		return HeaderDps.SETTINGS;
	}

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
