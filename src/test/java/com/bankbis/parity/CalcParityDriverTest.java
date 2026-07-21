package com.bankbis.parity;

import com.bankbis.data.EquipmentJson;
import com.bankbis.data.MonsterJson;
import com.bankbis.data.NpcStats;
import com.bankbis.data.WikiDataMapper;
import com.duckblade.osrs.dpscalc.calc.DpsComputable;
import com.duckblade.osrs.dpscalc.calc.DpsComputeModule;
import com.duckblade.osrs.dpscalc.calc.HitChanceComputable;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeContext;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeInputs;
import com.duckblade.osrs.dpscalc.calc.maxhit.TrueMaxHitComputable;
import com.duckblade.osrs.dpscalc.calc.model.AttackStyle;
import com.duckblade.osrs.dpscalc.calc.model.CombatStyle;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.duckblade.osrs.dpscalc.calc.model.Prayer;
import com.duckblade.osrs.dpscalc.calc.model.Skills;
import com.duckblade.osrs.dpscalc.calc.model.Spell;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Injector;
import com.google.inject.Guice;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.junit.jupiter.api.Test;

/**
 * Engine side of the calc-parity oracle. NOT a real unit test: it reads the
 * shared case list produced by the generator, runs each case through our
 * engine, and writes {id, maxHit, accuracy, dps} back out for diffing against
 * the wiki calc. Skips silently when the harness has not been staged (no
 * cases.json), so it is inert during a normal build / CI.
 *
 * Driven by tools/calc-parity/run.mjs. See tools/calc-parity/README.md.
 */
class CalcParityDriverTest
{

	private static final Path DIR = Paths.get(
		System.getenv().getOrDefault("CALC_PARITY_DIR", "tools/calc-parity"));
	private static final Path CDN = DIR.resolve("wiki-calc/cdn/json");
	private static final Path OUT = DIR.resolve("out");

	private static final Gson GSON = new GsonBuilder().serializeNulls().create();

	private static final Map<String, EquipmentInventorySlot> SLOTS = new java.util.HashMap<>();
	private static final Map<String, Skill> SKILLS = new java.util.HashMap<>();

	static
	{
		SLOTS.put("head", EquipmentInventorySlot.HEAD);
		SLOTS.put("cape", EquipmentInventorySlot.CAPE);
		SLOTS.put("neck", EquipmentInventorySlot.AMULET);
		SLOTS.put("ammo", EquipmentInventorySlot.AMMO);
		SLOTS.put("weapon", EquipmentInventorySlot.WEAPON);
		SLOTS.put("body", EquipmentInventorySlot.BODY);
		SLOTS.put("shield", EquipmentInventorySlot.SHIELD);
		SLOTS.put("legs", EquipmentInventorySlot.LEGS);
		SLOTS.put("hands", EquipmentInventorySlot.GLOVES);
		SLOTS.put("feet", EquipmentInventorySlot.BOOTS);
		SLOTS.put("ring", EquipmentInventorySlot.RING);

		SKILLS.put("atk", Skill.ATTACK);
		SKILLS.put("str", Skill.STRENGTH);
		SKILLS.put("def", Skill.DEFENCE);
		SKILLS.put("hp", Skill.HITPOINTS);
		SKILLS.put("ranged", Skill.RANGED);
		SKILLS.put("magic", Skill.MAGIC);
		SKILLS.put("prayer", Skill.PRAYER);
	}

	@Test
	void runEngineOverCases() throws IOException
	{
		Path casesFile = OUT.resolve("cases.json");
		assumeTrue(Files.exists(casesFile) && Files.exists(CDN.resolve("equipment.json")),
			"calc-parity not staged (no cases.json or wiki-calc clone); run tools/calc-parity/run.mjs");

		List<Case> cases = readJson(casesFile, new TypeToken<List<Case>>() {}.getType());
		Map<String, EquipmentJson> equipment = indexEquipment();
		Map<String, MonsterJson> monsters = indexMonsters();

		Injector injector = Guice.createInjector(new DpsComputeModule());
		DpsComputable dps = injector.getInstance(DpsComputable.class);
		TrueMaxHitComputable maxHit = injector.getInstance(TrueMaxHitComputable.class);
		HitChanceComputable hitChance = injector.getInstance(HitChanceComputable.class);

		List<Result> results = new ArrayList<>(cases.size());
		for (Case c : cases)
		{
			Result r = new Result();
			r.id = c.id;
			try
			{
				ComputeContext ctx = buildContext(c, equipment, monsters);
				r.maxHit = ctx.get(maxHit);
				r.accuracy = ctx.get(hitChance);
				r.dps = ctx.get(dps);
			}
			catch (RuntimeException ex)
			{
				r.error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
			}
			results.add(r);
		}

		Files.createDirectories(OUT);
		Files.write(OUT.resolve("engine-out.json"),
			GSON.toJson(results).getBytes(StandardCharsets.UTF_8));
	}

