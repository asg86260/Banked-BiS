package com.bankbis.data;

import com.duckblade.osrs.dpscalc.calc.VoidLevelComputable;
import com.duckblade.osrs.dpscalc.calc.gearbonus.BlackMaskGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.CrystalGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.InquisitorsGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.SalveAmuletGearBonus;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import net.runelite.client.RuneLite;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Audits the live wiki equipment data against the engine's special-case id
 * lists: every cosmetic/ornamented/locked variant of an item family whose
 * power comes from id membership (void, slayer helms, salve, inquisitor's,
 * crystal) must be present in the corresponding engine set, or players
 * owning that variant silently lose the bonus.
 *
 * <p>Runs against the plugin's cached wiki data and skips when absent, so
 * it also flags newly added variants whenever the cache is fresh.
 */
class SpecialItemVariantAuditTest
{

	private static List<EquipmentJson> equipment;

	@BeforeAll
	static void setUp() throws Exception
	{
		File cached = new File(RuneLite.RUNELITE_DIR, "bank-bis/equipment.json");
		Assumptions.assumeTrue(cached.exists(), "no cached wiki data on this machine");
		Type type = new TypeToken<List<EquipmentJson>>()
		{
		}.getType();
		try (Reader reader = Files.newBufferedReader(cached.toPath(), StandardCharsets.UTF_8))
		{
			equipment = new Gson().fromJson(reader, type);
		}
	}

	private static List<String> missingFrom(Set<Integer> engineSet, Predicate<String> nameMatches)
	{
		List<String> missing = new ArrayList<>();
		for (EquipmentJson e : equipment)
		{
			String name = e.getName() == null ? "" : e.getName().toLowerCase(Locale.ROOT);
			String version = e.getVersion() == null ? "" : e.getVersion();
			// unwearable/bonus-less states aren't ownership candidates
			if (version.contains("Broken") || version.contains("Inactive") || version.contains("Locked"))
			{
				continue;
			}
			if (nameMatches.test(name) && !engineSet.contains(e.getId()))
			{
				missing.add(e.getName() + (version.isEmpty() ? "" : " [" + version + "]") + " id=" + e.getId());
			}
		}
		return missing;
	}

	private static void assertCovered(String family, Set<Integer> engineSet, Predicate<String> nameMatches)
	{
		List<String> missing = missingFrom(engineSet, nameMatches);
		assertTrue(missing.isEmpty(), family + " variants unknown to the engine: " + missing);
	}

	@Test
	void voidPieces()
	{
		assertCovered("void ranger helm", VoidLevelComputable.VOID_RANGER_HELMS, n -> n.equals("void ranger helm"));
		assertCovered("void mage helm", VoidLevelComputable.VOID_MAGE_HELMS, n -> n.equals("void mage helm"));
		assertCovered("void melee helm", VoidLevelComputable.VOID_MELEE_HELMS, n -> n.equals("void melee helm"));
		assertCovered("void top", Sets.union(VoidLevelComputable.VOID_KNIGHT_TOPS, VoidLevelComputable.ELITE_VOID_TOPS),
			n -> n.equals("void knight top") || n.equals("elite void top"));
		assertCovered("void robe", Sets.union(VoidLevelComputable.VOID_KNIGHT_ROBES, VoidLevelComputable.ELITE_VOID_ROBES),
			n -> n.equals("void knight robe") || n.equals("elite void robe"));
		assertCovered("void gloves", VoidLevelComputable.VOID_KNIGHT_GLOVES, n -> n.equals("void knight gloves"));
	}

	@Test
	void slayerHelmsAndBlackMasks()
	{
		assertCovered("slayer helmet / black mask", BlackMaskGearBonus.BLACK_MASKS_MELEE,
			n -> n.contains("slayer helmet") || n.startsWith("black mask"));
	}

	@Test
	void imbuedSlayerHelmsCountForRanged()
	{
		assertCovered("imbued slayer helmet", BlackMaskGearBonus.BLACK_MASKS_MAGE_RANGED,
			n -> (n.contains("slayer helmet") || n.startsWith("black mask")) && n.contains("(i)"));
	}

	@Test
	void salveAmulets()
	{
		assertCovered("salve amulet", SalveAmuletGearBonus.SALVE_ALL, n -> n.startsWith("salve amulet"));
	}

	@Test
	void inquisitorsPieces()
	{
		assertCovered("inquisitor's helm", InquisitorsGearBonus.INQ_HELM_IDS, n -> n.equals("inquisitor's great helm"));
		assertCovered("inquisitor's hauberk", InquisitorsGearBonus.INQ_BODY_IDS, n -> n.equals("inquisitor's hauberk"));
		assertCovered("inquisitor's plateskirt", InquisitorsGearBonus.INQ_LEGS_IDS, n -> n.equals("inquisitor's plateskirt"));
	}

	@Test
	void crystalArmour()
	{
		// the (basic)/(attuned)/(perfected) versions are Gauntlet-internal
		// and never reach a bank, so only the overworld pieces are audited
		assertCovered("crystal helm", CrystalGearBonus.CRYSTAL_HELM_IDS, n -> n.equals("crystal helm"));
		assertCovered("crystal body", CrystalGearBonus.CRYSTAL_BODY_IDS, n -> n.equals("crystal body"));
		assertCovered("crystal legs", CrystalGearBonus.CRYSTAL_LEGS_IDS, n -> n.equals("crystal legs"));
	}

}
