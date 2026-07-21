package com.bankbis.data;

import com.duckblade.osrs.dpscalc.calc.model.DefenderAttributes;
import com.duckblade.osrs.dpscalc.calc.model.DefensiveBonuses;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.duckblade.osrs.dpscalc.calc.model.Skills;
import com.duckblade.osrs.dpscalc.calc.model.WeaponCategory;
import com.google.common.collect.ImmutableMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;

/**
 * Maps wiki JSON entries onto the calc engine's model types.
 */
@Slf4j
public class WikiDataMapper
{

	private static final Map<String, EquipmentInventorySlot> SLOTS = ImmutableMap.<String, EquipmentInventorySlot>builder()
		.put("head", EquipmentInventorySlot.HEAD)
		.put("cape", EquipmentInventorySlot.CAPE)
		.put("neck", EquipmentInventorySlot.AMULET)
		.put("ammo", EquipmentInventorySlot.AMMO)
		.put("weapon", EquipmentInventorySlot.WEAPON)
		.put("body", EquipmentInventorySlot.BODY)
		.put("shield", EquipmentInventorySlot.SHIELD)
		.put("legs", EquipmentInventorySlot.LEGS)
		.put("hands", EquipmentInventorySlot.GLOVES)
		.put("feet", EquipmentInventorySlot.BOOTS)
		.put("ring", EquipmentInventorySlot.RING)
		.build();

	// categories the wiki added after the engine's WeaponCategory enum was written,
	// aliased to the closest style layout until proper support is added
	private static final Map<String, WeaponCategory> CATEGORY_ALIASES = ImmutableMap.of(
		"FLAIL", WeaponCategory.SPIKED,
		"MULTI_MELEE", WeaponCategory.CLAW,
		"GUN", WeaponCategory.UNARMED,
		"BLASTER", WeaponCategory.THROWN
	);

	public static EquipmentInventorySlot slotOf(EquipmentJson e)
	{
		return SLOTS.get(e.getSlot());
	}

	public static ItemStats toItemStats(EquipmentJson e)
	{
		EquipmentInventorySlot slot = slotOf(e);
		return ItemStats.builder()
			.itemId(e.getId())
			.name(e.getName())
			.accuracyStab(e.getOffensive().getStab())
			.accuracySlash(e.getOffensive().getSlash())
			.accuracyCrush(e.getOffensive().getCrush())
			.accuracyMagic(e.getOffensive().getMagic())
			.accuracyRanged(e.getOffensive().getRanged())
			.strengthMelee(e.getBonuses().getStr())
			.strengthRanged(e.getBonuses().getRangedStr())
			// wiki data stores magic damage in tenths of a percent; the engine expects whole percents
			.strengthMagic((int) Math.round(e.getBonuses().getMagicStr() / 10.0))
			.prayer(e.getBonuses().getPrayer())
			.speed(e.getSpeed() > 0 ? e.getSpeed() : 4)
			.slot(slot != null ? slot.getSlotIdx() : -1)
			.is2h(e.isTwoHanded())
			.weaponCategory(categoryOf(e))
			.build();
	}

	public static WeaponCategory categoryOf(EquipmentJson e)
	{
		String raw = e.getCategory();
		if (raw == null || raw.isEmpty())
		{
			return WeaponCategory.UNARMED;
		}

		String normalized = raw.toUpperCase(Locale.ROOT)
			.replace("2H SWORD", "TWO_HANDED_SWORD")
			.replace(' ', '_')
			.replace('-', '_');

		WeaponCategory alias = CATEGORY_ALIASES.get(normalized);
		if (alias != null)
		{
			return alias;
		}

		try
		{
			return WeaponCategory.valueOf(normalized);
		}
		catch (IllegalArgumentException ex)
		{
			log.debug("Unknown weapon category [{}] for item [{}], treating as unarmed", raw, e.getName());
			return WeaponCategory.UNARMED;
		}
	}

	public static Skills toNpcSkills(MonsterJson m)
	{
		return Skills.builder()
			.level(Skill.ATTACK, m.getSkills().getAtk())
			.level(Skill.STRENGTH, m.getSkills().getStr())
			.level(Skill.DEFENCE, m.getSkills().getDef())
			.level(Skill.MAGIC, m.getSkills().getMagic())
			.level(Skill.RANGED, m.getSkills().getRanged())
			.level(Skill.HITPOINTS, m.getSkills().getHp())
			.build();
	}

	public static DefensiveBonuses toDefensiveBonuses(MonsterJson m)
	{
		return DefensiveBonuses.builder()
			.defenseStab(m.getDefensive().getStab())
			.defenseSlash(m.getDefensive().getSlash())
			.defenseCrush(m.getDefensive().getCrush())
			.defenseMagic(m.getDefensive().getMagic())
			// split ranged defence: standard = bows, light = thrown, heavy = crossbows
			.defenseRanged(m.getDefensive().getStandard())
			.defenseRangedLight(m.getDefensive().getLight())
			.defenseRangedHeavy(m.getDefensive().getHeavy())
			.build();
	}

	public static DefenderAttributes toDefenderAttributes(MonsterJson m)
	{
		boolean demon = m.getAttributes().contains("demon");
		boolean dragon = m.getAttributes().contains("dragon");
		boolean kalphite = m.getAttributes().contains("kalphite");
		boolean leafy = m.getAttributes().contains("leafy");
		boolean undead = m.getAttributes().contains("undead");
		boolean fiery = m.getAttributes().contains("fiery");

		return DefenderAttributes.builder()
			.npcId(m.getId())
			.name(displayName(m))
			.isDemon(demon)
			.isDragon(dragon)
			.isKalphite(kalphite)
			.isLeafy(leafy)
			.isUndead(undead)
			.isFiery(fiery)
			.isVampyre1(m.getAttributes().contains("vampyre1"))
			.isVampyre2(m.getAttributes().contains("vampyre2"))
			.isVampyre3(m.getAttributes().contains("vampyre3"))
			.size(m.getSize())
			.accuracyMagic(m.getOffensive().getMagic())
			.elementalWeakness(m.getWeakness() == null || m.getWeakness().getElement() == null
				? null : m.getWeakness().getElement().toLowerCase(Locale.ROOT))
			.elementalWeaknessSeverity(m.getWeakness() == null ? 0 : m.getWeakness().getSeverity())
			.build();
	}

	public static String displayName(MonsterJson m)
	{
		if (m.getVersion() == null || m.getVersion().isEmpty())
		{
			return m.getName();
		}
		return m.getName() + " (" + m.getVersion() + ")";
	}

}
