package com.bankbis.ui;

import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import net.runelite.client.ui.laf.RuneLiteLAF;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Renders the panel with fake data to build/panel-preview.png so UI work
 * can be reviewed without launching the client. Skipped when headless.
 * For a live, clickable version of the same panel, run
 * {@link PanelPreviewApp#main}.
 */
class PanelPreviewTest
{

	private static final File OUTPUT = new File("build/panel-preview.png");

	@Test
	void renderPreview() throws Exception
	{
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				UIManager.setLookAndFeel(new RuneLiteLAF());
				BankBisPanel panel = PanelPreviewApp.createPanel();
				panel.render(PanelPreviewApp.sampleTarget(), PanelPreviewApp.sampleResult(), null);

				JFrame frame = new JFrame();
				frame.setUndecorated(true);
				frame.add(panel);
				frame.pack();
				frame.setSize(242, Math.max(650, panel.getPreferredSize().height + 20));
				frame.validate();

				BufferedImage shot = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics2D g = shot.createGraphics();
				frame.getContentPane().paint(g);
				g.dispose();
				OUTPUT.getParentFile().mkdirs();
				ImageIO.write(shot, "png", OUTPUT);
				frame.dispose();
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});

		assertTrue(OUTPUT.exists());
	}

}
