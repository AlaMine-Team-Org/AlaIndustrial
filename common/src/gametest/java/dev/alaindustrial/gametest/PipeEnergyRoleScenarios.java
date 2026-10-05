package dev.alaindustrial.gametest;

import dev.alaindustrial.block.entity.FluidPipeBlockEntity;
import dev.alaindustrial.block.entity.GeneratorBlockEntity;
import dev.alaindustrial.block.entity.ItemPipeBlockEntity;
import dev.alaindustrial.core.energy.EnergyNetwork;
import dev.alaindustrial.core.energy.EnergyNetworkDiagnostics;
import dev.alaindustrial.core.energy.EnergyPortHost;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * MOD-691 — the item and fluid pipes are not ends of the energy network, on either loader.
 *
 * <p>Both pipes extend {@code EnergyBlockEntity} for its tick and persistence scaffolding, with a
 * zero-capacity buffer. Left on the default {@code BOTH} face role, every face had a live energy port.
 * Fabric never noticed, because it does not register the pipes for energy
 * ({@code BlockCapabilityRoster.NO_ENERGY_CAPABILITY}) and its network finds endpoints through that
 * capability; NeoForge's own lookup takes {@code energyPort()} straight from the block entity, so there a
 * pipe pressed against a cable became both a producer and a consumer of that network. It moved no EU (the
 * buffer is empty and full at once), but a network with a consumer never sleeps, and the analyzer drew the
 * pipe as a source and a sink. Wrapped by the Fabric {@code PipeEnergyRoleGameTest} and registered on the
 * NeoForge lane ({@code mod691_*}).
 */
public final class PipeEnergyRoleScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(PipeEnergyRoleScenarios::pipesAreNotEnergyEndpoints,
								"mod691_pipes_are_not_energy_endpoints")
						.fabricId("PipeEnergyRoleGameTest", "mod691PipesAreNotEnergyEndpoints").ticks(20, 40));

		private Roster() {}
	}

	private PipeEnergyRoleScenarios() {
	}

	private static final BlockPos GEN = new BlockPos(1, 2, 1);
	private static final BlockPos CABLE = new BlockPos(2, 2, 1);
	/** On top of the cable. */
	private static final BlockPos ITEM_PIPE = new BlockPos(2, 3, 1);
	/** South of the cable. */
	private static final BlockPos FLUID_PIPE = new BlockPos(2, 2, 2);

	/**
	 * A generator, one cable and both pipes touching it: no pipe exposes an energy port on any face, the
	 * network lists neither pipe among its producers or consumers, and once the line is full the network
	 * sleeps (ADR-007) — which it cannot do while it believes it has a consumer.
	 *
	 * <p>The port check is loader-neutral and red on both loaders before the fix; the network checks are
	 * red on NeoForge only, where the pipes' ports reached the network.
	 *
	 * @implements MOD-691-PIPES — a pipe is not an energy endpoint
	 * @covers MOD-691
	 */
	public static void pipesAreNotEnergyEndpoints(GameTestHelper helper) {
		helper.setBlock(GEN, ModContent.GENERATOR.get());
		helper.setBlock(CABLE, ModContent.COPPER_CABLE.get());
		helper.setBlock(ITEM_PIPE, ModContent.ITEM_PIPE.get());
		helper.setBlock(FLUID_PIPE, ModContent.FLUID_PIPE.get());
		if (EnergyScenarioSupport.be(helper, GEN) instanceof GeneratorBlockEntity gen) {
			gen.setItem(GeneratorBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, 64));
		}

		List<String> violations = new ArrayList<>();
		BlockEntity itemPipe = EnergyScenarioSupport.be(helper, ITEM_PIPE);
		BlockEntity fluidPipe = EnergyScenarioSupport.be(helper, FLUID_PIPE);
		if (!(itemPipe instanceof ItemPipeBlockEntity) || !(fluidPipe instanceof FluidPipeBlockEntity)) {
			helper.fail("precondition: the pipes did not produce their block entities (item=" + itemPipe
					+ ", fluid=" + fluidPipe + ")");
			return;
		}
		for (BlockEntity pipe : List.of(itemPipe, fluidPipe)) {
			for (Direction face : Direction.values()) {
				if (pipe instanceof EnergyPortHost host && host.energyPort(face) != null) {
					violations.add(pipe.getClass().getSimpleName() + " exposes an energy port on its "
							+ face.getName() + " face");
				}
			}
		}

		// R-NRG-09 puts a producer-only line of one segment to sleep within ten ticks; twenty leave margin.
		for (int i = 0; i < 20; i++) {
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, GEN));
			EnergyScenarioSupport.tick(helper, EnergyScenarioSupport.be(helper, CABLE));
			NetworkManager.tickAll(helper.getLevel());
		}
		EnergyNetwork net = NetworkManager.networkAt(helper.getLevel(), helper.absolutePos(CABLE));
		if (net == null) {
			helper.fail("precondition: the cable formed no energy network");
			return;
		}
		EnergyNetworkDiagnostics d = net.diagnostics();
		for (BlockPos rel : List.of(ITEM_PIPE, FLUID_PIPE)) {
			BlockPos abs = helper.absolutePos(rel);
			if (d.producerPositions().contains(abs)) {
				violations.add("the pipe at " + abs + " is listed as a producer of the energy network");
			}
			if (d.consumerPositions().contains(abs)) {
				violations.add("the pipe at " + abs + " is listed as a consumer of the energy network");
			}
		}
		if (net.isAwake()) {
			violations.add("a network of one generator and a full cable stays awake with pipes pressed against"
					+ " it (consumers=" + d.consumerPositions() + ") — it must sleep (ADR-007)");
		}

		if (!violations.isEmpty()) {
			helper.fail("MOD-691: " + String.join("; ", violations));
			return;
		}
		helper.succeed();
	}
}
