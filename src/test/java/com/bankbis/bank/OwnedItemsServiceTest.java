package com.bankbis.bank;

import com.bankbis.bank.OwnedItemsService.Source;
import java.util.Map;
import java.util.function.IntPredicate;
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
	private static final IntPredicate NO_PLACEHOLDERS = id -> false;

	private OwnedItemsService service;

	@BeforeEach
	void setUp()
	{
		service = new OwnedItemsService(null, null, null, null, null);
	}

	@Test
	void mergesQuantitiesAcrossSources()
	{
		service.updateSource(Source.BANK, new Item[]{new Item(4151, 1), new Item(560, 5000)}, IDENTITY, NO_PLACEHOLDERS);
		service.updateSource(Source.EQUIPMENT, new Item[]{new Item(4151, 1)}, IDENTITY, NO_PLACEHOLDERS);

		Map<Integer, Integer> owned = service.getOwnedQuantities();
		assertEquals(2, (int) owned.get(4151));
		assertEquals(5000, (int) owned.get(560));
	}

	@Test
	void dropsEmptySlotsAndZeroQuantities()
	{
		service.updateSource(Source.BANK, new Item[]{
			new Item(-1, 0), // empty slot
			new Item(4151, 0), // zero quantity
			new Item(11802, 1),
		}, IDENTITY, NO_PLACEHOLDERS);

		Map<Integer, Integer> owned = service.getOwnedQuantities();
		assertFalse(owned.containsKey(4151));
		assertFalse(owned.containsKey(-1));
		assertEquals(1, (int) owned.get(11802));
	}

	@Test
	void dropsPlaceholdersEvenWithNonzeroQuantity()
	{
		// 14032 stands in for the whip placeholder variant; canonicalize
		// resolves it to the real whip id, so without the placeholder check
		// it would be counted as an owned whip
		IntUnaryOperator canonicalize = id -> id == 14032 ? 4151 : id;
		IntPredicate isPlaceholder = id -> id == 14032;
		service.updateSource(Source.BANK, new Item[]{
			new Item(14032, 1), // placeholder
			new Item(11802, 1),
		}, canonicalize, isPlaceholder);

		Map<Integer, Integer> owned = service.getOwnedQuantities();
		assertFalse(owned.containsKey(4151));
		assertFalse(owned.containsKey(14032));
		assertEquals(1, (int) owned.get(11802));
	}

	@Test
	void canonicalizesNotedItems()
	{
		// noted whip (4152) canonicalizes to whip (4151)
		IntUnaryOperator canonicalize = id -> id == 4152 ? 4151 : id;
		service.updateSource(Source.INVENTORY, new Item[]{new Item(4152, 3), new Item(4151, 1)}, canonicalize, NO_PLACEHOLDERS);

		Map<Integer, Integer> owned = service.getOwnedQuantities();
		assertEquals(4, (int) owned.get(4151));
		assertFalse(owned.containsKey(4152));
	}

	@Test
	void replacesSourceContentsOnUpdate()
	{
		service.updateSource(Source.INVENTORY, new Item[]{new Item(4151, 1)}, IDENTITY, NO_PLACEHOLDERS);
		service.updateSource(Source.INVENTORY, new Item[]{new Item(560, 100)}, IDENTITY, NO_PLACEHOLDERS);

		Map<Integer, Integer> owned = service.getOwnedQuantities();
		assertFalse(owned.containsKey(4151));
		assertEquals(100, (int) owned.get(560));
	}

}