	private ComputeContext buildContext(Case c, Map<String, EquipmentJson> equipment,
		Map<String, MonsterJson> monsters)
	{
		MonsterJson mj = monsters.get(key(c.monster.name, c.monster.version));
		if (mj == null)
		{
			throw new IllegalArgumentException("monster not found: " + c.monster.name);
		}
		NpcStats npc = NpcStats.of(mj);

		Map<EquipmentInventorySlot, ItemStats> worn = new EnumMap<>(EquipmentInventorySlot.class);
		ItemStats weapon = null;
		for (Map.Entry<String, ItemRef> e : c.equipment.entrySet())
		{
			EquipmentInventorySlot slot = SLOTS.get(e.getKey());
			if (slot == null)
			{
				throw new IllegalArgumentException("unknown slot: " + e.getKey());
			}
			ItemRef ref = e.getValue();
			EquipmentJson ej = equipment.get(key(ref.name, ref.version));
			if (ej == null)
			{
				throw new IllegalArgumentException("equipment not found: " + ref.name);
			}
			ItemStats stats = WikiDataMapper.toItemStats(ej);
			worn.put(slot, stats);
			if (slot == EquipmentInventorySlot.WEAPON)
			{
				weapon = stats;
			}
		}
		if (weapon == null)
		{
			throw new IllegalArgumentException("no weapon in loadout");
		}

		Skills.SkillsBuilder skills = Skills.builder();
		for (Map.Entry<String, Integer> e : c.skills.entrySet())
		{
			Skill sk = SKILLS.get(e.getKey());
			if (sk != null)
			{
				skills.level(sk, e.getValue());
			}
		}

		Set<Prayer> prayers = EnumSet.noneOf(Prayer.class);
		if (c.prayers != null)
		{
			for (String p : c.prayers)
			{
				prayers.add(Prayer.valueOf(p));
			}
		}

		ComputeContext ctx = new ComputeContext();
		ctx.put(ComputeInputs.ATTACKER_SKILLS, skills.build());
		ctx.put(ComputeInputs.ATTACKER_ITEMS, worn);
		ctx.put(ComputeInputs.ATTACKER_PRAYERS, prayers);
		ctx.put(ComputeInputs.DEFENDER_SKILLS, npc.getSkills());
		ctx.put(ComputeInputs.DEFENDER_BONUSES, npc.getDefensiveBonuses());
		ctx.put(ComputeInputs.DEFENDER_ATTRIBUTES, npc.getAttributes());
		ctx.put(ComputeInputs.ON_SLAYER_TASK, c.onSlayerTask);

		if (c.spell != null && !c.spell.isEmpty())
		{
			ctx.put(ComputeInputs.ATTACK_STYLE, AttackStyle.MANUAL_CAST);
			ctx.put(ComputeInputs.SPELL, findSpell(c.spell));
		}
		else
		{
			ctx.put(ComputeInputs.ATTACK_STYLE, selectStyle(weapon, c.style));
		}
		return ctx;
	}

	private AttackStyle selectStyle(ItemStats weapon, StyleRef style)
	{
		if (style == null)
		{
			throw new IllegalArgumentException("no style and no spell");
		}
		CombatStyle stance = CombatStyle.valueOf(style.stance.toUpperCase().replace(" ", ""));
		return weapon.getWeaponCategory().getAttackStyles().stream()
			.filter(as -> as.getAttackType() != null
				&& as.getAttackType().name().equalsIgnoreCase(style.type)
				&& as.getCombatStyle() == stance)
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException(
				"no style " + style.type + "/" + style.stance + " for "
					+ weapon.getWeaponCategory()));
	}

	private Spell findSpell(String name)
	{
		for (Spell s : Spell.values())
		{
			if (name.equalsIgnoreCase(s.getDisplayName()))
			{
				return s;
			}
		}
		throw new IllegalArgumentException("spell not found: " + name);
	}

	private Map<String, EquipmentJson> indexEquipment() throws IOException
	{
		List<EquipmentJson> all = readJson(CDN.resolve("equipment.json"),
			new TypeToken<List<EquipmentJson>>() {}.getType());
		Map<String, EquipmentJson> map = new java.util.HashMap<>();
		for (EquipmentJson e : all)
		{
			map.putIfAbsent(key(e.getName(), e.getVersion()), e);
		}
		return map;
	}

	private Map<String, MonsterJson> indexMonsters() throws IOException
	{
		List<MonsterJson> all = readJson(CDN.resolve("monsters.json"),
			new TypeToken<List<MonsterJson>>() {}.getType());
		Map<String, MonsterJson> map = new java.util.HashMap<>();
		for (MonsterJson m : all)
		{
			map.putIfAbsent(key(m.getName(), m.getVersion()), m);
		}
		return map;
	}

	private static String key(String name, String version)
	{
		return name + " " + (version == null ? "" : version);
	}

	private static <T> T readJson(Path path, Type type) throws IOException
	{
		try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8))
		{
			return GSON.fromJson(r, type);
		}
	}

	// --- shared case schema (see tools/calc-parity/README.md) ---

	private static final class Case
	{
		String id;
		MonsterRef monster;
		Map<String, Integer> skills;
		Map<String, ItemRef> equipment;
		StyleRef style;
		String spell;
		List<String> prayers;
		boolean onSlayerTask;
	}

	private static final class MonsterRef
	{
		String name;
		String version;
	}

	private static final class ItemRef
	{
		String name;
		String version;
	}

	private static final class StyleRef
	{
		String type;
		String stance;
	}

	private static final class Result
	{
		String id;
		Integer maxHit;
		Double accuracy;
		Double dps;
		String error;
	}

}
