package dev.alaindustrial.client.compat;

import dev.alaindustrial.client.ServerBalance;

/**
 * The EU cost and the base time one recipe card prints, resolved when the card is DRAWN (MOD-743).
 *
 * <p><b>Why at draw time.</b> On a dedicated server the numbers that matter are the server's, and they
 * reach the client in {@code ServerBalance} only after the recipe viewer has built its cards: on NeoForge
 * the recipes are sent before the player-join hook that sends the config snapshot, on Fabric the snapshot
 * is queued behind them, and {@code /ala config reload} sends a new snapshot without restarting the
 * viewer at all. A figure baked into a card at registration is therefore the player's own file, whichever
 * class it was read through. Computed here, on every draw, it is always the server's current one — the
 * same choice MOD-695 made for the viewer's info pages.
 *
 * <p><b>The electric furnace's default cost.</b> A vanilla smelt mirrored into the electric furnace's
 * category carries no cost of its own ({@code VanillaSmeltingMirror.FURNACE_DEFAULT_ENERGY}, 0): the
 * machine runs it at its default operation, the same "energy &le; 0 means the machine's default" rule
 * the furnace itself applies. This class is the one place that turns that zero into the furnace's EU and
 * duration, and only for the electric furnace's family — on a card of another family a zero prints as a
 * zero. (That is the viewer's rule, not the machines': every machine of
 * {@code AbstractProcessingMachineBlockEntity} runs a recipe of energy &le; 0 at its default operation;
 * no shipped recipe outside the furnace's mirrors states one.)
 *
 * <p><b>REI's mirror arrives priced.</b> REI builds its cards on the SERVER and sends them over
 * (owner decision A2): the mirror reaches the client carrying the EU the server's filler stated, not its
 * own zero, and the wire format has no other field that tells a mirror from a mod recipe. Timing that EU
 * at the family's rate is not the furnace's duration once the speed multiplier is not 1, because the EU
 * carries the multiplier (at 0.3 a 100-tick, 2 EU/t smelt costs 333 EU, which read as 8.3 s against
 * JEI's 5 s).
 * {@link #resolveServerStated} therefore recognises the mirror by its price — see there for what that
 * costs.
 *
 * <p>Minecraft-free (it reads only {@link ServerBalance} and {@link RecipeViewerLayout}), so L1 covers it.
 *
 * @param energy EU one operation costs
 * @param ticks base processing time in ticks, without the global speed multiplier (the viewer shows the
 *     recipe's intrinsic time, see {@code MachineRecipeFamily.ticksFor})
 */
public record RecipeViewerCost(int energy, int ticks) {

	/** The label the card prints: {@code "400 EU · 10 s"}. */
	public String label() {
		return RecipeViewerLayout.costLabel(energy, ticks);
	}

	/**
	 * A recipe that states its own cost: the EU as written, the time at the family's draw per tick
	 * ({@code familyEuPerTick} — the server's, read through the family's declaration).
	 */
	public static RecipeViewerCost of(int energy, int familyEuPerTick) {
		return new RecipeViewerCost(energy, Math.max(1, energy / Math.max(1, familyEuPerTick)));
	}

	/**
	 * One vanilla smelt in the electric furnace on the server's numbers: the EU the furnace really ticks
	 * away ({@link ServerBalance#electricFurnaceVanillaSmeltEu}) and its base duration.
	 */
	public static RecipeViewerCost electricFurnaceDefault() {
		return new RecipeViewerCost(ServerBalance.electricFurnaceVanillaSmeltEu(),
				ServerBalance.electricFurnaceDuration());
	}

	/**
	 * The cost a card prints. {@code electricFurnaceFamily} is whether the recipe belongs to the electric
	 * furnace's family ({@code ModRecipes.SMELTING}); only there does a recipe energy of 0 or less stand
	 * for the furnace's default operation.
	 */
	public static RecipeViewerCost resolve(boolean electricFurnaceFamily, int recipeEnergy, int familyEuPerTick) {
		if (electricFurnaceFamily && recipeEnergy <= 0) {
			return electricFurnaceDefault();
		}
		return of(recipeEnergy, familyEuPerTick);
	}

	/**
	 * The cost a card prints when its EU was stated by the SERVER (REI): as {@link #resolve}, and in the
	 * electric furnace's family an EU equal to the server's vanilla-smelt price
	 * ({@link ServerBalance#electricFurnaceVanillaSmeltEu}) is read as the furnace's default operation too —
	 * that is the number the server's filler puts on a mirror, so REI prints the same EU and time as JEI.
	 *
	 * <p>Two known limits, both REI-only. A mod or datapack furnace recipe that costs exactly that EU is
	 * shown as a vanilla smelt: the EU is the same number, and so is the time whenever the price divides
	 * back into the duration (always at a speed multiplier of 1), otherwise its time reads as the furnace's
	 * duration instead of {@code energy ÷ rate}. And after {@code /ala config reload} the mirror still
	 * carries the old price until {@code /reload} rebuilds REI's cards; it no longer matches the new one, so
	 * the card prints the old EU timed at the new rate.
	 */
	public static RecipeViewerCost resolveServerStated(boolean electricFurnaceFamily, int statedEnergy,
			int familyEuPerTick) {
		if (electricFurnaceFamily && statedEnergy == ServerBalance.electricFurnaceVanillaSmeltEu()) {
			return electricFurnaceDefault();
		}
		return resolve(electricFurnaceFamily, statedEnergy, familyEuPerTick);
	}
}
