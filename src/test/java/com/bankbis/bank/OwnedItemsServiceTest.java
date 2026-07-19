package com.bankbis.bank;

import com.bankbis.bank.OwnedItemsService.Source;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import net.runelite.api.Item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnedItemsServiceTest
{

	private static final IntUnaryOperator IDENTITY = IntUnaryOperator.identity();

	private OwnedItemsService service;

	@BeforeEach
	void setUp()
	{
		service = new OwnedItemsService(null, null, null, null, null);
	}

	@Test
	void mergesQuantitiesAcrossSources()
	{
		service.updateSource(Source.BANK, new Item[]{new Item(4151, 1), new Item(560, 5000)}, IDENTITY);
		service.updateSource(Source.EQUIPMENT, new Item[]{new Item(4151, 1)}, IDENTITY);

		Map<Integer, Integer> owned = service.getOwnedQuantities();
		assertEquals(2, (int) owned.get(4151));
		assertEquals(5000, (int) owned.get(560));
	}

	@Test
	void dropsEmptySlotsAndPlaceholders()
	{
		service.updateSource(Source.BANK, new Item[]{
			new Item(-1, 0), // empty slot
			new Item(4151, 0), // placeholder
			new Item(11802, 1),
		}, IDENTITY);

		Map<Integer, Integer> owned = service.getOwnedQuantities();
		assertFalse(owned.containsKey(4151));
		assertFalse(owned.containsKey(-1));
		assertEquals(1, (int) owned.get(11802));
	}

	@Test
	void canonicalizesNotedItems()
	{
		// noted whip (4152) canonicalizes to whip (4151)
		IntUnaryOperator canonicalize = id -> id == 4152 ? 4151 : id;
		service.updateSource(Source.INVENTORY, new Item[]{new Item(4152, 3), new Item(4151, 1)}, canonicalize);

		Map<Integer, Integer> owned = service.getOwnedQuantities();
		assertEquals(4, (int) owned.get(4151));
		assertFalse(owned.containsKey(4152));
	}

	@Test
	void replacesSourceContentsOnUpdate()
	{
		service.updateSource(Source.INVENTORY, new Item[]{new Item(4151, 1)}, IDENTITY);
		service.updateSource(Source.INVENTORY, new Item[]{new Item(560, 100)}, IDENTITY);

		Map<Integer, Integer> owned = service.getOwnedQuantities();
		assertFalse(owned.containsKey(4151));
		assertEquals(100, (int) owned.get(560));
	}

}
