package com.bankbis.optimizer;

import lombok.Value;

/**
 * Which unlockable offensive prayers the player actually has, read from
 * varbits at recommendation time. Piety and Chivalry share one gate
 * (Knight Waves Training Grounds, which requires King's Ransom).
 */
@Value
public class PrayerUnlocks
{

	public static final PrayerUnlocks ALL = new PrayerUnlocks(true, true, true);

	boolean pietyChivalry;
	boolean rigour;
	boolean augury;

}
