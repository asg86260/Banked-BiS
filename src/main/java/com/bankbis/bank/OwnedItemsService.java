package com.bankbis.bank;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.IntUnaryOperator;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

/**
 * Tracks every item the player owns across bank, group storage, inventory,
 * and worn equipment. Containers are snapshotted whenever the client updates
 * them and persisted per-account, so a full picture is available even after
 * the bank is closed or the client restarted (bank/group storage require
 * having been opened at least once).
 */
@Slf4j
@Singleton
public class OwnedItemsService
{

	public enum Source
	{
		BANK,
		GROUP_STORAGE,
		INVENTORY,
		EQUIPMENT,
	}

	private static final Map<Integer, Source> CONTAINER_SOURCES = buildContainerSources();

	private static final Type PERSIST_TYPE = new TypeToken<EnumMap<Source, HashMap<Integer, Integer>>>()
	{
	}.getType();

	private final Client client;
	private final ItemManager itemManager;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final File dataDir;

	private final Map<Source, Map<Integer, Integer>> owned = new EnumMap<>(Source.class);
	private long loadedAccount = -1;

	@Inject
	public OwnedItemsService(Client client, ItemManager itemManager, Gson gson, ScheduledExecutorService executor)
	{
		this(client, itemManager, gson, executor, new File(RuneLite.RUNELITE_DIR, "bank-bis"));
	}

	OwnedItemsService(Client client, ItemManager itemManager, Gson gson, ScheduledExecutorService executor, File dataDir)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.gson = gson;
		this.executor = executor;
		this.dataDir = dataDir;
	}

	private static Map<Integer, Source> buildContainerSources()
	{
		Map<Integer, Source> sources = new HashMap<>();
		sources.put(InventoryID.BANK, Source.BANK);
		sources.put(InventoryID.INV_GROUP_TEMP, Source.GROUP_STORAGE);
		sources.put(InventoryID.INV, Source.INVENTORY);
		sources.put(InventoryID.WORN, Source.EQUIPMENT);
		return Collections.unmodifiableMap(sources);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		long account = client.getAccountHash();
		if (account == -1 || account == loadedAccount)
		{
			return;
		}

		synchronized (owned)
		{
			owned.clear();
			loadedAccount = account;
		}
		executor.execute(() -> loadFromDisk(account));
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		Source source = CONTAINER_SOURCES.get(e.getContainerId());
		if (source == null)
		{
			return;
		}

		// runs on the client thread, where canonicalize is safe
		updateSource(source, e.getItemContainer().getItems(), itemManager::canonicalize);

		long account = client.getAccountHash();
		if (account != -1)
		{
			Map<Source, Map<Integer, Integer>> copy = copyOwned();
			executor.execute(() -> saveToDisk(account, copy));
		}
	}

	/**
	 * Replaces one source's contents. Noted items and placeholders resolve
	 * to their canonical id; empty slots and zero quantities (placeholders)
	 * are dropped.
	 */
	void updateSource(Source source, Item[] items, IntUnaryOperator canonicalize)
	{
		Map<Integer, Integer> quantities = new HashMap<>();
		for (Item item : items)
		{
			if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			int id = canonicalize.applyAsInt(item.getId());
			quantities.merge(id, item.getQuantity(), Integer::sum);
		}

		synchronized (owned)
		{
			owned.put(source, quantities);
		}
	}

	/**
	 * All owned items merged across sources, quantities summed.
	 */
	public Map<Integer, Integer> getOwnedQuantities()
	{
		Map<Integer, Integer> merged = new HashMap<>();
		synchronized (owned)
		{
			for (Map<Integer, Integer> source : owned.values())
			{
				source.forEach((id, qty) -> merged.merge(id, qty, Integer::sum));
			}
		}
		return merged;
	}

	public Map<Integer, Integer> getSource(Source source)
	{
		synchronized (owned)
		{
			Map<Integer, Integer> quantities = owned.get(source);
			return quantities == null ? Collections.emptyMap() : new HashMap<>(quantities);
		}
	}

	public boolean hasBankSnapshot()
	{
		synchronized (owned)
		{
			return owned.containsKey(Source.BANK);
		}
	}

	private Map<Source, Map<Integer, Integer>> copyOwned()
	{
		Map<Source, Map<Integer, Integer>> copy = new EnumMap<>(Source.class);
		synchronized (owned)
		{
			owned.forEach((k, v) -> copy.put(k, new HashMap<>(v)));
		}
		return copy;
	}

	private File fileFor(long account)
	{
		return new File(dataDir, "owned-" + Long.toHexString(account) + ".json");
	}

	private void loadFromDisk(long account)
	{
		File file = fileFor(account);
		if (!file.exists())
		{
			return;
		}

		try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8))
		{
			Map<Source, Map<Integer, Integer>> persisted = gson.fromJson(reader, PERSIST_TYPE);
			if (persisted == null)
			{
				return;
			}
			synchronized (owned)
			{
				// live container updates win over persisted state
				persisted.forEach(owned::putIfAbsent);
			}
			log.debug("Loaded owned-items snapshot for account {}", Long.toHexString(account));
		}
		catch (Exception e)
		{
			log.warn("Failed to load owned-items snapshot", e);
		}
	}

	private void saveToDisk(long account, Map<Source, Map<Integer, Integer>> snapshot)
	{
		File file = fileFor(account);
		try
		{
			Files.createDirectories(dataDir.toPath());
			Path tmp = file.toPath().resolveSibling(file.getName() + ".tmp");
			Files.write(tmp, gson.toJson(snapshot, PERSIST_TYPE).getBytes(StandardCharsets.UTF_8));
			Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException e)
		{
			log.warn("Failed to save owned-items snapshot", e);
		}
	}

}
