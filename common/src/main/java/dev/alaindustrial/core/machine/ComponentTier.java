package dev.alaindustrial.core.machine;

import dev.alaindustrial.Config;
import java.util.function.IntSupplier;

/**
 * The grades of a generator's consumable component (MOD-385) — the wind mill's rotor and the water
 * mill's wheel, each in three tiers, and the balance numbers that make them different.
 *
 * <p>Before this existed the component was a <b>binary gate</b>: {@code produce()} asked only "is the
 * slot empty?", so the same wooden rotor sat in the first T1 mill and in a storm T2 one, and the wind
 * branch had nothing to progress into. Each grade now carries its own three knobs:
 *
 * <ul>
 *   <li>{@link #outputMultiplier()} — scales the generator's mechanical rate. Applied <b>inside</b>
 *       {@code WindMillOutput.euFor} / {@code WaterMillOutput.euFor}, before their final clamp, so a
 *       better component reaches the existing {@code *MaxEuPerTick} ceiling sooner but can never
 *       raise it (see "Why the multiplier lives inside euFor" below).</li>
 *   <li>{@link #euPerDamage()} — EU of production per one durability point, read live every tick by
 *       {@code AbstractGeneratorBlockEntity#wearComponent}.</li>
 *   <li>{@link #maxDamage()} — the durability bar, baked into the item at registration.</li>
 * </ul>
 *
 * <p><b>Ladder</b> (source of truth: {@code docs/PERFORMANCE.md}). Every grade is strictly better
 * than the one before on both axes, so the choice is real rather than cosmetic:
 *
 * <table border="1">
 *   <caption>Component grades</caption>
 *   <tr><th>Grade</th><th>Output</th><th>Durability</th><th>EU/point</th><th>Total life</th></tr>
 *   <tr><td>{@link #WINDMILL_ROTOR}</td><td>×1.00</td><td>1000</td><td>480</td><td>480 000 EU</td></tr>
 *   <tr><td>{@link #WINDMILL_ROTOR_REINFORCED}</td><td>×1.25</td><td>3000</td><td>600</td><td>1 800 000 EU</td></tr>
 *   <tr><td>{@link #WINDMILL_ROTOR_ADVANCED}</td><td>×1.50</td><td>6000</td><td>720</td><td>4 320 000 EU</td></tr>
 *   <tr><td>{@link #WATER_MILL_WHEEL}</td><td>×1.00</td><td>1000</td><td>320</td><td>320 000 EU</td></tr>
 *   <tr><td>{@link #WATER_MILL_WHEEL_REINFORCED}</td><td>×1.25</td><td>3000</td><td>400</td><td>1 200 000 EU</td></tr>
 *   <tr><td>{@link #WATER_MILL_WHEEL_ADVANCED}</td><td>×1.50</td><td>6000</td><td>480</td><td>2 880 000 EU</td></tr>
 *   <tr><td>{@link #LIGHTNING_ROD_TIP}</td><td>×1.00</td><td>100</td><td>1000</td><td>100 000 EU</td></tr>
 *   <tr><td>{@link #LIGHTNING_ROD_TIP_REINFORCED}</td><td>×1.25</td><td>320</td><td>1250</td><td>400 000 EU</td></tr>
 *   <tr><td>{@link #LIGHTNING_ROD_TIP_ADVANCED}</td><td>×1.50</td><td>800</td><td>1500</td><td>1 200 000 EU</td></tr>
 * </table>
 *
 * <p>The conductor tips (MOD-386) join the same ladder with one difference worth stating: their
 * "output" is not a rate but the pair (capacitor size, bleed rate), and their wear is charged
 * <b>per strike</b> rather than per tick. The arithmetic is unchanged — {@code ComponentWear.step}
 * divides whatever EU it is handed by {@code euPerDamage}, so a single 20 000 EU call spends 20
 * durability points at once — which is why a tip's life is stated in STRIKES: 5 / 20 / 60 at the shipped 20 000 EU strike.
 * Their durability bars are two orders of magnitude shorter than a rotor's for that reason — the
 * rotor spends its life a few EU per tick, the tip spends a fifth of its own in one event.
 *
 * <p><b>Why {@code euPerDamage} grows with the multiplier.</b> Wear is charged on EU produced, so a
 * component that makes 25 % more EU would wear 25 % faster at a fixed rate and the promised gain in
 * calendar life would silently shrink. Raising the rate by the same factor keeps "wear per unit of
 * mechanical work" constant across the ladder: the entire life gain comes from {@code maxDamage},
 * which is exactly what the player is told.
 *
 * <p><b>Why the multiplier lives inside {@code euFor} and not at the call site.</b> Applying it to
 * the value {@code euFor} already returned would put it <em>after</em> that method's
 * {@code Math.min(..., maxOutput)}, making "a better rotor must not raise the cap" a property of
 * today's numbers rather than of the code — the next balance pass could break it silently. Feeding it
 * in before the clamp makes the ceiling hold for ANY multiplier an operator can configure.
 *
 * <p><b>Deliberately free of Minecraft types</b> (grades are keyed by their registry <em>path</em>,
 * a plain String), so the L1 unit suite — which runs without the game on the classpath — can assert
 * the ladder directly. Resolving an {@code ItemStack} to a grade is the caller's job; see
 * {@code AbstractGeneratorBlockEntity#tierOf}.
 *
 * <p>All numbers are read <b>live</b> from {@link Config} — the contract {@code core.energy.CableType}
 * established for cable grades — so a server operator can retune them from
 * {@code config/alaindustrial.json} without a code change.
 * Rotor and wheel keep <b>separate</b> knobs even where the defaults coincide (both ×1.25 at the
 * reinforced grade): T1 already stores {@code windMillRotorMaxDamage} and
 * {@code waterMillWheelMaxDamage} apart at an identical 1000, and an operator must be able to retune
 * the wind branch without touching the water one.
 */
