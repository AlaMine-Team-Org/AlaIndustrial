package dev.alaindustrial.item.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.alaindustrial.junit.StopEphemeralServerBeforeFmlTeardown;
import dev.alaindustrial.registry.ModContent;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * MOD-723: the blueprint layout is a data-component value, so it must compare by value. Two layouts
 * built or decoded separately are separate {@link ItemStack} instances, and {@code ItemStack} has
 * identity equality — a record's generated {@code equals} over them would call them different.
 */
@ExtendWith(EphemeralTestServerProvider.class)
@ExtendWith(StopEphemeralServerBeforeFmlTeardown.class)
class BlueprintPatternEqualityTest {

	/** A fresh grid each call: every stack is a new instance, so identity can never make it equal. */
	private static List<ItemStack> grid() {
		return List.of(
				new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT),
				ItemStack.EMPTY, new ItemStack(Items.REDSTONE), ItemStack.EMPTY,
				new ItemStack(Items.COPPER_INGOT), ItemStack.EMPTY, new ItemStack(Items.COPPER_INGOT));
	}

	private static List<ItemStack> gridWith(int index, ItemStack cell) {
		ItemStack[] cells = grid().toArray(ItemStack[]::new);
		cells[index] = cell;
		return List.of(cells);
	}

	private static RegistryOps<JsonElement> ops(MinecraftServer server) {
		return server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
	}

	@Test
	void layoutsBuiltSeparatelyAreEqualAndHashEqual(MinecraftServer server) {
		BlueprintPattern a = BlueprintPattern.of(grid());
		BlueprintPattern b = BlueprintPattern.of(grid());

		assertNotSame(a.items().get(0), b.items().get(0), "the test needs distinct stack instances");
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	void layoutsDecodedSeparatelyAreEqualToEachOtherAndTheOriginal(MinecraftServer server) {
		BlueprintPattern original = BlueprintPattern.of(grid());
		JsonElement encoded = BlueprintPattern.CODEC.encodeStart(ops(server), original).getOrThrow();

		BlueprintPattern first = BlueprintPattern.CODEC.parse(ops(server), encoded).getOrThrow();
		BlueprintPattern second = BlueprintPattern.CODEC.parse(ops(server), encoded).getOrThrow();

		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
		assertEquals(original, first);
		assertEquals(original.hashCode(), first.hashCode());
	}

	@Test
	void networkCodecReturnsAnEqualLayout(MinecraftServer server) {
		BlueprintPattern original = BlueprintPattern.of(grid());
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
				server.registryAccess(), ConnectionType.OTHER);
		BlueprintPattern.STREAM_CODEC.encode(buffer, original);

		BlueprintPattern fromWire = BlueprintPattern.STREAM_CODEC.decode(buffer);

		assertEquals(original, fromWire);
		assertEquals(original.hashCode(), fromWire.hashCode());
	}

	@Test
	void differentLayoutsAreNotEqual(MinecraftServer server) {
		BlueprintPattern base = BlueprintPattern.of(grid());

		assertNotEquals(base, BlueprintPattern.of(gridWith(4, new ItemStack(Items.GLOWSTONE_DUST))),
				"another item in one cell");
		assertNotEquals(base, BlueprintPattern.of(gridWith(1, new ItemStack(Items.REDSTONE))),
				"an empty cell filled");
		ItemStack named = new ItemStack(Items.REDSTONE);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Dust"));
		assertNotEquals(base, BlueprintPattern.of(gridWith(4, named)), "a component on one cell");
		List<ItemStack> swapped = grid();
		assertNotEquals(base, BlueprintPattern.of(List.of(
				swapped.get(4), swapped.get(1), swapped.get(2),
				swapped.get(3), swapped.get(0), swapped.get(5),
				swapped.get(6), swapped.get(7), swapped.get(8))), "the same items in another order");
		// Kept apart on purpose: the codec tells them apart too, even though both read as blank.
		assertNotEquals(BlueprintPattern.EMPTY, BlueprintPattern.of(List.of(
				ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
				ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY)));
	}

	/**
	 * The promise in {@link AssemblyBlueprintItem#toggleSubstitution}: a blueprint switched on and off
	 * again is the same item as one never touched — which only holds if the layouts compare by value.
	 */
	@Test
	void blueprintsOfOneLayoutWrittenSeparatelyAreTheSameItem(MinecraftServer server) {
		ItemStack blank = new ItemStack(ModContent.ASSEMBLY_BLUEPRINT.get());
		ItemStack untouched = AssemblyBlueprintItem.record(blank, BlueprintPattern.of(grid()));
		ItemStack toggled = AssemblyBlueprintItem.record(blank, BlueprintPattern.of(grid()));

		assertTrue(ItemStack.isSameItemSameComponents(untouched, toggled));
		assertTrue(AssemblyBlueprintItem.toggleSubstitution(toggled));
		assertFalse(ItemStack.isSameItemSameComponents(untouched, toggled), "the flag on is a difference");
		assertFalse(AssemblyBlueprintItem.toggleSubstitution(toggled));
		assertTrue(ItemStack.isSameItemSameComponents(untouched, toggled));
	}
}
