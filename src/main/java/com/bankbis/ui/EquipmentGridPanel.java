package com.bankbis.ui;

import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

/**
 * Renders a loadout in the same slot arrangement as the in-game
 * worn-equipment tab. Empty slots show the game's placeholder silhouette;
 * hover a slot for the item name. Items lent by party members get a colored
 * outline; items sitting in group storage get a "G" badge.
 */
class EquipmentGridPanel extends JPanel
{
	private static final int SLOT_SIZE = 40;

	private final Map<EquipmentInventorySlot, ItemStats> items;
	private final Set<Integer> partyItemIds;
	private final Set<Integer> groupStorageItemIds;
	private final Set<Integer> wornItemIds;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;

	EquipmentGridPanel(Map<EquipmentInventorySlot, ItemStats> items, Set<Integer> partyItemIds,
		Set<Integer> groupStorageItemIds, Set<Integer> wornItemIds, ItemManager itemManager, SpriteManager spriteManager)
	{
		this.items = items;
		this.partyItemIds = partyItemIds;
		this.groupStorageItemIds = groupStorageItemIds;
		this.wornItemIds = wornItemIds;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;

		setLayout(new GridBagLayout());
		setOpaque(false);

		addSlot(EquipmentInventorySlot.HEAD, SpriteID.Wornicons.HEAD, 1, 0);
		addSlot(EquipmentInventorySlot.CAPE, SpriteID.Wornicons.CAPE, 0, 1);
		addSlot(EquipmentInventorySlot.AMULET, SpriteID.Wornicons.NECK, 1, 1);
		addSlot(EquipmentInventorySlot.AMMO, SpriteID.Wornicons.AMMUNITION, 2, 1);
		addSlot(EquipmentInventorySlot.WEAPON, SpriteID.Wornicons.WEAPON, 0, 2);
		addSlot(EquipmentInventorySlot.BODY, SpriteID.Wornicons.TORSO, 1, 2);
		addSlot(EquipmentInventorySlot.SHIELD, SpriteID.Wornicons.SHIELD, 2, 2);
		addSlot(EquipmentInventorySlot.LEGS, SpriteID.Wornicons.LEGS, 1, 3);
		addSlot(EquipmentInventorySlot.GLOVES, SpriteID.Wornicons.HANDS, 0, 4);
		addSlot(EquipmentInventorySlot.BOOTS, SpriteID.Wornicons.FEET, 1, 4);
		addSlot(EquipmentInventorySlot.RING, SpriteID.Wornicons.RING, 2, 4);
	}

	private void addSlot(EquipmentInventorySlot slot, int placeholderSpriteId, int col, int row)
	{
		ItemStats item = items.get(slot);
		boolean fromParty = item != null && partyItemIds.contains(item.getItemId());
		boolean fromGroup = item != null && groupStorageItemIds.contains(item.getItemId());
		boolean worn = item != null && wornItemIds.contains(item.getItemId());

		SlotPanel slotPanel = new SlotPanel(fromGroup, worn);
		slotPanel.setBorder(BorderFactory.createLineBorder(
			fromParty ? ColorScheme.PROGRESS_INPROGRESS_COLOR : ColorScheme.DARK_GRAY_COLOR));

		JLabel icon = new JLabel();
		if (item != null)
		{
			String tooltip = item.getName();
			if (fromParty)
			{
				tooltip += " (party)";
			}
			else if (fromGroup)
			{
				tooltip += " (group storage)";
			}
			if (worn)
			{
				tooltip += " (worn)";
			}
			slotPanel.setToolTipText(tooltip);
			icon.setToolTipText(tooltip);
			itemManager.getImage(item.getItemId()).addTo(icon);
		}
		else
		{
			spriteManager.getSpriteAsync(placeholderSpriteId, 0, sprite ->
				SwingUtilities.invokeLater(() ->
					icon.setIcon(new ImageIcon(ImageUtil.alphaOffset(sprite, 0.5f)))));
		}
		slotPanel.add(icon);

		GridBagConstraints c = new GridBagConstraints();
		c.gridx = col;
		c.gridy = row;
		c.insets = new Insets(2, 2, 2, 2);
		add(slotPanel, c);
	}

	private static class SlotPanel extends JPanel
	{
		private final boolean groupBadge;
		private final boolean wornBadge;

		SlotPanel(boolean groupBadge, boolean wornBadge)
		{
			super(new GridBagLayout());
			this.groupBadge = groupBadge;
			this.wornBadge = wornBadge;
			setPreferredSize(new Dimension(SLOT_SIZE, SLOT_SIZE));
			setMinimumSize(new Dimension(SLOT_SIZE, SLOT_SIZE));
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
		}

		@Override
		protected void paintChildren(Graphics g)
		{
			super.paintChildren(g);
			if (wornBadge)
			{
				// already wearing this piece - no swap needed
				g.setColor(ColorScheme.PROGRESS_COMPLETE_COLOR);
				g.fillOval(3, 3, 5, 5);
			}
			if (groupBadge)
			{
				g.setFont(FontManager.getRunescapeSmallFont());
				int x = getWidth() - 10;
				int y = getHeight() - 3;
				g.setColor(java.awt.Color.BLACK);
				g.drawString("G", x + 1, y + 1);
				g.setColor(ColorScheme.PROGRESS_COMPLETE_COLOR);
				g.drawString("G", x, y);
			}
		}
	}
}
