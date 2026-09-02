package dev.alaindustrial.core.fluid;

import java.util.function.Predicate;
import dev.alaindustrial.core.energy.EnergyBuffer;
import dev.alaindustrial.core.energy.EnergyPort;
import net.minecraft.core.IdMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The platform-neutral single-fluid tank backing the pump and geothermal generator (MOD-028). Owns the
 * stored fluid + amount and implements {@link FluidPort} directly, mirroring how {@link EnergyBuffer}
 * is both the backing store and its own self-view.
 *
 * <p><b>Semantics.</b> The transaction-safe {@link #insert}/{@link #extract} math is an exact
 * re-implementation of Fabric's {@code SingleVariantStorage} and NeoForge's {@code ResourceStacksResourceHandler}
 * (byte-for-byte identical transfer math on both): {@code inserted = min(maxAmount, capacity - amount)}
 * when the tank is empty or already holds the same fluid and {@code canInsert} allows it;
 * {@code extracted = min(maxAmount, amount)} when the requested fluid matches what is stored and
 * {@code canExtract} allows it. A non-zero move enlists the tank with the transaction (snapshot-before-mutate,
 * reusing {@link EnergyPort.Participant}) and then adjusts {@link #fluid}/{@link #amount}.
 *
 * <p><b>L1 testability.</b> Because {@link #insert}/{@link #extract} take a {@link FluidHolder}
 * (which wraps {@code net.minecraft.world.level.material.Fluid}, a type absent from {@code :common}'s
 * L1 test runtime), this class itself is not exercised by the L1 JUnit suite — its loader-neutral
 * behaviour is pinned instead by the Fabric L2 gametests. The <em>pure arithmetic</em> ({@code capacity - amount},
 * the {@code Math.min} clamps, the {@code amount == 0} fluid-clear tests, the capacity guard) is
 * extracted into {@link TankMath} and covered there by {@code TankMathTest} + pitest (see MOD-113):
 * every {@code TankMath.*} call below is a one-line delegate to the tested helper.
 *
 * <p><b>Fluid lifecycle: deferred clear, not snapshotted.</b> {@link EnergyPort.Participant} is hard
 * {@code long}-typed (mirrors {@link EnergyBuffer}'s single {@code amount} field), so this tank's
 * transactional snapshot/rollback only carries {@link #amount}; the {@link #fluid} field is never put into
 * the snapshot journal. To keep `amount > 0 ⇒ fluid ≠ EMPTY` true across a rollback, {@link #extract}
 * therefore does <em>not</em> clear {@code fluid} when it drives {@code amount} to 0 — clearing happens at
 * the two transaction terminals instead: {@link #readSnapshot} on a rollback to 0, {@link #onFinalCommit}
 * on a commit to 0. This is what lets a full-drain followed by a rollback restore both {@code amount}
 * <em>and</em> {@code fluid}: extract left {@code fluid} in place, and the rollback to a positive
 * {@code amount} has nothing to undo on the {@code fluid} field. (An earlier version cleared {@code fluid}
 * eagerly inside {@code extract}; that broke the cross-mod capability contract — a rolled-back full drain
 * left the tank reporting {@code amount > 0, fluid == EMPTY}, making it invisible to other mods probing it
 * via {@code fluid()}.)
 *
 * <p><b>Transactions.</b> This tank is an {@link EnergyPort.Participant}: it snapshots/restores
 * {@link #amount}, and fires {@link #onCommit} once when the outermost modifying transaction commits. The
 * loader's {@link EnergyPort.Txn} bridges these hooks onto its native snapshot journal exactly as it does
 * for {@link EnergyBuffer} — see {@link FluidPort} class doc for why fluid reuses the energy transaction
 * seam instead of a parallel type.
 *
 * <p><b>Direct field access (internal drain/production).</b> {@link #fluid} and {@link #amount} are
 * public and mutable so machine content (the geothermal generator burning its own tank) can mutate them
 * directly outside any transaction — the same pattern {@link EnergyBuffer#amount} allows. Direct mutators
 * MUST uphold the same invariant (clear {@link #fluid} to {@link FluidHolder#EMPTY} whenever they drop
 * {@link #amount} to 0).
 *
 * <p><b>Persistence (MOD-556).</b> The tank writes and reads itself through {@link #save} / {@link #load}.
 * Before that, "amount + fluid registry id" was copied verbatim into six block entities together with the
 * key encoder, the key decoder and the GUI sync-id clamp — eighteen methods for one format, where a single
 * forgotten edit would have silently emptied a player's machine. The on-disk shape is unchanged by the
 * move: same key literals, same values, same order.
 */
public class FluidTank implements FluidPort, EnergyPort.Participant {

	/** Fluid currently held, or {@link FluidHolder#EMPTY}. Public — see class doc. */
	public FluidHolder fluid = FluidHolder.EMPTY;

	/** Stored amount in mB. Public — see class doc. */
	public long amount;

	public final long capacity;
	private final Predicate<FluidHolder> canInsert;
	private final Predicate<FluidHolder> canExtract;

	/** Fired once after the outermost transaction that inserted/extracted through this tank commits. */
	private final Runnable onCommit;

	/**
	 * @param capacity   maximum mB this tank holds
	 * @param canInsert  which fluids may be inserted (e.g. pump/geo: lava only)
	 * @param canExtract which fluids may be extracted by a neighbour (pump: any; geo: never — R-CON-08)
	 * @param onCommit   run once when the outermost modifying transaction commits (persistence + wake);
	 *                   may be a no-op
	 */
	public FluidTank(long capacity, Predicate<FluidHolder> canInsert, Predicate<FluidHolder> canExtract,
			Runnable onCommit) {
		// Capacity guard extracted to TankMath.checkCapacity so the L1 suite (and pitest) can cover the
		// < 0 boundary without a live Minecraft runtime (FluidHolder wraps net.minecraft.Fluid — see
		// TankMath class doc). Pure extract: identical check, identical exception.
		this.capacity = TankMath.checkCapacity(capacity);
		this.canInsert = canInsert;
		this.canExtract = canExtract;
		this.onCommit = onCommit;
	}

	@Override
	public long insert(FluidHolder inserted, long maxAmount, EnergyPort.Txn txn) {
		if (maxAmount < 0) {
			throw new IllegalArgumentException("maxAmount must be non-negative");
		}
		if (inserted == null || inserted.isEmpty() || maxAmount == 0) {
			return 0;
		}
		if (!canInsert.test(inserted)) {
			return 0;
		}
		if (!fluid.isEmpty() && !fluid.equals(inserted)) {
			return 0; // tank already holds a different fluid — single-variant, like SingleVariantStorage
		}
		// toInsert math extracted to TankMath.toInsert (the load-bearing Math.min + capacity-amount
		// kernel) so the L1 suite + pitest cover the +/- and min/max mutants without a live
		// FluidHolder. Pure extract: returns Math.min(maxAmount, capacity - amount).
		long toInsert = TankMath.toInsert(amount, capacity, maxAmount);
		if (toInsert > 0) {
			txn.enlist(this);
			fluid = inserted;
			amount += toInsert;
			return toInsert;
		}
		return 0;
	}

	@Override
	public long extract(FluidHolder requested, long maxAmount, EnergyPort.Txn txn) {
		if (maxAmount < 0) {
			throw new IllegalArgumentException("maxAmount must be non-negative");
		}
		if (requested == null || requested.isEmpty() || maxAmount == 0) {
			return 0;
		}
		if (fluid.isEmpty() || !fluid.equals(requested)) {
			return 0;
		}
		if (!canExtract.test(fluid)) {
			return 0;
		}
		// toExtract math extracted to TankMath.toExtract (Math.min(maxAmount, amount)) — same rationale
		// as toInsert above: makes the stored-amount clamp L1-testable + pitest-covered.
		long toExtract = TankMath.toExtract(amount, maxAmount);
		if (toExtract > 0) {
			txn.enlist(this);
			amount -= toExtract;
			// NOTE: `fluid` is NOT cleared here even when amount hits 0. The snapshot journal only carries
			// `amount` (the EnergyPort.Participant API is long-typed — see EnergyPort.Participant
			// class doc), so on a rollback to a positive amount this tank could not restore a `fluid` that
			// extract() had already wiped — the tank would read amount>0 with fluid==EMPTY, becoming
			// invisible to capability readers (TankAsFluidStorage/TankAsResourceHandler report fluid()). The
			// fluid-empty invariant is upheld instead at the two transaction terminals: readSnapshot clears
			// `fluid` on a rollback to 0, onFinalCommit clears it on a commit to 0.
			return toExtract;
		}
		return 0;
	}

	@Override
	public FluidHolder fluid() {
		return fluid;
	}

	@Override
	public long getAmount() {
		return amount;
	}

	@Override
	public long getCapacity() {
		return capacity;
	}

	@Override
	public boolean supportsInsertion() {
		return TankMath.supportsOp(capacity);
	}

	@Override
	public boolean supportsExtraction() {
		return TankMath.supportsOp(capacity);
	}

	// --- EnergyPort.Participant: the loader's native journal drives these ---

	@Override
	public long createSnapshot() {
		return amount;
	}

	@Override
	public void readSnapshot(long snapshot) {
		amount = snapshot;
		// Restore the fluid-empty invariant (see class doc): a rollback to 0 must clear the fluid. A
		// rollback to a positive amount leaves `fluid` untouched — it was non-empty before the transaction
		// (extract requires an exact fluid match), and extract() no longer pre-clears it (see extract()),
		// so the pre-transaction fluid is still in place and needs no separate restore.
		// The `amount == 0` test extracted to TankMath.shouldClearFluid for L1/pitest coverage.
		if (TankMath.shouldClearFluid(amount)) {
			fluid = FluidHolder.EMPTY;
		}
	}

	@Override
	public void onFinalCommit() {
		// Uphold the fluid-empty invariant on the commit path too: a committed extract that drained the
		// tank to 0 must clear `fluid` (extract() no longer does this itself — see extract()).
		// The `amount == 0` test extracted to TankMath.shouldClearFluid for L1/pitest coverage.
		if (TankMath.shouldClearFluid(amount)) {
			fluid = FluidHolder.EMPTY;
		}
		onCommit.run();
	}

	// --- persistence (26.2 ValueInput/ValueOutput) ----------------------------------------------

	/**
	 * Key suffix holding the stored amount in mB. Together with {@link #FLUID_KEY_SUFFIX} it fixes the
	 * on-disk shape of a tank: {@code <prefix>Mb} + {@code <prefix>Fluid}, which is exactly what the six
	 * machines wrote before MOD-556 moved the code here. Changing either literal rewrites every existing
	 * world's tanks to empty — that is what the format version on the block entity is for.
	 */
	private static final String AMOUNT_KEY_SUFFIX = "Mb";

	/** Key suffix holding the stored fluid's registry id, or {@code ""} for an empty tank. */
	private static final String FLUID_KEY_SUFFIX = "Fluid";

	/**
	 * Write this tank under {@code prefix} as the pair {@code <prefix>Mb} + {@code <prefix>Fluid}
	 * (MOD-556).
	 *
	 * <p>The fluid id is written even when the tank is empty (as {@code ""}), because that is what the
	 * six machines wrote before this method existed and the byte-for-byte shape is the point of the
	 * move. The identity is never implicit: a machine's tank accepts whatever a datapack recipe or a
	 * foreign {@code c:} tag puts in it, so hardcoding the fluid on load is the geothermal generator's
	 * save-corruption bug (flagged in MOD-261).
	 */
	public void save(ValueOutput output, String prefix) {
		output.putLong(prefix + AMOUNT_KEY_SUFFIX, amount);
		output.putString(prefix + FLUID_KEY_SUFFIX, registryKey(fluid));
	}

	/**
	 * Restore this tank from the pair {@link #save} wrote, clamped to {@link #capacity} and upholding the
	 * tank invariant in both directions: no fluid means no amount, and no amount means no fluid. A save
	 * whose fluid id no longer resolves (its mod was removed) drops the contents rather than keeping a
	 * phantom amount the machine could never consume.
	 */
	public void load(ValueInput input, String prefix) {
		load(input, prefix, FluidHolder.EMPTY, 0L);
	}

	/**
	 * {@link #load(ValueInput, String)} with the two legacy fallbacks the pump needs.
	 *
	 * @param legacyAmountMb amount to use when {@code <prefix>Mb} is absent or zero — the pump converts
	 *                       the Fabric v0.1.0 droplet-valued {@code "FluidTank"} key into this
	 * @param legacyFluid    fluid to assume when a POSITIVE amount carries no resolvable id — the very
	 *                       first pump revision was lava-only and wrote no id at all, so dropping those
	 *                       contents would empty a real player's pump
	 */
	public void load(ValueInput input, String prefix, FluidHolder legacyFluid, long legacyAmountMb) {
		long stored = input.getLongOr(prefix + AMOUNT_KEY_SUFFIX, 0L);
		if (stored == 0L) {
			stored = legacyAmountMb;
		}
		amount = Math.max(0L, Math.min(capacity, stored));
		FluidHolder restored = fromRegistryKey(input.getStringOr(prefix + FLUID_KEY_SUFFIX, ""));
		if (amount > 0 && restored.isEmpty()) {
			restored = legacyFluid;
		}
		fluid = amount > 0 ? restored : FluidHolder.EMPTY;
		if (fluid.isEmpty()) {
			amount = 0L;
		}
	}

	/**
	 * What {@link #fluidSyncId()} reports for an empty (or unrepresentable) tank — the vanilla
	 * {@link IdMap#DEFAULT}. Screens compare against it to decide whether to draw a fluid at all, so the
	 * sentinel and the method that emits it are deliberately one declaration: before MOD-556 each machine
	 * held its own copy of both, and a copy of a sentinel is a copy that can drift from its producer.
	 */
	public static final int FLUID_ID_NONE = IdMap.DEFAULT;

	/**
	 * The {@link BuiltInRegistries#FLUID} registry id of the stored fluid ({@link IdMap#DEFAULT} when the
	 * tank is empty), for a client to resolve the fluid type over a {@code ContainerData} channel.
	 *
	 * <p>Ids above {@link Short#MAX_VALUE} report as empty rather than as their truncated low 16 bits,
	 * which a channel's short encoding would otherwise resolve to an unrelated fluid — a wrongly-labelled
	 * tank is worse than an unlabelled one. Only reachable in a pack with &gt;32767 registered fluids;
	 * the tank itself is unaffected either way.
	 */
	public int fluidSyncId() {
		if (fluid.isEmpty()) {
			return FLUID_ID_NONE;
		}
		int id = BuiltInRegistries.FLUID.getId(fluid.fluid());
		return id > Short.MAX_VALUE ? FLUID_ID_NONE : id;
	}

	/** Registry id of {@code fluid} for persistence (e.g. {@code "minecraft:water"}), or {@code ""}. */
	private static String registryKey(FluidHolder fluid) {
		return fluid.isEmpty() ? "" : BuiltInRegistries.FLUID.getKey(fluid.fluid()).toString();
	}

	/**
	 * Resolve a persisted registry id back to a fluid; anything absent, malformed or no longer
	 * registered reads as {@link FluidHolder#EMPTY}.
	 *
	 * <p>Note on the pump's pre-MOD-099 bare spellings {@code "lava"} / {@code "water"}: they need no
	 * branch of their own. {@code Identifier.tryParse} with no {@code ':'} falls through to
	 * {@code withDefaultNamespace}, so a bare path becomes {@code minecraft:<path>} and resolves exactly
	 * as the old special case did (verified against the 26.2 bytecode of
	 * {@code Identifier.tryBySeparator}). The pump gametest pins it.
	 */
	private static FluidHolder fromRegistryKey(String key) {
		if (key == null || key.isEmpty()) {
			return FluidHolder.EMPTY;
		}
		Identifier id = Identifier.tryParse(key);
		if (id == null) {
			return FluidHolder.EMPTY;
		}
		Fluid resolved = BuiltInRegistries.FLUID.getValue(id);
		return resolved == null ? FluidHolder.EMPTY : FluidHolder.of(resolved);
	}
}
