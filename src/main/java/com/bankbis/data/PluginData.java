package com.bankbis.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.runelite.client.RuneLite;

/**
 * The plugin's on-disk home (.runelite/bank-bis/) and shared file helpers.
 */
public final class PluginData
{

	public static final File DIR = new File(RuneLite.RUNELITE_DIR, "bank-bis");

	private PluginData()
	{
	}

	/**
	 * Writes via a unique temp file + rename so readers never observe a
	 * partial file, and concurrent writers cannot corrupt each other.
	 */
	public static void writeAtomically(File target, byte[] bytes) throws IOException
	{
		Files.createDirectories(target.toPath().getParent());
		Path tmp = Files.createTempFile(target.toPath().getParent(), target.getName(), ".tmp");
		try
		{
			Files.write(tmp, bytes);
			try
			{
				Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException e)
			{
				Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		}
		finally
		{
			Files.deleteIfExists(tmp);
		}
	}

}
