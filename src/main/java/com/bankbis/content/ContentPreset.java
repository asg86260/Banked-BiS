package com.bankbis.content;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Maps an activity the player wants to do onto concrete calc targets.
 * Monster ids reference the wiki data (monsters.json), which retains
 * historical ids for some renumbered NPCs - always resolve against wiki
 * data, never against the live cache. Multi-phase content lists each
 * relevant form; the first id is the primary/most representative.
 */
@Getter
@RequiredArgsConstructor
public enum ContentPreset
{

	// raids
	COX_GREAT_OLM(PresetCategory.RAIDS, "Great Olm (CoX)", ImmutableList.of(7551, 7552, 7550), false, 1),
	TOB_VERZIK(PresetCategory.RAIDS, "Verzik Vitur (ToB)", ImmutableList.of(8374, 8372), false, 5),
	TOA_ZEBAK(PresetCategory.RAIDS, "Zebak (ToA)", ImmutableList.of(11730), false, 1),
	TOA_KEPHRI(PresetCategory.RAIDS, "Kephri (ToA)", ImmutableList.of(11721), false, 1),
	TOA_BABA(PresetCategory.RAIDS, "Ba-Ba (ToA)", ImmutableList.of(11778), false, 1),
	TOA_AKKHA(PresetCategory.RAIDS, "Akkha (ToA)", ImmutableList.of(11789), false, 1),
	TOA_WARDENS(PresetCategory.RAIDS, "Wardens P3 (ToA)", ImmutableList.of(11762), false, 1),

	// god wars
	GENERAL_GRAARDOR(PresetCategory.GOD_WARS, "General Graardor", ImmutableList.of(2215), false, 1),
	COMMANDER_ZILYANA(PresetCategory.GOD_WARS, "Commander Zilyana", ImmutableList.of(2205), false, 1),
	KREEARRA(PresetCategory.GOD_WARS, "Kree'arra", ImmutableList.of(3162), false, 1),
	KRIL_TSUTSAROTH(PresetCategory.GOD_WARS, "K'ril Tsutsaroth", ImmutableList.of(3129), false, 1),
	NEX(PresetCategory.GOD_WARS, "Nex", ImmutableList.of(11278), false, 1),

	// wilderness
	CALLISTO(PresetCategory.WILDERNESS, "Callisto", ImmutableList.of(6609), false, 1),
	VETION(PresetCategory.WILDERNESS, "Vet'ion", ImmutableList.of(6611, 6612), false, 1),
	VENENATIS(PresetCategory.WILDERNESS, "Venenatis", ImmutableList.of(6610), false, 1),

	// slayer (assumes on-task)
	ABYSSAL_DEMONS(PresetCategory.SLAYER, "Abyssal demons", ImmutableList.of(415), true, 1),
	GARGOYLES(PresetCategory.SLAYER, "Gargoyles", ImmutableList.of(412), true, 1),
	KURASKS(PresetCategory.SLAYER, "Kurasks", ImmutableList.of(410), true, 1),
	CERBERUS(PresetCategory.SLAYER, "Cerberus", ImmutableList.of(5862), true, 1),
	ALCHEMICAL_HYDRA(PresetCategory.SLAYER, "Alchemical Hydra", ImmutableList.of(8615, 8619, 8620, 8621), true, 1),
	THERMY(PresetCategory.SLAYER, "Thermonuclear smoke devil", ImmutableList.of(499), true, 1),
	KRAKEN(PresetCategory.SLAYER, "Kraken", ImmutableList.of(494), true, 1),
	GROTESQUE_GUARDIANS(PresetCategory.SLAYER, "Grotesque Guardians", ImmutableList.of(7851, 7887), true, 1),

	// desert treasure 2
	DUKE_SUCELLUS(PresetCategory.DESERT_TREASURE_2, "Duke Sucellus", ImmutableList.of(12191), false, 1),
	THE_LEVIATHAN(PresetCategory.DESERT_TREASURE_2, "The Leviathan", ImmutableList.of(12214), false, 1),
	VARDORVIS(PresetCategory.DESERT_TREASURE_2, "Vardorvis", ImmutableList.of(12223), false, 1),
	THE_WHISPERER(PresetCategory.DESERT_TREASURE_2, "The Whisperer", ImmutableList.of(12204), false, 1),

	// other bosses
	ZULRAH(PresetCategory.BOSSES, "Zulrah", ImmutableList.of(2042, 2043, 2044), false, 1),
	VORKATH(PresetCategory.BOSSES, "Vorkath", ImmutableList.of(8059), false, 1),
	PHANTOM_MUSPAH(PresetCategory.BOSSES, "Phantom Muspah", ImmutableList.of(12077, 12078, 12080), false, 1),
	CORPOREAL_BEAST(PresetCategory.BOSSES, "Corporeal Beast", ImmutableList.of(319), false, 1),
	ARAXXOR(PresetCategory.BOSSES, "Araxxor", ImmutableList.of(13668), false, 1),
	THE_HUEYCOATL(PresetCategory.BOSSES, "The Hueycoatl", ImmutableList.of(14009), false, 1),
	SARACHNIS(PresetCategory.BOSSES, "Sarachnis", ImmutableList.of(8713), false, 1),
	KALPHITE_QUEEN(PresetCategory.BOSSES, "Kalphite Queen", ImmutableList.of(965, 963), false, 1),
	KING_BLACK_DRAGON(PresetCategory.BOSSES, "King Black Dragon", ImmutableList.of(239), false, 1),
	GIANT_MOLE(PresetCategory.BOSSES, "Giant Mole", ImmutableList.of(5779), false, 1),
	DAGANNOTH_REX(PresetCategory.BOSSES, "Dagannoth Rex", ImmutableList.of(2267), false, 1),
	DAGANNOTH_PRIME(PresetCategory.BOSSES, "Dagannoth Prime", ImmutableList.of(2266), false, 1),
	DAGANNOTH_SUPREME(PresetCategory.BOSSES, "Dagannoth Supreme", ImmutableList.of(2265), false, 1),
	;

	private final PresetCategory category;
	private final String label;
	private final List<Integer> monsterIds;
	private final boolean onSlayerTask;
	private final int raidPartySize;

	public int getPrimaryMonsterId()
	{
		return monsterIds.get(0);
	}

	@Override
	public String toString()
	{
		return label;
	}

}
