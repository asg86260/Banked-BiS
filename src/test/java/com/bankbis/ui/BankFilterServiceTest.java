package com.bankbis.ui;

import java.util.Set;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.banktags.BankTag;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BankFilterServiceTest
{

	@Mock
	private ClientThread clientThread;

	@Mock
	private TagManager tagManager;

	@Mock
	private BankTagsService bankTagsService;

	@Mock
	private ItemManager itemManager;

	private BankFilterService service;

	@BeforeEach
	void setUp()
	{
		// run client-thread work inline
		doAnswer(inv ->
		{
			((Runnable) inv.getArgument(0)).run();
			return null;
		}).when(clientThread).invoke(any(Runnable.class));
		when(itemManager.canonicalize(anyInt())).thenAnswer(inv -> inv.getArgument(0));
		service = new BankFilterService(clientThread, tagManager, bankTagsService, itemManager);
	}

	@Test
	void showRegistersVirtualTagAndOpensIt()
	{
		service.show(Set.of(4151));

		ArgumentCaptor<BankTag> tag = ArgumentCaptor.forClass(BankTag.class);
		verify(tagManager).registerTag(eq("banked-bis"), tag.capture());
		verify(bankTagsService).openBankTag(eq("banked-bis"), anyInt());

		assertTrue(tag.getValue().contains(4151));
		assertFalse(tag.getValue().contains(11802));
	}

	@Test
	void clearClosesTheFilterWeOpened()
	{
		service.show(Set.of(4151));
		service.clear();
		verify(bankTagsService).closeBankTag();
		verify(tagManager).unregisterTag("banked-bis");
	}

	@Test
	void clearWithoutShowNeverClosesForeignTags()
	{
		// nothing of ours is open; a user's own tag tab must stay open
		service.clear();
		verify(bankTagsService, never()).closeBankTag();
		verify(tagManager).unregisterTag("banked-bis");
	}

	@Test
	void bankTagsFailuresAreSwallowed()
	{
		doAnswer(inv ->
		{
			throw new IllegalStateException("bank tags unavailable");
		}).when(tagManager).registerTag(anyString(), any(BankTag.class));
		service.show(Set.of(4151)); // must not throw
	}

}
