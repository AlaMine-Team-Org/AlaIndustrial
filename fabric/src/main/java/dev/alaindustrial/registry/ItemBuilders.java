package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.item.ElectricDrillItem;
import dev.alaindustrial.item.EnergyPackItem;
import dev.alaindustrial.item.FluidTankBlockItem;
import dev.alaindustrial.item.HintItem;
import dev.alaindustrial.item.MagnetItem;
import dev.alaindustrial.item.ModArmorMaterials;
import dev.alaindustrial.item.NetworkAnalyzerItem;
import dev.alaindustrial.item.PouchItem;
import dev.alaindustrial.item.ScytheItem;
import dev.alaindustrial.item.TeleporterRemoteItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Static item-construction helpers used by {@link ModItems} to register the mod's items against
 * Fabric's eager {@link Registry#register}. Extracted from {@code ModItems} so that file holds the
 * item declarations and the creative-tab wiring, while this file is purely "given a path + a factory,
 * return a registered item/block-item".
 *
 * <p>Package-private — internal to the registration pass, not part of the mod's API. The methods
 * share a single pattern: build the {@link ResourceKey} from {@link Industrialization#id}, apply it
 * to {@link Item.Properties#setId}, then {@link Registry#register} the result.
 *
 * <p>The {@code <T extends Item>} generic helpers ({@link #item(String, java.util.function.Function)},
 * {@link #blockItem(String, Block, java.util.function.Function)}) exist so the typed factories (e.g.
 * a {@code PouchItem::new}) can be passed directly without a wrapping lambda on every call site.
 */
final class ItemBuilders {
	private ItemBuilders() {
	}

	// --- Plain items ---

	static Item item(String path) {
		ResourceKey<Item> key = key(path);
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
	}

	/** A plain item with two gray hint lines (keys {@code item.alaindustrial.<path>.hint}/{@code .hint2}). */
	static Item hintItem(String path) {
		ResourceKey<Item> key = key(path);
		return Registry.register(BuiltInRegistries.ITEM, key,
				new HintItem(new Item.Properties().setId(key),
						"item.alaindustrial." + path + ".hint", "item.alaindustrial." + path + ".hint2"));
	}

	static Item teleporterRemote(String path) {
		return register(path, p -> new TeleporterRemoteItem(p.stacksTo(1)));
	}

	static Item networkAnalyzer(String path) {
		// The default AnalyzerMode (TRAVERSE) is NOT set via Item.Properties.component(...): that path
		// resolves the DataComponentType during ModItems' static init, before the loader binds the
		// neutral handle (see IndustrializationFabric/ModDataComponentsNeoForge). NetworkAnalyzerItem
		// treats a missing component as TRAVERSE instead, and switchMode persists it on first use.
		return register(path, p -> new NetworkAnalyzerItem(p.stacksTo(1)));
	}

	static Item wrench(String path) {
		return register(path, p -> new dev.alaindustrial.item.WrenchItem(p.stacksTo(1)));
	}

	static Item guideBook(String path) {
		return register(path, p -> new dev.alaindustrial.item.GuideBookItem(p.stacksTo(1)));
	}

	static Item pouch(String path) {
		// Like networkAnalyzer: no Item.Properties.component(...) defaults — PouchItem/ItemEnergy treat
		// an absent pouch_energy/pouch_contents as 0 EU / empty (MOD-052).
		return register(path, p -> new PouchItem(p.stacksTo(1)));
	}

	// Energy pack (MOD-065). Equipment properties come from the common helper (EQUIPPABLE + token armor
	// attribute, no ArmorMaterial — see EnergyPackItem.equipmentProperties); like the pouch, no
	// pouch_energy default is preset, ItemEnergy reads an absent component as 0 EU.
	static Item energyPack(String path) {
		return register(path, p -> new EnergyPackItem(EnergyPackItem.equipmentProperties(p)));
	}

	// Electric Drill (MOD-079). Like the Energy Pack: a plain-Item subclass whose properties come from
	// a common static factory (the hand-built TOOL component + EU-item bar, no MAX_DAMAGE — see
	// ElectricDrillItem.electricDrillProperties). No pouch_energy default is preset; ItemEnergy reads an
	// absent component as 0 EU.
	static Item electricDrill(String path) {
		return register(path, p -> new ElectricDrillItem(ElectricDrillItem.electricDrillProperties(p)));
	}

	// Electromagnet (MOD-132). A plain-Item subclass; stacksTo(1) because it carries per-item state
	// (EU + on/off). No pouch_energy default — ItemEnergy reads an absent component as 0 EU.
	static Item magnet(String path) {
		return register(path, p -> new MagnetItem(p.stacksTo(1)));
	}

	// Vacuum Capsule (MOD-063). Empty capsule stacks to the vanilla default (64); no fluid component
	// default — ItemFluid treats an absent capsule_fluid as empty.
	static Item vacuumCapsule(String path) {
		return register(path, dev.alaindustrial.item.VacuumCapsuleItem::new);
	}

	// Filled capsule: one bucket of a single fluid, stacking to FilledCapsuleItem.STACK_SIZE (16).
	// MOD-077: craftRemainder = the empty capsule, so a lava capsule burnt in a vanilla furnace returns an
	// empty capsule (the fuel remainder), exactly as a lava bucket returns an empty bucket. VACUUM_CAPSULE is
	// initialised just above (field order), so it is already registered here.
	static Item filledCapsule(String path, Item remainder) {
		return register(path, p -> new dev.alaindustrial.item.FilledCapsuleItem(
				p.stacksTo(dev.alaindustrial.item.FilledCapsuleItem.STACK_SIZE).craftRemainder(remainder)));
	}

	// --- Tempered-iron hand-held tools (MOD-054) ---

	// In MC 26.2 PickaxeItem/SwordItem/DiggerItem were removed — a pickaxe/sword is a plain Item whose
	// `minecraft:tool` component is attached via Item.Properties.{pickaxe,sword}(ToolMaterial,
	// attackDamage, attackSpeed). AxeItem/HoeItem/ShovelItem still exist (they carry useOn behavior:
	// stripping/tilling/path) and are used for those three tools — see temperedIronSubclass.
	// attackDamage/attackSpeed mirror vanilla iron.
	static Item temperedIronTool(String path, java.util.function.UnaryOperator<Item.Properties> toolProps) {
		ResourceKey<Item> key = key(path);
		return Registry.register(BuiltInRegistries.ITEM, key,
				new Item(toolProps.apply(new Item.Properties()).setId(key)));
	}

	// Axe/Hoe/Shovel subclass helper. The vanilla ctors (ToolMaterial, float attackDamage, float
	// attackSpeed, Properties) themselves call props.{axe,hoe,shovel}(...) and super(...), so the
	// tool component AND the useOn behavior (log stripping / dirt tilling / grass path) come free.
	// factory binds the two floats to a concrete ctor reference like AxeItem::new.
	static Item temperedIronSubclass(String path, java.util.function.Function<Item.Properties, Item> factory) {
		return register(path, factory);
	}

	// Tempered-iron armor helper (MOD-056). humanoidArmor(material, type) wires durability
	// (type.getDurability(material.durability())), attributes, enchantability, the EQUIPPABLE
	// component (equip sound + asset id from the material) and the repair tag in one call — this
	// is exactly how vanilla Items.IRON_HELMET is built (javap-verified against the 26.2 jar).
	static Item temperedArmor(String path, ArmorType type) {
		return register(path, p -> new Item(p.humanoidArmor(ModArmorMaterials.TEMPERED_IRON, type)));
	}

	// --- Scythe (MOD-068) ---

	// .hoe(material, attackDamage, attackSpeed) attaches the data-driven tool component (durability/
	// mining speed/enchantability/attack) exactly like a vanilla hoe, but the instance is a ScytheItem
	// so right-click runs the AOE clear instead of tilling.
	static Item scythe(String path, ToolMaterial material, float attackDamage,
			ScytheItem.Profile profile, boolean fireResistant) {
		return register(path, p -> {
			Item.Properties props = p.hoe(material, attackDamage, -1.0f);
			if (fireResistant) {
				props.fireResistant();
			}
			return new ScytheItem(profile, props);
		});
	}

	// --- Spawning / entity-bound ---
	// (StockDisplayFrameItem is constructed inline in ModItems because its ctor takes the EntityType
	// at construction time, captured from ModEntities rather than threaded through a builder here.)

	// --- Block items ---

	static net.minecraft.world.item.BlockItem blockItem(String path, Block block) {
		ResourceKey<Item> key = key(path);
		net.minecraft.world.item.BlockItem item = new net.minecraft.world.item.BlockItem(block,
				new Item.Properties().useBlockDescriptionPrefix().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	/** {@link #blockItem} for the item pipe, whose block item carries a tooltip (MOD-108). */
	static net.minecraft.world.item.BlockItem pipeItem(String path, Block block) {
		ResourceKey<Item> key = key(path);
		net.minecraft.world.item.BlockItem item = new dev.alaindustrial.item.ItemPipeBlockItem(block,
				new Item.Properties().useBlockDescriptionPrefix().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	static net.minecraft.world.item.BlockItem fluidTankBlockItem(String path, Block block) {
		ResourceKey<Item> key = key(path);
		net.minecraft.world.item.BlockItem item = new FluidTankBlockItem(block,
				new Item.Properties().useBlockDescriptionPrefix().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	// Torch-style item (MOD-085): places `standing` on the floor, `wall` on a vertical face — exactly how
	// vanilla Items.TORCH is built (StandingAndWallBlockItem(TORCH, WALL_TORCH, Direction.DOWN, p)).
	static net.minecraft.world.item.BlockItem standingAndWallBlockItem(String path, Block standing, Block wall) {
		ResourceKey<Item> key = key(path);
		net.minecraft.world.item.StandingAndWallBlockItem item =
				new net.minecraft.world.item.StandingAndWallBlockItem(standing, wall,
						net.minecraft.core.Direction.DOWN, new Item.Properties().useBlockDescriptionPrefix().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	// --- Shared internals ---

	private static ResourceKey<Item> key(String path) {
		return ResourceKey.create(Registries.ITEM, Industrialization.id(path));
	}

	private static <T extends Item> T register(String path, java.util.function.Function<Item.Properties, T> factory) {
		ResourceKey<Item> key = key(path);
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}
}
