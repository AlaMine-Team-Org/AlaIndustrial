package dev.alaindustrial.registry.neoforge;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.attachment.PlayerAttachmentAccessor;
import dev.alaindustrial.registry.ModPlayerAttachments;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge registration of the per-player attachments: a replay of the shared
 * {@link ModPlayerAttachments#PLAYER_ATTACHMENTS} list (MOD-708) through a {@link DeferredRegister} — the
 * attachment registry freezes before mod init — plus the one {@link PlayerAttachmentAccessor} that binds
 * every entry into its server-side store.
 *
 * <p>NeoForge's save form is the entry's {@code MapCodec} ({@code serialize(mapCodec)}) — the form every
 * NeoForge player file holds; Fabric keeps its {@code Codec} form. {@code copyOnDeath} makes NeoForge copy
 * the value on the death clone, and the sync predicate sends it to a player only when that player
 * <em>is</em> the holder.
 */
public final class ModAttachmentsNeoForge {
	public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
			DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Industrialization.MOD_ID);

	/** Every entry, queued on {@link #ATTACHMENTS} the moment this class loads, by entry id. */
	private static final Map<String, DeferredHolder<AttachmentType<?>, ?>> REGISTERED = registerAll();

	private ModAttachmentsNeoForge() {
	}

	private static Map<String, DeferredHolder<AttachmentType<?>, ?>> registerAll() {
		Map<String, DeferredHolder<AttachmentType<?>, ?>> registered = new LinkedHashMap<>();
		for (ModPlayerAttachments.PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			if (registered.put(def.id(), ATTACHMENTS.register(def.id(), () -> build(def))) != null) {
				throw new IllegalStateException("PLAYER_ATTACHMENTS declares attachment id '" + def.id() + "' twice");
			}
		}
		return Map.copyOf(registered);
	}

	private static <T> AttachmentType<T> build(ModPlayerAttachments.PlayerAttachmentDef<T> def) {
		AttachmentType.Builder<T> builder = AttachmentType.builder(def::empty).serialize(def.mapCodec());
		if (def.copyOnDeath()) {
			builder = builder.copyOnDeath();
		}
		if (def.syncToOwner()) {
			builder = builder.sync((holder, player) -> holder == player, def.streamCodec());
		}
		return builder.build();
	}

	/** The queued holder of an entry. The cast cannot lie: the entry built the type under its id. */
	@SuppressWarnings("unchecked")
	public static <T> DeferredHolder<AttachmentType<?>, AttachmentType<T>> holder(
			ModPlayerAttachments.PlayerAttachmentDef<T> def) {
		DeferredHolder<AttachmentType<?>, ?> holder = REGISTERED.get(def.id());
		if (holder == null) {
			throw new IllegalStateException("no NeoForge attachment for '" + def.id() + "'");
		}
		return (DeferredHolder<AttachmentType<?>, AttachmentType<T>>) holder;
	}

	/** Binds every entry's server-side store to this loader's accessor. Called from the {@code @Mod} ctor. */
	public static void init() {
		for (ModPlayerAttachments.PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			bindStore(def);
		}
	}

	private static <T> void bindStore(ModPlayerAttachments.PlayerAttachmentDef<T> def) {
		DeferredHolder<AttachmentType<?>, AttachmentType<T>> type = holder(def);
		def.bindStore().accept(new PlayerAttachmentAccessor<>() {
			@Override
			public T get(ServerPlayer player) {
				// Read without creating (MOD-483). NeoForge's getData INSTALLS the default value when none
				// exists and syncs it — so a plain read writes state and puts a packet on the wire, and on
				// a player with no connection (a vanilla gametest mock) it throws outright. Same rule as
				// ADR-010 for containers, one layer up.
				T stored = player.getExistingDataOrNull(type);
				return stored != null ? stored : def.empty();
			}

			@Override
			public void set(ServerPlayer player, T value) {
				player.setData(type, value);
			}
		});
	}
}
