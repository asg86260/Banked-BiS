package com.bankbis.ui;

import javax.inject.Singleton;

/**
 * Armed state of the "pick a monster" eyedropper. Armed from the panel's
 * Swing thread, checked/disarmed from the client thread on click.
 */
@Singleton
public class TargetPickerState
{

	private volatile boolean armed;

	public boolean isArmed()
	{
		return armed;
	}

	public void setArmed(boolean armed)
	{
		this.armed = armed;
	}

}
