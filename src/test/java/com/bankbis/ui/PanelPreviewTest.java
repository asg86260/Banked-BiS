package com.bankbis.ui;

import com.bankbis.BankBisConfig;
import com.bankbis.RecommendationService;
import com.bankbis.bank.OwnedItemsService;
import com.bankbis.content.Target;
import com.bankbis.data.WikiDataService;
import com.bankbis.optimizer.CombatClass;
import com.bankbis.optimizer.Loadout;
import com.duckblade.osrs.dpscalc.calc.model.AttackStyle;
import com.duckblade.osrs.dpscalc.calc.model.AttackType;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Renders the panel with fake data to build/panel-preview.png so UI work
 * can be reviewed without launching the client. Skipped when headless.
 */
class PanelPreviewTest
{

	private static final File OUTPUT = new File("build/panel-preview.png");

	@Test
	void renderPreview() throws Exception
	{
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

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
			Consumer<BufferedImage> consumer = inv.getArgument(2);
			BufferedImage sprite = new BufferedImage(26, 23, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = sprite.createGraphics();
			g.setColor(new Color(0x55FFFFFF, true));
			g.drawOval(5, 3, 16, 16);
			g.dispose();
			consumer.accept(sprite);
			return null;
		}).when(spriteManager).getSpriteAsync(anyInt(), anyInt(), any(Consumer.class));

		RecommendationService recommendationService = mock(RecommendationService.class);
		WikiDataService wikiDataService = mock(WikiDataService.class);
		when(wikiDataService.getNpcStatsById()).thenReturn(Collections.emptyMap());
		OwnedItemsService ownedItemsService = mock(OwnedItemsService.class);
		when(ownedItemsService.getWornItemIds()).thenReturn(Set.of(1001, 1002));
		ConfigManager configManager = mock(ConfigManager.class);
		BankBisConfig config = mock(BankBisConfig.class);
		when(config.headerDps()).thenReturn(BankBisConfig.HeaderDps.SETTINGS);

		Target target = new Target(2042, "Zulrah (Serpentine)", false, 1);
		RecommendationService.Result result = new RecommendationService.Result(
			List.of(
				loadout(CombatClass.RANGED, "Rapid", 11.02, 7.81, 9.43, 11.02),
				loadout(CombatClass.MELEE, "Aggressive", 8.54, 6.02, 7.33, 8.54)),
			List.of("Bank not scanned yet - open your bank once so all your items are known."),
			Set.of(1004),  // party outline demo
			Set.of(1003)); // group storage badge demo

		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				UIManager.setLookAndFeel(new RuneLiteLAF());
				BankBisPanel panel = new BankBisPanel(recommendationService, itemManager, spriteManager,
					new BankHighlightState(), wikiDataService, new TargetPickerState(), ownedItemsService,
					configManager, config, new Gson());
				panel.render(target, result, null);

				JFrame frame = new JFrame();
				frame.setUndecorated(true);
				frame.add(panel);
				frame.pack();
				frame.setSize(242, Math.max(650, panel.getPreferredSize().height + 20));
				frame.validate();

				BufferedImage shot = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics2D g = shot.createGraphics();
				frame.getContentPane().paint(g);
				g.dispose();
				OUTPUT.getParentFile().mkdirs();
				ImageIO.write(shot, "png", OUTPUT);
				frame.dispose();
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});

		assertTrue(OUTPUT.exists());
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
			.breakdown(new Loadout.DpsBreakdown(base, prayed, potted))
			.build();
	}

}
