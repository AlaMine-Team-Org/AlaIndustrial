package dev.alaindustrial.client.screen;

import net.minecraft.resources.Identifier;

/**
 * Everything a machine screen with no logic of its own is made of (MOD-716, CLI-3 item 3): its GUI atlas,
 * its energy bar, its left-to-right progress sprite and its status row. A {@link LayoutMachineScreen} draws
 * all four from this one declaration, so such a screen is a constant and a constructor instead of a
 * texture method, a bar method, a progress spec handed through a constructor and an
 * {@code extractContents} override that draws the status.
 *
 * @param texture    the 256 × 256 atlas — see {@link MachineScreen#texture()}
 * @param energyBar  the vertical energy bar the base draws with its tooltip, or null for none
 * @param progress   the progress sprite a {@link ProgressMachineScreen} fills, or null for a screen that
 *                   draws its own indicator (a flame, a sun, twin arrows)
 * @param statusBand where the screen's blocking status is written, or null for a screen that writes its
 *                   status itself (in another colour, in another pass, or never)
 */
public record MachineLayout(Identifier texture, MachineScreen.EnergyBarSpec energyBar,
		ProgressMachineScreen.ProgressSpec progress, StatusBand statusBand) {

	/**
	 * The row a blocking status is centred in: its baseline and the band between two x edges, all relative
	 * to the frame — drawn through {@link MachineScreen#drawFittedStatus} in {@link GuiStyle#STATUS_BLOCKING}.
	 */
	public record StatusBand(int y, int left, int right) {
		/** The family's row: below the slot row, between the energy bar and the frame's inner border. */
		public static final StatusBand STANDARD = new StatusBand(MachineScreen.STATUS_ROW_Y,
				MachineScreen.STATUS_ROW_LEFT, MachineScreen.STATUS_ROW_RIGHT);
	}

	/** A screen with an atlas and an energy bar, and nothing more declared. */
	public static MachineLayout of(Identifier texture, MachineScreen.EnergyBarSpec energyBar) {
		return new MachineLayout(texture, energyBar, null, null);
	}

	/** The same layout with a progress sprite. */
	public MachineLayout withProgress(ProgressMachineScreen.ProgressSpec sprite) {
		return new MachineLayout(texture, energyBar, sprite, statusBand);
	}

	/** The same layout with a status row. */
	public MachineLayout withStatus(StatusBand band) {
		return new MachineLayout(texture, energyBar, progress, band);
	}
}
