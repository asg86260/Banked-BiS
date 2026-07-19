package com.bankbis.data;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/**
 * One entry of equipment.json from weirdgloop/osrs-dps-calc.
 */
@Data
public class EquipmentJson
{

	private String name;
	private int id;
	private String version;
	private String slot;
	private int speed;
	private String category;
	private Bonuses bonuses;
	private Offensive offensive;
	private Defensive defensive;

	@SerializedName("isTwoHanded")
	private boolean twoHanded;

	@Data
	public static class Bonuses
	{
		private int str;

		@SerializedName("ranged_str")
		private int rangedStr;

		@SerializedName("magic_str")
		private int magicStr;

		private int prayer;
	}

	@Data
	public static class Offensive
	{
		private int stab;
		private int slash;
		private int crush;
		private int magic;
		private int ranged;
	}

	@Data
	public static class Defensive
	{
		private int stab;
		private int slash;
		private int crush;
		private int magic;
		private int ranged;
	}

}