public enum ComponentTier {
	/** Wooden rotor — the shipped T1 baseline. Its multiplier is 1.0 by default, not by hardcoding. */
	WINDMILL_ROTOR("windmill_rotor",
			() -> Config.windMillRotorOutputMultiplier,
			() -> Config.windMillRotorEuPerDamage,
			() -> Config.windMillRotorMaxDamage,
			() -> Config.repairBenchTier1EuCost),
	/** Reinforced rotor — tempered iron and an iron gear: ×1.25 output, ×3 durability. */
	WINDMILL_ROTOR_REINFORCED("windmill_rotor_reinforced",
			() -> Config.windMillRotorReinforcedOutputMultiplier,
			() -> Config.windMillRotorReinforcedEuPerDamage,
			() -> Config.windMillRotorReinforcedMaxDamage,
			() -> Config.repairBenchTier2EuCost),
	/** Advanced rotor — electronic circuitry: ×1.50 output, ×6 durability. The top grade. */
	WINDMILL_ROTOR_ADVANCED("windmill_rotor_advanced",
			() -> Config.windMillRotorAdvancedOutputMultiplier,
			() -> Config.windMillRotorAdvancedEuPerDamage,
			() -> Config.windMillRotorAdvancedMaxDamage,
			() -> Config.repairBenchTier3EuCost),
	/** Wooden water wheel — the shipped T1 baseline. */
	WATER_MILL_WHEEL("water_mill_wheel",
			() -> Config.waterMillWheelOutputMultiplier,
			() -> Config.waterMillWheelEuPerDamage,
			() -> Config.waterMillWheelMaxDamage,
			() -> Config.repairBenchTier1EuCost),
	/** Reinforced wheel — tempered iron and an iron gear: ×1.25 output, ×3 durability. */
	WATER_MILL_WHEEL_REINFORCED("water_mill_wheel_reinforced",
			() -> Config.waterMillWheelReinforcedOutputMultiplier,
			() -> Config.waterMillWheelReinforcedEuPerDamage,
			() -> Config.waterMillWheelReinforcedMaxDamage,
			() -> Config.repairBenchTier2EuCost),
	/** Advanced wheel — electronic circuitry: ×1.50 output, ×6 durability. The top grade. */
	WATER_MILL_WHEEL_ADVANCED("water_mill_wheel_advanced",
			() -> Config.waterMillWheelAdvancedOutputMultiplier,
			() -> Config.waterMillWheelAdvancedEuPerDamage,
			() -> Config.waterMillWheelAdvancedMaxDamage,
			() -> Config.repairBenchTier3EuCost),
	/**
	 * Copper conductor tip — the lightning rod's T1 baseline (MOD-386). Its multiplier scales the
	 * capacitor's SIZE and its bleed rate rather than a per-tick output, because the rod's input is an
	 * event: a better tip banks more of a strike and pays it out faster, it does not make lightning
	 * stronger. Both are clamped inside {@code LightningRodOutput}, so the ceilings still hold.
	 */
	LIGHTNING_ROD_TIP("lightning_rod_conductor_tip",
			() -> Config.lightningRodTipOutputMultiplier,
			() -> Config.lightningRodTipEuPerDamage,
			() -> Config.lightningRodTipMaxDamage,
			() -> Config.repairBenchTier1EuCost),
	/** Reinforced conductor tip — tempered iron: ×1.25 capacity/bleed, ×3 durability. */
	LIGHTNING_ROD_TIP_REINFORCED("lightning_rod_conductor_tip_reinforced",
			() -> Config.lightningRodTipReinforcedOutputMultiplier,
			() -> Config.lightningRodTipReinforcedEuPerDamage,
			() -> Config.lightningRodTipReinforcedMaxDamage,
			() -> Config.repairBenchTier2EuCost),
	/** Advanced conductor tip — electronic circuitry: ×1.50 capacity/bleed, ×6 durability. Top grade. */
	LIGHTNING_ROD_TIP_ADVANCED("lightning_rod_conductor_tip_advanced",
			() -> Config.lightningRodTipAdvancedOutputMultiplier,
			() -> Config.lightningRodTipAdvancedEuPerDamage,
			() -> Config.lightningRodTipAdvancedMaxDamage,
			() -> Config.repairBenchTier3EuCost);

