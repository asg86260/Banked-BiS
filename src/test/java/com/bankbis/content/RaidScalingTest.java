package com.bankbis.content;

import com.bankbis.data.NpcStats;
import com.duckblade.osrs.dpscalc.calc.model.Skills;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;

class RaidScalingTest
{

	private static NpcStats npc(int def, int magic)
	{
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.DEFENCE, def);
		levels.put(Skill.MAGIC, magic);
		levels.put(Skill.HITPOINTS, 800);
		return NpcStats.builder()
			.displayName("Test monster")
			.speed(4)
			.skills(Skills.builder().levels(levels).build())
			.build();
	}

	private static Target coxTarget(int npcId, int partySize, boolean cm)
	{
		return Target.builder()
			.npcId(npcId)
			.label("Test")
			.raid(RaidType.COX)
			.raidPartySize(partySize)
			.coxChallengeMode(cm)
			.build();
	}

	@Test
	void nonRaidAndSoloNormalModeUnchanged()
	{
		NpcStats stats = npc(200, 200);
		assertSame(stats, RaidScaling.scale(stats, Target.builder().npcId(1).label("x").build()));
		assertSame(stats, RaidScaling.scale(stats, coxTarget(7551, 1, false)));
	}

	@Test
	void coxFiveManScalesDefenceAndMagic()
	{
		// party 5: m1=4 -> defensive pct = 100 + isqrt(4) + 4*7/10 = 104,
		// offensive pct = 100 + isqrt(4)*7 + 4 = 118 (magic offensive for Olm head)
		NpcStats scaled = RaidScaling.scale(npc(200, 200), coxTarget(7551, 5, false));
		assertEquals(208, (int) scaled.getSkills().getLevels().get(Skill.DEFENCE));
		assertEquals(236, (int) scaled.getSkills().getLevels().get(Skill.MAGIC));
	}

	@Test
	void coxMagicDefensiveMonsterUsesDefensivePct()
	{
		// Olm mage hand (7552): magic scales with the defensive pct
		NpcStats scaled = RaidScaling.scale(npc(200, 200), coxTarget(7552, 5, false));
		assertEquals(208, (int) scaled.getSkills().getLevels().get(Skill.MAGIC));
	}

	@Test
	void challengeModeAddsFiftyPercentDefence()
	{
		NpcStats scaled = RaidScaling.scale(npc(200, 200), coxTarget(7551, 1, true));
		assertEquals(300, (int) scaled.getSkills().getLevels().get(Skill.DEFENCE));
	}

	@Test
	void toaDoesNotScaleStats()
	{
		NpcStats stats = npc(80, 100);
		NpcStats scaled = RaidScaling.scale(stats, Target.builder()
			.npcId(11730).label("Zebak").raid(RaidType.TOA).toaInvocationLevel(500).raidPartySize(8).build());
		assertSame(stats, scaled);
	}

}
