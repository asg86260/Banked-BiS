package com.bankbis.testutil;

import com.bankbis.data.EquipmentJson;
import com.bankbis.data.MonsterJson;
import com.bankbis.data.NpcStats;
import com.bankbis.data.WikiDataMapper;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.duckblade.osrs.dpscalc.calc.model.Skills;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.runelite.api.Skill;

/**
 * Shared fixture loading for tests. Fixture files live next to the test
 * class passed as anchor.
 */
public final class TestFixtures
{

	private static final Gson GSON = new Gson();

	private static final Type EQUIPMENT_TYPE = new TypeToken<List<EquipmentJson>>()
	{
	}.getType();

	private static final Type MONSTERS_TYPE = new TypeToken<List<MonsterJson>>()
	{
	}.getType();

	private TestFixtures()
	{
	}

	public static List<EquipmentJson> loadEquipmentJson(Class<?> anchor, String resource)
	{
		return parse(anchor, resource, EQUIPMENT_TYPE);
	}

	public static List<MonsterJson> loadMonsterJson(Class<?> anchor, String resource)
	{
		return parse(anchor, resource, MONSTERS_TYPE);
	}

	public static List<ItemStats> loadItemStats(Class<?> anchor, String resource)
	{
		return loadEquipmentJson(anchor, resource).stream()
			.map(WikiDataMapper::toItemStats)
			.collect(Collectors.toList());
	}

	public static Map<Integer, EquipmentJson> equipmentById(Class<?> anchor, String resource)
	{
		return loadEquipmentJson(anchor, resource).stream()
			.collect(Collectors.toMap(EquipmentJson::getId, Function.identity()));
	}

	public static Map<String, MonsterJson> monstersByDisplayName(Class<?> anchor, String resource)
	{
		return loadMonsterJson(anchor, resource).stream()
			.collect(Collectors.toMap(WikiDataMapper::displayName, Function.identity(), (a, b) -> a));
	}

	public static Map<String, NpcStats> npcStatsByDisplayName(Class<?> anchor, String resource)
	{
		return loadMonsterJson(anchor, resource).stream()
			.collect(Collectors.toMap(WikiDataMapper::displayName, NpcStats::of, (a, b) -> a));
	}

	/**
	 * Maxed combat stats with super combat / ranging potion / imbued heart
	 * style boosts, the reference player for optimizer assertions.
	 */
	public static Skills maxedWithBoosts()
	{
		return Skills.builder()
			.level(Skill.ATTACK, 99).boost(Skill.ATTACK, 19)
			.level(Skill.STRENGTH, 99).boost(Skill.STRENGTH, 19)
			.level(Skill.DEFENCE, 99).boost(Skill.DEFENCE, 19)
			.level(Skill.RANGED, 99).boost(Skill.RANGED, 13)
			.level(Skill.MAGIC, 99).boost(Skill.MAGIC, 13)
			.level(Skill.HITPOINTS, 99)
			.level(Skill.PRAYER, 99)
			.build();
	}

	private static <T> T parse(Class<?> anchor, String resource, Type type)
	{
		InputStream in = anchor.getResourceAsStream(resource);
		if (in == null)
		{
			throw new IllegalArgumentException("Missing test fixture " + resource + " next to " + anchor.getName());
		}
		try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			return GSON.fromJson(reader, type);
		}
		catch (Exception e)
		{
			throw new RuntimeException("Failed to parse fixture " + resource, e);
		}
	}

}
