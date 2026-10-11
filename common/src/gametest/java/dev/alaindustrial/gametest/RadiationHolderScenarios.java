package dev.alaindustrial.gametest;

import dev.alaindustrial.core.radiation.RadiationConfig;
import dev.alaindustrial.core.radiation.RadiationCore;
import dev.alaindustrial.core.radiation.RadiationSources;
import dev.alaindustrial.core.radiation.RadiationVehicles;
import dev.alaindustrial.item.energy.PouchContents;
import dev.alaindustrial.item.energy.PouchItem;
import dev.alaindustrial.item.misc.ShieldingPouchItem;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Radiation from items HELD by entities (MOD-788): item frames, armour stands, mobs' hands and armour, and
 * the inventories of villagers, allays and piglins.
 *
 * <p>The rig of {@code RadiationVehicleScenarios}: both radii pinned to 3, the holder at {@code (1,2,1)}, a
 * cow two blocks away as the observer, everything called synchronously. Every mob is frozen ({@code NoAI},
 * no loot pickup) — a mob that walks off the rig is the MOD-794 flake — and emptied before it is discarded,
 * so nothing it held is left radiating next to a neighbour's rig.
 *
 * <p>The strength a holder radiates is read from the ONE entity collector, {@link RadiationVehicles#collectVehicles};
 * the cow itself is in that query's box and holds nothing, so it adds no source.
 */
public final class RadiationHolderScenarios {

	/** This class's world gametests for both loaders (ADR-038); nested so reading them does not initialise it. */
	public static final class Roster {
		public static final List<RosterEntry> ENTRIES = List.of(
				RosterEntry.of(RadiationHolderScenarios::framedStackRadiatesUncapped,
						"rad_holder_framed_stack_radiates_uncapped").ticks(20, 40),
				RosterEntry.of(RadiationHolderScenarios::stackInAMobsHandRadiatesAsOnTheFloor,
						"rad_holder_stack_in_a_mobs_hand_radiates_as_on_the_floor").ticks(20, 40),
				RosterEntry.of(RadiationHolderScenarios::armorStandSumsItsHandAndArmour,
						"rad_holder_armor_stand_sums_hand_and_armour").ticks(20, 40),
				RosterEntry.of(RadiationHolderScenarios::inventoryLeaksAsAChestAndIrradiatesItsCarrier,
						"rad_holder_inventory_leaks_as_a_chest").ticks(20, 40),
				RosterEntry.of(RadiationHolderScenarios::playerIsNotCountedAsAHolder,
						"rad_holder_player_is_not_counted_twice").ticks(20, 40),
				RosterEntry.of(RadiationHolderScenarios::wallStopsAFramedStack,
						"rad_holder_wall_stops_a_framed_stack").ticks(20, 40),
				RosterEntry.of(RadiationHolderScenarios::shieldingPouchHeldOrFramedStaysShielded,
						"rad_holder_shielding_pouch_stays_shielded").ticks(20, 40));

		private Roster() {}
	}

	private RadiationHolderScenarios() {
	}

	private static final BlockPos RACK = new BlockPos(1, 2, 1);
	private static final BlockPos BEHIND_RACK = new BlockPos(1, 2, 0);
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

	private static int exposure(ServerLevel level, Entity viewer) {
		return RadiationSources.exposureAt(level, viewer, RadiationConfig.radiationSourceRadius);
	}

	private static int detected(ServerLevel level, Entity viewer) {
		return RadiationSources.detectedAt(level, viewer, RadiationConfig.radiationSourceRadius,
				RadiationConfig.radiationGroundRadius);
	}

	/** The one entity source around the viewer, or 0 when the entity collector reports none. */
	private static int holderStrength(ServerLevel level, Entity viewer, GameTestHelper helper, String what) {
		List<RadiationSources.Source> out = new ArrayList<>();
		RadiationVehicles.collectVehicles(level, viewer.position(), RadiationConfig.radiationSourceRadius,
				RadiationConfig.radiationGroundRadius, out);
		if (out.size() > 1) {
			helper.fail(what + ": expected one source, got " + out.size());
		}
		return out.isEmpty() ? 0 : out.get(0).strength();
	}

	/** The player's carried strength, as {@code RadiationTicker} sums it per tag. */
	private static int carriedStrength(ServerPlayer player) {
		return RadiationSources.carried(player, ModTags.Items.RADIOACTIVE_LOW) * RadiationConfig.radiationDoseLowPerItem
				+ RadiationSources.carried(player, ModTags.Items.RADIOACTIVE_MEDIUM)
						* RadiationConfig.radiationDoseMediumPerItem
				+ RadiationSources.carried(player, ModTags.Items.RADIOACTIVE_HIGH)
						* RadiationConfig.radiationDoseHighPerItem;
	}

	private static ItemStack uranium(int count) {
		return new ItemStack(ModContent.REFINED_URANIUM.get(), count);
	}

	/** Raw strength of a stack — what it radiates lying on the floor. */
	private static int raw(ItemStack stack) {
		return RadiationSources.strengthOf(stack);
	}

	private static int chestCapped(ItemStack stack) {
		return RadiationCore.containerLeak(raw(stack), RadiationConfig.radiationContainerMaxItems,
				RadiationConfig.radiationDoseHighPerItem);
	}

	private static Cow viewer(GameTestHelper helper) {
		Cow cow = helper.spawn(EntityTypes.COW, BYSTANDER);
		cow.setNoAi(true);
		return cow;
	}

	/** A mob frozen in place: no goals, no loot pickup, never despawned mid-test. */
	private static <T extends Mob> T frozen(GameTestHelper helper, EntityType<T> type, BlockPos at) {
		T mob = helper.spawn(type, at);
		mob.setNoAi(true);
		mob.setCanPickUpLoot(false);
		mob.setPersistenceRequired();
		return mob;
	}

	/** A frame hanging at {@code RACK} on the block behind it, facing the cow. */
	private static ItemFrame frame(GameTestHelper helper, boolean glow, ItemStack held) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BEHIND_RACK, Blocks.STONE);
		BlockPos at = helper.absolutePos(RACK);
		ItemFrame frame = glow ? new GlowItemFrame(level, at, Direction.SOUTH)
				: new ItemFrame(level, at, Direction.SOUTH);
		level.addFreshEntity(frame);
		frame.setItem(held, false);
		return frame;
	}

	private static void removeFrame(ItemFrame frame) {
		frame.setItem(ItemStack.EMPTY, false);
		frame.discard();
	}

	/** Empty every slot (and the pockets) before discarding, so nothing is dropped and keeps radiating. */
	private static void removeHolder(LivingEntity holder) {
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			holder.setItemSlot(slot, ItemStack.EMPTY);
		}
		if (holder instanceof InventoryCarrier carrier) {
			carrier.getInventory().clearContent();
		}
		holder.discard();
	}

	/**
	 * What an item frame holds — plain or glowing — radiates its full raw strength, as on the floor. A frame
	 * shows ONE item, so the item is a bundle of sixteen refined uranium (its contents count, MOD-545): four
	 * times the chest ceiling, so a capped frame would read the ceiling instead.
	 *
	 * @implements R-RAD-32 — see docs/testing/RULES.md
	 */
	public static void framedStackRadiatesUncapped(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = viewer(helper);
			ItemStack sixteen = bundleOf(uranium(16));
			if (raw(sixteen) <= chestCapped(sixteen)) {
				helper.fail("rig is wrong: a bundle of sixteen uranium must be above the chest ceiling; raw="
						+ raw(sixteen) + ", capped=" + chestCapped(sixteen));
				return;
			}
			for (boolean glow : new boolean[] {false, true}) {
				String what = glow ? "glow item frame" : "item frame";
				ItemFrame frame = frame(helper, glow, sixteen.copy());
				int strength = holderStrength(level, viewer, helper, what);
				int dose = exposure(level, viewer);
				removeFrame(frame);
				if (strength != raw(sixteen) || dose <= 0) {
					helper.fail("the " + what + " holding a bundle of sixteen uranium must radiate the bundle's raw "
							+ raw(sixteen)
							+ " (no ceiling) and dose the cow; strength=" + strength + ", dose=" + dose);
					return;
				}
			}
			helper.succeed();
		});
	}

	/**
	 * A mob that picks a stack up radiates exactly what the stack radiated on the floor — a zombie, a piglin's
	 * hand, the copper golem's carried item. Before MOD-788 the pickup switched the source off.
	 *
	 * @implements R-RAD-33 — see docs/testing/RULES.md
	 */
	public static void stackInAMobsHandRadiatesAsOnTheFloor(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = viewer(helper);
			ItemStack sixteen = uranium(16);

			Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(RACK));
			ItemEntity loose = new ItemEntity(level, at.x, at.y, at.z, sixteen.copy());
			loose.setNoPickUpDelay();
			level.addFreshEntity(loose);
			List<RadiationSources.Source> ground = new ArrayList<>();
			RadiationSources.collectGround(level, viewer.position(), RadiationConfig.radiationSourceRadius,
					RadiationConfig.radiationGroundRadius, ground);
			loose.discard();
			int onTheFloor = ground.isEmpty() ? 0 : ground.get(0).strength();
			if (onTheFloor != raw(sixteen)) {
				helper.fail("rig is wrong: the loose stack must radiate its raw " + raw(sixteen) + "; got "
						+ onTheFloor);
				return;
			}

			List<EntityType<? extends Mob>> kinds = List.of(EntityTypes.ZOMBIE, EntityTypes.PIGLIN,
					EntityTypes.COPPER_GOLEM);
			for (EntityType<? extends Mob> kind : kinds) {
				String what = BuiltInRegistries.ENTITY_TYPE.getKey(kind).toString();
				Mob mob = frozen(helper, kind, RACK);
				mob.setItemSlot(EquipmentSlot.MAINHAND, sixteen.copy());
				int held = holderStrength(level, viewer, helper, what);
				int dose = exposure(level, viewer);
				removeHolder(mob);
				if (held != onTheFloor || dose <= 0) {
					helper.fail(what + " holding the stack must radiate as the stack on the floor: " + held + " vs "
							+ onTheFloor + ", dose=" + dose);
					return;
				}
			}
			helper.succeed();
		});
	}

	/**
	 * An armour stand radiates the sum of every slot — here a hand and the head, uncapped.
	 *
	 * @implements R-RAD-34 — see docs/testing/RULES.md
	 */
	public static void armorStandSumsItsHandAndArmour(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = viewer(helper);
			ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, RACK);
			stand.setItemSlot(EquipmentSlot.MAINHAND, uranium(16));
			int handOnly = holderStrength(level, viewer, helper, "armor stand, hand");
			stand.setItemSlot(EquipmentSlot.HEAD, uranium(1));
			int both = holderStrength(level, viewer, helper, "armor stand, hand and head");
			int dose = exposure(level, viewer);
			removeHolder(stand);
			int expected = raw(uranium(16)) + raw(uranium(1));
			if (handOnly != raw(uranium(16)) || both != expected || dose <= 0) {
				helper.fail("an armour stand must radiate every slot, uncapped: hand " + handOnly + " (want "
						+ raw(uranium(16)) + "), hand+head " + both + " (want " + expected + "), dose=" + dose);
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * What sits in a mob's inventory leaks as a chest does — the ceiling — while its hand stays uncapped and is
	 * added on top (the allay); a villager whose pockets hold uranium irradiates itself.
	 *
	 * @implements R-RAD-35 — see docs/testing/RULES.md
	 */
	public static void inventoryLeaksAsAChestAndIrradiatesItsCarrier(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = viewer(helper);
			int capped = chestCapped(uranium(64));
			if (capped <= 0 || capped >= raw(uranium(64))) {
				helper.fail("rig is wrong: 64 uranium must be capped below its raw strength; capped=" + capped);
				return;
			}

			List<EntityType<? extends Mob>> kinds = List.of(EntityTypes.VILLAGER, EntityTypes.ALLAY,
					EntityTypes.PIGLIN);
			for (EntityType<? extends Mob> kind : kinds) {
				String what = BuiltInRegistries.ENTITY_TYPE.getKey(kind).toString();
				Mob mob = frozen(helper, kind, RACK);
				((InventoryCarrier) mob).getInventory().setItem(0, uranium(64));
				int pockets = holderStrength(level, viewer, helper, what);
				removeHolder(mob);
				if (pockets != capped) {
					helper.fail(what + " with 64 uranium in its inventory must leak as a chest: " + pockets + " vs "
							+ capped);
					return;
				}
			}

			Mob allay = frozen(helper, EntityTypes.ALLAY, RACK);
			((InventoryCarrier) allay).getInventory().setItem(0, uranium(64));
			allay.setItemSlot(EquipmentSlot.MAINHAND, uranium(16));
			int handAndPockets = holderStrength(level, viewer, helper, "allay, hand and pockets");
			removeHolder(allay);
			if (handAndPockets != capped + raw(uranium(16))) {
				helper.fail("the allay's hand is on view and adds uncapped to its capped pockets: " + handAndPockets
						+ " vs " + (capped + raw(uranium(16))));
				return;
			}

			// Alone in the rig, away from the cow: only its own pockets can dose it.
			viewer.discard();
			Mob villager = frozen(helper, EntityTypes.VILLAGER, BYSTANDER);
			((InventoryCarrier) villager).getInventory().setItem(0, uranium(64));
			int self = exposure(level, villager);
			removeHolder(villager);
			if (self <= 0) {
				helper.fail("a villager carrying uranium must irradiate itself; dose=" + self);
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * A player's pockets, both hands and armour slots are dosed through {@code RadiationTicker} as its carried
	 * dose; the entity collector must not count the player again. The same stack on an armour stand in the
	 * same spot is the positive control, and the carried count must include the off-hand and the helmet slot —
	 * {@code getNonEquipmentItems()} alone holds neither, which once left uranium in the left hand inert.
	 *
	 * @implements R-RAD-36 — see docs/testing/RULES.md
	 */
	public static void playerIsNotCountedAsAHolder(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = viewer(helper);
			ItemStack sixteen = uranium(16);

			ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, RACK);
			stand.setItemSlot(EquipmentSlot.MAINHAND, sixteen.copy());
			int control = holderStrength(level, viewer, helper, "armor stand control");
			removeHolder(stand);
			if (control != raw(sixteen)) {
				helper.fail("rig is wrong: the armour stand control must radiate " + raw(sixteen) + "; got " + control);
				return;
			}

			ServerPlayer player = AlaGameTestHelper.survivalPlayer(helper);
			Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(RACK));
			player.snapTo(at.x, at.y, at.z, 0.0f, 0.0f);
			player.setItemSlot(EquipmentSlot.MAINHAND, sixteen.copy());
			player.setItemSlot(EquipmentSlot.OFFHAND, uranium(4));
			player.setItemSlot(EquipmentSlot.HEAD, uranium(1));
			boolean seen = level.getEntities((Entity) null, player.getBoundingBox().inflate(0.1), e -> e == player)
					.contains(player);
			int counted = holderStrength(level, viewer, helper, "player");
			int carried = carriedStrength(player);
			player.getInventory().clearContent();
			player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			int expected = raw(sixteen) + raw(uranium(4)) + raw(uranium(1));
			if (carried != expected) {
				helper.fail("a player's main hand, off-hand and helmet slot must all be its carried dose: carried="
						+ carried + ", expected " + expected + " (main hand alone " + raw(sixteen) + ")");
				return;
			}
			if (!seen) {
				helper.fail("rig is wrong: the mock player is not in the level's entity lookup, so this proves "
						+ "nothing");
				return;
			}
			if (counted != 0) {
				helper.fail("a player's held items must not be an entity source (they are its carried dose); got "
						+ counted);
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * A wall between a framed stack and the cow stops the dose as it stops a chest's, and the counter still
	 * hears it, damped (MOD-579). The same frame with the wall away is the positive control.
	 *
	 * @implements R-RAD-37 — see docs/testing/RULES.md
	 */
	public static void wallStopsAFramedStack(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = viewer(helper);
			ItemFrame frame = frame(helper, false, uranium(16));
			int open = exposure(level, viewer);
			helper.setBlock(WALL_LOW, Blocks.STONE);
			helper.setBlock(WALL_HIGH, Blocks.STONE);
			int walled = exposure(level, viewer);
			int heard = detected(level, viewer);
			removeFrame(frame);
			if (open <= 0) {
				helper.fail("rig is wrong: a framed stack of uranium in the open must irradiate; got " + open);
				return;
			}
			if (walled != 0 || heard <= 0) {
				helper.fail("a wall must stop the framed stack's dose (got " + walled
						+ ") while the counter still hears it, damped (got " + heard + ")");
				return;
			}
			helper.succeed();
		});
	}

	/**
	 * A shielding pouch keeps its contents shielded wherever it is held — in a frame or in a mob's hand — while
	 * the same uranium bare in the same place radiates.
	 *
	 * @implements R-RAD-38 — see docs/testing/RULES.md
	 */
	public static void shieldingPouchHeldOrFramedStaysShielded(GameTestHelper helper) {
		withIsolatedField(() -> {
			ServerLevel level = helper.getLevel();
			Cow viewer = viewer(helper);

			ItemFrame bareFrame = frame(helper, false, uranium(16));
			int bare = holderStrength(level, viewer, helper, "bare framed uranium");
			removeFrame(bareFrame);
			if (bare <= 0) {
				helper.fail("rig is wrong: bare framed uranium must radiate; got " + bare);
				return;
			}

			ItemFrame pouchFrame = frame(helper, false, shieldingPouchOf(uranium(16)));
			int framed = holderStrength(level, viewer, helper, "framed shielding pouch");
			removeFrame(pouchFrame);

			Mob zombie = frozen(helper, EntityTypes.ZOMBIE, RACK);
			zombie.setItemSlot(EquipmentSlot.MAINHAND, shieldingPouchOf(uranium(16)));
			int held = holderStrength(level, viewer, helper, "zombie holding a shielding pouch");
			int dose = exposure(level, viewer);
			removeHolder(zombie);
			if (framed != 0 || held != 0 || dose != 0) {
				helper.fail("a shielding pouch must shield wherever it is held: framed " + framed + ", in hand " + held
						+ ", dose " + dose + " (all must be 0)");
				return;
			}
			helper.succeed();
		});
	}

	/** A vanilla bundle holding {@code stack} — the same construction as {@code RadiationScenarios}. */
	private static ItemStack bundleOf(ItemStack stack) {
		ItemStack bundle = new ItemStack(Items.BUNDLE);
		bundle.set(DataComponents.BUNDLE_CONTENTS,
				new BundleContents(List.of(new ItemStackTemplate(stack.getItem(), stack.getCount()))));
		return bundle;
	}

	/** A shielding pouch holding {@code stack} — the same construction as {@code RadiationScenarios}. */
	private static ItemStack shieldingPouchOf(ItemStack stack) {
		ItemStack pouch = new ItemStack(ModContent.SHIELDING_POUCH.get());
		PouchItem.setContents(pouch,
				PouchContents.EMPTY.insert(stack, ShieldingPouchItem.storageCapacity()).contents());
		return pouch;
	}
}
