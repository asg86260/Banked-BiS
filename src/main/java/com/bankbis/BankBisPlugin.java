package com.bankbis;

import com.bankbis.bank.OwnedItemsService;
import com.bankbis.data.WikiDataService;
import com.bankbis.ui.BankBisPanel;
import com.duckblade.osrs.dpscalc.calc.DpsComputeModule;
import com.google.inject.Binder;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Bank BiS",
	description = "Recommends the best gear setup you own for a chosen activity, based on your bank and group storage",
	tags = {"gear", "bis", "dps", "bank", "equipment", "loadout"}
)
public class BankBisPlugin extends Plugin
{
	@Inject
	private BankBisConfig config;

	@Inject
	private EventBus eventBus;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OwnedItemsService ownedItemsService;

	@Inject
	private WikiDataService wikiDataService;

	@Inject
	private BankBisPanel panel;

	private NavigationButton navButton;

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

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Bank BiS")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		eventBus.unregister(ownedItemsService);
		ownedItemsService.flush();
		clientToolbar.removeNavigation(navButton);
	}

	@Provides
	BankBisConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankBisConfig.class);
	}
}
