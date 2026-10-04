package dev.alaindustrial.command.demo;

import dev.alaindustrial.item.energy.PoweredItem;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Zone <b>bench</b> (MOD-659, row z=2, x=58..78): what a tester reaches for first — a recharge point
 * and four chests of gear they would otherwise have to conjure one item at a time.
 *
 * <p>The recharge point is a creative energy source behind a copper cable into a charging pad: stand
 * on the pad and the gear in the hands and on the body fills up, indefinitely, unlike every other
 * charged block on the stand (their buffers run dry within minutes).
 *
 * <p>The chests hold the things that are awkward to get in the creative inventory because they are
 * only interesting FULL: the electric tools, devices and armour arrive charged; the chips, parts and
 * rotors, and the buckets, seeds and fuel needed to try a machine, come stacked. Nothing here is a
 * demonstration; it is a supply point, and the four chests are the four things it supplies.
 *
 * <p><b>The first chest is derived, not listed</b> (MOD-709, batch 4): it holds every item the registry
 * knows to be a {@link PoweredItem} — in registry id order, each filled to its buffer — so a new electric
 * tool lands on the bench without anyone remembering to add it. Only what the interface cannot say stays
 * in a hand-kept list: the tools and devices without a buffer behind the powered ones, the armour and
 * iron tools of the second chest, the chips and parts of the third, the supplies of the fourth.
 *
 * <p>Domain (coding standard, section 1): cross-cutting view (the tester's supply point); the
 * exhibits belong to the domains of their own blocks.
 */
final class BenchZone implements DemoZone {
	/** Camera of {@code /ala demo tp bench}. */
	static final DemoStand.TpPoint BENCH_CAMERA =
			new DemoStand.TpPoint("bench", 68.0, 6.0, -3.0, 0.0f, 28.0f, false);

	/** Row z of the test bench: the front row, beside the tiers, where a tester arrives. */
	private static final int BENCH_Z = 2;

	@Override
	public void build(StandWriter w) {
		w.set(58, 1, BENCH_Z, ModContent.CREATIVE_ENERGY_SOURCE.get());
		w.set(59, 1, BENCH_Z, ModContent.COPPER_CABLE.get());
		w.set(60, 1, BENCH_Z, ModContent.CHARGE_PAD.get());

		poweredChest(w);
		armourChest(w);
		partsChest(w);
		suppliesChest(w);
	}

	/**
	 * Every powered item of the registry, charged, then the tools and devices that hold no buffer. The
	 * powered ones come from the interface, so they cannot go stale; a hand-kept entry that later grows
	 * a buffer is dropped from the tail by {@link #unpowered} instead of appearing twice.
	 */
	private static void poweredChest(StandWriter w) {
		w.set(66, 1, BENCH_Z, ModContent.DIAMOND_CHEST.get());
		List<ItemStack> stacks = new ArrayList<>(StandWriter.chargedPoweredItems());
		stacks.addAll(unpowered(
				new ItemStack(ModContent.VACUUM_CAPSULE.get()), new ItemStack(ModContent.NETWORK_ANALYZER.get()),
				new ItemStack(ModContent.WIND_GAUGE.get()), new ItemStack(ModContent.GEIGER_COUNTER.get()),
				new ItemStack(ModContent.TELEPORTER_REMOTE.get()), new ItemStack(ModContent.ENERGY_CRYSTAL.get()),
				new ItemStack(ModContent.LAPOTRON_CRYSTAL.get()), new ItemStack(ModContent.RESONANT_CRYSTAL.get()),
				new ItemStack(ModContent.WRENCH.get()), new ItemStack(ModContent.CABLE_BREAKER.get()),
				new ItemStack(ModContent.FORGE_HAMMER.get()), new ItemStack(ModContent.GARDEN_DRONE.get()),
				new ItemStack(ModContent.SCYTHE_WOOD.get()), new ItemStack(ModContent.SCYTHE_STONE.get()),
				new ItemStack(ModContent.SCYTHE_COPPER.get()), new ItemStack(ModContent.SCYTHE_IRON.get()),
				new ItemStack(ModContent.SCYTHE_GOLD.get()), new ItemStack(ModContent.SCYTHE_TEMPERED_IRON.get()),
				new ItemStack(ModContent.SCYTHE_DIAMOND.get()), new ItemStack(ModContent.SCYTHE_NETHERITE.get()),
				new ItemStack(ModContent.GUIDE_BOOK.get()),
				new ItemStack(ModContent.STOCK_DISPLAY_FRAME_ITEM.get(), 16)));
		w.stock(66, 1, BENCH_Z, stacks);
	}

	/** The armour sets and the tempered iron tools: none of them owns an EU buffer, so none is charged. */
	private static void armourChest(StandWriter w) {
		w.set(70, 1, BENCH_Z, ModContent.DIAMOND_CHEST.get());
		w.stock(70, 1, BENCH_Z, unpowered(
				new ItemStack(ModContent.SHIELDING_HELMET.get()), new ItemStack(ModContent.SHIELDING_CHESTPLATE.get()),
				new ItemStack(ModContent.SHIELDING_LEGGINGS.get()), new ItemStack(ModContent.SHIELDING_BOOTS.get()),
				new ItemStack(ModContent.INSULATED_HELMET.get()), new ItemStack(ModContent.INSULATED_CHESTPLATE.get()),
				new ItemStack(ModContent.INSULATED_LEGGINGS.get()), new ItemStack(ModContent.INSULATED_BOOTS.get()),
				new ItemStack(ModContent.TEMPERED_IRON_HELMET.get()),
				new ItemStack(ModContent.TEMPERED_IRON_CHESTPLATE.get()),
				new ItemStack(ModContent.TEMPERED_IRON_LEGGINGS.get()),
				new ItemStack(ModContent.TEMPERED_IRON_BOOTS.get()),
				new ItemStack(ModContent.TEMPERED_IRON_PICKAXE.get()),
				new ItemStack(ModContent.TEMPERED_IRON_AXE.get()),
				new ItemStack(ModContent.TEMPERED_IRON_SHOVEL.get()), new ItemStack(ModContent.TEMPERED_IRON_HOE.get()),
				new ItemStack(ModContent.TEMPERED_IRON_SWORD.get())));
	}

	/** Chips, cards, blueprints, clots, rotors, wheels, tips, blades and modules. */
	private static void partsChest(StandWriter w) {
		w.set(74, 1, BENCH_Z, ModContent.DIAMOND_CHEST.get());
		w.stock(74, 1, BENCH_Z, List.of(
				new ItemStack(ModContent.OVERCLOCKER_CHIP_I.get()), new ItemStack(ModContent.OVERCLOCKER_CHIP_II.get()),
				new ItemStack(ModContent.OVERCLOCKER_CHIP_III.get()), new ItemStack(ModContent.MUTE_CHIP.get()),
				new ItemStack(ModContent.STATS_CHIP.get()), new ItemStack(ModContent.ALIGNMENT_CHIP_DAY.get()),
				new ItemStack(ModContent.ALIGNMENT_CHIP_NIGHT.get()), new ItemStack(ModContent.RESONANCE_CHIP.get()),
				new ItemStack(ModContent.RTP_CHIP.get()), new ItemStack(ModContent.EMPTY_CHIP.get()),
				new ItemStack(ModContent.MUTATION_CHIP_TRANSFORM.get()),
				new ItemStack(ModContent.MUTATION_CHIP_DUPLICATE.get()),
				new ItemStack(ModContent.MUTATION_CHIP_CREATE.get()), new ItemStack(ModContent.CAPACITY_CARD.get()),
				new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get()), new ItemStack(ModContent.ENERGY_CLOT_I.get()),
				new ItemStack(ModContent.ENERGY_CLOT_II.get()), new ItemStack(ModContent.ENERGY_CLOT_III.get()),
				new ItemStack(ModContent.WINDMILL_ROTOR.get()), new ItemStack(ModContent.WINDMILL_ROTOR_REINFORCED.get()),
				new ItemStack(ModContent.WINDMILL_ROTOR_ADVANCED.get()), new ItemStack(ModContent.WATER_MILL_WHEEL.get()),
				new ItemStack(ModContent.WATER_MILL_WHEEL_REINFORCED.get()),
				new ItemStack(ModContent.WATER_MILL_WHEEL_ADVANCED.get()),
				new ItemStack(ModContent.LIGHTNING_ROD_CONDUCTOR_TIP.get()),
				new ItemStack(ModContent.LIGHTNING_ROD_CONDUCTOR_TIP_REINFORCED.get()),
				new ItemStack(ModContent.LIGHTNING_ROD_CONDUCTOR_TIP_ADVANCED.get()),
				new ItemStack(ModContent.RECYCLER_BLADES_IRON.get()), new ItemStack(ModContent.RECYCLER_BLADES_TEMPERED.get()),
				new ItemStack(ModContent.RECYCLER_BLADES_DIAMOND.get()), new ItemStack(ModContent.DRILL_COLUMN_MODULE.get()),
				new ItemStack(ModContent.CORE_BARREL.get())));
	}

	/** Buckets, fuel, seeds and the odds and ends needed to try a machine. */
	private static void suppliesChest(StandWriter w) {
		w.set(78, 1, BENCH_Z, ModContent.DIAMOND_CHEST.get());
		w.stock(78, 1, BENCH_Z, List.of(
				new ItemStack(ModContent.OIL_BUCKET.get()), new ItemStack(ModContent.DIESEL_BUCKET.get()),
				new ItemStack(ModContent.FUEL_OIL_BUCKET.get()), new ItemStack(ModContent.BIOFUEL_BUCKET.get()),
				new ItemStack(ModContent.NUTRIENT_SOLUTION_BUCKET.get()), new ItemStack(Items.WATER_BUCKET),
				new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.COAL, 64), new ItemStack(Items.REDSTONE, 64),
				new ItemStack(Items.LEVER, 16), new ItemStack(ModContent.URANIUM_FUEL_ROD.get(), 16),
				new ItemStack(ModContent.EMPTY_CAN.get(), 64), new ItemStack(Items.COOKED_BEEF, 64),
				new ItemStack(ModContent.COTTON_SEEDS.get(), 64), new ItemStack(ModContent.KOK_SAGYZ_SEEDS.get(), 64),
				new ItemStack(Items.BONE_MEAL, 64), new ItemStack(Items.IRON_HOE)));
	}

	/** The hand-kept stacks that are not powered items — a powered one is the registry's to stock, not ours. */
	private static List<ItemStack> unpowered(ItemStack... stacks) {
		List<ItemStack> kept = new ArrayList<>(stacks.length);
		for (ItemStack stack : stacks) {
			if (!(stack.getItem() instanceof PoweredItem)) {
				kept.add(stack);
			}
		}
		return kept;
	}
}
