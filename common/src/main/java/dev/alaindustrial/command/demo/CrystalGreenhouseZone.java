package dev.alaindustrial.command.demo;

import dev.alaindustrial.block.CrystalSeedbedBlock;
import dev.alaindustrial.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Zone <b>crystal greenhouse</b> (MOD-505): a real, sealed greenhouse standing beside the reactor
 * room, not a row of loose blocks.
 *
 * <p>A row is how this started, and it was the wrong shape for this feature. The reactor zone can
 * be a row because its parts read on their own; a greenhouse's whole point is the moment it closes
 * — the shell drops its seams, the panel lights, the beds start budding — and none of that happens
 * to a block lying on a shelf. So the stand builds one that actually seals, and the controller's
 * own scan turns it on a second or two after {@code /ala demo build} finishes.
 *
 * <p>Placed at x 16..22, z 30..36, east of the reactor room's 7×7×7 at x 4..10 with five blocks of
 * air between them (room for the reactor room's water feed and for a visitor):
 * two multiblocks side by side, which is the comparison worth showing. Like the room it is a
 * 7×7×7 shell with a 5×5×5 interior since MOD-659 (the scan asks for at least 27 cells and takes up
 * to 4096); the interior holds a row of five beds that show the whole life of the block at once.
 * The write ledger ({@link DemoStand#lastBuildProblems}) reports any block a later zone overwrites.
 *
 * <p>Domain (coding standard, section 1): Agriculture.
 */
final class CrystalGreenhouseZone implements DemoZone {
	@Override
	public void build(StandWriter w) {
		BlockPos origin = w.origin();
		final int bx = 16;
		final int by = 1;
		final int bz = 30;
		final int edge = 7;

		// Deck underfoot and overhead, glass everywhere else: the interior is 5×5×5, 125 cells against the
		// 27 the scan asks for at least.
		for (int lx = 0; lx < edge; lx++) {
			for (int ly = 0; ly < edge; ly++) {
				for (int lz = 0; lz < edge; lz++) {
					boolean perimeter = lx == 0 || lx == edge - 1 || ly == 0 || ly == edge - 1
							|| lz == 0 || lz == edge - 1;
					if (!perimeter) {
						continue;
					}
					boolean deck = ly == 0 || ly == edge - 1;
					w.set(bx + lx, by + ly, bz + lz,
							deck ? ModContent.CRYSTAL_FARM_FLOOR.get()
									: ModContent.CRYSTAL_FARM_GLASS.get());
				}
			}
		}

		// Controller in the north wall, panel outward — FACING names the way it looks OUT, and the
		// scan walks inward along the opposite.
		w.refit(origin.offset(bx + 1, by + 1, bz),
				ModContent.CRYSTAL_FARM_CONTROLLER.get().defaultBlockState()
						.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));

		// Door in the same wall, both halves by hand: setPlacedBy, which normally raises the upper
		// leaf, does not run for a programmatic setBlock.
		BlockState door = ModContent.CRYSTAL_FARM_DOOR.get().defaultBlockState()
				.setValue(DoorBlock.FACING, Direction.SOUTH);
		w.refit(origin.offset(bx + 3, by + 1, bz), door);
		w.refit(origin.offset(bx + 3, by + 2, bz),
				door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

		// Water in the far corner: the free half of the growth bonus, and the panel reports it.
		w.place(origin.offset(bx + edge - 2, by + 1, bz + edge - 2),
				Blocks.WATER.defaultBlockState());

		// A row of five beds along the back, a row from the door so the walk in stays free: one dead as
		// crafted, then four awake ones carrying vanilla amethyst at every stage of its growth, so the
		// stand shows the whole life of the block at once.
		final int bedRow = bz + edge - 3;
		w.set(bx + 1, by + 1, bedRow, ModContent.CRYSTAL_SEEDBED.get());
		Block[] stages = { Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.LARGE_AMETHYST_BUD,
				Blocks.AMETHYST_CLUSTER };
		int lx = 2;
		for (Block stage : stages) {
			BlockPos bed = origin.offset(bx + lx, by + 1, bedRow);
			w.place(bed, ModContent.CRYSTAL_SEEDBED.get().defaultBlockState()
					.setValue(CrystalSeedbedBlock.CHARGES, CrystalSeedbedBlock.MAX_CHARGES));
			w.place(bed.above(), stage.defaultBlockState()
					.setValue(AmethystClusterBlock.FACING, Direction.UP));
			lx++;
		}
	}
}
