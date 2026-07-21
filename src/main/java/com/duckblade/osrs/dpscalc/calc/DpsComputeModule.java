package com.duckblade.osrs.dpscalc.calc;

import com.duckblade.osrs.dpscalc.calc.ammo.AmmoItemStatsComputable;
import com.duckblade.osrs.dpscalc.calc.ammo.AmmolessRangedAmmoItemStatsComputable;
import com.duckblade.osrs.dpscalc.calc.ammo.BlowpipeDartsItemStatsComputable;
import com.duckblade.osrs.dpscalc.calc.defender.skills.SkillScaling;
import com.duckblade.osrs.dpscalc.calc.defender.skills.TheatreSkillScaling;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeOutput;
import com.duckblade.osrs.dpscalc.calc.gearbonus.AhrimsAutocastGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.BlackMaskGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.ChinchompaDistanceGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.CrystalGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.DragonHunterGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.GearBonusComputable;
import com.duckblade.osrs.dpscalc.calc.gearbonus.InquisitorsGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.KerisGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.LeafyGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.MageDemonbaneGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.MeleeDemonbaneGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.RevenantWeaponGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.SalveAmuletGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.SmokeBattlestaffGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.TbowGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.TomesGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.VampyreBaneGearBonus;
import com.duckblade.osrs.dpscalc.calc.gearbonus.VoidGearBonus;
import com.duckblade.osrs.dpscalc.calc.maxhit.limiters.CombatStyleImmunityMaxHitLimiter;
import com.duckblade.osrs.dpscalc.calc.maxhit.limiters.MaxHitLimiter;
import com.duckblade.osrs.dpscalc.calc.maxhit.limiters.Tier2VampyreImmunities;
import com.duckblade.osrs.dpscalc.calc.maxhit.limiters.Tier3VampyreImmunities;
import com.duckblade.osrs.dpscalc.calc.maxhit.limiters.ZulrahMaxHitLimiter;
import com.duckblade.osrs.dpscalc.calc.maxhit.magic.MagicMaxHitComputable;
import com.duckblade.osrs.dpscalc.calc.maxhit.magic.MagicSalamanderMaxHitComputable;
import com.duckblade.osrs.dpscalc.calc.maxhit.magic.PoweredStaffMaxHitComputable;
import com.duckblade.osrs.dpscalc.calc.maxhit.magic.SpellMaxHitComputable;
import com.duckblade.osrs.dpscalc.calc.multihit.ColossalBladeDptComputable;
import com.duckblade.osrs.dpscalc.calc.multihit.DharoksDptComputable;
import com.duckblade.osrs.dpscalc.calc.multihit.KarilsDptComputable;
import com.duckblade.osrs.dpscalc.calc.multihit.KerisDptComputable;
import com.duckblade.osrs.dpscalc.calc.multihit.MultiHitDptComputable;
import com.duckblade.osrs.dpscalc.calc.multihit.ScytheDptComputable;
import com.duckblade.osrs.dpscalc.calc.multihit.VeracsDptComputable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import java.util.List;
import java.util.Set;

public class DpsComputeModule extends AbstractModule
{

	@Override
	protected void configure()
	{
		// CHECKSTYLE:OFF
		bind(new TypeLiteral<List<ComputeOutput<Integer>>>() {})
			.annotatedWith(Names.named("EffectMaxHitOutputs"))
			.toInstance(ImmutableList.of(
				ColossalBladeDptComputable.COLOSSAL_BLADE_MAX_HIT,
				DharoksDptComputable.DHAROKS_MAX_HIT,
				KarilsDptComputable.KARILS_MAX_HIT,
				KerisDptComputable.KERIS_MAX_HIT,
				ScytheDptComputable.SCY_MAX_HIT_SUM
			));
		// CHECKSTYLE:ON

		bind(DptComputable.class).asEagerSingleton();
	}

	// Plain @Provides set bindings instead of Guice Multibinder: the plugin
	// hub's build environment ships a Guice without the multibindings
	// package. ImmutableSet preserves insertion order, which matters for
	// the first-applicable lookups (magic max hit, multi-hit dpt).

	@Provides
	@Singleton
	Set<AmmoItemStatsComputable> ammoItemStatsComputables(
		AmmolessRangedAmmoItemStatsComputable ammoless,
		BlowpipeDartsItemStatsComputable blowpipeDarts)
	{
		return ImmutableSet.of(ammoless, blowpipeDarts);
	}

	@Provides
	@Singleton
	Set<SkillScaling> skillScalings(TheatreSkillScaling theatre)
	{
		return ImmutableSet.of(theatre);
	}

	@Provides
	@Singleton
	Set<GearBonusComputable> gearBonusComputables(
		AhrimsAutocastGearBonus ahrims,
		BlackMaskGearBonus blackMask,
		ChinchompaDistanceGearBonus chinchompa,
		CrystalGearBonus crystal,
		MageDemonbaneGearBonus mageDemonbane,
		DragonHunterGearBonus dragonHunter,
		InquisitorsGearBonus inquisitors,
		KerisGearBonus keris,
		LeafyGearBonus leafy,
		MeleeDemonbaneGearBonus meleeDemonbane,
		RevenantWeaponGearBonus revenant,
		SalveAmuletGearBonus salve,
		SmokeBattlestaffGearBonus smokeBattlestaff,
		TbowGearBonus tbow,
		TomesGearBonus tomes,
		VampyreBaneGearBonus vampyreBane,
		VoidGearBonus voidBonus)
	{
		return ImmutableSet.of(ahrims, blackMask, chinchompa, crystal, mageDemonbane,
			dragonHunter, inquisitors, keris, leafy, meleeDemonbane, revenant, salve,
			smokeBattlestaff, tbow, tomes, vampyreBane, voidBonus);
	}

	@Provides
	@Singleton
	Set<MagicMaxHitComputable> magicMaxHitComputables(
		MagicSalamanderMaxHitComputable salamander,
		PoweredStaffMaxHitComputable poweredStaff,
		SpellMaxHitComputable spell)
	{
		return ImmutableSet.of(salamander, poweredStaff, spell);
	}

	@Provides
	@Singleton
	Set<MaxHitLimiter> maxHitLimiters(
		CombatStyleImmunityMaxHitLimiter styleImmunity,
		Tier2VampyreImmunities vampyre2,
		Tier3VampyreImmunities vampyre3,
		ZulrahMaxHitLimiter zulrah)
	{
		return ImmutableSet.of(styleImmunity, vampyre2, vampyre3, zulrah);
	}

	@Provides
	@Singleton
	Set<MultiHitDptComputable> multiHitDptComputables(
		ColossalBladeDptComputable colossalBlade,
		DharoksDptComputable dharoks,
		KarilsDptComputable karils,
		KerisDptComputable keris,
		ScytheDptComputable scythe,
		VeracsDptComputable veracs)
	{
		return ImmutableSet.of(colossalBlade, dharoks, karils, keris, scythe, veracs);
	}

}
