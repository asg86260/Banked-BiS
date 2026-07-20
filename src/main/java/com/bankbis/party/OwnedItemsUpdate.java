package com.bankbis.party;

import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Broadcasts the sender's equippable owned items (id -> quantity) to their
 * RuneLite party.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class OwnedItemsUpdate extends PartyMemberMessage
{

	private final Map<Integer, Integer> items;

}
