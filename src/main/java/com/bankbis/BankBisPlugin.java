package com.bankbis;

import com.bankbis.bank.OwnedItemsService;
import com.bankbis.data.WikiDataService;
import com.duckblade.osrs.dpscalc.calc.DpsComputeModule;
import com.google.inject.Binder;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
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
	private BankBisConfig config;

	@Inject
	private EventBus eventBus;

	@Inject
	private OwnedItemsService ownedItemsService;

	@Inject
	private WikiDataService wikiDataService;

	@Override
	public void configure(Binder binder)
	{
		binder.install(new DpsComputeModule());
	}

	@Override
	protected void startUp() throws Exception
	{
		eventBus.register(ownedItemsService);
		wikiDataService.load()
			.exceptionally(e ->
			{
				log.warn("Failed to load wiki data", e);
				return null;
			});
		log.debug("Bank BiS started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		eventBus.unregister(ownedItemsService);
		log.debug("Bank BiS stopped");
	}

	@Provides
	BankBisConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankBisConfig.class);
	}
}
