package dev.alaindustrial.client.render;

/**
 * Every item tint source the mod adds to vanilla's late-bound registry, behind one call both loaders
 * make. No gate compares the loaders' client init line by line, so a source registered on one loader
 * only would leave the build green and its item uncoloured on the other (MOD-452): a new source goes in
 * here, never as a second line in a loader's client class.
 */
public final class ModItemTintSources {
	private ModItemTintSources() {
	}

	public static void register() {
		FluidTankItemTintSource.register();
		CapsuleGlassTintSource.register();
	}
}
