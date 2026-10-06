package dev.alaindustrial.block;

import dev.alaindustrial.block.entity.MobWheelBlockEntity;
import dev.alaindustrial.block.entity.MobWheelRoster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shared behaviour of the mob wheel's frame posts, running wheel, gate and invisible cells (MOD-763): what
 * every member of the structure does that the drive does too, written once.
 *
 * <ul>
 *   <li>Loose, a part draws its own block model; formed, it draws nothing — the drive's renderer draws the
 *       whole machine.</li>
 *   <li>Placing a part, or a neighbour changing next to a loose one, tries to assemble the wheel.</li>
 *   <li>A formed member that is replaced by another block takes the structure apart
 *       ({@link MobWheelStructure#onMemberRemoved}).</li>
 *   <li>Right-click on a formed member with planks recolours the whole wheel; with an empty hand it opens
 *       the drive's screen.</li>
 * </ul>
 */
public abstract class MobWheelMemberBlock extends Block {
	protected MobWheelMemberBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return MobWheelStructure.isFormedMember(state) ? RenderShape.INVISIBLE : RenderShape.MODEL;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
			ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		MobWheelStructure.tryAssembleNear(level, pos);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			@Nullable Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (MobWheelStructure.isPart(state) && !MobWheelStructure.isFormedMember(state)) {
			MobWheelStructure.tryAssembleNear(level, pos);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
			boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		MobWheelStructure.onMemberRemoved(level, pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		if (MobWheelStructure.isFormedMember(state)) {
			InteractionResult recoloured = recolour(stack, level, MobWheelStructure.findFormedController(level, pos),
					player);
			if (recoloured != null) {
				return recoloured;
			}
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (!MobWheelStructure.isFormedMember(state)) {
			return InteractionResult.PASS;
		}
		BlockPos controller = MobWheelStructure.findFormedController(level, pos);
		InteractionResult ledIn = leadIn(level, controller, player);
		if (ledIn != null) {
			return ledIn;
		}
		if (controller != null && level.getBlockEntity(controller) instanceof MenuProvider provider) {
			if (!level.isClientSide()) {
				player.openMenu(provider);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/**
	 * The lead shortcut (MOD-763): a player with a mob the wheel takes on a lead right-clicks any part of the
	 * formed wheel, and the mob goes in, the gate shuts and the lead comes back. Of several such mobs the one
	 * nearest the drive goes. {@code null} when the player leads no such mob, so the caller does its own use;
	 * reached with any item in hand, since {@code useItemOn} falls through to {@code useWithoutItem}.
	 */
	@Nullable
	static InteractionResult leadIn(Level level, @Nullable BlockPos controller, Player player) {
		if (controller == null) {
			return null;
		}
		Mob mob = null;
		double best = Double.MAX_VALUE;
		for (Leashable leashable : Leashable.leashableLeashedTo(player)) {
			if (leashable instanceof Mob candidate && MobWheelRoster.profileOf(candidate) != null) {
				double distance = candidate.distanceToSqr(Vec3.atCenterOf(controller));
				if (distance < best) {
					best = distance;
					mob = candidate;
				}
			}
		}
		if (mob == null) {
			return null;
		}
		if (!level.isClientSide() && level.getBlockEntity(controller) instanceof MobWheelBlockEntity drive
				&& !drive.leadIn(mob, player)) {
			player.sendOverlayMessage(Component.translatable("message.alaindustrial.mob_wheel.occupied"));
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * The wood family a stack of planks names — {@code oak} for {@code minecraft:oak_planks} — or {@code null}
	 * when the stack is not the planks of a vanilla {@link WoodType} (D2: the families are never listed by hand).
	 */
	@Nullable
	public static String woodOfPlanks(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (id == null || !"minecraft".equals(id.getNamespace()) || !id.getPath().endsWith("_planks")) {
			return null;
		}
		String wood = id.getPath().substring(0, id.getPath().length() - "_planks".length());
		return WoodType.values().anyMatch(type -> type.name().equals(wood)) ? wood : null;
	}

	/**
	 * Recolour the wheel driven from {@code controller} with the planks in {@code stack}: one plank is spent
	 * outside creative. {@code null} when the stack is not planks, so the caller falls through to its own use.
	 */
	@Nullable
	static InteractionResult recolour(ItemStack stack, Level level, @Nullable BlockPos controller, Player player) {
		String wood = woodOfPlanks(stack);
		if (wood == null || controller == null) {
			return null;
		}
		if (!(level.getBlockEntity(controller) instanceof MobWheelBlockEntity drive)) {
			return null;
		}
		if (wood.equals(drive.wood())) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			drive.setWood(wood);
			stack.consume(1, player);
			level.playSound(null, controller, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
		}
		return InteractionResult.SUCCESS;
	}
}
