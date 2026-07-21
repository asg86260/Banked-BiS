package com.bankbis;

import com.bankbis.bank.OwnedItemsService;
import com.bankbis.data.WikiDataService;
import com.bankbis.party.OwnedItemsUpdate;
import com.bankbis.party.PartyItemsService;
import com.bankbis.ui.BankBisPanel;
import com.bankbis.ui.BankHighlightOverlay;
import com.bankbis.ui.TargetPickerState;
import com.duckblade.osrs.dpscalc.calc.DpsComputeModule;
import com.google.inject.Binder;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Banked BiS",
	description = "Recommends the best gear setup you own for a chosen activity, based on your bank and group storage",
	tags = {"gear", "bis", "dps", "bank", "equipment", "loadout"}
)
public class BankBisPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private EventBus eventBus;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OwnedItemsService ownedItemsService;

	@Inject
	private WikiDataService wikiDataService;

	@Inject
	private PartyItemsService partyItemsService;

	@Inject
	private WSClient wsClient;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BankHighlightOverlay bankHighlightOverlay;

	@Inject
	private TargetPickerState targetPickerState;

	// injected lazily: the panel must be constructed in startUp(), after the
	// client has installed its look-and-feel, or its components render unstyled
	@Inject
	private Provider<BankBisPanel> panelProvider;

	private BankBisPanel panel;
	private NavigationButton navButton;

	/**
	 * While the picker is armed, put a "Select target" entry on top of the
	 * menu for the hovered NPC (wiki-lookup style): the top-left hover text
	 * shows the action, left-click runs it, and nothing goes to the server.
	 * PostMenuSort fires after the client has built and sorted the frame's
	 * menu - entries added earlier (e.g. during ClientTick) get wiped.
	 */
	@Subscribe
	public void onPostMenuSort(PostMenuSort e)
	{
		if (!targetPickerState.isArmed() || client.isMenuOpen())
		{
			return;
		}
		MenuEntry[] entries = client.getMenu().getMenuEntries();
		for (int i = entries.length - 1; i >= 0; i--)
		{
			NPC npc = entries[i].getNpc();
			if (npc != null)
			{
				client.getMenu().createMenuEntry(-1)
					.setOption("Select target")
					.setTarget(entries[i].getTarget())
					.setType(MenuAction.RUNELITE)
					.onClick(me -> pickNpc(npc));
				return;
			}
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		if (!targetPickerState.isArmed())
		{
			return;
		}
		NPC npc = e.getMenuEntry().getNpc();
		if (npc == null)
		{
			return; // not a monster click; stay armed so a misclick doesn't cancel
		}
		e.consume(); // don't attack/interact - this click was a selection
		pickNpc(npc);
	}

	private void pickNpc(NPC npc)
	{
		if (panel == null)
		{
			return;
		}
		targetPickerState.setArmed(false);
		int npcId = npc.getId();
		String npcName = npc.getName();
		SwingUtilities.invokeLater(() -> panel.setPickedTarget(npcId, npcName));
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed e)
	{
		if (e.getGroupId() == InterfaceID.BANKMAIN && panel != null)
		{
			// the highlight is a transient aid; drop it when the bank closes
			SwingUtilities.invokeLater(panel::clearBankHighlight);
		}
	}

	@Override
	public void configure(Binder binder)
	{
		binder.install(new DpsComputeModule());
	}

	@Override
	protected void startUp() throws Exception
	{
		eventBus.register(ownedItemsService);
		eventBus.register(partyItemsService);
		wsClient.registerMessage(OwnedItemsUpdate.class);
		overlayManager.add(bankHighlightOverlay);
		wikiDataService.load()
			.exceptionally(e ->
			{
				log.warn("Failed to load wiki data", e);
				return null;
			});

		panel = panelProvider.get();
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Banked BiS")
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
		eventBus.unregister(partyItemsService);
		wsClient.unregisterMessage(OwnedItemsUpdate.class);
		overlayManager.remove(bankHighlightOverlay);
		targetPickerState.setArmed(false);
		ownedItemsService.flush();
		wikiDataService.shutdown();
		clientToolbar.removeNavigation(navButton);
	}

	@Provides
	BankBisConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankBisConfig.class);
	}
}
