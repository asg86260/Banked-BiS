package com.bankbis.ui;

import com.bankbis.BankBisConfig;
import com.bankbis.RecommendationService;
import com.bankbis.bank.OwnedItemsService;
import com.bankbis.content.ContentPreset;
import com.bankbis.content.PresetCategory;
import com.bankbis.content.Target;
import com.bankbis.data.NpcStats;
import com.bankbis.data.WikiDataService;
import com.bankbis.optimizer.Loadout;
import com.bankbis.optimizer.PotionBoost;
import com.bankbis.optimizer.PrayerAssumption;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class BankBisPanel extends PluginPanel
{

	private final RecommendationService recommendationService;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final BankHighlightState highlightState;
	private final WikiDataService wikiDataService;
	private final TargetPickerState pickerState;
	private final OwnedItemsService ownedItemsService;
	private final ConfigManager configManager;
	private final BankBisConfig config;

	private final List<JToggleButton> highlightButtons = new ArrayList<>();

	// lazily built from wiki data: lowercased display name -> id / display name
	private final Map<String, Integer> monsterIdByName = new HashMap<>();
	private final Map<String, String> monsterDisplayByName = new HashMap<>();
	private final List<String> monsterNames = new ArrayList<>();

	private final JPopupMenu suggestionPopup = new JPopupMenu();
	private boolean suppressSuggestions;

	private final JComboBox<PresetCategory> categoryCombo = new JComboBox<>(PresetCategory.values());
	private final JComboBox<ContentPreset> presetCombo = new JComboBox<>();
	private final IconTextField searchField = new IconTextField();
	private final JToggleButton pickButton = new JToggleButton("Pick");
	private final JComboBox<String> partyCombo = new JComboBox<>(
		new String[]{"Auto", "1", "2", "3", "4", "5", "6", "7", "8", "16", "24", "50", "100"});
	private final JComboBox<PotionBoost> potionCombo = new JComboBox<>(PotionBoost.values());
	private final JComboBox<PrayerAssumption> prayerCombo = new JComboBox<>(PrayerAssumption.values());
	private final JButton refreshButton = new JButton("Find my best gear");
	private final JToggleButton advancedToggle = new JToggleButton("Advanced ▸");
	private final JPanel advancedHolder = new JPanel(new BorderLayout());
	private final JLabel statusLabel = new JLabel();
	private final JPanel resultsPanel = new JPanel();

	@Inject
	public BankBisPanel(RecommendationService recommendationService, ItemManager itemManager,
		SpriteManager spriteManager, BankHighlightState highlightState, WikiDataService wikiDataService,
		TargetPickerState pickerState, OwnedItemsService ownedItemsService, ConfigManager configManager,
		BankBisConfig config)
	{
		this.config = config;
		this.recommendationService = recommendationService;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		this.highlightState = highlightState;
		this.wikiDataService = wikiDataService;
		this.pickerState = pickerState;
		this.ownedItemsService = ownedItemsService;
		this.configManager = configManager;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// keep the scrollbar gutter reserved at all times so results
		// appearing/disappearing never shifts the content horizontally
		if (getScrollPane() != null)
		{
			getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		}

		JPanel controls = new JPanel(new GridLayout(0, 1, 0, 6));
		controls.setOpaque(false);

		JLabel title = new JLabel("Banked BiS");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		controls.add(title);

		categoryCombo.setFont(FontManager.getRunescapeSmallFont());
		presetCombo.setFont(FontManager.getRunescapeSmallFont());
		categoryCombo.addActionListener(e -> refreshPresetChoices());
		controls.add(categoryCombo);
		controls.add(presetCombo);

		// free-text monster search; when non-empty it takes precedence over
		// the preset dropdowns above
		searchField.setIcon(IconTextField.Icon.SEARCH);
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		suggestionPopup.setFocusable(false); // keep keystrokes in the text field
		searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
		{
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent e)
			{
				SwingUtilities.invokeLater(BankBisPanel.this::updateSuggestions);
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent e)
			{
				SwingUtilities.invokeLater(BankBisPanel.this::updateSuggestions);
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent e)
			{
			}
		});
		searchField.addActionListener(e ->
		{
			suggestionPopup.setVisible(false);
			compute();
		});

		pickButton.setFont(FontManager.getRunescapeSmallFont());
		pickButton.setFocusPainted(false);
		pickButton.setToolTipText("Then click a monster in-game to target it");
		pickButton.addActionListener(e ->
		{
			pickerState.setArmed(pickButton.isSelected());
			statusLabel.setText(pickButton.isSelected() ? "Click a monster in-game..." : "");
		});

		JPanel searchRow = new JPanel(new BorderLayout(4, 0));
		searchRow.setOpaque(false);
		searchRow.add(searchField, BorderLayout.CENTER);
		searchRow.add(pickButton, BorderLayout.EAST);
		controls.add(searchRow);

		advancedToggle.setFont(FontManager.getRunescapeSmallFont());
		advancedToggle.setFocusPainted(false);
		advancedToggle.addActionListener(e -> toggleAdvanced());
		controls.add(advancedToggle);

		refreshButton.setFont(FontManager.getRunescapeSmallFont());
		refreshButton.setFocusPainted(false);
		refreshButton.addActionListener(e -> compute());

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		// the advanced section sits between the toggle and the button; its
		// rows are added/removed on toggle so the layout collapses fully
		advancedHolder.setOpaque(false);

		JPanel bottomControls = new JPanel(new GridLayout(0, 1, 0, 6));
		bottomControls.setOpaque(false);
		bottomControls.add(refreshButton);
		bottomControls.add(statusLabel);

		JPanel north = new JPanel(new BorderLayout(0, 6));
		north.setOpaque(false);
		north.add(controls, BorderLayout.NORTH);
		north.add(advancedHolder, BorderLayout.CENTER);
		north.add(bottomControls, BorderLayout.SOUTH);
		add(north, BorderLayout.NORTH);

		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
		resultsPanel.setOpaque(false);
		add(resultsPanel, BorderLayout.CENTER);

		refreshPresetChoices();
	}

	private void toggleAdvanced()
	{
		advancedHolder.removeAll();
		if (advancedToggle.isSelected())
		{
			advancedToggle.setText("Advanced ▾");

			JPanel rows = new JPanel(new GridLayout(0, 1, 0, 4));
			rows.setOpaque(false);
			rows.add(advancedLabel("Potions"));
			potionCombo.setFont(FontManager.getRunescapeSmallFont());
			rows.add(potionCombo);
			rows.add(advancedLabel("Prayers"));
			prayerCombo.setFont(FontManager.getRunescapeSmallFont());
			rows.add(prayerCombo);
			rows.add(advancedLabel("Raid party size"));
			partyCombo.setFont(FontManager.getRunescapeSmallFont());
			rows.add(partyCombo);
			advancedHolder.add(rows, BorderLayout.NORTH);
		}
		else
		{
			advancedToggle.setText("Advanced ▸");
		}
		advancedHolder.revalidate();
		advancedHolder.repaint();
	}

	private static JLabel advancedLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
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
		Target target = resolveTarget();
		if (target == null)
		{
			return;
		}

		refreshButton.setEnabled(false);
		statusLabel.setText("Computing...");
		resultsPanel.removeAll();
		resultsPanel.revalidate();
		resultsPanel.repaint();

		PotionBoost potionBoost = (PotionBoost) potionCombo.getSelectedItem();
		PrayerAssumption prayerAssumption = (PrayerAssumption) prayerCombo.getSelectedItem();
		recommendationService.recommend(target, potionBoost, prayerAssumption).whenComplete((result, error) ->
			SwingUtilities.invokeLater(() -> render(target, result, error)));
	}

	/**
	 * The monster search wins when non-empty; otherwise the preset dropdowns.
	 * Party size comes from the advanced combo, falling back to the preset's
	 * default (1 for searched monsters).
	 */
	private Target resolveTarget()
	{
		String query = searchField.getText() == null ? "" : searchField.getText().trim();
		if (!query.isEmpty())
		{
			ensureMonsterIndex();
			if (monsterIdByName.isEmpty())
			{
				statusLabel.setText("Monster data is still loading; try again shortly.");
				return null;
			}
			String key = query.toLowerCase(Locale.ROOT);
			Integer id = monsterIdByName.get(key);
			String label = monsterDisplayByName.get(key);
			if (id == null)
			{
				List<String> matches = new ArrayList<>();
				for (String name : monsterIdByName.keySet())
				{
					if (name.contains(key))
					{
						matches.add(name);
					}
				}
				if (matches.size() != 1)
				{
					statusLabel.setText(matches.isEmpty()
						? "No monster matches '" + query + "'."
						: "Several monsters match; pick a suggestion.");
					return null;
				}
				id = monsterIdByName.get(matches.get(0));
				label = monsterDisplayByName.get(matches.get(0));
			}
			return new Target(id, label, onSlayerTask(label), partySize(1));
		}

		ContentPreset preset = (ContentPreset) presetCombo.getSelectedItem();
		if (preset == null)
		{
			return null;
		}
		Target target = Target.ofPreset(preset);
		return target.withPartySize(partySize(target.getRaidPartySize()));
	}

	/**
	 * Called from the plugin when the armed picker consumed a click on an
	 * NPC. Resolves the live NPC to a wiki entry - by game id first, then by
	 * name (the wiki keeps historical ids for some renumbered NPCs) - then
	 * runs the recommendation.
	 */
	public void setPickedTarget(int npcId, String npcName)
	{
		pickButton.setSelected(false);
		String cleanName = npcName == null ? "" : Text.removeTags(npcName);

		NpcStats stats = wikiDataService.getNpcStatsById().get(npcId);
		String display = stats != null ? stats.getDisplayName() : null;
		if (display == null && !cleanName.isEmpty())
		{
			ensureMonsterIndex();
			String key = cleanName.toLowerCase(Locale.ROOT);
			display = monsterDisplayByName.get(key);
			if (display == null)
			{
				for (Map.Entry<String, String> entry : monsterDisplayByName.entrySet())
				{
					if (entry.getKey().startsWith(key))
					{
						display = entry.getValue();
						break;
					}
				}
			}
		}

		if (display == null)
		{
			statusLabel.setText("No combat data for " + (cleanName.isEmpty() ? "that monster" : cleanName) + ".");
			return;
		}
		searchField.setText(display);
		compute();
	}

	/**
	 * Matches a searched/picked monster against the built-in Slayer plugin's
	 * tracked task so slayer-helm math applies automatically. Name-based
	 * heuristic: the task category ("Abyssal demons") is singularized and
	 * compared against the monster's base name.
	 */
	private boolean onSlayerTask(String monsterDisplay)
	{
		String task = configManager.getConfiguration("slayer", "taskName");
		if (task == null || task.isEmpty() || monsterDisplay == null)
		{
			return false;
		}
		String monster = monsterDisplay.toLowerCase(Locale.ROOT);
		int variantIdx = monster.indexOf(" (");
		if (variantIdx > 0)
		{
			monster = monster.substring(0, variantIdx);
		}
		String taskSingular = task.toLowerCase(Locale.ROOT);
		if (taskSingular.endsWith("s"))
		{
			taskSingular = taskSingular.substring(0, taskSingular.length() - 1);
		}
		return monster.contains(taskSingular) || taskSingular.contains(monster);
	}

	private int partySize(int defaultSize)
	{
		String selected = (String) partyCombo.getSelectedItem();
		return selected == null || "Auto".equals(selected) ? defaultSize : Integer.parseInt(selected);
	}

	private void ensureMonsterIndex()
	{
		if (!monsterIdByName.isEmpty())
		{
			return;
		}
		Map<Integer, NpcStats> stats = wikiDataService.getNpcStatsById();
		if (stats.isEmpty())
		{
			return;
		}
		stats.forEach((id, npc) ->
		{
			String display = npc.getDisplayName();
			if (display == null || display.isEmpty())
			{
				return;
			}
			String key = display.toLowerCase(Locale.ROOT);
			if (monsterIdByName.putIfAbsent(key, id) == null)
			{
				monsterDisplayByName.put(key, display);
				monsterNames.add(display);
			}
		});
		monsterNames.sort(String.CASE_INSENSITIVE_ORDER);
	}

	/**
	 * Type-ahead: show the best matches for the current text in a popup
	 * under the search field. Prefix matches rank above contains matches;
	 * capped at 10 rows so the popup never towers over the panel.
	 */
	private void updateSuggestions()
	{
		if (suppressSuggestions)
		{
			return;
		}
		suggestionPopup.setVisible(false);
		suggestionPopup.removeAll();

		String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
		if (query.length() < 2)
		{
			return;
		}
		ensureMonsterIndex();

		List<String> matches = new ArrayList<>();
		for (String name : monsterNames)
		{
			if (name.toLowerCase(Locale.ROOT).startsWith(query))
			{
				matches.add(name);
			}
		}
		for (String name : monsterNames)
		{
			if (matches.size() >= 10)
			{
				break;
			}
			String lower = name.toLowerCase(Locale.ROOT);
			if (!lower.startsWith(query) && lower.contains(query))
			{
				matches.add(name);
			}
		}
		if (matches.isEmpty())
		{
			return;
		}
		if (matches.size() > 10)
		{
			matches = matches.subList(0, 10);
		}

		for (String name : matches)
		{
			JMenuItem item = new JMenuItem(name);
			item.setFont(FontManager.getRunescapeSmallFont());
			item.addActionListener(e -> selectSuggestion(name));
			suggestionPopup.add(item);
		}
		suggestionPopup.show(searchField, 0, searchField.getHeight());
	}

	private void selectSuggestion(String name)
	{
		suppressSuggestions = true;
		searchField.setText(name);
		suppressSuggestions = false;
		suggestionPopup.setVisible(false);
		compute();
	}

	private void render(Target target, RecommendationService.Result result, Throwable error)
	{
		refreshButton.setEnabled(true);
		resultsPanel.removeAll();
		highlightButtons.clear();
		highlightState.clear();

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
			resultsPanel.add(warningLabel("No usable setup found for " + target.getLabel() + "."));
		}

		for (Loadout loadout : result.getLoadouts())
		{
			resultsPanel.add(loadoutSection(loadout, result));
			resultsPanel.add(Box.createVerticalStrut(10));
		}

		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	private Component warningLabel(String text)
	{
		JLabel label = new JLabel("<html>" + text + "</html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.PROGRESS_ERROR_COLOR.brighter());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private Component loadoutSection(Loadout loadout, RecommendationService.Result result)
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		section.setBorder(BorderFactory.createEmptyBorder(8, 8, 10, 8));
		section.setAlignmentX(Component.LEFT_ALIGNMENT);

		double headerDps = loadout.getDps();
		String headerSuffix = "";
		Loadout.DpsBreakdown breakdown = loadout.getBreakdown();
		if (breakdown != null)
		{
			switch (config.headerDps())
			{
				case BASE:
					headerDps = breakdown.getBase();
					headerSuffix = " base";
					break;
				case PRAYER:
					headerDps = breakdown.getPrayed();
					headerSuffix = " prayer";
					break;
				case POTTED:
					headerDps = breakdown.getPotted();
					headerSuffix = " pray+pots";
					break;
				case SETTINGS:
				default:
					break;
			}
		}

		JLabel header = new JLabel(String.format("%s  -  %.2f DPS%s", Text.titleCase(loadout.getCombatClass()), headerDps, headerSuffix));
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(ColorScheme.BRAND_ORANGE);
		header.setHorizontalAlignment(SwingConstants.CENTER);
		header.setAlignmentX(Component.CENTER_ALIGNMENT);
		section.add(header);

		if (breakdown != null)
		{
			JLabel breakdownLabel = new JLabel(String.format("base %.1f · prayer %.1f · pray+pots %.1f",
				breakdown.getBase(), breakdown.getPrayed(), breakdown.getPotted()));
			breakdownLabel.setFont(FontManager.getRunescapeSmallFont());
			breakdownLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker());
			breakdownLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			section.add(breakdownLabel);
		}

		JLabel style = new JLabel(loadout.getAttackStyle().getDisplayName());
		style.setFont(FontManager.getRunescapeSmallFont());
		style.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		style.setAlignmentX(Component.CENTER_ALIGNMENT);
		section.add(style);
		section.add(Box.createVerticalStrut(6));

		EquipmentGridPanel grid = new EquipmentGridPanel(loadout.getItems(), result.getPartyItemIds(),
			result.getGroupStorageItemIds(), ownedItemsService.getWornItemIds(), itemManager, spriteManager);
		grid.setAlignmentX(Component.CENTER_ALIGNMENT);
		section.add(grid);
		section.add(Box.createVerticalStrut(6));

		Set<Integer> loadoutItemIds = new HashSet<>();
		for (ItemStats item : loadout.getItems().values())
		{
			loadoutItemIds.add(item.getItemId());
		}

		JToggleButton highlight = new JToggleButton("Highlight in bank");
		highlight.setFont(FontManager.getRunescapeSmallFont());
		highlight.setFocusPainted(false);
		highlight.setAlignmentX(Component.CENTER_ALIGNMENT);
		highlight.addActionListener(e ->
		{
			if (highlight.isSelected())
			{
				// only one loadout highlighted at a time
				for (JToggleButton other : highlightButtons)
				{
					if (other != highlight)
					{
						other.setSelected(false);
					}
				}
				highlightState.set(loadoutItemIds);
			}
			else
			{
				highlightState.clear();
			}
		});
		highlightButtons.add(highlight);
		section.add(highlight);
		return section;
	}

	/**
	 * Called when the bank closes: the highlight is a transient aid, not a
	 * persistent marker, so drop it and reset the buttons.
	 */
	public void clearBankHighlight()
	{
		highlightState.clear();
		for (JToggleButton button : highlightButtons)
		{
			button.setSelected(false);
		}
	}

}