	/**
	 * A {@code float}-valued supplier. {@link java.util.function.DoubleSupplier} would force every
	 * caller through {@code double} arithmetic, and {@code Math.round(double)} returns a {@code long}
	 * while {@code Math.round(float)} returns an {@code int} — the generators' rate maths is int/float
	 * throughout, so widening here would spread casts across four block entities for no benefit.
	 */
	@FunctionalInterface
	public interface FloatSupplier {
		float getAsFloat();
	}

	/** Registry path of the item this grade describes — the key {@link #forItemPath} matches on. */
	private final String itemPath;
	private final FloatSupplier outputMultiplier;
	private final IntSupplier euPerDamage;
	private final IntSupplier maxDamage;
	private final IntSupplier repairEuCost;

	ComponentTier(String itemPath, FloatSupplier outputMultiplier, IntSupplier euPerDamage,
			IntSupplier maxDamage, IntSupplier repairEuCost) {
		this.itemPath = itemPath;
		this.outputMultiplier = outputMultiplier;
		this.euPerDamage = euPerDamage;
		this.maxDamage = maxDamage;
		this.repairEuCost = repairEuCost;
	}

	/** Stable registry path of this grade's item — never rename without a save migration. */
	public String itemPath() {
		return itemPath;
	}

	/**
	 * Scale applied to the generator's mechanical rate. Fed into {@code WindMillOutput.euFor} /
	 * {@code WaterMillOutput.euFor} <b>before</b> their final clamp, so it can never lift a cap.
	 */
	public float outputMultiplier() {
		return outputMultiplier.getAsFloat();
	}

	/** EU of production per one durability point. Read live every tick by the wear path. */
	public int euPerDamage() {
		return euPerDamage.getAsInt();
	}

	/**
	 * The durability bar this grade's item is registered with. Baked at registration time, so a config
	 * change needs a restart — the same contract the T1 component has had since MOD-189.
	 *
	 * <p>Since MOD-384 this doubles as the <b>original</b> ceiling the repair bench decays from: a
	 * repaired stack carries a lowered per-stack {@code max_damage} component, so its own
	 * {@code getMaxDamage()} is no longer the baseline and cannot be used to compute the next step.
	 */
	public int maxDamage() {
		return maxDamage.getAsInt();
	}

	/**
	 * EU one repair of this grade costs at the repair bench (MOD-384). Read live from {@link Config},
	 * like every other number here.
	 *
	 * <p>Rotor and wheel of the same grade deliberately share one config key — unlike
	 * {@link #maxDamage()} and {@link #euPerDamage()}, which stay separate per family. Repair cost is
	 * priced off the grade's <em>materials</em> (plain plate → tempered plate → circuit), and those are
	 * identical for the rotor and the wheel of a grade, so two keys holding the same number for ever
	 * would be a rename waiting to drift rather than a knob anyone would turn independently.
	 */
	public int repairEuCost() {
		return repairEuCost.getAsInt();
	}

	/**
	 * The grade whose item has registry path {@code path}, or {@code null} when the path names no
	 * component of this mod.
	 *
	 * <p>Callers must have already established the namespace is this mod's: the lookup is on the path
	 * alone so the enum stays free of Minecraft types, which means an identically-pathed item from
	 * another namespace would otherwise match. {@code AbstractGeneratorBlockEntity#tierOf} does that
	 * check. A linear scan over six constants beats a static map here — it allocates nothing, and six
	 * reference comparisons per tick are far below the 7×7×7 interference sweeps these same block
	 * entities already run.
	 */
	public static ComponentTier forItemPath(String path) {
		for (ComponentTier tier : values()) {
			if (tier.itemPath.equals(path)) {
				return tier;
			}
		}
		return null;
	}
}
