package dev.alaindustrial.item;

import dev.alaindustrial.core.FluidPort;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Platform-neutral bridge to fluid containers held in a machine's own slots (MOD-107): moves one bucket
 * between a container item and a {@link FluidPort}, through whatever item fluid API the running loader
 * speaks — Fabric {@code FluidStorage.ITEM} versus NeoForge {@code Capabilities.Fluid.ITEM}. This is the
 * item-side counterpart of {@link dev.alaindustrial.core.FluidLookup} (which only resolves blocks), and the
 * slot-side counterpart of {@link ItemEnergyBridge} (which only reaches a player's inventory).
 *
 * <p><b>Why a capability and not a list of known items?</b> Both loaders publish this capability for vanilla
 * buckets <i>and</i> our own Vacuum Capsule ({@code CapsuleItemFluidStorage} / {@code CapsuleResourceHandler}),
 * and so does every other mod for its own cells and canisters. Going through the capability therefore serves
 * buckets, our capsule and foreign containers with one mechanism — a whitelist of item types could never
 * cover the last group, which is the whole point of the task.
 *
 * <p><b>All-or-nothing.</b> Every method moves either a whole bucket or nothing, and rolls back otherwise:
 * committing a partial move while leaving the container item unchanged would duplicate or void fluid (the
 * same hazard {@code CapsuleInteractions} guards against on the right-click path).
 *
 * <p>The active implementation is installed once at mod init by each loader's entrypoint via
 * {@link #install(ItemFluidBridge)}; common code reaches it through {@link #get()}.
 */
public interface ItemFluidBridge {

	/**
	 * Drain up to {@code maxMb} from the container item in {@code inSlot} into {@code tank}, leaving the
	 * emptied container in {@code outSlot}, and return how many millibuckets moved.
	 *
	 * <p>Returns 0 — changing nothing — when the slot is empty, its item exposes no fluid capability, it
	 * holds no fluid, the tank will not take a whole bucket of it (wrong variant or no room), or
	 * {@code outSlot} cannot accept the emptied container. A slot holding a stack is drained one item per
	 * call, mirroring the pump's bucket handling.
	 */
	long drainSlotIntoTank(Container container, int inSlot, int outSlot, FluidPort tank, long maxMb);

	/**
	 * Fill the empty container in {@code inSlot} from {@code tank} with up to {@code maxMb}, leaving the
	 * filled container in {@code outSlot}, and return how many millibuckets moved. Same all-or-nothing and
	 * zero-return contract as {@link #drainSlotIntoTank}.
	 */
	long fillSlotFromTank(Container container, int inSlot, int outSlot, FluidPort tank, long maxMb);

	/**
	 * Whether {@code stack} is a fluid container this bridge can work with at all — used to gate machine
	 * input slots (hoppers included). Answers only "does it speak fluid", not "will this exchange succeed":
	 * the tank's state decides that, per call.
	 */
	boolean isFluidContainer(ItemStack stack);

	/** Bridge that moves nothing — used before a loader installs its own, and in loader-free unit tests. */
	ItemFluidBridge NONE = new ItemFluidBridge() {
		@Override
		public long drainSlotIntoTank(Container container, int inSlot, int outSlot, FluidPort tank, long maxMb) {
			return 0L;
		}

		@Override
		public long fillSlotFromTank(Container container, int inSlot, int outSlot, FluidPort tank, long maxMb) {
			return 0L;
		}

		@Override
		public boolean isFluidContainer(ItemStack stack) {
			return false;
		}
	};

	// --- service locator (installed by the loader entrypoint) ---

	ItemFluidBridge[] INSTANCE = new ItemFluidBridge[1];

	/** Install the loader's implementation (called once from the loader entrypoint at mod init). */
	static void install(ItemFluidBridge impl) {
		INSTANCE[0] = impl;
	}

	/**
	 * The installed loader implementation, or {@link #NONE} if none is installed yet. Like
	 * {@link ItemEnergyBridge#get()} and unlike {@link dev.alaindustrial.core.FluidLookup#get()}, this does
	 * not throw: a missing bridge only means "no container exchange in slots", which is exactly what
	 * {@link #NONE} does — a common-side unit test must not have to stand up a loader to tick a pump.
	 */
	static ItemFluidBridge get() {
		ItemFluidBridge impl = INSTANCE[0];
		return impl == null ? NONE : impl;
	}
}
