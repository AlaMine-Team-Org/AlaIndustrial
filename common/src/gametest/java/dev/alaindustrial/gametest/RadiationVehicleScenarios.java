package dev.alaindustrial.gametest;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.block.entity.IronChestBlockEntity;
import dev.alaindustrial.core.radiation.RadiationConfig;
import dev.alaindustrial.core.radiation.RadiationCore;
import dev.alaindustrial.core.radiation.RadiationSources;
import dev.alaindustrial.core.radiation.RadiationVehicles;
import dev.alaindustrial.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;

/**
 * Radiation from containers that move (MOD-786): chest boats, chest and hopper minecarts, pack animals.
 *
 * <p>Same rig and the same isolation as {@code RadiationScenarios}: both radii pinned to 3, the source at
 * {@code (1,2,1)}, a cow two blocks away as the observer — a cow rather than a mock player, whose
 * creative mode and payload handling have nothing to do with the field. The exposure functions are
 * called synchronously, so nothing has to tick.
 *
 * <p><b>Cleaning up is part of every body.</b> A chest boat or minecart removed with {@code discard()}
 * spills its contents (the removal reason counts as destroying it), and the spilled uranium would keep
 * radiating on the floor next to a neighbour's rig; a pending loot table would be rolled by that spill.
 * Every vehicle is emptied, and its loot table dropped, before it is discarded.
 */
