package com.bankbis.party;

import com.bankbis.BankBisConfig;
import java.util.Map;
import net.runelite.client.party.PartyService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PartyItemsServiceTest
{

	@Mock
	private PartyService partyService;

	@Mock
	private BankBisConfig config;

	@InjectMocks
	private PartyItemsService service;

	private static OwnedItemsUpdate update(long memberId, Map<Integer, Integer> items)
	{
		OwnedItemsUpdate u = new OwnedItemsUpdate(items);
		u.setMemberId(memberId);
		return u;
	}

	@Test
	void mergesItemsAcrossMembers()
	{
		when(config.sharePartyBanks()).thenReturn(true);
		lenient().when(partyService.getLocalMember()).thenReturn(null);

		service.onOwnedItemsUpdate(update(1L, Map.of(4151, 1, 560, 100)));
		service.onOwnedItemsUpdate(update(2L, Map.of(4151, 2)));

		Map<Integer, Integer> merged = service.getPartyQuantities();
		assertEquals(3, (int) merged.get(4151));
		assertEquals(100, (int) merged.get(560));
	}

	@Test
	void replacesMemberSnapshotOnUpdate()
	{
		when(config.sharePartyBanks()).thenReturn(true);
		lenient().when(partyService.getLocalMember()).thenReturn(null);

		service.onOwnedItemsUpdate(update(1L, Map.of(4151, 1)));
		service.onOwnedItemsUpdate(update(1L, Map.of(560, 5)));

		Map<Integer, Integer> merged = service.getPartyQuantities();
		assertTrue(!merged.containsKey(4151));
		assertEquals(5, (int) merged.get(560));
	}

	@Test
	void emptyWhenDisabled()
	{
		when(config.sharePartyBanks()).thenReturn(false);
		assertTrue(service.getPartyQuantities().isEmpty());
	}

}
