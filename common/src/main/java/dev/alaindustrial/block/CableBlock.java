package dev.alaindustrial.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alaindustrial.Config;
import dev.alaindustrial.block.entity.CableBlockEntity;
import dev.alaindustrial.core.energy.CableType;
import dev.alaindustrial.core.energy.NetworkManager;
import dev.alaindustrial.core.energy.ShockGuardMaterial;
import dev.alaindustrial.core.energy.ShockInsulation;
import dev.alaindustrial.registry.ModContent;
import dev.alaindustrial.registry.ModCriteria;
import dev.alaindustrial.registry.ModDamageTypes;
import dev.alaindustrial.registry.ModTags;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * LV copper cable. Connects (visually + for routing) to adjacent cables and machines via the six
 * {@code north/south/east/west/up/down} connection properties (reused from {@link PipeBlock}),
 * so a multipart blockstate can join segments with arm models. Energy relay lives in
 * {@link CableBlockEntity}.
 *
 * <p>Horizontal arms toward a <b>half-block</b> neighbour (e.g. a Solar Panel, top at Y=8px) drop
 * to the base of the block via a per-direction {@code *_low} flag — see {@link #ARMS_LOW} — so the
 * sleeve hugs the slab's side instead of floating 3px above its surface (MOD-042). The flag is
 * derived generically from the neighbour's collision shape ({@code maxY <= 0.5}), not from any
 * specific block type, so any future half-block machine connects the same way.
 */
public class CableBlock extends AbstractMachineBlock {
	/**
	 * Carries the grade's balance numbers (throughput/packet cap/loss) — see {@link CableType}. Cannot use
	 * {@code simpleCodec} like the other blocks, because that requires a lone {@code (Properties)}
	 * constructor; the extra field takes the same {@code RecordCodecBuilder} + {@code propertiesCodec()}
	 * shape already used by {@link EnrichedUraniumTorchBlock}.
	 */
	private static final Codec<CableType> TYPE_CODEC = Codec.STRING.xmap(
			s -> {
				for (CableType t : CableType.values()) {
					if (t.serializedName().equals(s)) {
						return t;
					}
				}
				return CableType.COPPER;
			},
			CableType::serializedName);

	public static final MapCodec<CableBlock> CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(TYPE_CODEC.fieldOf("cable_type").forGetter(CableBlock::type), propertiesCodec())
					.apply(i, CableBlock::new));

	private final CableType type;

	/** Collision/outline that matches the model: a 6px core plus an arm toward each connection. */
	private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
	/**
	 * The four worn slots an insulating set can occupy (MOD-466). Listed rather than derived: 26.2 has
	 * no {@code getArmorSlots()}, and {@code EquipmentSlot.values()} would also sweep the hands and the
	 * body slot, where a rubber sleeve is not being worn by anyone.
	 */
	private static final EquipmentSlot[] ARMOUR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
	static {
		ARMS.put(Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11));
		ARMS.put(Direction.UP, Block.box(5, 11, 5, 11, 16, 11));
		ARMS.put(Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5));
		ARMS.put(Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16));
		ARMS.put(Direction.WEST, Block.box(0, 5, 5, 5, 11, 11));
		ARMS.put(Direction.EAST, Block.box(11, 5, 5, 16, 11, 11));
	}

	/**
	 * Low arms for horizontal connections to a half-block neighbour (e.g. a Solar Panel, top at
	 * Y=8). The normal horizontal arm sits at Y=5..11 (the cable's vertical centre), so its upper
	 * 3px would float in the air above a 0.5-block surface. These drop the sleeve to Y=0..5 so it
	 * hugs the base/side of the slab — same 6px cross-section, lowered. See {@link #LOW_FLAGS}.
	 */
	private static final Map<Direction, VoxelShape> ARMS_LOW = new EnumMap<>(Direction.class);
	static {
		ARMS_LOW.put(Direction.NORTH, Shapes.or(
				Block.box(5, 5, 2, 11, 11, 5),
				Block.box(5, 2, 0, 11, 8, 2)));
		ARMS_LOW.put(Direction.SOUTH, Shapes.or(
				Block.box(5, 5, 11, 11, 11, 14),
				Block.box(5, 2, 14, 11, 8, 16)));
		ARMS_LOW.put(Direction.WEST, Shapes.or(
				Block.box(2, 5, 5, 5, 11, 11),
				Block.box(0, 2, 5, 2, 8, 11)));
		ARMS_LOW.put(Direction.EAST, Shapes.or(
				Block.box(11, 5, 5, 14, 11, 11),
				Block.box(14, 2, 5, 16, 8, 11)));
	}

	/**
	 * Per-horizontal-direction {@code *Low} flag — {@code true} when the neighbour in that direction
	 * is a half-block (see {@link HalfBlockNeighbour}), so the arm drops to
	 * {@link #ARMS_LOW}. Vertical directions are not flagged: a cable above/below a slab connects
	 * at the cell centre already and is visually fine.
	 */
	private static final Map<Direction, BooleanProperty> LOW_FLAGS = new EnumMap<>(Direction.class);
	static {
		LOW_FLAGS.put(Direction.NORTH, BooleanProperty.create("north_low"));
		LOW_FLAGS.put(Direction.SOUTH, BooleanProperty.create("south_low"));
		LOW_FLAGS.put(Direction.WEST, BooleanProperty.create("west_low"));
		LOW_FLAGS.put(Direction.EAST, BooleanProperty.create("east_low"));
	}

	/**
	 * A visually negligible contact tolerance around the solid cable shape. It only needs to exceed
	 * Minecraft's internal collision epsilon: an equal inside/collision shape can never overlap because
	 * collision resolution stops the entity exactly at the solid surface.
	 */
	private static final double SHOCK_CONTACT_MARGIN = 1.0E-3;

	/**
	 * Public accessor for the four horizontal {@code *_low} flags, used by
	 * {@link CableBlockEntity#validateShape} to re-derive the low-arm geometry on chunk load (MOD-061).
	 * Returns {@code null} for vertical directions (UP/DOWN have no low variant), matching the
	 * {@link #LOW_FLAGS} contents.
	 */
	public static BooleanProperty lowFlagFor(Direction dir) {
		return LOW_FLAGS.get(dir);
	}

	/**
	 * Whether a breaker on this segment is thrown open (MOD-276).
	 *
	 * <p><b>Why this lives in the blockstate and not only in the block entity.</b> Connection arms are
	 * decided by {@link AbstractMachineBlock#isCableConnectable(BlockState, Direction)}, which reads a
	 * {@link BlockState} and deliberately never touches a block entity (no load race — see that
	 * method's contract). An open breaker has to physically break the run — the wire must show a gap,
	 * not merely stop conducting — so the state the neighbours consult must be in the palette.
	 */
	public static final BooleanProperty BREAKER_OPEN = BooleanProperty.create("breaker_open");

	public CableBlock(CableType type, Properties properties) {
		super(properties);
		this.type = type;
		BlockState state = stateDefinition.any();
		for (BooleanProperty prop : PipeBlock.PROPERTY_BY_DIRECTION.values()) {
			state = state.setValue(prop, false);
		}
		for (BooleanProperty prop : LOW_FLAGS.values()) {
			state = state.setValue(prop, false);
		}
		registerDefaultState(state.setValue(BREAKER_OPEN, false));
	}

	/**
	 * An open breaker makes the whole segment inert to cable arms: neighbours stop drawing an arm
	 * toward it, and it draws none itself, so the run visibly gaps at the switch (MOD-276). This is the
	 * same one-way rule inert machine faces already follow, so {@code CableFaceParityScenarios} covers
	 * it for free.
	 */
	@Override
	public boolean isCableConnectable(BlockState state, Direction side) {
		return !state.getValue(BREAKER_OPEN) && super.isCableConnectable(state, side);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	/**
	 * This cable's grade — the single place its throughput/packet cap/loss come from. Read by
	 * {@link CableBlockEntity} at construction (via {@link #typeOf(BlockState)}) so the block entity is
	 * built with the right buffer and tier, and by {@code MachineTooltips} to show per-grade numbers.
	 */
	public CableType type() {
		return type;
	}

	/**
	 * The cable grade of the block in {@code state}, or {@link CableType#COPPER} when {@code state} is not
	 * a cable. The fallback is not expected in practice — {@link CableBlockEntity} is only ever created by
	 * {@link #newBlockEntity} — but a block entity must never fail to construct: a
	 * corrupted/mismatched chunk palette entry would otherwise throw during world load. Copper is the
	 * right fallback because it is the historical behaviour every cable had before MOD-219.
	 */
	public static CableType typeOf(BlockState state) {
		return state.getBlock() instanceof CableBlock cable ? cable.type : CableType.COPPER;
	}

	/**
	 * Shocks a survival player only while energy actually moved through this exact bare segment.
	 * Vanilla hurt immunity supplies the cactus-style contact cooldown; insulated cables and the
	 * config-off path return before registry lookup, particles, sound or any other overhead.
	 */
	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
			InsideBlockEffectApplier effectApplier, boolean isInside) {
		tryShockPlayer(level, pos, entity);
	}

	/** Public for the shared Fabric/NeoForge GameTest body; normal gameplay enters via {@link #entityInside}. */
	public boolean tryShockPlayer(Level level, BlockPos pos, Entity entity) {
		if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof ServerPlayer player)
				|| !shouldShockPlayer(level, pos, player)) {
			return false;
		}
		if (!passesShockGuard(serverLevel, pos, player)) {
			return false;
		}

		float raw = type.shockDamage();
		float remaining = insulatedShockDamage(raw, player);
		wearInsulation(player, raw - remaining);
		if (remaining <= 0.0f) {
			// No hurtServer means no vanilla invulnerability window either, and both hazard paths run
			// every tick of contact — without this the set would be charged wear twenty times a second
			// and a helmet would die in three seconds of standing still. Exactly the trap MOD-279's
			// stand fell into; here it bites durability instead of the hit chance.
			player.invulnerableTime = Config.shockGuardGraceTicks;
			return false;
		}

		if (!player.hurtServer(serverLevel, ModDamageTypes.electricShock(level), remaining)) {
			return false;
		}
		serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
				player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(),
				8, 0.25, 0.35, 0.25, 0.08);
		serverLevel.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_IMPACT,
				SoundSource.BLOCKS, 0.35f, 1.8f);
		return true;
	}

	/**
	 * Rolls an installed insulating stand's chance to absorb a shock that {@link #shouldShockPlayer}
	 * already ruled eligible (MOD-279). Returns {@code true} when the shock should land.
	 *
	 * <p>Deliberately <b>not</b> folded into {@link #shouldShockPlayer}: that method is a pure,
	 * repeatable predicate that both GameTest lanes assert directly, and making it randomly answer
	 * differently on identical input would break that contract. The roll therefore sits on the one path
	 * that actually deals damage.
	 *
	 * <p>A bare segment short-circuits before touching the RNG at all, so the shipped MOD-260 behaviour
	 * keeps its exact random stream — no stand, no roll, guaranteed hit.
	 *
	 * <p>When a stand does absorb the hit, the player is granted the same invulnerability window a real
	 * hit would have produced. Without it the caller would roll again on the very next tick of contact
	 * (both {@code entityInside} and the proximity sweep run every tick), so a 50 % stand would still
	 * let a shock through within a fraction of a second — the chance would read as per-tick when the
	 * player experiences it as per-contact.
	 *
	 * <p>Public for the same reason {@link #shouldShockPlayer} is: both loader GameTest lanes drive this
	 * decision directly. They cannot go through {@link #tryShockPlayer} for the case where the shock
	 * <em>lands</em>, because that reaches {@code hurtServer}, and a GameTest mock player has no
	 * {@code connection} for vanilla to notify — the same constraint that already keeps
	 * {@code energizedBareOnly} on the predicate rather than on health loss.
	 */
	public boolean passesShockGuard(ServerLevel level, BlockPos pos, ServerPlayer player) {
		if (!(level.getBlockEntity(pos) instanceof CableBlockEntity cable)) {
			return true;
		}
		ShockGuardMaterial guard = cable.shockGuard();
		if (!guard.isPresent()) {
			return true;
		}
		if (level.getRandom().nextDouble() < guard.hitChance()) {
			return true;
		}
		player.invulnerableTime = Config.shockGuardGraceTicks;
		return false;
	}

	/**
	 * Worn pieces of insulating armour on this player, 0..4 (MOD-466).
	 *
	 * <p>Membership is an <b>item tag</b> ({@code #alaindustrial:shock_insulating}) rather than a Java
	 * type, the same choice the shielding suit made for radiation: a pack can add its own insulating
	 * gear with a datapack and no code, and this block needs no knowledge of what armour exists.
	 *
	 * <p>A broken piece cannot be worn at all, so no explicit durability check belongs here — vanilla
	 * removes a piece that hits zero, which is what makes "a broken piece stops protecting" true for
	 * free rather than by a rule someone has to remember.
	 *
	 * <p>Side-effect-free and public so both loader GameTest lanes can assert it directly, for the
	 * same reason {@link #shouldShockPlayer} and {@link #passesShockGuard} are: the landing path calls
	 * {@code hurtServer}, which notifies the player's {@code connection}, and a mock GameTest player
	 * has none.
	 */
	public static int wornInsulatingPieces(ServerPlayer player) {
		int worn = 0;
		for (EquipmentSlot slot : ARMOUR_SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty() && stack.is(ModTags.Items.SHOCK_INSULATING)) {
				worn++;
			}
		}
		return worn;
	}

	/**
	 * Shock damage left after the worn insulating set takes its share — {@code 0} when a full set stops
	 * the hit outright. Side-effect-free, and the predicate both GameTest lanes assert instead of
	 * health loss.
	 *
	 * <p>What survives here still goes through {@code hurtServer}, so ordinary armour points and
	 * Protection reduce it further. That is not double-dipping: {@code electric_shock} carries no
	 * {@code bypasses_armor} tag and never did, so plain armour has always blunted a shock, and this
	 * set is simply the first that can take all of it.
	 */
	public static float insulatedShockDamage(float raw, ServerPlayer player) {
		return ShockInsulation.remaining(raw, wornInsulatingPieces(player),
				Config.bareCableShockInsulationPerPiecePercent);
	}

	/**
	 * Spend one round of durability across the worn insulating pieces for the damage they stopped.
	 *
	 * <p>Goes through vanilla {@code hurtAndBreak} rather than a hand-rolled counter, which is what
	 * makes Unbreiking apply and a creative player pay nothing — a manual {@code setDamageValue} would
	 * silently do neither (MOD-319). The slot overload is the one to use: it is vanilla's own, unlike
	 * the {@code (int, ServerLevel, LivingEntity, Consumer)} shape, which exists only as a NeoForge
	 * patch and would not compile against Fabric.
	 */
	private static void wearInsulation(ServerPlayer player, float prevented) {
		int wear = ShockInsulation.wearFor(prevented,
				Config.bareCableShockInsulationDamagePerDurability);
		if (wear <= 0) {
			return;
		}
		for (EquipmentSlot slot : ARMOUR_SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty() && stack.is(ModTags.Items.SHOCK_INSULATING)) {
				stack.hurtAndBreak(wear, player, slot);
			}
		}
	}

	/** Side-effect-free eligibility check shared with both loader GameTest lanes. */
	public boolean shouldShockPlayer(Level level, BlockPos pos, ServerPlayer player) {
		return Config.bareCableShockEnabled
				&& !player.getAbilities().instabuild
				&& !player.isSpectator()
				&& !type.isInsulated()
				&& player.invulnerableTime <= 0
				&& level.getBlockEntity(pos) instanceof CableBlockEntity cable
				&& cable.isEnergizedForShock()
				&& shockReachesPlayer(cable, pos, player);
	}

	/**
	 * Whether the hazard can reach {@code player} at all given this segment's insulating stand
	 * (MOD-279). A bare segment reaches everyone, as it always has. A stood segment only reaches a
	 * player standing <b>on top of it</b>: the plate is under the wire, so it shields anyone walking
	 * past at floor level or standing underneath, and a player who climbs onto the cable is above the
	 * protection and still at risk (at the material's reduced chance — see {@link #passesShockGuard}).
	 *
	 * <p>Side-effect-free and part of {@link #shouldShockPlayer}, so both hazard paths honour it: the
	 * direct-contact {@code entityInside} and the MOD-269 proximity sweep. Putting it in the shared
	 * predicate rather than in the damage path is what makes "walking past a stood run is safe" true
	 * for the radius rule too, which is the whole point of the stand.
	 */
	private static boolean shockReachesPlayer(CableBlockEntity cable, BlockPos pos, ServerPlayer player) {
		if (!cable.shockGuard().isPresent()) {
			return true;
		}
		// Feet above the cable's mid-height means the player is standing on the wire rather than beside
		// it. A cable's collision tops out at 11px, and a player next to it on the same floor has their
		// feet exactly at the cell's own Y, so the halfway mark separates the two cases cleanly.
		return player.getBoundingBox().minY >= pos.getY() + 0.5;
	}

	/**
	 * Installs an insulating stand when the player right-clicks a bare segment with planks, wool or
	 * glass (MOD-279).
	 *
	 * <p><b>Building with those blocks still works.</b> Vanilla's {@code ServerPlayerGameMode.useItemOn}
	 * skips block interaction entirely when the player is sneaking with a non-empty hand (verified
	 * against 26.2 bytecode), so sneak+place puts the plank down as usual and this method never runs —
	 * the sneak-to-bypass convention players already know covers the collision for free, no code needed.
	 *
	 * <p>Anything this method does not claim returns {@code super}'s {@code TRY_WITH_EMPTY_HAND} rather
	 * than a consuming result, which is what preserves the MOD-039 fix: a consuming result here would
	 * stop vanilla before {@code BlockItem.place} and make cables unplaceable flush against each other.
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
			Player player, InteractionHand hand, BlockHitResult hit) {
		// Breaker first (MOD-276): its item is not a stand material, so the two claims cannot overlap.
		if (stack.is(ModContent.CABLE_BREAKER.get())
				&& level.getBlockEntity(pos) instanceof CableBlockEntity cable
				&& !cable.hasBreaker()) {
			if (level instanceof ServerLevel serverLevel) {
				cable.setBreakerInstalled(true);
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
				serverLevel.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 1.4f);
			}
			return InteractionResult.SUCCESS;
		}
		ShockGuardMaterial material = shockGuardMaterialFor(stack);
		if (material.isPresent() && canHoldShockGuard(state)
				&& level.getBlockEntity(pos) instanceof CableBlockEntity cable
				&& cable.shockGuardBlock() == null) {
			if (level instanceof ServerLevel serverLevel) {
				cable.setShockGuard(Block.byItem(stack.getItem()));
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
				serverLevel.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.9f, 1.1f);
			}
			return InteractionResult.SUCCESS;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	/**
	 * Removes an installed stand on an empty-handed right-click and returns the item (MOD-279).
	 *
	 * <p>The empty-hand test is explicit rather than implied: vanilla routes a
	 * {@code TRY_WITH_EMPTY_HAND} result here even when the player <em>is</em> holding something (any
	 * item this block does not claim above), so without the check a right-click with, say, a pickaxe
	 * would knock the stand off. Everything else defers to {@code super}, which returns {@code PASS}
	 * for a menu-less block — the MOD-039 contract.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hit) {
		// An installed breaker claims the empty hand (MOD-276): plain click throws the switch, sneak
		// pries it back off. It takes precedence over the stand's removal gesture because throwing the
		// switch is the frequent action and the stand is still reachable — pry the breaker off first.
		if (player.getMainHandItem().isEmpty()
				&& level.getBlockEntity(pos) instanceof CableBlockEntity breakerCable
				&& breakerCable.hasBreaker()) {
			if (level instanceof ServerLevel serverLevel) {
				if (player.isSecondaryUseActive()) {
					breakerCable.setBreakerInstalled(false);
					if (!player.getAbilities().instabuild) {
						Block.popResource(serverLevel, pos, new ItemStack(ModContent.CABLE_BREAKER.get()));
					}
					serverLevel.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.7f, 1.4f);
				} else {
					boolean closed = !breakerCable.isBreakerClosed();
					breakerCable.setBreakerClosed(closed);
					// Pitch carries the state for anyone not looking straight at the lever: up = live.
					serverLevel.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS,
							0.8f, closed ? 1.0f : 0.6f);
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (player.getMainHandItem().isEmpty()
				&& level.getBlockEntity(pos) instanceof CableBlockEntity cable
				&& cable.shockGuardBlock() != null) {
			if (level instanceof ServerLevel serverLevel) {
				// The very block the player installed, not a canonical stand-in for its category.
				ItemStack drop = new ItemStack(cable.shockGuardBlock());
				cable.setShockGuard(null);
				if (!player.getAbilities().instabuild) {
					Block.popResource(serverLevel, pos, drop);
				}
				serverLevel.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.9f, 0.9f);
			}
			return InteractionResult.SUCCESS;
		}
		return super.useWithoutItem(state, level, pos, player, hit);
	}

	/**
	 * Hand back whatever was clamped onto the segment when a player breaks it (MOD-276).
	 *
	 * <p>Both accessories are returned: the breaker, and the insulating stand that until now vanished
	 * silently on break — the stand had a drop path only for the DOWN-arm collision
	 * ({@link CableBlockEntity#dropShockGuardIfObstructed}), never for the wire simply being mined.
	 * The cable itself still comes from its loot table, so nothing here can duplicate it.
	 *
	 * <p>{@code playerWillDestroy} rather than a removal hook: it fires while the block entity is still
	 * present and only for a real player break, so a chunk unload or a piston does not scatter items.
	 */
	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel serverLevel && !player.getAbilities().instabuild
				&& serverLevel.getBlockEntity(pos) instanceof CableBlockEntity cable) {
			if (cable.hasBreaker()) {
				Block.popResource(serverLevel, pos, new ItemStack(ModContent.CABLE_BREAKER.get()));
			}
			if (cable.shockGuardBlock() != null) {
				Block.popResource(serverLevel, pos, new ItemStack(cable.shockGuardBlock()));
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/**
	 * Whether this segment can carry an insulating stand at all. An insulated grade is refused because
	 * it has no shock hazard left to reduce ({@link #shouldShockPlayer} already returns {@code false}
	 * there) — allowing it would sell the player a decoration that does nothing. A segment already
	 * connected downward is refused because the stand and the {@code DOWN} arm occupy the same volume
	 * at the floor of the cell; the reverse order (a neighbour appearing later) is handled by
	 * {@link CableBlockEntity#dropShockGuardIfObstructed}.
	 */
	private boolean canHoldShockGuard(BlockState state) {
		return !type.isInsulated() && !state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.DOWN));
	}

	/**
	 * Maps a held stack to the stand material it installs, or {@link ShockGuardMaterial#NONE} for
	 * anything that is not a valid stand.
	 *
	 * <p>Planks and wool have vanilla item tags; <b>glass does not</b> — there is no
	 * {@code ItemTags.GLASS} in 26.2, plain {@code Blocks.GLASS} is a bare {@code Block} with no
	 * dedicated subclass, and this project pulls in no conventional-tag library. Hence the explicit
	 * triple: the plain block by item identity, plus the two subclasses that cover every dyed and
	 * tinted variant.
	 */
	private static ShockGuardMaterial shockGuardMaterialFor(ItemStack stack) {
		if (stack.is(ItemTags.PLANKS) || stack.is(ItemTags.LOGS)) {
			return ShockGuardMaterial.WOOD;
		}
		if (stack.is(ItemTags.WOOL)) {
			return ShockGuardMaterial.WOOL;
		}
		Block block = Block.byItem(stack.getItem());
		if (stack.is(Items.GLASS) || block instanceof StainedGlassBlock || block instanceof TintedGlassBlock) {
			return ShockGuardMaterial.GLASS;
		}
		return ShockGuardMaterial.NONE;
	}

	/**
	 * The balance category of a block already installed as a stand. The block-side mirror of
	 * {@link #shockGuardMaterialFor(ItemStack)}, used by {@link CableBlockEntity#shockGuard()} to
	 * derive the category from the concrete block it stores instead of persisting both and risking
	 * them disagreeing.
	 */
	public static ShockGuardMaterial shockGuardMaterialOf(Block block) {
		return shockGuardMaterialFor(new ItemStack(block));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		PipeBlock.PROPERTY_BY_DIRECTION.values().forEach(builder::add);
		LOW_FLAGS.values().forEach(builder::add);
		builder.add(BREAKER_OPEN);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState();
		LevelReader level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		for (Direction dir : Direction.values()) {
			state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(dir), connectsTo(level, pos, dir));
			BooleanProperty lowFlag = LOW_FLAGS.get(dir);
			if (lowFlag != null) {
				state = state.setValue(lowFlag, isLowNeighbour(level, pos.relative(dir)));
			}
		}
		return state;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
			BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState,
			RandomSource random) {
		// An open breaker holds every arm retracted (MOD-276). This gate is load-bearing, not defensive:
		// writing BREAKER_OPEN with UPDATE_ALL makes the neighbours re-run this method on THIS block too,
		// and without the check the very update that opens the switch immediately re-derives the arms
		// from the (perfectly connectable) neighbours and re-joins the gap.
		state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction),
				!state.getValue(BREAKER_OPEN) && connectsTo(level, pos, direction));
		BooleanProperty lowFlag = LOW_FLAGS.get(direction);
		if (lowFlag != null) {
			// Re-read at neighborPos so the shape sees any neighbour-dependent (updateShape) state,
			// not the static default. level is a LevelReader, which extends BlockGetter.
			state = state.setValue(lowFlag, isLowNeighbour(level, neighborPos));
		}
		return state;
	}

	/**
	 * A neighbour block changed next to this cable (e.g. a machine was placed or broken adjacent to
	 * it without touching a cable). Mark the owning {@link dev.alaindustrial.core.energy.EnergyNetwork}
	 * dirty so it re-discovers its producer/consumer endpoints on the next tick — otherwise a newly
	 * placed machine is never picked up (an asleep network stays asleep; an awake one ignores it).
	 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			Orientation orientation, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
		if (level instanceof ServerLevel serverLevel) {
			NetworkManager.onNeighbourChanged(serverLevel, pos);
			// A neighbour under this segment may have just claimed the space an insulating stand sits in
			// (MOD-279). The block entity re-checks this every tick anyway; doing it here too makes the
			// stand pop the instant the connection forms rather than up to a tick later.
			if (serverLevel.getBlockEntity(pos) instanceof CableBlockEntity cable) {
				cable.dropShockGuardIfObstructed(serverLevel, pos);
			}
		}
	}

	/**
	 * A cable connects to any adjacent energy block of this mod — other cables, generators, machines,
	 * storage — but not to pure-container blocks that happen to inherit {@link AbstractMachineBlock}
	 * for non-energy reasons (e.g. {@link IronChestBlock}, which is a chest, not an energy receiver).
	 * The face-aware {@link AbstractMachineBlock#isCableConnectable(BlockState, Direction)} marker
	 * carries both that distinction and the per-face energy role, while keeping the check at the Block
	 * level (always available), so the connection is correct the instant the block is placed — no
	 * block-entity load race, no visual gaps (MOD-038).
	 *
	 * <p>{@code dir.getOpposite()} is the world face of the neighbour block the cable touches, so a
	 * block with a non-uniform energy role (e.g. a wind mill outputting only from its back) draws an
	 * arm only toward the face that actually carries an energy port.
	 */
	private static boolean connectsTo(LevelReader level, BlockPos pos, Direction dir) {
		BlockState neighborState = level.getBlockState(pos.relative(dir));
		Block block = neighborState.getBlock();
		if (block instanceof AbstractMachineBlock machine) {
			return machine.isCableConnectable(neighborState, dir.getOpposite());
		}
		// The reactor outlet is the one non-machine a cable may join. It cannot extend
		// AbstractMachineBlock — it has to be a ReactorShellBlock to carry the room's formed/edge
		// painting and to count as casing in the room scan — yet it publishes an energy port on every
		// face and is the reactor's only power tap. Without this clause the cable drew no arm and the
		// player read a working connection as a broken one.
		return block instanceof ReactorOutletBlock;
	}

	/**
	 * Public mirror of {@link #connectsTo} so {@link CableBlockEntity} can re-derive a cable's
	 * connection flags without duplicating the "is the neighbour a connectable machine" logic. Used
	 * by the once-per-load {@code validateShape} migration (MOD-061): flags saved in chunk palette
	 * NBT go stale when {@code isCableConnectable} semantics change (e.g. the FACING-inert default),
	 * and {@code updateShape} only runs on {@code neighborChanged}, not on chunk load — so the entity
	 * re-derives them on its first server tick. Same {@code dir} convention: direction from the cable
	 * toward the neighbour.
	 */
	public static boolean shouldConnectTo(LevelReader level, BlockPos pos, Direction dir) {
		return connectsTo(level, pos, dir);
	}

	/**
	 * Whether the block at {@code neighborPos} is a "low" (half-block) neighbour — collision shape
	 * top at or below {@link HalfBlockNeighbour#LOW_NEIGHBOUR_THRESHOLD} (e.g. a Solar Panel). Drives the
	 * per-direction {@code *_low} flag so the horizontal arm drops to {@link #ARMS_LOW}. This reads
	 * the neighbour's shape generically (no block-type special-casing), so any future half-block
	 * machine connects the same way. {@link LevelReader} extends {@link BlockGetter}, so it satisfies
	 * {@link BlockState#getShape(BlockGetter, BlockPos)}; empty shapes (air, fluids) are not low.
	 */
	private static boolean isLowNeighbour(LevelReader level, BlockPos neighborPos) {
		return HalfBlockNeighbour.isLow(level, neighborPos);
	}

	/**
	 * Public mirror of {@link #isLowNeighbour} for the {@link CableBlockEntity} once-per-load shape
	 * migration (MOD-061): the four horizontal {@code *_low} flags must be re-derived together with
	 * the connection flags so a migrated cable's geometry stays self-consistent with
	 * {@code updateShape}/{@code getStateForPlacement}. For full-cube machine neighbours this is
	 * provably {@code false}; re-deriving it is free (we already walk the neighbours) and future-proofs
	 * the half-block case (MOD-042 sibling).
	 */
	public static boolean isLowNeighbourAt(LevelReader level, BlockPos neighborPos) {
		return isLowNeighbour(level, neighborPos);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		VoxelShape shape = CORE;
		for (Map.Entry<Direction, BooleanProperty> entry : PipeBlock.PROPERTY_BY_DIRECTION.entrySet()) {
			Direction dir = entry.getKey();
			if (state.getValue(entry.getValue())) {
				BooleanProperty lowFlag = LOW_FLAGS.get(dir);
				VoxelShape arm = lowFlag != null && state.getValue(lowFlag) ? ARMS_LOW.get(dir) : ARMS.get(dir);
				shape = Shapes.or(shape, arm);
			}
		}
		return shape;
	}

	/**
	 * Restricts {@link #entityInside} to a tiny shell around the thin cable geometry. Returning
	 * the solid shape itself would make contact impossible because entity collision prevents overlap;
	 * returning Minecraft's default full cube would shock players standing beside the cable.
	 */
	@Override
	protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			Entity entity) {
		VoxelShape contact = Shapes.empty();
		for (var box : getShape(state, level, pos, CollisionContext.of(entity)).toAabbs()) {
			contact = Shapes.or(contact, Shapes.box(
					Math.max(0.0, box.minX - SHOCK_CONTACT_MARGIN),
					Math.max(0.0, box.minY - SHOCK_CONTACT_MARGIN),
					Math.max(0.0, box.minZ - SHOCK_CONTACT_MARGIN),
					Math.min(1.0, box.maxX + SHOCK_CONTACT_MARGIN),
					Math.min(1.0, box.maxY + SHOCK_CONTACT_MARGIN),
					Math.min(1.0, box.maxZ + SHOCK_CONTACT_MARGIN)));
		}
		return contact;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CableBlockEntity(pos, state);
	}

	/**
	 * Eagerly registers the new cable with {@link NetworkManager} via {@link CableBlockEntity#ensureRegistered()}
	 * (idempotent, and keeps the entity's own {@code registered} flag in sync so it can never leak an
	 * unregistered-on-removal entry — see the MOD-015/016 code review) so the resulting network's
	 * awake state is known synchronously, and fires {@link ModCriteria#NETWORK_ENERGIZED} if placing
	 * this cable just connected a producer to a consumer (MOD-015 "Closed circuit").
	 */
	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel
				&& serverLevel.getBlockEntity(pos) instanceof CableBlockEntity cable) {
			cable.ensureRegistered();
			if (placer instanceof ServerPlayer player) {
				ModCriteria.tryFireNetworkEnergized(serverLevel, pos, player);
			}
		}
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return machineTicker(level);
	}
}
