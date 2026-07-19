package com.bankbis;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Bank BiS",
	description = "Recommends the best gear setup you own for a chosen activity, based on your bank and group storage",
	tags = {"gear", "bis", "dps", "bank", "equipment"}
)
public class BankBisPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private BankBisConfig config;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Bank BiS started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Bank BiS stopped");
	}

	@Provides
	BankBisConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankBisConfig.class);
	}
}