public final class RadiationVehicleScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(RadiationVehicleScenarios::vehiclesIrradiateLikeAChestBlock,
						"rad_vehicle_irradiates_like_a_chest_block").ticks(20, 40),
				RosterEntry.of(RadiationVehicleScenarios::shieldingChestBoatStopsWhatAnIronOneDoesNot,
						"rad_vehicle_shielding_boat_stops_what_an_iron_one_does_not").ticks(20, 40),
				RosterEntry.of(RadiationVehicleScenarios::donkeyWithAChestIrradiates,
						"rad_vehicle_donkey_with_a_chest_irradiates").ticks(20, 40),
				RosterEntry.of(RadiationVehicleScenarios::sweepLeavesAMinecartsUngeneratedLootAlone,
						"rad_vehicle_leaves_ungenerated_loot_alone").ticks(20, 40),
				RosterEntry.of(RadiationVehicleScenarios::wallStopsAVehicleAsItStopsAChest,
						"rad_vehicle_wall_stops_it_as_it_stops_a_chest").ticks(20, 40),
				RosterEntry.of(RadiationVehicleScenarios::reachIsMeasuredToTheVehiclesCentre,
						"rad_vehicle_reach_is_measured_to_the_centre").ticks(20, 40));

		private Roster() {}
	}

	private RadiationVehicleScenarios() {
	}

	private static final BlockPos RACK = new BlockPos(1, 2, 1);
	private static final BlockPos BYSTANDER = new BlockPos(1, 2, 3);
	private static final BlockPos WALL_LOW = new BlockPos(1, 2, 2);
	private static final BlockPos WALL_HIGH = new BlockPos(1, 3, 2);

	/** Both radii pinned to 3, restored afterwards — see {@code RadiationScenarios.withIsolatedField}. */
	private static void withIsolatedField(Runnable body) {
		try (ConfigOverrides o = ConfigOverrides.sync()) {
			o.set("radiationSourceRadius", 3);
			o.set("radiationGroundRadius", 3);
			body.run();
		}
	}

	private static int exposure(ServerLevel level, Cow viewer) {
		return RadiationSources.exposureAt(level, viewer, RadiationConfig.radiationSourceRadius);
	}

	private static int detected(ServerLevel level, Cow viewer) {
		return RadiationSources.detectedAt(level, viewer, RadiationConfig.radiationSourceRadius,
				RadiationConfig.radiationGroundRadius);
	}

	/** The one source strength a collector reports around the viewer, or 0 when it reports none. */
	private static int strength(List<RadiationSources.Source> sources, GameTestHelper helper, String what) {
		if (sources.size() > 1) {
			helper.fail(what + ": expected one source, got " + sources.size());
		}
		return sources.isEmpty() ? 0 : sources.get(0).strength();
	}

	private static int vehicleStrength(ServerLevel level, Cow viewer, GameTestHelper helper, String what) {
		List<RadiationSources.Source> out = new ArrayList<>();
		RadiationVehicles.collectVehicles(level, viewer.position(), RadiationConfig.radiationSourceRadius,
				RadiationConfig.radiationGroundRadius, out);
		return strength(out, helper, what);
	}

	private static int chestStrength(ServerLevel level, Cow viewer, GameTestHelper helper) {
		List<RadiationSources.Source> out = new ArrayList<>();
		RadiationSources.collectContainers(level, viewer.position(), RadiationConfig.radiationSourceRadius,
				RadiationConfig.radiationGroundRadius, out);
		return strength(out, helper, "iron chest block");
	}

	/** One refined uranium — under the leak ceiling, so "the same strength" is not the cap agreeing with itself. */
	private static ItemStack one() {
		return new ItemStack(ModContent.REFINED_URANIUM.get(), 1);
	}

	private static ItemStack stack() {
		return new ItemStack(ModContent.REFINED_URANIUM.get(), 64);
	}

	/** Empty first, then discard: a discarded chest vehicle spills, and the spill would keep radiating. */
	private static void removeVehicle(Entity vehicle) {
		((Container) vehicle).clearContent();
		vehicle.discard();
	}

	@SuppressWarnings("unchecked")
	private static EntityType<? extends Entity> modBoat(String path) {
		return (EntityType<? extends Entity>) BuiltInRegistries.ENTITY_TYPE.getValue(Industrialization.id(path));
	}

	/**
	 * Every kind of container vehicle irradiates exactly as an iron chest block with the same contents —
	 * one item below the ceiling and a packed hold at the ceiling — and the counter hears it too.
	 *
	 * <p>Before MOD-786 none of them was seen at all: the field read 0 next to a minecart full of uranium.
	 *
	 * @implements R-RAD-26 — see docs/testing/RULES.md
	 */
	public static void vehiclesIrradiateLikeAChestBlock(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = helper.spawn(EntityTypes.COW, BYSTANDER);

			// The reference: an iron chest BLOCK in the same spot, one item and then packed.
			helper.setBlock(RACK, ModContent.IRON_CHEST.get());
			Container chest = helper.getBlockEntity(RACK, IronChestBlockEntity.class);
			chest.setItem(0, one());
			int chestOne = chestStrength(level, viewer, helper);
			for (int slot = 0; slot < chest.getContainerSize(); slot++) {
				chest.setItem(slot, stack());
			}
			int chestFull = chestStrength(level, viewer, helper);
			chest.clearContent();
			helper.setBlock(RACK, Blocks.AIR);
			if (chestOne <= 0 || chestFull <= chestOne) {
				helper.fail("rig is wrong: the iron chest reference must radiate and cap above one item; got one="
						+ chestOne + ", packed=" + chestFull);
				return;
			}

			List<EntityType<? extends Entity>> kinds = List.of(EntityTypes.CHEST_MINECART,
					EntityTypes.HOPPER_MINECART, EntityTypes.OAK_CHEST_BOAT, EntityTypes.BAMBOO_CHEST_RAFT,
					modBoat("oak_iron_chest_boat"), modBoat("bamboo_iron_chest_raft"));
			for (EntityType<? extends Entity> kind : kinds) {
				String what = BuiltInRegistries.ENTITY_TYPE.getKey(kind).toString();
				Entity vehicle = helper.spawn(kind, RACK);
				Container hold = (Container) vehicle;
				hold.setItem(0, one());
				int dose = exposure(level, viewer);
				int heard = RadiationSources.detectedAt(level, viewer, RadiationConfig.radiationSourceRadius,
						RadiationConfig.radiationGroundRadius);
				int single = vehicleStrength(level, viewer, helper, what);
				for (int slot = 0; slot < hold.getContainerSize(); slot++) {
					hold.setItem(slot, stack());
				}
				int packed = vehicleStrength(level, viewer, helper, what);
				removeVehicle(vehicle);
				if (dose <= 0 || heard <= 0) {
					helper.fail(what + " holding uranium must irradiate and be heard; dose=" + dose + ", counter="
							+ heard);
					return;
				}
				if (single != chestOne || packed != chestFull) {
					helper.fail(what + " must leak as an iron chest does: one item " + single + " vs " + chestOne
							+ ", packed " + packed + " vs " + chestFull);
					return;
				}
			}
			helper.succeed();
		});
	}

	/**
	 * The mod's boat with the SHIELDING chest stops what the same boat with an iron chest lets through.
	 *
	 * <p>The iron boat in the same spot with the same stack is the positive control: without it a zero
	 * from the shielding boat would also pass against a field that sees no vehicle at all.
	 *
	 * @implements R-RAD-27 — see docs/testing/RULES.md
	 */
	public static void shieldingChestBoatStopsWhatAnIronOneDoesNot(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = helper.spawn(EntityTypes.COW, BYSTANDER);

			Entity iron = helper.spawn(modBoat("oak_iron_chest_boat"), RACK);
			((Container) iron).setItem(0, stack());
			int exposed = exposure(level, viewer);
			removeVehicle(iron);
			if (exposed <= 0) {
				helper.fail("uranium in a boat with an iron chest must irradiate; got " + exposed);
				return;
			}

			Entity shielding = helper.spawn(modBoat("oak_shielding_chest_boat"), RACK);
			((Container) shielding).setItem(0, stack());
			int stopped = exposure(level, viewer);
			int heard = RadiationSources.detectedAt(level, viewer, RadiationConfig.radiationSourceRadius,
					RadiationConfig.radiationGroundRadius);
			removeVehicle(shielding);
			if (stopped != 0 || heard != 0) {
				helper.fail("the same uranium in a boat with the shielding chest must not irradiate at all; dose="
						+ stopped + ", counter=" + heard);
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * A donkey carrying a chest radiates its cargo like any other chest — it is not a container entity,
	 * which is why it needs its own case.
	 *
	 * @implements R-RAD-28 — see docs/testing/RULES.md
	 */
	public static void donkeyWithAChestIrradiates(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = helper.spawn(EntityTypes.COW, BYSTANDER);

			helper.setBlock(RACK, ModContent.IRON_CHEST.get());
			Container chest = helper.getBlockEntity(RACK, IronChestBlockEntity.class);
			chest.setItem(0, one());
			int chestOne = chestStrength(level, viewer, helper);
			chest.clearContent();
			helper.setBlock(RACK, Blocks.AIR);

			Donkey donkey = helper.spawn(EntityTypes.DONKEY, RACK);
			// Slot 499 is the chest itself: equipping it builds the pack inventory, as a player's click does.
			donkey.getSlot(AbstractHorse.INVENTORY_SLOT_OFFSET - 1).set(new ItemStack(Items.CHEST));
			if (!donkey.hasChest()
					|| !donkey.getSlot(AbstractHorse.INVENTORY_SLOT_OFFSET).set(one())) {
				helper.fail("rig is wrong: the donkey did not take a chest and a stack into it");
				return;
			}
			int dose = exposure(level, viewer);
			int single = vehicleStrength(level, viewer, helper, "donkey");
			donkey.getSlot(AbstractHorse.INVENTORY_SLOT_OFFSET).set(ItemStack.EMPTY);
			donkey.discard();
			if (dose <= 0 || single != chestOne) {
				helper.fail("a donkey's chest of uranium must irradiate as an iron chest does; dose=" + dose
						+ ", strength " + single + " vs " + chestOne);
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * The sweep must not roll a chest minecart's loot (MOD-524, ADR-010): on a vehicle {@code getItem}
	 * unpacks the table with no player, exactly as on a chest block.
	 *
	 * <p>Positive control first — the same minecart in the same spot, loaded by hand, is reached — so the
	 * untouched table proves a decision rather than an absence. The table field is the only safe witness.
	 *
	 * @implements R-RAD-29 — see docs/testing/RULES.md
	 */
	public static void sweepLeavesAMinecartsUngeneratedLootAlone(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = helper.spawn(EntityTypes.COW, BYSTANDER);

			MinecartChest loaded = helper.spawn(EntityTypes.CHEST_MINECART, RACK);
			loaded.setItem(0, stack());
			int reached = exposure(level, viewer);
			removeVehicle(loaded);
			if (reached <= 0) {
				helper.fail("rig is wrong: uranium in a chest minecart here must irradiate, otherwise the "
						+ "loot assertion below proves nothing; got " + reached);
				return;
			}

			MinecartChest pending = helper.spawn(EntityTypes.CHEST_MINECART, RACK);
			ContainerEntity cart = pending;
			cart.setContainerLootTable(BuiltInLootTables.ABANDONED_MINESHAFT);
			exposure(level, viewer);
			RadiationSources.detectedAt(level, viewer, RadiationConfig.radiationSourceRadius,
					RadiationConfig.radiationGroundRadius);
			boolean untouched = cart.getContainerLootTable() != null;
			// Drop the table before removing the cart, or the spill on discard would roll it.
			cart.setContainerLootTable(null);
			removeVehicle(pending);
			if (!untouched) {
				helper.fail("the sweep generated a chest minecart's loot with no player: the pending table is "
						+ "gone (MOD-524, ADR-010)");
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * A solid wall stops a vehicle's radiation exactly as it stops a chest block's: dose 0 behind it for
	 * both, while the counter still hears both through it, damped (MOD-579). The same cart with the wall
	 * taken away is the positive control.
	 *
	 * @implements R-RAD-30 — see docs/testing/RULES.md
	 */
	public static void wallStopsAVehicleAsItStopsAChest(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = helper.spawn(EntityTypes.COW, BYSTANDER);

			Entity cart = helper.spawn(EntityTypes.HOPPER_MINECART, RACK);
			((Container) cart).setItem(0, one());
			int open = exposure(level, viewer);
			// The trace from the cow's eyes to the cart's centre crosses both cells of this wall.
			helper.setBlock(WALL_LOW, Blocks.STONE);
			helper.setBlock(WALL_HIGH, Blocks.STONE);
			int walledCart = exposure(level, viewer);
			int heardCart = detected(level, viewer);
			removeVehicle(cart);

			helper.setBlock(RACK, ModContent.IRON_CHEST.get());
			Container chest = helper.getBlockEntity(RACK, IronChestBlockEntity.class);
			chest.setItem(0, one());
			int walledChest = exposure(level, viewer);
			int heardChest = detected(level, viewer);
			chest.clearContent();
			helper.setBlock(RACK, Blocks.AIR);

			if (open <= 0) {
				helper.fail("rig is wrong: a hopper minecart of uranium in the open must irradiate; got " + open);
				return;
			}
			if (walledCart != walledChest || walledCart != 0) {
				helper.fail("a wall must stop the cart as it stops the chest: cart " + walledCart + ", chest "
						+ walledChest + " (both must be 0)");
				return;
			}
			if (heardCart <= 0 || heardChest <= 0) {
				helper.fail("the counter must hear both through the wall, damped: cart " + heardCart + ", chest "
						+ heardChest);
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * The reach is measured from the observer to the CENTRE of the vehicle's box — the point the source
	 * sits at — not to its bottom, and a vehicle outside the reach adds nothing even when its box still
	 * touches the query box. The cart hangs straight above the cow, where the 0.35-block difference between
	 * bottom and centre decides it.
	 *
	 * @implements R-RAD-31 — see docs/testing/RULES.md
	 */
	public static void reachIsMeasuredToTheVehiclesCentre(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = helper.spawn(EntityTypes.COW, BYSTANDER);
			double reach = Math.min(RadiationConfig.radiationSourceRadius, RadiationConfig.radiationGroundRadius);
			int expected = RadiationCore.containerLeak(RadiationSources.strengthOf(one()),
					RadiationConfig.radiationContainerMaxItems, RadiationConfig.radiationDoseHighPerItem);

			Entity cart = helper.spawn(EntityTypes.CHEST_MINECART, new Vec3(1.5, 4.5, 3.5));
			((Container) cart).setItem(0, one());
			int inside = vehicleStrength(level, viewer, helper, "cart inside the reach");
			double insideCentre = cart.getBoundingBox().getCenter().distanceTo(viewer.position());

			cart.setPos(helper.absoluteVec(new Vec3(1.5, 4.9, 3.5)));
			int outside = vehicleStrength(level, viewer, helper, "cart beyond the reach");
			double outsideCentre = cart.getBoundingBox().getCenter().distanceTo(viewer.position());
			double outsideBottom = cart.position().distanceTo(viewer.position());
			removeVehicle(cart);

			if (insideCentre > reach || outsideCentre <= reach || outsideBottom > reach) {
				helper.fail("rig is wrong: centre distances " + insideCentre + " / " + outsideCentre + ", bottom "
						+ outsideBottom + " against a reach of " + reach);
				return;
			}
			if (inside != expected) {
				helper.fail("a cart whose centre is inside the reach must be one source of " + expected + "; got "
						+ inside);
				return;
			}
			if (outside != 0) {
				helper.fail("a cart whose centre is " + outsideCentre + " away must not count against a reach of "
						+ reach + " (its bottom, " + outsideBottom + " away, is not where the source is); got "
						+ outside);
				return;
			}
			helper.succeed();
		});
	}
}
