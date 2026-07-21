package com.bankbis.optimizer;

import com.bankbis.data.NpcStats;
import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
import com.duckblade.osrs.dpscalc.calc.model.Skills;
import java.util.Collection;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class OptimizeRequest
{

	/**
	 * Equipment stats for every item the player owns (already resolved
	 * from item ids via the wiki data).
	 */
	private final Collection<ItemStats> ownedEquipment;

	private final Skills playerSkills;

	private final NpcStats target;

	@Builder.Default
	private final boolean onSlayerTask = false;

	@Builder.Default
	private final int raidPartySize = 1;

	@Builder.Default
	private final PrayerAssumption prayerAssumption = PrayerAssumption.AUTO;

}
