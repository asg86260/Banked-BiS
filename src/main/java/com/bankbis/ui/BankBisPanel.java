package com.bankbis.ui;

import com.bankbis.RecommendationService;
import com.bankbis.content.ContentPreset;
import com.bankbis.content.PresetCategory;
import com.bankbis.optimizer.Loadout;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

@Slf4j
@Singleton
public class BankBisPanel extends PluginPanel
{

	private static final List<EquipmentInventorySlot> DISPLAY_ORDER = List.of(
		EquipmentInventorySlot.WEAPON,
		EquipmentInventorySlot.SHIELD,
		EquipmentInventorySlot.AMMO,
		EquipmentInventorySlot.HEAD,
		EquipmentInventorySlot.CAPE,
		EquipmentInventorySlot.AMULET,
		EquipmentInventorySlot.BODY,
		EquipmentInventorySlot.LEGS,
		EquipmentInventorySlot.GLOVES,
		EquipmentInventorySlot.BOOTS,
		EquipmentInventorySlot.RING
	);

	private final RecommendationService recommendationService;
	private final ItemManager itemManager;

	private final JComboBox<PresetCategory> categoryCombo = new JComboBox<>(PresetCategory.values());
	private final JComboBox<ContentPreset> presetCombo = new JComboBox<>();
	private final JButton refreshButton = new JButton("Find my best gear");
	private final JLabel statusLabel = new JLabel();
	private final JPanel resultsPanel = new JPanel();

	@Inject
	public BankBisPanel(RecommendationService recommendationService, ItemManager itemManager)
	{
		this.recommendationService = recommendationService;
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel controls = new JPanel(new GridLayout(0, 1, 0, 6));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Bank BiS");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		controls.add(title);

		categoryCombo.addActionListener(e -> refreshPresetChoices());
		controls.add(categoryCombo);
		controls.add(presetCombo);

		refreshButton.addActionListener(e -> compute());
		controls.add(refreshButton);

		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		controls.add(statusLabel);

		add(controls, BorderLayout.NORTH);

		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
		resultsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(resultsPanel, BorderLayout.CENTER);

		refreshPresetChoices();
	}

	private void refreshPresetChoices()
	{
		PresetCategory category = (PresetCategory) categoryCombo.getSelectedItem();
		presetCombo.removeAllItems();
		for (ContentPreset preset : ContentPreset.values())
		{
			if (preset.getCategory() == category)
			{
				presetCombo.addItem(preset);
			}
		}
	}

	private void compute()
	{
		ContentPreset preset = (ContentPreset) presetCombo.getSelectedItem();
		if (preset == null)
		{
			return;
		}

		refreshButton.setEnabled(false);
		statusLabel.setText("Computing...");
		resultsPanel.removeAll();
		resultsPanel.revalidate();
		resultsPanel.repaint();

		recommendationService.recommend(preset).whenComplete((result, error) ->
			SwingUtilities.invokeLater(() -> render(preset, result, error)));
	}

	private void render(ContentPreset preset, RecommendationService.Result result, Throwable error)
	{
		refreshButton.setEnabled(true);
		resultsPanel.removeAll();

		if (error != null)
		{
			log.warn("Recommendation failed", error);
			statusLabel.setText("Something went wrong; see logs.");
			resultsPanel.revalidate();
			resultsPanel.repaint();
			return;
		}

		statusLabel.setText("");
		for (String warning : result.getWarnings())
		{
			resultsPanel.add(warningLabel(warning));
			resultsPanel.add(Box.createVerticalStrut(6));
		}

		if (result.getLoadouts().isEmpty() && result.getWarnings().isEmpty())
		{
			resultsPanel.add(warningLabel("No usable setup found for " + preset.getLabel() + "."));
		}

		for (Loadout loadout : result.getLoadouts())
		{
			resultsPanel.add(loadoutSection(loadout, result.getPartyItemIds()));
			resultsPanel.add(Box.createVerticalStrut(10));
		}

		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	private Component warningLabel(String text)
	{
		JLabel label = new JLabel("<html>" + text + "</html>");
		label.setForeground(ColorScheme.PROGRESS_ERROR_COLOR.brighter());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private Component loadoutSection(Loadout loadout, java.util.Set<Integer> partyItemIds)
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		section.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		section.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel header = new JLabel(String.format("%s  -  %.2f DPS", loadout.getCombatClass(), loadout.getDps()));
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(ColorScheme.BRAND_ORANGE);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		section.add(header);

		JLabel style = new JLabel(loadout.getAttackStyle().getDisplayName());
		style.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		style.setAlignmentX(Component.LEFT_ALIGNMENT);
		section.add(style);
		section.add(Box.createVerticalStrut(4));

		Map<EquipmentInventorySlot, ItemStats> items = loadout.getItems();
		for (EquipmentInventorySlot slot : DISPLAY_ORDER)
		{
			ItemStats item = items.get(slot);
			if (item != null)
			{
				section.add(itemRow(item, partyItemIds.contains(item.getItemId())));
			}
		}
		return section;
	}

	private Component itemRow(ItemStats item, boolean fromParty)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 1));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(26, 24));
		itemManager.getImage(item.getItemId()).addTo(icon);
		row.add(icon);

		JLabel name = new JLabel(fromParty ? item.getName() + " (party)" : item.getName());
		name.setForeground(fromParty ? ColorScheme.PROGRESS_INPROGRESS_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
		row.add(name);
		return row;
	}

}
