package com.bankbis.ui;

import com.bankbis.BankBisConfig;
import com.bankbis.RecommendationService;
import com.bankbis.bank.OwnedItemsService;
import com.bankbis.content.Target;
import com.bankbis.data.NpcStats;
import com.bankbis.data.WikiDataService;
import com.bankbis.optimizer.CombatClass;
import com.bankbis.optimizer.Loadout;
import com.duckblade.osrs.dpscalc.calc.model.AttackStyle;
import com.duckblade.osrs.dpscalc.calc.model.AttackType;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.laf.RuneLiteLAF;
import net.runelite.client.util.AsyncBufferedImage;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Live panel preview: run this main() (VS Code "Run" link above it) to
 * open the panel in a window with mocked services and fake data - no
 * client launch needed. The Find button, type-ahead search, and card
 * actions all work against the fake data. PanelPreviewTest reuses this
 * setup to render the screenshot PNG.
 */
public class PanelPreviewApp
{

	public static void main(String[] args)
	{
		SwingUtilities.invokeLater(() ->
		{
			try
			{
				UIManager.setLookAndFeel(new RuneLiteLAF());
				BankBisPanel panel = createPanel();
				panel.render(sampleTarget(), sampleResult(), null);

				JFrame frame = new JFrame("Banked BiS preview");
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frame.add(panel.getWrappedPanel());
				frame.setSize(265, 900);
				frame.setLocationByPlatform(true);
				frame.setVisible(true);
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
	}

	static BankBisPanel createPanel()
	{
		ClientThread clientThread = mock(ClientThread.class);

		ItemManager itemManager = mock(ItemManager.class);
		when(itemManager.getImage(anyInt())).thenAnswer(inv ->
		{
			// stand-in item art: a rounded amber blob like a generic item
			AsyncBufferedImage img = new AsyncBufferedImage(clientThread, 36, 32, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			g.setColor(new Color(0xB8860B));
			g.fillRoundRect(6, 4, 24, 24, 8, 8);
			g.setColor(new Color(0x8B6914));
			g.drawRoundRect(6, 4, 24, 24, 8, 8);
			g.dispose();
			return img;
		});

		SpriteManager spriteManager = mock(SpriteManager.class);
		doAnswer(inv ->
		{
			int spriteId = inv.getArgument(0);
			Consumer<BufferedImage> consumer = inv.getArgument(2);
			BufferedImage sprite = new BufferedImage(26, 23, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = sprite.createGraphics();
			if (spriteId == 1359) // red damage hitsplat: stand in for the real sprite
			{
				g.setColor(new Color(0xC81E1E));
				g.fillOval(0, 0, 26, 22);
			}
			else
			{
				g.setColor(new Color(0x55FFFFFF, true));
				g.drawOval(5, 3, 16, 16);
			}
			g.dispose();
			consumer.accept(sprite);
			return null;
		}).when(spriteManager).getSpriteAsync(anyInt(), anyInt(), any(Consumer.class));

		RecommendationService recommendationService = mock(RecommendationService.class);
		when(recommendationService.recommend(any(), any(), any()))
			.thenReturn(CompletableFuture.completedFuture(sampleResult()));

		WikiDataService wikiDataService = mock(WikiDataService.class);
		when(wikiDataService.getNpcStatsById()).thenReturn(sampleMonsters());

		OwnedItemsService ownedItemsService = mock(OwnedItemsService.class);
		when(ownedItemsService.getWornItemIds()).thenReturn(Set.of(1001, 1002));
		when(ownedItemsService.getOwnedQuantities()).thenReturn(Collections.emptyMap());

		ConfigManager configManager = mock(ConfigManager.class);
		BankBisConfig config = mock(BankBisConfig.class);
		when(config.headerDps()).thenReturn(BankBisConfig.HeaderDps.SETTINGS);

		return new BankBisPanel(recommendationService, itemManager, spriteManager,
			new BankHighlightState(), wikiDataService, new TargetPickerState(), ownedItemsService,
			configManager, config, mock(BankFilterService.class));
	}

	static Target sampleTarget()
	{
		return Target.builder().npcId(2042).label("Zulrah (Serpentine)").build();
	}

	static RecommendationService.Result sampleResult()
	{
		return new RecommendationService.Result(
			List.of(
				loadout(CombatClass.RANGED, "Rapid", 11.02, 7.81, 9.43, 11.02),
				loadout(CombatClass.MELEE, "Aggressive", 8.54, 6.02, 7.33, 8.54)),
			List.of("Bank not scanned yet - open your bank once so all your items are known."),
			Set.of(1004),  // party outline demo
			Set.of(1003)); // group storage badge demo
	}

	static Map<Integer, NpcStats> sampleMonsters()
	{
		Map<Integer, NpcStats> monsters = new HashMap<>();
		int id = 2000;
		for (String name : new String[]{
			"Zulrah (Serpentine)", "Zulrah (Magma)", "Zulrah (Tanzanite)",
			"Vorkath", "Kraken", "Man", "Woman", "Lizardman shaman", "General Graardor"})
		{
			monsters.put(++id, NpcStats.builder().displayName(name).build());
		}
		return monsters;
	}

	private static Loadout loadout(CombatClass combatClass, String styleName, double dps, double base, double prayed, double potted)
	{
		Map<EquipmentInventorySlot, ItemStats> items = new EnumMap<>(EquipmentInventorySlot.class);
		int id = 1000;
		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			if (slot == EquipmentInventorySlot.SHIELD && combatClass == CombatClass.MELEE)
			{
				continue; // leave a slot empty to show the placeholder sprite
			}
			if (slot == EquipmentInventorySlot.AMMO && combatClass == CombatClass.MELEE)
			{
				continue;
			}
			items.put(slot, ItemStats.builder().itemId(++id).name("Example item " + id).build());
		}
		return Loadout.builder()
			.combatClass(combatClass)
			.items(items)
			.attackStyle(AttackStyle.builder().displayName(styleName).attackType(AttackType.SLASH).build())
			.dps(dps)
			.maxHit((int) Math.round(dps * 4))
			.accuracy(0.62)
			.breakdown(new Loadout.DpsBreakdown(base, prayed, potted))
			.build();
	}

}
