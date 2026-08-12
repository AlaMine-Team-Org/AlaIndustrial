package dev.alaindustrial.block.entity;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.energy.EnergyRole;
import dev.alaindustrial.core.energy.EnergyTier;
import dev.alaindustrial.menu.EnergyCondenserMenu;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Energy Condenser (MOD-393): it banks the grid's SURPLUS and packs it into an energy clot.
 *
 * <p>Three deliberate properties, and each one is load-bearing:
 *
 * <ul>
 *   <li><b>It cannot compete with machines.</b> Declared a storage sink, so the grid serves it only
 *       after every working machine, and its free buffer never counts as machine demand — which is
 *       what would otherwise make batteries discharge on its behalf.</li>
 *   <li><b>It cannot drain stores.</b> No cascade, no storage-feed channel, and
 *       {@code maxExtract = 0}: energy that goes in never comes back out as energy. Only live
 *       generation reaches it.</li>
 *   <li><b>It accepts one MV packet at a time.</b> {@link Config#condenserInputRate} is a pacing
 *       number, NOT the guard against starving machines — that job belongs entirely to the serve
 *       order above, which drains the line for machines in a separate, earlier pass. Treating the
 *       intake as the guard is what once pinned it at 12 EU/t and made a whole solar farm pointless:
 *       the block banked 12 whether it was fed by two panels or eighty-five.</li>
 * </ul>
 *
 * <p><b>The tier is chosen by the player, not by the machine.</b> The bank grows without pause; the
 * output slot always shows the clot the current bank is worth. Take it at 300k and it is a tier-I
 * clot; wait for a million and the same slot now holds tier II. Taking it empties the bank
 * completely — so pulling out early burns whatever was banked above that tier's price. That trade,
 * greed against patience, is the whole mechanic, which is why the screen spells out both the current
 * tier and what is still missing for the next one.
 */
public class EnergyCondenserBlockEntity extends MachineBlockEntity implements MenuProvider {

	/** The only slot: where the clot the current bank is worth appears. */
	public static final int OUTPUT_SLOT = 0;
	private static final int[] NO_SLOTS = new int[0];

	/**
	 * The shared four (energy, capacity, progress, maxProgress) plus six of our own. Hides
	 * {@link MachineBlockEntity#DATA_COUNT} the way the sawmill does — the client stub is sized from
	 * THIS constant, and a stub narrower than what the block entity projects desyncs the channel.
	 */
	public static final int DATA_COUNT = MachineBlockEntity.DATA_COUNT + 6;
	public static final int DATA_FILL_PERMILLE = MachineBlockEntity.DATA_COUNT;
	public static final int DATA_TIER = MachineBlockEntity.DATA_COUNT + 1;
	public static final int DATA_NEXT_PERMILLE = MachineBlockEntity.DATA_COUNT + 2;
	/** Banked EU, split across two channels — see {@link #BANK_RADIX}. */
	public static final int DATA_BANK_LO = MachineBlockEntity.DATA_COUNT + 3;
	public static final int DATA_BANK_HI = MachineBlockEntity.DATA_COUNT + 4;
	/** The threshold the bank is currently heading for, in thousands of EU; 0 at the top tier. */
	public static final int DATA_NEXT_THOUSANDS = MachineBlockEntity.DATA_COUNT + 5;

	/**
	 * Split base for the banked total. A {@code DataSlot} is a signed 16-bit channel, so the millions
	 * this block deals in cannot cross it whole — they arrive negative. Splitting keeps the number
	 * EXACT on the client, which matters because the screen prints it: a percentage alone was what made
	 * the block look broken, since below the first tier it rounds to a stationary zero for minutes.
	 */
	public static final int BANK_RADIX = 30_000;

	private final ContainerData condenserData = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_FILL_PERMILLE -> fillPermille();
				case DATA_TIER -> tierForBank();
				case DATA_NEXT_PERMILLE -> progressToNextTierPermille();
				case DATA_BANK_LO -> (int) (energy.getAmount() % BANK_RADIX);
				case DATA_BANK_HI -> (int) (energy.getAmount() / BANK_RADIX);
				case DATA_NEXT_THOUSANDS -> nextThresholdThousands();
				default -> dataAccess.get(index);
			};
		}

		@Override
		public void set(int index, int value) {
			if (index < MachineBlockEntity.DATA_COUNT) {
				dataAccess.set(index, value);
			}
			// Our own six are derived from the bank — nothing to store.
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public EnergyCondenserBlockEntity(BlockPos pos, BlockState state) {
		// One slot; the bank is the buffer. maxExtract = 0 — the grid must never be able to pull the
		// bank back out, otherwise this becomes a battery and the whole "surplus only" idea collapses.
		super(ModContent.ENERGY_CONDENSER_BE.get(), pos, state, EnergyTier.LV, 1,
				Config.condenserCapacity, Config.condenserInputRate, 0L);
	}

	/** Serve me after the working machines — see the class javadoc. */
	@Override
	public boolean isEnergyStorageSink() {
		return true;
	}

	/**
	 * Energy enters through the top and bottom faces only; the four sides are inert so items and power
	 * never share an edge. The block class refuses cable arms on the same faces — the two must agree,
	 * or a cable draws an arm to a face that carries nothing.
	 */
	@Override
	public EnergyRole energyRoleForFace(Direction worldFace) {
		return worldFace.getAxis() == Direction.Axis.Y ? EnergyRole.IN : EnergyRole.NONE;
	}

	/** Items leave through the sides only — the vertical faces belong to the cable. */
	@Override
	public int[] getSlotsForFace(Direction side) {
		return side.getAxis() == Direction.Axis.Y ? NO_SLOTS : new int[] { OUTPUT_SLOT };
	}

	@Override
	protected boolean isOutputSlot(int slot) {
		return slot == OUTPUT_SLOT;
	}

	/** Nothing goes in: the slot is where the condenser's own product appears. */
	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return false;
	}

	/**
	 * No upgrade panel. A condenser is meant to sip what is left over; an overclocker in it would turn
	 * it into a pump on the grid — the exact opposite of the design.
	 */
	@Override
	public boolean hasUpgradePanel() {
		return false;
	}

	/** Banked EU as permille — the GUI channel is 16-bit and 4 000 000 would arrive negative. */
	public int fillPermille() {
		long cap = Math.max(1L, energy.getCapacity());
		return (int) Math.min(1000L, energy.getAmount() * 1000L / cap);
	}

	/** Tier the current bank is worth: 0 while below the first threshold. */
	public int tierForBank() {
		long banked = energy.getAmount();
		if (banked >= Config.clotThresholdIII) {
			return 3;
		}
		if (banked >= Config.clotThresholdII) {
			return 2;
		}
		return banked >= Config.clotThresholdI ? 1 : 0;
	}

	/**
	 * How far along the player is towards the NEXT tier, in permille — what the screen turns into
	 * "still missing". 1000 once the top tier is reached, since there is nothing further to reach.
	 */
	public int progressToNextTierPermille() {
		long banked = energy.getAmount();
		long from = switch (tierForBank()) {
			case 0 -> 0L;
			case 1 -> Config.clotThresholdI;
			case 2 -> Config.clotThresholdII;
			default -> -1L;
		};
		if (from < 0) {
			return 1000;
		}
		long to = switch (tierForBank()) {
			case 0 -> Config.clotThresholdI;
			case 1 -> Config.clotThresholdII;
			default -> Config.clotThresholdIII;
		};
		long span = Math.max(1L, to - from);
		return (int) Math.max(0L, Math.min(1000L, (banked - from) * 1000L / span));
	}

	/**
	 * The threshold the bank is climbing toward, in thousands of EU — 0 once the top tier is reached.
	 *
	 * <p>Thousands rather than raw EU because the channel is 16-bit and 4 000 000 does not fit; every
	 * threshold is a round number of thousands, so nothing is lost. The screen needs this to print
	 * "banked / target" — without the target, a raw bank figure says nothing about how close the player
	 * is, which is the one question this GUI exists to answer.
	 */
	public int nextThresholdThousands() {
		return switch (tierForBank()) {
			case 0 -> Config.clotThresholdI / 1000;
			case 1 -> Config.clotThresholdII / 1000;
			case 2 -> Config.clotThresholdIII / 1000;
			default -> 0;
		};
	}

	private static Item clotFor(int tier) {
		return switch (tier) {
			case 1 -> ModContent.ENERGY_CLOT_I.get();
			case 2 -> ModContent.ENERGY_CLOT_II.get();
			default -> ModContent.ENERGY_CLOT_III.get();
		};
	}

	/**
	 * Keep the output slot showing what the bank is worth right now — that display IS the mechanic:
	 * the player decides when to take it, and the slot has to tell the truth about what they would get.
	 */
	@Override
	protected int onServerTick(Level level, BlockPos pos, BlockState state) {
		int tier = tierForBank();
		ItemStack shown = items.get(OUTPUT_SLOT);
		if (tier == 0) {
			if (!shown.isEmpty()) {
				setItem(OUTPUT_SLOT, ItemStack.EMPTY);
			}
		} else {
			Item want = clotFor(tier);
			if (!shown.is(want) || shown.getCount() != 1) {
				setItem(OUTPUT_SLOT, new ItemStack(want));
			}
		}
		updateLit(energy.getAmount() > 0);
		// Never sleeps: the bank is fed from outside and the shown tier must follow it promptly. The
		// tick itself is a couple of comparisons.
		return 0;
	}

	/**
	 * Taking the clot spends the WHOLE bank, not the tier's price. That is what makes waiting a real
	 * decision instead of an obvious one — pull out at 950k and the extra 700k goes with it.
	 *
	 * <p>Both removal paths are covered: {@code removeItem} is what a player click, a hopper and the
	 * item pipe all end up calling.
	 */
	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack taken = super.removeItem(slot, count);
		if (slot == OUTPUT_SLOT && !taken.isEmpty()) {
			energy.setAmountUntracked(0);
			setChanged();
			syncBlockEntityToClient();
		}
		return taken;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.alaindustrial.energy_condenser");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		return new EnergyCondenserMenu(syncId, inventory, this, condenserData,
				ContainerLevelAccess.create(level, worldPosition));
	}

	/** The widened GUI channel: the shared four, then the condenser's own three. */
	@Override
	public ContainerData getDataAccess() {
		return condenserData;
	}
}
