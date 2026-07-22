package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.item.FilledCapsuleItem;
import dev.alaindustrial.item.FluidTankBlockItem;
import dev.alaindustrial.item.HintItem;
import dev.alaindustrial.item.ItemPipeBlockItem;
import dev.alaindustrial.item.ModArmorMaterials;
import dev.alaindustrial.item.StockDisplayFrameItem;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Item-construction helpers used by {@link ModItemsNeoForge} — the NeoForge mirror of the Fabric
 * {@code dev.alaindustrial.registry.ItemBuilders}. It holds the item factories and property operators
 * that would otherwise be inline lambdas scattered through the {@code DeferredRegister} declarations, so
 * the two loader modules keep a symmetric structure (main registry class + a sibling builders class).
 *
 * <p>Package-private — internal to the registration pass, not part of the mod's API.
 *
 * <p><b>Why factories/operators, not full registration:</b> unlike the Fabric side (eager
 * {@link net.minecraft.core.Registry#register}), NeoForge registers lazily through the module-owned
 * {@code DeferredRegister.Items ITEMS} on {@link ModItemsNeoForge}. That register — and its
 * {@code registerItem(...)} call, which derives the id from the deferred key — must stay next to the
 * field it assigns. So each helper here returns the {@code Function<Item.Properties, ? extends Item>}
 * factory (or the {@code Supplier}/{@code UnaryOperator} property step) that {@code registerItem(...)}
 * consumes, and the id literal stays on the {@code ITEMS.registerItem("id", ...)} call site.
 *
 * <p>Block/entity references are resolved with {@code .get()} <em>inside</em> the returned lambda, so
 * they resolve during the item {@code RegisterEvent} (never at static-init) — same lazy timing as the
 * inline lambdas this replaces.
 */
final class ItemBuildersNeoForge {
	private ItemBuildersNeoForge() {
	}

	// --- Plain items ---

	/** Factory for a plain item with two gray hint lines (keys {@code item.alaindustrial.<path>.hint}/{@code .hint2}). */
	static Function<Item.Properties, Item> hint(String path) {
		return p -> new HintItem(p,
				"item.alaindustrial." + path + ".hint", "item.alaindustrial." + path + ".hint2");
	}

	// --- Tempered-iron armor (MOD-056) ---

	// Property operator: humanoidArmor(material, type) wires durability, attributes, enchantability, the
	// EQUIPPABLE component (equip sound + asset id from the material) and the repair tag in one call —
	// exactly how vanilla Items.IRON_HELMET is built. Each piece stays `registerItem(name, Item::new, op)`.
	static UnaryOperator<Item.Properties> temperedArmor(ArmorType type) {
		return p -> p.humanoidArmor(ModArmorMaterials.TEMPERED_IRON, type);
	}

	// --- Spawning / entity-bound ---

	// Stock Display Frame (MOD-066). The ScreenHandler-less item wraps the entity type; resolving the
	// entity-type holder inside the lambda keeps it to the item RegisterEvent (entity register already fired).
	static Function<Item.Properties, StockDisplayFrameItem> stockDisplayFrame() {
		return p -> new StockDisplayFrameItem(ModEntitiesNeoForge.STOCK_DISPLAY_FRAME.get(), p);
	}

	// Filled capsule (MOD-077): craftRemainder = the empty capsule, so a lava capsule burnt in a vanilla
	// furnace returns an empty capsule (the fuel remainder), like a lava bucket returns an empty bucket.
	static UnaryOperator<Item.Properties> filledCapsuleProperties(Supplier<? extends Item> remainder) {
		return p -> p.stacksTo(FilledCapsuleItem.STACK_SIZE).craftRemainder(remainder.get());
	}

	// --- Block items ---

	/** {@code useBlockDescriptionPrefix()} base properties for a block item (id applied by NeoForge). */
	static Supplier<Item.Properties> blockItemProperties() {
		return () -> new Item.Properties().useBlockDescriptionPrefix();
	}

	/** Block-item factory for the item pipe, whose block item carries a tooltip (MOD-108). */
	static Function<Item.Properties, BlockItem> pipeItem(Supplier<? extends Block> block) {
		return p -> new ItemPipeBlockItem(block.get(), p.useBlockDescriptionPrefix());
	}

	static Function<Item.Properties, FluidTankBlockItem> fluidTankBlockItem(Supplier<? extends Block> block) {
		return p -> new FluidTankBlockItem(block.get(), p.useBlockDescriptionPrefix());
	}

	// Torch-style item (MOD-085): places `standing` on the floor, `wall` on a vertical face — exactly how
	// vanilla Items.TORCH is built (StandingAndWallBlockItem(TORCH, WALL_TORCH, Direction.DOWN, p)). Paired
	// with blockItemProperties() as the property supplier at the call site.
	static Function<Item.Properties, BlockItem> standingAndWallBlockItem(
			Supplier<? extends Block> standing, Supplier<? extends Block> wall) {
		return p -> new StandingAndWallBlockItem(standing.get(), wall.get(), Direction.DOWN, p);
	}
}
