package com.bankbis.data;

import com.duckblade.osrs.dpscalc.calc.model.ItemStats;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Loads equipment and monster data maintained by the OSRS Wiki team
 * (weirdgloop/osrs-dps-calc), with an on-disk cache under .runelite/bank-bis/.
 */
@Slf4j
@Singleton
public class WikiDataService
{

	private static final String BASE_URL = "https://raw.githubusercontent.com/weirdgloop/osrs-dps-calc/main/cdn/json/";
	private static final Duration CACHE_TTL = Duration.ofHours(24);

	private static final Type EQUIPMENT_TYPE = new TypeToken<List<EquipmentJson>>()
	{
	}.getType();
	private static final Type MONSTERS_TYPE = new TypeToken<List<MonsterJson>>()
	{
	}.getType();

	private final OkHttpClient okHttpClient;
	private final Gson gson;
	private final File cacheDir;

	@Getter
	private volatile Map<Integer, ItemStats> itemStatsById = Collections.emptyMap();

	@Getter
	private volatile Map<Integer, NpcStats> npcStatsById = Collections.emptyMap();

	@Inject
	public WikiDataService(OkHttpClient okHttpClient, Gson gson)
	{
		this(okHttpClient, gson, new File(RuneLite.RUNELITE_DIR, "bank-bis"));
	}

	WikiDataService(OkHttpClient okHttpClient, Gson gson, File cacheDir)
	{
		this.okHttpClient = okHttpClient.newBuilder()
			.addInterceptor(chain -> chain.proceed(
				chain.request()
					.newBuilder()
					.header("User-Agent", "RuneLite plugin bank-bis")
					.build()))
			.build();
		this.gson = gson;
		this.cacheDir = cacheDir;
	}

	/**
	 * Loads both data files, from network if the disk cache is stale,
	 * falling back to the disk cache when the network is unavailable.
	 * Never call on the client thread.
	 */
	public CompletableFuture<Void> load()
	{
		CompletableFuture<Void> equipment = loadFile("equipment.json", EQUIPMENT_TYPE, (List<EquipmentJson> parsed) ->
		{
			Map<Integer, ItemStats> byId = new HashMap<>();
			for (EquipmentJson e : parsed)
			{
				byId.put(e.getId(), WikiDataMapper.toItemStats(e));
			}
			itemStatsById = Collections.unmodifiableMap(byId);
			log.debug("Loaded {} equipment entries", byId.size());
		});

		CompletableFuture<Void> monsters = loadFile("monsters.json", MONSTERS_TYPE, (List<MonsterJson> parsed) ->
		{
			Map<Integer, NpcStats> byId = new HashMap<>();
			for (MonsterJson m : parsed)
			{
				byId.put(m.getId(), NpcStats.of(m));
			}
			npcStatsById = Collections.unmodifiableMap(byId);
			log.debug("Loaded {} monster entries", byId.size());
		});

		return CompletableFuture.allOf(equipment, monsters);
	}

	private <T> CompletableFuture<Void> loadFile(String fileName, Type type, Consumer<T> onParsed)
	{
		File cached = new File(cacheDir, fileName);
		if (isFresh(cached))
		{
			return CompletableFuture.runAsync(() -> parseFromDisk(cached, type, onParsed));
		}

		CompletableFuture<Void> future = new CompletableFuture<>();
		Request request = new Request.Builder().url(BASE_URL + fileName).build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						throw new IOException("Request for " + fileName + " failed: " + response);
					}
					byte[] bytes = body.bytes();
					T parsed = gson.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
					onParsed.accept(parsed);
					writeCache(cached, bytes);
					future.complete(null);
				}
				catch (Exception e)
				{
					fallBackToDisk(e);
				}
			}

			@Override
			public void onFailure(Call call, IOException e)
			{
				fallBackToDisk(e);
			}

			private void fallBackToDisk(Exception cause)
			{
				if (cached.exists())
				{
					log.debug("Fetch of {} failed, using stale disk cache", fileName, cause);
					try
					{
						parseFromDisk(cached, type, onParsed);
						future.complete(null);
						return;
					}
					catch (Exception e)
					{
						cause = e;
					}
				}
				log.warn("Failed to load {}", fileName, cause);
				future.completeExceptionally(cause);
			}
		});
		return future;
	}

	private <T> void parseFromDisk(File cached, Type type, Consumer<T> onParsed)
	{
		try (Reader reader = Files.newBufferedReader(cached.toPath(), StandardCharsets.UTF_8))
		{
			T parsed = gson.fromJson(reader, type);
			onParsed.accept(parsed);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Failed to read cached " + cached.getName(), e);
		}
	}

	private void writeCache(File cached, byte[] bytes)
	{
		try
		{
			Files.createDirectories(cacheDir.toPath());
			Path tmp = cached.toPath().resolveSibling(cached.getName() + ".tmp");
			Files.write(tmp, bytes);
			Files.move(tmp, cached.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException e)
		{
			log.warn("Failed to write cache file {}", cached, e);
		}
	}

	private boolean isFresh(File cached)
	{
		return cached.exists()
			&& Instant.ofEpochMilli(cached.lastModified()).plus(CACHE_TTL).isAfter(Instant.now());
	}

}
