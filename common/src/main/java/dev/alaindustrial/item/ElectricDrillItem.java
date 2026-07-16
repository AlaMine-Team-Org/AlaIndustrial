package dev.alaindustrial.item;

import dev.alaindustrial.Config;
import dev.alaindustrial.core.EnergyTier;
import dev.alaindustrial.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Electric Drill (MOD-079) — the first powered hand tool: a diamond-tier pickaxe that runs on EU
 * instead of durability, the mining-side counterpart of the mod's worn EU buffers (Pouch MOD-052,
 * Energy Pack MOD-065). Charge lives in the shared {@code pouch_energy} component through
 * {@link ItemEnergy}; the drill registers its own capacity/input-rate there, and everything that
 * charges an EU item (the Battery Box charge slot, a worn Energy Pack) then charges the drill with
 * no changes on their side.
 *
 * <h2>Why a hand-built {@code TOOL} component (path A)</h2>
 * MC 26.2 has no {@code PickaxeItem}/{@code Tier} class: a pickaxe is a plain {@link Item} whose
 * behaviour is the data-driven {@code minecraft:tool} component. The {@code Item.Properties.pickaxe(
 * ToolMaterial, ...)} helper would attach that component, but it also calls {@code .durability(...)}
 * — i.e. {@code MAX_DAMAGE} — which makes the item damageable and hands the item bar to vanilla
 * durability. For the "EU only, never breaks" model (like the Energy Pack, built without
 * {@code humanoidArmor} for the same reason) we assemble the {@code TOOL} component by hand in
 * {@link #electricDrillProperties}: the same two rules a diamond pickaxe uses, but no
 * {@code MAX_DAMAGE}/{@code REPAIRABLE}, so the bar is free to show the EU charge.
 *
 * <h2>EU behaviour</h2>
 * <ul>
 * <li>{@link #getDestroySpeed}: full {@code TOOL} speed (8.5 on {@code #minecraft:mineable/pickaxe})
 * while the drill holds at least one block's worth of EU; otherwise exactly hand speed (1.0f). The
 * mining level and drops come from the {@code TOOL} component either way, so a flat drill still
 * mines diamond-tier blocks and keeps their drops — it is just slow.</li>
 * <li>{@link #mineBlock}: drains {@link Config#electricDrillEuPerBlock} per successfully mined block,
 * server-side only and only for blocks with non-zero hardness (mirroring vanilla's durability gate),
 * and only when there was enough EU to run at tool speed in the first place.</li>
 * <li>{@link #useOn}: a mining QoL — right-clicking a block places a torch from the player's inventory
 * (the {@code enriched_uranium_torch} first, then the vanilla {@code torch}), so the drill never leaves
 * the hotbar while lighting a tunnel. Placement is delegated to the torch's own {@link BlockItem#place}
 * via a 1-item copy, so wall/floor orientation, {@code canSurvive}, the uranium torch's waterlogged
 * handling, the place sound and protection events all come for free; one torch is consumed from the
 * inventory and {@link Config#electricDrillTorchEuCost} EU is drained (skipped gracefully when the
 * drill can't afford it — the torch still places).</li>
 * </ul>
 */
public class ElectricDrillItem extends Item {

	/** Mining speed on {@code #minecraft:mineable/pickaxe} — a touch above the diamond value (8.0). The
	 * drill is crafted around a diamond pickaxe (see the recipe), so it out-digs the tool that goes into
	 * it; it still keeps the diamond mining tier. */
	private static final float MINING_SPEED = 8.5f;
	/** Enchantability — the diamond value (ToolMaterial.DIAMOND.enchantmentValue), for the enchanting table. */
	private static final int ENCHANT_VALUE = 10;
	/** Attack numbers — one above a vanilla diamond pickaxe: +5.0 damage modifier → 6 displayed
	 * (1 player base + 5), -2.7 speed → 1.3 displayed (4.0 base - 2.7). No {@code WEAPON} component:
	 * attacking neither drains EU nor wears the drill. */
	private static final double ATTACK_DAMAGE = 5.0;
	private static final double ATTACK_SPEED = -2.7;

	public ElectricDrillItem(Properties properties) {
		super(properties);
	}

	/**
	 * The drill's item properties, applied identically by both loaders (Fabric adds {@code setId},
	 * NeoForge supplies the id from its deferred key — that is the only difference).
	 *
	 * <p>Builds the {@code TOOL} component by hand rather than via {@code Properties.pickaxe(...)},
	 * which would also attach {@code MAX_DAMAGE} (see the class javadoc). The two rules replicate a
	 * vanilla diamond pickaxe: {@code deniesDrops} first (best practice, verbatim like the vanilla
	 * pickaxe), then {@code minesAndDrops} on {@code #mineable/pickaxe}. In vanilla 26.2
	 * {@code #incorrect_for_diamond_tool} is empty (diamond is the top mining tier — ancient debris is
	 * in {@code #needs_diamond_tool}, so a diamond pickaxe, and this drill, correctly mine it), so the
	 * deny rule only guards modded above-diamond blocks; order still matters for those. Mining speed is
	 * 8.5 here, a touch above the diamond value, while the tier stays diamond.
	 * {@code damagePerBlock = 0} means {@code super.mineBlock} never calls {@code hurtAndBreak} — the
	 * drill has no durability to spend. {@code stacksTo(1)} is set explicitly because we skip
	 * {@code durability(...)}, which is where a vanilla tool's max-stack-size of 1 comes from.
	 */
	public static Properties electricDrillProperties(Properties props) {
		HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
		return props.stacksTo(1)
				.component(DataComponents.TOOL, new Tool(
						List.of(
								Tool.Rule.deniesDrops(blocks.getOrThrow(BlockTags.INCORRECT_FOR_DIAMOND_TOOL)),
								Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_PICKAXE), MINING_SPEED)),
						1.0f, /*damagePerBlock*/ 0, /*canDestroyBlocksInCreative*/ true))
				.enchantable(ENCHANT_VALUE)
				.attributes(ItemAttributeModifiers.builder()
						.add(Attributes.ATTACK_DAMAGE,
								new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, ATTACK_DAMAGE,
										AttributeModifier.Operation.ADD_VALUE),
								EquipmentSlotGroup.MAINHAND)
						.add(Attributes.ATTACK_SPEED,
								new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, ATTACK_SPEED,
										AttributeModifier.Operation.ADD_VALUE),
								EquipmentSlotGroup.MAINHAND)
						.build());
	}

	// --- mining: full speed while charged, hand speed when flat (drops kept either way) ---

	/**
	 * Returns exactly {@code 1.0f} when the drill can't afford a block. That value is deliberate, not
	 * an approximation: {@code Player.getDestroySpeed} only adds the Efficiency bonus when the tool's
	 * speed is {@code > 1.0F}, so a flat drill is a plain hand — Efficiency cannot revive it. Any value
	 * slightly above 1.0f would switch the enchantment back on for an empty drill.
	 */
	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		if (ItemEnergy.get(stack) >= Config.electricDrillEuPerBlock) {
			return super.getDestroySpeed(stack, state);
		}
		return 1.0f;
	}

	/**
	 * Drains EU for the block just broken. Two guards mirror vanilla's durability gate
	 * ({@code Item.mineBlock}): {@code !isClientSide} because {@code mineBlock} runs on both sides and
	 * the charge must only change on the server (the client picks up the new value from the synced
	 * {@code pouch_energy} component), and non-zero hardness so instant-break blocks (grass, torches,
	 * flowers) cost nothing — just as they never wear a vanilla tool. Creative needs no handling:
	 * {@code ServerPlayerGameMode.destroyBlock} returns on {@code preventsBlockDrops()} before this is
	 * ever called. The drain is only taken when there was enough EU to mine at tool speed, so a block
	 * broken at hand speed (EU below the per-block cost) is free.
	 */
	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
		if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0.0f
				&& ItemEnergy.get(stack) >= Config.electricDrillEuPerBlock) {
			ItemEnergy.add(stack, -Config.electricDrillEuPerBlock);
		}
		return super.mineBlock(stack, level, state, pos, owner);
	}

	// --- right-click: place a torch from the inventory (MOD-089) — keep the drill in the hotbar ---

	/**
	 * Right-clicking a block with the drill in the main hand places a torch pulled from the player's
	 * inventory — a mining QoL so the hotbar never has to leave the drill while lighting a tunnel.
	 *
	 * <p>Priority is the {@code enriched_uranium_torch} first (the advanced, waterlog-safe torch), then
	 * the vanilla {@code torch}; with neither present this is a no-op and returns {@code PASS}, which lets
	 * a torch held in the off-hand place normally. Sneak-clicking and the off-hand are likewise reserved
	 * for normal interaction (mirror of {@code ScytheItem.useOn}).
	 *
	 * <p>Placement is delegated to the torch's own {@link BlockItem#place} via a fresh
	 * {@link BlockPlaceContext} built around a 1-item <b>copy</b> of the torch stack. Vanilla's
	 * {@code place()} consumes from the stack held in that context, so passing the drill directly would
	 * try to consume the drill; passing the copy lets vanilla pick floor-vs-wall orientation, enforce
	 * {@code canSurvive}, handle the uranium torch's waterlogged state, play the place sound and fire the
	 * place event for protection mods — all for free. The real inventory decrement is taken by hand,
	 * server-side only, only when placement actually succeeded ({@code consumesAction()}), and skipped in
	 * creative ({@code instabuild}).
	 *
	 * <p>EU drain ({@link Config#electricDrillTorchEuCost}) is taken the same way, but is skipped when the
	 * drill holds less than that — the torch still places, matching the drill's graceful-degradation rule
	 * for a flat battery (see {@link #mineBlock}). No cooldown: vanilla {@code BlockItem.place} already
	 * can't spam-place a single position, so a cooldown would only slow legitimate tunnel lighting.
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		// Main-hand only; reserve the off-hand and shift-click for normal interaction (matches ScytheItem).
		if (player == null || context.getHand() != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		Level level = context.getLevel();

		// Pick the torch to place: uranium first (advanced, waterlog-safe), then vanilla.
		BlockItem uraniumItem = (BlockItem) ModContent.ENRICHED_URANIUM_TORCH_ITEM.get();
		BlockItem torchItem = uraniumItem;
		ItemStack torchStack = findInInventory(player, uraniumItem);
		if (torchStack.isEmpty()) {
			torchItem = (BlockItem) Items.TORCH;
			torchStack = findInInventory(player, torchItem);
		}
		if (torchStack.isEmpty()) {
			// No torches anywhere in the inventory → no-op; lets an off-hand torch place normally.
			return InteractionResult.PASS;
		}

		ItemStack drill = context.getItemInHand();
		BlockPos clicked = context.getClickedPos();
		if (!player.mayBuild() || !player.mayUseItemAt(clicked, context.getClickedFace(), drill)) {
			return InteractionResult.PASS;
		}

		// Client only predicts the swing — never mutate inventory or place here (prevents double-place).
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		// Delegate to the torch's vanilla place() via a 1-item copy, so vanilla consumes from the copy
		// (not the drill) and we keep floor/wall selection, canSurvive, waterlogged, sound and events.
		// BlockItem.place() returns exactly SUCCESS (placement happened) or FAIL (no support / water /
		// protection veto / 5 early-return paths), and Success.consumesAction() is unconditionally true —
		// this is the authoritative signal vanilla itself trusts (BlockItem.useOn, ServerPlayerGameMode).
		// Do NOT recompute the placement position: BlockPlaceContext may flip replaceClicked and put the
		// torch at the clicked cell (e.g. side of tall grass / snow) rather than clicked.relative(face),
		// so a naive block-compare against the wrong pos would miss a real placement.
		ItemStack proxy = torchStack.copyWithCount(1);
		BlockHitResult hit = new BlockHitResult(
				context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
		BlockPlaceContext placeCtx = new BlockPlaceContext(player, context.getHand(), proxy, hit);

		if (!torchItem.place(placeCtx).consumesAction()) {
			// Invalid spot (no support, water for a vanilla torch, protection veto, …) → consume nothing.
			return InteractionResult.PASS;
		}
		if (!player.getAbilities().instabuild) {
			torchStack.shrink(1);
		}
		if (ItemEnergy.get(drill) >= Config.electricDrillTorchEuCost) {
			ItemEnergy.add(drill, -Config.electricDrillTorchEuCost);
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * First non-empty stack of {@code item} in the player's hotbar + main inventory, in slot order (so
	 * the selected hotbar slot wins first, matching player expectation). The off-hand is intentionally
	 * excluded — {@code getNonEquipmentItems()} mirrors the {@code EnergyPackItem} charge-scan convention.
	 * Compares by {@link ItemStack#getItem()} reference (not {@code stack.is(...)}, which in 26.2 takes a
	 * {@code Predicate<Holder<Item>>} and is for tag/membership tests, not a plain item identity check).
	 */
	private static ItemStack findInInventory(Player player, Item item) {
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (!stack.isEmpty() && stack.getItem() == item) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	// --- item bar shows the EU charge in the LV tier colour (numbers are in the tooltip) ---

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		long capacity = ItemEnergy.capacity(stack);
		if (capacity <= 0) {
			return 0;
		}
		return (int) Math.min(MAX_BAR_WIDTH, MAX_BAR_WIDTH * ItemEnergy.get(stack) / capacity);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return EnergyTier.LV.color();
	}
}
