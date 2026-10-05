package dev.alaindustrial.gametest;

import static dev.alaindustrial.gametest.EnergyScenarioSupport.be;
import static dev.alaindustrial.gametest.EnergyScenarioSupport.tick;

import dev.alaindustrial.block.HorizontalMachineBlock;
import dev.alaindustrial.block.entity.CreativeEnergySourceBlockEntity;
import dev.alaindustrial.block.entity.EnergyBlockEntity;
import dev.alaindustrial.block.entity.MachineBlockEntity;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.EnergyNetworkDiagnostics;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * One reference circuit of the energy network's world golden (MOD-715, batch 0): real cables, real
 * endpoints, the real {@link EnergyNetwork} — cache, discharge channels and distribution kernel together.
 *
 * <p>The network is ticked directly ({@code isAwake()} first, as {@code tickAll} does), never through
 * {@link NetworkManager#tickAll}: gametests share one level, and {@code tickAll} would both tick every
 * other scenario's rig and spend this rig's turn of the per-tick budget on them (the lesson of
 * {@code EnergyNetworkPerfScenarios}). Positions are relative and written as three digits, so the golden
 * is the same wherever the lane places the structure — the network is translation-invariant (MOD-304),
 * and a golden that is not would be the finding.
 *
 * <p>Order within a tick: sources, cables, the network, then every other endpoint — the order
 * {@link EnergyLine} drives a line in.
 */
final class EnergyGoldenRig {

	private final GameTestHelper helper;
	private final String name;
	private final List<BlockPos> cables = new ArrayList<>();
	private final List<BlockPos> sources = new ArrayList<>();
	private final List<BlockPos> others = new ArrayList<>();
	/** Endpoints whose buffer the golden records, in declaration order. */
	private final List<BlockPos> recorded = new ArrayList<>();
	private boolean registered;

	EnergyGoldenRig(GameTestHelper helper, String name) {
		this.helper = helper;
		this.name = name;
	}

	static boolean inRig(BlockPos p) {
		return p.getX() >= 0 && p.getX() <= 7 && p.getY() >= 0 && p.getY() <= 7 && p.getZ() >= 0 && p.getZ() <= 7;
	}

	/** A position as three digits; every rig stays inside the 8×8×8 structure, so one digit each. */
	static String code(BlockPos p) {
		if (!inRig(p)) {
			throw new IllegalArgumentException("golden rig position outside the structure: " + p);
		}
		return "" + p.getX() + p.getY() + p.getZ();
	}

	void cable(BlockPos pos) {
		helper.setBlock(pos, ModContent.COPPER_CABLE.get());
		cables.add(pos);
	}

	/** Copper cables from {@code from} to {@code to} along one axis, both ends included. */
	void run(BlockPos from, BlockPos to) {
		BlockPos step = new BlockPos(Integer.signum(to.getX() - from.getX()), Integer.signum(to.getY() - from.getY()),
				Integer.signum(to.getZ() - from.getZ()));
		for (BlockPos p = from; ; p = p.offset(step)) {
			cable(p);
			if (p.equals(to)) {
				return;
			}
		}
	}

	/** A creative source dialled to {@code output} EU/t — a generator whose supply never varies. */
	void source(BlockPos pos, int output) {
		helper.setBlock(pos, ModContent.CREATIVE_ENERGY_SOURCE.get());
		if (be(helper, pos) instanceof CreativeEnergySourceBlockEntity source) {
			source.setEnabled(true);
			source.setOutputLimit(output);
		} else {
			throw new IllegalStateException("creative source did not place at " + pos);
		}
		sources.add(pos);
		recorded.add(pos);
	}

	/** A horizontal machine facing {@code facing}, with {@code input} in its first slot when non-empty. */
	void machine(BlockPos pos, Block block, Direction facing, ItemStack input) {
		helper.setBlock(pos, block.defaultBlockState().setValue(HorizontalMachineBlock.FACING, facing));
		if (!input.isEmpty() && be(helper, pos) instanceof MachineBlockEntity machine) {
			machine.setItem(0, input);
		}
		others.add(pos);
		recorded.add(pos);
	}

	/** A storage block (Battery Box, Teleporter) facing {@code facing}, holding {@code amount} EU. */
	void store(BlockPos pos, Block block, Direction facing, long amount) {
		helper.setBlock(pos, block.defaultBlockState().setValue(HorizontalMachineBlock.FACING, facing));
		if (be(helper, pos) instanceof EnergyBlockEntity store) {
			store.getEnergyStorage().setAmountUntracked(Math.min(amount, store.getEnergyStorage().getCapacity()));
		} else {
			throw new IllegalStateException("store did not place at " + pos);
		}
		others.add(pos);
		recorded.add(pos);
	}

	/**
	 * An energy block with no {@code FACING} (an Energy Condenser), placed in its default state, ticked and
	 * recorded like a store (MOD-731).
	 */
	void block(BlockPos pos, Block block) {
		helper.setBlock(pos, block.defaultBlockState());
		if (!(be(helper, pos) instanceof EnergyBlockEntity)) {
			throw new IllegalStateException("energy block did not place at " + pos);
		}
		others.add(pos);
		recorded.add(pos);
	}

	/** An endpoint placed by the caller that is recorded but never ticked (a multiblock core). */
	void passive(BlockPos pos) {
		recorded.add(pos);
	}

	/** The network of this rig's cables, or {@code null} while they have not registered. */
	EnergyNetwork network() {
		return NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(cables.get(0)));
	}

	/**
	 * Drive the rig {@code ticks} times and return its golden lines: a state line every tick, the
	 * network's flow faces on the ticks in {@code flowAt}, and its endpoint order after the first tick.
	 */
	List<String> drive(int ticks, int... flowAt) {
		List<String> lines = new ArrayList<>();
		for (int t = 1; t <= ticks; t++) {
			boolean awake = step();
			EnergyNetwork net = network();
			lines.add(stateLine(t, net, awake));
			if (t == 1 && net != null) {
				lines.add(endpointLine(t, net.diagnostics()));
			}
			for (int at : flowAt) {
				if (at == t && net != null) {
					lines.add(flowLine(t, net.diagnostics()));
				}
			}
		}
		return lines;
	}

	/**
	 * Keep driving after {@link #drive} up to tick {@code toTick}, recording a state line every
	 * {@code every} ticks with {@code w=}, the EU the network drew out of storage over that window — long
	 * enough for the storage channels to cross their boundaries (a cascade levelling out, a feed reaching
	 * the donor's reserve), which the first ticks never reach.
	 */
	List<String> driveOn(int fromTick, int toTick, int every) {
		List<String> lines = new ArrayList<>();
		long window = 0;
		for (int t = fromTick + 1; t <= toTick; t++) {
			boolean awake = step();
			EnergyNetwork net = network();
			window += net == null ? 0 : net.lastTickFromStorage();
			if ((t - fromTick) % every == 0) {
				lines.add(stateLine(t, net, awake) + " w=" + window);
				window = 0;
			}
		}
		return lines;
	}

	/**
	 * One game tick of the rig: sources, cables, the network if awake, then every other endpoint. The first
	 * call also gives every cable the server tick that registers it. Returns whether the network ticked.
	 */
	boolean step() {
		if (!registered) {
			registered = true;
			for (BlockPos cable : cables) {
				tick(helper, be(helper, cable));
			}
		}
		for (BlockPos pos : sources) {
			tick(helper, be(helper, pos));
		}
		for (BlockPos pos : cables) {
			tick(helper, be(helper, pos));
		}
		EnergyNetwork net = network();
		boolean awake = net != null && net.isAwake();
		if (awake) {
			net.tick();
		}
		for (BlockPos pos : others) {
			tick(helper, be(helper, pos));
		}
		return awake;
	}

	private String prefix(int t) {
		return name + " t" + (t < 10 ? "0" : "") + t;
	}

	/** The buffer of the energy block at relative {@code pos}, or -1 when there is none. */
	long amountAt(BlockPos pos) {
		return be(helper, pos) instanceof EnergyBlockEntity e ? e.getEnergyStorage().getAmount() : -1L;
	}

	private String stateLine(int t, EnergyNetwork net, boolean awake) {
		StringBuilder line = new StringBuilder(prefix(t)).append(" c=");
		for (int i = 0; i < cables.size(); i++) {
			line.append(i == 0 ? "" : ",").append(amountAt(cables.get(i)));
		}
		line.append(" e=");
		for (int i = 0; i < recorded.size(); i++) {
			line.append(i == 0 ? "" : ",").append(amountAt(recorded.get(i)));
		}
		if (net != null) {
			line.append(" m=").append(net.lastTickMoved()).append('/').append(net.lastTickToStorage())
					.append('/').append(net.lastTickFromStorage());
		}
		return line.append(" a=").append(awake ? 1 : 0).toString();
	}

	private String endpointLine(int t, EnergyNetworkDiagnostics diagnostics) {
		return prefix(t) + " p=" + codes(diagnostics.producerPositions())
				+ " k=" + codes(diagnostics.consumerPositions())
				+ " fed=" + codes(diagnostics.lastTickFed());
	}

	private String flowLine(int t, EnergyNetworkDiagnostics diagnostics) {
		StringBuilder line = new StringBuilder(prefix(t)).append(" f=");
		boolean first = true;
		for (Map.Entry<BlockPos, Integer> entry : diagnostics.cableFlowFaces().entrySet()) {
			line.append(first ? "" : ",").append(code(relative(entry.getKey()))).append(':')
					.append(Integer.toHexString(entry.getValue()));
			first = false;
		}
		return line.toString();
	}

	/**
	 * {@code absolute} back in rig coordinates: the inverse of {@code helper.absolutePos} for an unrotated
	 * rig. Not {@code helper.relativePos}: on 26.3 it transforms by the test's rotation turned a further
	 * 180° (javap of {@code GameTestHelper.relativePos}), so on an unrotated test it does not invert
	 * {@code absolutePos} and answered a position outside the structure.
	 */
	private BlockPos relative(BlockPos absolute) {
		return absolute.subtract(helper.absolutePos(BlockPos.ZERO));
	}

	private String codes(Collection<BlockPos> absolute) {
		StringBuilder out = new StringBuilder();
		for (BlockPos pos : absolute) {
			out.append(out.isEmpty() ? "" : ",").append(code(relative(pos)));
		}
		return out.toString();
	}

	/** Empty the rig volume for the next circuit; cables leave their network as they go. */
	static void clear(GameTestHelper helper) {
		for (int y = 2; y <= 4; y++) {
			for (int x = 0; x <= 7; x++) {
				for (int z = 0; z <= 7; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}
}
