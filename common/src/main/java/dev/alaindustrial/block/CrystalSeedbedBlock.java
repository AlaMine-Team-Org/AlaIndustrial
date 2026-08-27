package dev.alaindustrial.block;

import com.mojang.serialization.MapCodec;
import dev.alaindustrial.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The crystal seedbed (MOD-505) — a dead block of amethyst the player brings back to life by feeding
 * it shards, after which it buds real amethyst exactly as a natural geode does.
 *
 * <p><b>What it is, in one line: a budding amethyst you can craft.</b> Vanilla's own budding block
 * cannot be crafted, mined or moved at all — a farm is built around whichever geode the world
 * happened to generate, and that is the single thing about it worth changing rather than copying.
 * So the crafted block starts <em>spent</em>: the same crystalline stone, drained of colour, with
 * nothing growing on it. One amethyst shard wakes it up, and each one after that buys more buds.
 *
 * <p><b>The buds are vanilla's, not the mod's.</b> What grows here is
 * {@code small_amethyst_bud → medium → large → amethyst_cluster}, the actual blocks — so they look,
 * sound, break and drop exactly as the ones in a geode, and Fortune works on them because it is
 * vanilla's own loot table doing the work. A mod-made lookalike would have been four more textures
 * and a subtly different feel for no gain.
 *
 * <p><b>Charges make it a cycle rather than a tap.</b> A woken bed puts out the buds it was paid for
 * and then goes back to sleep — it does not vanish, it greys out and waits to be fed again. One
 * shard buys {@link Config#crystalSeedbedChargesPerShard} buds, so the loop turns a profit, but it
 * never becomes something the player can walk away from forever.
 *
 * <p><b>No block entity, and no random tick either.</b> The growing is done by
 * {@link dev.alaindustrial.block.entity.CrystalFarmControllerBlockEntity}, which walks the room it
 * seals. A hundred beds therefore add no ticking objects <em>and</em> no random-tick work, and
 * growth outside a sealed greenhouse is impossible by construction — the room is the machine.
 */
public class CrystalSeedbedBlock extends Block {

	public static final MapCodec<CrystalSeedbedBlock> CODEC = simpleCodec(CrystalSeedbedBlock::new);

	/**
	 * Buds this bed can still put out before it needs feeding again; {@code 0} is the dead state the
	 * player crafts and comes back to.
	 *
	 * <p>The range is fixed at compile time because a blockstate property must be: a feeding is
	 * clamped into it rather than defining it, so a generous config tops out at the ceiling instead of
	 * throwing on a state that does not exist.
	 */
	public static final int MAX_CHARGES = 16;

	public static final IntegerProperty CHARGES = IntegerProperty.create("charges", 0, MAX_CHARGES);

	/**
	 * Whether a sealed greenhouse is currently looking after this bed.
	 *
	 * <p>Painted by the controller as it walks its interior, and cleared when the room comes apart —
	 * the same mechanism that gives the deck and glazing their sealed look. The bed has no block
	 * entity and no way to go looking for a controller itself; a search would mean sweeping tens of
	 * thousands of blocks on a right-click to answer a question the controller already knows.
	 *
	 * <p>It exists for one reason: so a bed can TELL the player it is not going to grow anything.
	 * Playtest five fed a bed standing in the open, watched the charge counter climb, and had no way
	 * to learn that nothing would ever come of it.
	 */
	public static final BooleanProperty TENDED = BooleanProperty.create("tended");

	public CrystalSeedbedBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(CHARGES, 0).setValue(TENDED, false));
	}

	@Override
	protected MapCodec<? extends CrystalSeedbedBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(CHARGES, TENDED);
	}

	/** Whether this bed is awake and still has charge to put out another bud. */
	public static boolean canBud(BlockState state) {
		return state.getValue(CHARGES) > 0;
	}

	/** Spends one charge; at zero the bed greys out and waits to be fed again. */
	public static BlockState spendCharge(BlockState state) {
		return state.setValue(CHARGES, Math.max(0, state.getValue(CHARGES) - 1));
	}

	/** Buds one shard buys, clamped so a nonsensical config cannot make the farm free or crash it. */
	private static int chargesPerShard() {
		return Math.max(1, Config.crystalSeedbedChargesPerShard);
	}

	/**
	 * Feeding the bed: <b>one shard per click</b>, each buying {@link #chargesPerShard()} buds.
	 *
	 * <p>It used to swallow four shards in a single click, and playtest three rejected that outright:
	 * a click that empties a fistful of a scarce resource at once is alarming, and it gives the
	 * player no way to put in a little and see what happens. One at a time is also what makes the
	 * block legible — the charge counter visibly climbs, so the relationship between "shard in" and
	 * "buds out" can be discovered rather than read in a wiki. Topping up a working bed is allowed
	 * for the same reason; there was never a good reason to forbid it beyond tidiness.
	 *
	 * <p>A whole amethyst block counts as the four shards vanilla crafts it from, so a player who
	 * grabbed the block out of creative (which the search offers first) is not stuck.
	 *
	 * <p><b>Every refusal says why.</b> Playtest two reported "it just does not accept anything", and
	 * every way to be refused was silent. A block that ignores a click is indistinguishable from a
	 * broken one, so a refusal now answers on the action bar and returns
	 * {@link InteractionResult#SUCCESS} — which also stops an amethyst block in hand from being
	 * placed against the bed instead.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		boolean shard = stack.is(Items.AMETHYST_SHARD);
		boolean block = stack.is(Items.AMETHYST_BLOCK);
		if (!shard && !block) {
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		}
		int charges = state.getValue(CHARGES);
		if (charges >= MAX_CHARGES) {
			return refuse(level, pos, player, "message.alaindustrial.crystal_seedbed.full", charges);
		}
		// A block is four shards' worth, because that is exactly what vanilla crafts it from.
		int gain = chargesPerShard() * (block ? 4 : 1);
		if (charges + gain > MAX_CHARGES) {
			// Refused rather than clamped: clamping would swallow a whole block of amethyst to buy the
			// one charge that still fits, and silently binning three quarters of a scarce item is the
			// kind of thing a player only notices much later (found by audit). Shards still fit, so
			// this only ever turns away the bulk option on an almost-full bed.
			return refuse(level, pos, player, "message.alaindustrial.crystal_seedbed.no_room",
					MAX_CHARGES - charges);
		}
		if (!level.isClientSide()) {
			int now = Math.min(MAX_CHARGES, charges + gain);
			level.setBlock(pos, state.setValue(CHARGES, now), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f,
					// Rising pitch as the bed fills: feeding a nearly-full bed sounds different from
					// waking a dead one, without a single line of interface.
					0.8f + 0.4f * now / MAX_CHARGES);
			// The charge counter alone would be a lie by omission on a bed nobody is tending: it climbs
			// exactly the same, and nothing ever grows. Say so, every time, while the shards go in.
			player.sendOverlayMessage(state.getValue(TENDED)
					? Component.translatable("message.alaindustrial.crystal_seedbed.fed", now, MAX_CHARGES)
					: Component.translatable("message.alaindustrial.crystal_seedbed.untended", now)
							.withStyle(net.minecraft.ChatFormatting.GOLD));
			// consume() already respects creative — an infinite stack is left alone.
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}

	/** Turns a silent bow-out into an answer the player can act on. */
	private static InteractionResult refuse(Level level, BlockPos pos, Player player, String key,
			int arg) {
		if (!level.isClientSide()) {
			player.sendOverlayMessage(Component.translatable(key, arg));
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.7f, 0.6f);
		}
		return InteractionResult.SUCCESS;
	}
}
