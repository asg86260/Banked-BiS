package com.bankbis.ui;

import com.bankbis.BankBisConfig;
import com.bankbis.RecommendationService;
import com.bankbis.bank.OwnedItemsService;
import com.bankbis.content.ContentPreset;
import com.bankbis.content.MonsterSearchIndex;
import com.bankbis.content.PresetCategory;
import com.bankbis.content.RaidType;
import com.bankbis.content.SlayerTaskMatcher;
import com.bankbis.content.Target;
import com.bankbis.data.NpcStats;
import com.bankbis.data.WikiDataService;
import com.bankbis.optimizer.Loadout;
import com.bankbis.optimizer.PotionBoost;
import com.bankbis.optimizer.PrayerAssumption;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.AbstractButton;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
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
import net.runelite.client.util.LinkBrowser;
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
	private final BankFilterService bankFilterService;

	private final List<JToggleButton> highlightButtons = new ArrayList<>();

	// lazily built from wiki data on first use
	private final MonsterSearchIndex searchIndex = new MonsterSearchIndex();

	private final JPopupMenu suggestionPopup = new JPopupMenu();
	private boolean suppressSuggestions;
	private boolean refreshingPresets;

	private final JComboBox<PresetCategory> categoryCombo = new JComboBox<>(PresetCategory.values());
	private final JComboBox<ContentPreset> presetCombo = new JComboBox<>();
	private final IconTextField searchField = new IconTextField();
	private final JToggleButton pickButton = new JToggleButton("Pick");
	private final JComboBox<String> partyCombo = new JComboBox<>(
		new String[]{"Auto", "1", "2", "3", "4", "5", "6", "7", "8", "16", "24", "50", "100"});
	private final JCheckBox cmCheck = new JCheckBox("Challenge Mode");
	private final JSpinner invoSpinner = new JSpinner(new SpinnerNumberModel(150, 0, 600, 5));
	private final JPanel raidHolder = new JPanel(new BorderLayout());
	private final JComboBox<PotionBoost> potionCombo = new JComboBox<>(PotionBoost.values());
	private final JComboBox<PrayerAssumption> prayerCombo = new JComboBox<>(PrayerAssumption.values());
	private final JButton refreshButton = new PrimaryButton("Find my best gear");
	private final JToggleButton advancedToggle = new JToggleButton("ASSUMPTIONS  ▸");
	private final JPanel advancedHolder = new JPanel(new BorderLayout());
	private final JLabel statusLabel = new JLabel();
	private final JPanel resultsPanel = new JPanel();

	@Inject
	public BankBisPanel(RecommendationService recommendationService, ItemManager itemManager,
		SpriteManager spriteManager, BankHighlightState highlightState, WikiDataService wikiDataService,
		TargetPickerState pickerState, OwnedItemsService ownedItemsService, ConfigManager configManager,
		BankBisConfig config, BankFilterService bankFilterService)
	{
		this.config = config;
		this.bankFilterService = bankFilterService;
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

		JPanel stack = new JPanel();
		stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
		stack.setOpaque(false);

		JLabel title = new JLabel("Banked BiS");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		stack.add(fullWidth(title));
		stack.add(Box.createVerticalStrut(10));
		stack.add(fullWidth(sectionHeader("Target")));
		stack.add(Box.createVerticalStrut(6));

		categoryCombo.setFont(FontManager.getRunescapeSmallFont());
		presetCombo.setFont(FontManager.getRunescapeSmallFont());
		categoryCombo.addActionListener(e ->
		{
			refreshPresetChoices();
			updateRaidOptions();
			setSearchTextQuietly("");
		});
		// touching the preset dropdowns is a statement of intent: drop any
		// lingering search text so it stops overriding the selection
		presetCombo.addActionListener(e ->
		{
			if (!refreshingPresets)
			{
				setSearchTextQuietly("");
			}
		});
		raidHolder.setOpaque(false);
		cmCheck.setFont(FontManager.getRunescapeSmallFont());
		cmCheck.setOpaque(false);
		invoSpinner.setFont(FontManager.getRunescapeSmallFont());
		partyCombo.setFont(FontManager.getRunescapeSmallFont());

		stack.add(fullWidth(categoryCombo));
		stack.add(Box.createVerticalStrut(4));
		stack.add(fullWidth(presetCombo));
		// raid options hang directly off the boss dropdown they refine,
		// before the alternative search path below
		stack.add(fullWidthTall(raidHolder));
		stack.add(Box.createVerticalStrut(4));
		stack.add(fullWidth(orDivider()));
		stack.add(Box.createVerticalStrut(4));

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
		stack.add(fullWidth(searchRow));

		advancedHolder.setOpaque(false);

		// "Assumptions" is a collapsible section header, not a boxed button
		advancedToggle.setFont(FontManager.getRunescapeSmallFont());
		advancedToggle.setFocusPainted(false);
		advancedToggle.setContentAreaFilled(false);
		advancedToggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		advancedToggle.setIcon(new ImageIcon(sectionTick()));
		advancedToggle.setIconTextGap(6);
		advancedToggle.setHorizontalAlignment(SwingConstants.LEFT);
		advancedToggle.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		advancedToggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		advancedToggle.addActionListener(e -> toggleAdvanced());
		stack.add(Box.createVerticalStrut(10));
		stack.add(fullWidth(advancedToggle));
		stack.add(fullWidthTall(advancedHolder));

		refreshButton.setFocusPainted(false);
		refreshButton.addActionListener(e -> compute());
		stack.add(Box.createVerticalStrut(12));
		stack.add(fullWidth(refreshButton));
		stack.add(Box.createVerticalStrut(6));

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
		stack.add(fullWidth(statusLabel));

		add(stack, BorderLayout.NORTH);
		updateRaidOptions();

		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
		resultsPanel.setOpaque(false);
		add(resultsPanel, BorderLayout.CENTER);

		refreshPresetChoices();
	}

	/**
	 * Shows the options relevant to the selected raid: party size (and CM)
	 * for CoX where they scale defence, party size for ToB, invocation
	 * level for ToA. Hidden for non-raid categories.
	 */
	private void updateRaidOptions()
	{
		raidHolder.removeAll();
		PresetCategory category = (PresetCategory) categoryCombo.getSelectedItem();
		RaidType raid = category == null ? RaidType.NONE : category.getRaidType();
		if (raid != RaidType.NONE)
		{
			JPanel rows = new JPanel(new GridLayout(0, 1, 0, 4));
			rows.setOpaque(false);
			if (raid == RaidType.COX || raid == RaidType.TOB)
			{
				// CoX scales up to huge parties; ToB is capped at 5
				String[] options = raid == RaidType.COX
					? new String[]{"Auto", "1", "2", "3", "4", "5", "6", "7", "8", "16", "24", "50", "100"}
					: new String[]{"Auto", "1", "2", "3", "4", "5"};
				String previous = (String) partyCombo.getSelectedItem();
				partyCombo.setModel(new DefaultComboBoxModel<>(options));
				if (previous != null && java.util.Arrays.asList(options).contains(previous))
				{
					partyCombo.setSelectedItem(previous);
				}
				rows.add(labeledRow("Party size", partyCombo));
			}
			if (raid == RaidType.COX)
			{
				cmCheck.setToolTipText("Challenge Mode: +50% monster defence");
				rows.add(cmCheck);
			}
			if (raid == RaidType.TOA)
			{
				invoSpinner.setToolTipText("Invocation level scales monster HP only - it does not change which gear is best");
				rows.add(labeledRow("Invocation", invoSpinner));
			}
			raidHolder.add(indentBlock(rows), BorderLayout.NORTH);
		}
		raidHolder.revalidate();
		raidHolder.repaint();
	}

	// ---- layout helpers ------------------------------------------------

	/** Full-width row in the vertical stack, height fixed at preferred. */
	private static Component fullWidth(JComponent c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		return c;
	}

	/** Full-width row whose height changes at runtime (collapsible holders). */
	private static Component fullWidthTall(JComponent c)
	{
		c.setAlignmentX(Component.LEFT_ALIGNMENT);
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		return c;
	}

	/** Orange tick + muted uppercase label marking a section. */
	private static JLabel sectionHeader(String text)
	{
		JLabel label = new JLabel(text.toUpperCase(Locale.ROOT));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setIcon(new ImageIcon(sectionTick()));
		label.setIconTextGap(6);
		return label;
	}

	private static BufferedImage sectionTick()
	{
		BufferedImage img = new BufferedImage(3, 10, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(ColorScheme.BRAND_ORANGE);
		g.fillRect(0, 0, 3, 10);
		g.dispose();
		return img;
	}

	/**
	 * Indents content with a left rule: visually attaches contextual rows
	 * to the control above them (raid options to the boss, assumption rows
	 * to their header).
	 */
	private static JPanel indentBlock(JPanel content)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createEmptyBorder(6, 2, 0, 0),
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 2, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
				BorderFactory.createEmptyBorder(0, 8, 0, 0))));
		wrapper.add(content, BorderLayout.NORTH);
		return wrapper;
	}

	/** The panel's single filled primary action. */
	private static class PrimaryButton extends JButton
	{
		PrimaryButton(String text)
		{
			super(text);
			setFont(FontManager.getRunescapeBoldFont());
			setForeground(new Color(0x2B2317));
			setContentAreaFilled(false);
			setFocusPainted(false);
			setBorder(BorderFactory.createEmptyBorder(7, 0, 7, 0));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Color base = ColorScheme.BRAND_ORANGE;
			if (!isEnabled())
			{
				base = ColorScheme.MEDIUM_GRAY_COLOR;
			}
			else if (getModel().isPressed())
			{
				base = base.darker();
			}
			else if (getModel().isRollover())
			{
				base = base.brighter();
			}
			g.setColor(base);
			g.fillRect(0, 0, getWidth(), getHeight());
			super.paintComponent(g);
		}
	}

	/**
	 * A muted "-- or --" separator between the preset dropdowns and the
	 * free-text search, since the two are alternative ways to pick a target.
	 */
	private static JPanel orDivider()
	{
		JLabel label = new JLabel("or");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker());
		label.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

		JPanel divider = new JPanel(new GridBagLayout());
		divider.setOpaque(false);
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		divider.add(new JSeparator(), c);
		c.weightx = 0;
		divider.add(label, c);
		c.weightx = 1;
		divider.add(new JSeparator(), c);
		return divider;
	}

	private static JPanel labeledRow(String text, Component field)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.add(label, BorderLayout.WEST);
		row.add(field, BorderLayout.CENTER);
		return row;
	}

	private void toggleAdvanced()
	{
		advancedHolder.removeAll();
		if (advancedToggle.isSelected())
		{
			advancedToggle.setText("ASSUMPTIONS  ▾");

			JPanel rows = new JPanel(new GridLayout(0, 1, 0, 4));
			rows.setOpaque(false);
			rows.add(advancedLabel("Potions"));
			potionCombo.setFont(FontManager.getRunescapeSmallFont());
			rows.add(potionCombo);
			rows.add(advancedLabel("Prayers"));
			prayerCombo.setFont(FontManager.getRunescapeSmallFont());
			rows.add(prayerCombo);
			advancedHolder.add(indentBlock(rows), BorderLayout.NORTH);
		}
		else
		{
			advancedToggle.setText("ASSUMPTIONS  ▸");
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
		refreshingPresets = true;
		try
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
		finally
		{
			refreshingPresets = false;
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
			if (searchIndex.isEmpty())
			{
				statusLabel.setText("Monster data is still loading; try again shortly.");
				return null;
			}
			MonsterSearchIndex.Resolution resolution = searchIndex.resolve(query);
			if (!resolution.isMatch())
			{
				statusLabel.setText(resolution.isAmbiguous()
					? "Several monsters match; pick a suggestion."
					: "No monster matches '" + query + "'.");
				return null;
			}
			return Target.builder()
				.npcId(resolution.getNpcId())
				.label(resolution.getDisplayName())
				.onSlayerTask(onSlayerTask(resolution.getDisplayName()))
				.raidPartySize(partySize(1))
				.build();
		}

		ContentPreset preset = (ContentPreset) presetCombo.getSelectedItem();
		if (preset == null)
		{
			return null;
		}
		Target target = Target.ofPreset(preset);
		return target.toBuilder()
			.raidPartySize(partySize(target.getRaidPartySize()))
			.coxChallengeMode(cmCheck.isSelected())
			.toaInvocationLevel((Integer) invoSpinner.getValue())
			.build();
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
			display = searchIndex.displayForName(cleanName);
		}

		if (display == null)
		{
			statusLabel.setText("No combat data for " + (cleanName.isEmpty() ? "that monster" : cleanName) + ".");
			return;
		}
		setSearchTextQuietly(display);
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
		return SlayerTaskMatcher.matches(configManager.getConfiguration("slayer", "taskName"), monsterDisplay);
	}

	private int partySize(int defaultSize)
	{
		String selected = (String) partyCombo.getSelectedItem();
		return selected == null || "Auto".equals(selected) ? defaultSize : Integer.parseInt(selected);
	}

	private void ensureMonsterIndex()
	{
		searchIndex.build(wikiDataService.getNpcStatsById());
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

		String query = searchField.getText() == null ? "" : searchField.getText().trim();
		if (query.length() < 2)
		{
			return;
		}
		ensureMonsterIndex();
		if (searchIndex.hasExact(query))
		{
			return; // text already names a monster exactly; nothing to suggest
		}

		List<String> matches = searchIndex.suggest(query, 10);
		if (matches.isEmpty())
		{
			return;
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
		setSearchTextQuietly(name);
		compute();
	}

	/**
	 * Sets the search text without popping type-ahead suggestions. The
	 * document listener defers to invokeLater, so the suppress flag must be
	 * cleared in a later queued task, not synchronously after setText.
	 */
	private void setSearchTextQuietly(String text)
	{
		suppressSuggestions = true;
		searchField.setText(text);
		SwingUtilities.invokeLater(() -> suppressSuggestions = false);
		suggestionPopup.setVisible(false);
	}

	// package-private for PanelPreviewTest, which renders the panel to a
	// PNG so layout changes can be reviewed without launching the client
	void render(Target target, RecommendationService.Result result, Throwable error)
	{
		refreshButton.setEnabled(true);
		resultsPanel.removeAll();
		highlightButtons.clear();
		highlightState.clear();
		bankFilterService.clear();

		if (error != null)
		{
			log.warn("Recommendation failed", error);
			statusLabel.setText("Something went wrong; see logs.");
			resultsPanel.revalidate();
			resultsPanel.repaint();
			return;
		}

		statusLabel.setText("");
		resultsPanel.add(wikiLink(target));
		resultsPanel.add(Box.createVerticalStrut(6));
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
			resultsPanel.add(loadoutSection(loadout, result, target));
			resultsPanel.add(Box.createVerticalStrut(10));
		}

		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	/**
	 * Clickable target line above the results; opens the monster's wiki
	 * page via the wiki's npc-id lookup (resolves variants correctly).
	 */
	private Component wikiLink(Target target)
	{
		String base = target.getLabel();
		int variantIdx = base.indexOf(" (");
		if (variantIdx > 0)
		{
			base = base.substring(0, variantIdx);
		}
		String url = "https://oldschool.runescape.wiki/w/Special:Lookup?type=npc&id=" + target.getNpcId()
			+ "&name=" + URLEncoder.encode(base, StandardCharsets.UTF_8);

		JLabel link = new JLabel("<html>" + target.getLabel() + " &nbsp;<u>Wiki</u></html>");
		link.setFont(FontManager.getRunescapeSmallFont());
		link.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		link.setToolTipText("Open the wiki page for " + target.getLabel());
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.setAlignmentX(Component.LEFT_ALIGNMENT);
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				LinkBrowser.browse(url);
			}
		});
		return link;
	}

	private Component warningLabel(String text)
	{
		JLabel label = new JLabel("<html><body style='width:185px'>" + text + "</body></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.PROGRESS_ERROR_COLOR.brighter());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private Component loadoutSection(Loadout loadout, RecommendationService.Result result, Target target)
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

		JToggleButton highlight = new JToggleButton("Show in bank", actionIcon(ColorScheme.LIGHT_GRAY_COLOR, false));
		highlight.setSelectedIcon(actionIcon(ColorScheme.BRAND_ORANGE, false));
		styleActionButton(highlight, "Filter the open bank to this loadout and outline its items");
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
				Set<Integer> ids = loadoutItemIdsOf(loadout);
				highlightState.set(ids);
				bankFilterService.show(ids);
			}
			else
			{
				highlightState.clear();
				bankFilterService.clear();
			}
		});
		highlightButtons.add(highlight);

		// all direct children share LEFT alignment - BoxLayout shifts
		// children unpredictably when alignments are mixed
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		section.add(header);

		if (breakdown != null)
		{
			JLabel breakdownLabel = new JLabel(String.format("base %.1f · pray %.1f · pots %.1f",
				breakdown.getBase(), breakdown.getPrayed(), breakdown.getPotted()));
			breakdownLabel.setFont(FontManager.getRunescapeSmallFont());
			breakdownLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker());
			breakdownLabel.setToolTipText("This gear's DPS: unboosted / prayers only / prayers + potions");
			breakdownLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			section.add(breakdownLabel);
		}

		JLabel style = new JLabel(loadout.getAttackStyle().getDisplayName());
		style.setFont(FontManager.getRunescapeSmallFont());
		style.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		style.setAlignmentX(Component.LEFT_ALIGNMENT);
		section.add(style);
		section.add(Box.createVerticalStrut(6));

		EquipmentGridPanel grid = new EquipmentGridPanel(loadout.getItems(), result.getPartyItemIds(),
			result.getGroupStorageItemIds(), ownedItemsService.getWornItemIds(), itemManager, spriteManager);
		JPanel gridRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		gridRow.setOpaque(false);
		gridRow.add(grid);
		gridRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		section.add(gridRow);
		section.add(Box.createVerticalStrut(6));

		highlight.setAlignmentX(Component.LEFT_ALIGNMENT);
		highlight.setMaximumSize(new Dimension(Integer.MAX_VALUE, highlight.getPreferredSize().height));
		section.add(highlight);
		return section;
	}

	private static Set<Integer> loadoutItemIdsOf(Loadout loadout)
	{
		Set<Integer> ids = new HashSet<>();
		for (ItemStats item : loadout.getItems().values())
		{
			ids.add(item.getItemId());
		}
		return ids;
	}

	private static void styleActionButton(AbstractButton button, String tooltip)
	{
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.setIconTextGap(5);
		button.setMargin(new java.awt.Insets(2, 4, 2, 4));
		button.setToolTipText(tooltip);
	}

	/**
	 * Small 14px action glyphs drawn at runtime (no bundled assets):
	 * highlight = slot outline with a center dot, export = arrow into tray.
	 */
	private static ImageIcon actionIcon(java.awt.Color color, boolean export)
	{
		BufferedImage img = new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(color);
		g.setStroke(new BasicStroke(1.4f));
		if (export)
		{
			g.drawLine(7, 1, 7, 8);
			g.drawLine(4, 5, 7, 8);
			g.drawLine(10, 5, 7, 8);
			g.drawPolyline(new int[]{2, 2, 12, 12}, new int[]{9, 12, 12, 9}, 4);
		}
		else
		{
			g.drawRect(2, 2, 10, 10);
			g.fillOval(5, 5, 4, 4);
		}
		g.dispose();
		return new ImageIcon(img);
	}

	/**
	 * Called when the bank closes: the highlight is a transient aid, not a
	 * persistent marker, so drop it and reset the buttons.
	 */
	public void clearBankHighlight()
	{
		highlightState.clear();
		bankFilterService.clear();
		for (JToggleButton button : highlightButtons)
		{
			button.setSelected(false);
		}
	}

}
