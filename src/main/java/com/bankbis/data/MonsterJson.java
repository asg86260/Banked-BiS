package com.bankbis.data;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Data;

/**
 * One entry of monsters.json from weirdgloop/osrs-dps-calc.
 */
@Data
public class MonsterJson
{

	private int id;
	private String name;
	private String version;
	private int level;
	private int speed;
	private List<String> style;
	private int size;
	private Skills skills;
	private Offensive offensive;
	private Defensive defensive;
	private List<String> attributes;

	@SerializedName("is_slayer_monster")
	private boolean slayerMonster;

	@Data
	public static class Skills
	{
		private int atk;
		private int def;
		private int hp;
		private int magic;
		private int ranged;
		private int str;
	}

	@Data
	public static class Offensive
	{
		private int atk;
		private int magic;
		private int ranged;
		private int str;

		@SerializedName("magic_str")
		private int magicStr;

		@SerializedName("ranged_str")
		private int rangedStr;
	}

	@Data
	public static class Defensive
	{
		private int stab;
		private int slash;
		private int crush;
		private int magic;

		// modern split ranged defence; engine currently uses a single value
		private int light;
		private int standard;
		private int heavy;

		@SerializedName("flat_armour")
		private int flatArmour;
	}

}
