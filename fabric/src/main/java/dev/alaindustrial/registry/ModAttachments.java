package dev.alaindustrial.registry;

import dev.alaindustrial.Industrialization;
import dev.alaindustrial.attachment.PlayerAttachmentAccessor;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric registration of the per-player attachments: a replay of the shared
 * {@link ModPlayerAttachments#PLAYER_ATTACHMENTS} list (MOD-708) through fabric-api's
 * {@link AttachmentRegistry}, plus the one {@link PlayerAttachmentAccessor} that binds every entry into its
 * server-side store.
 *
 * <p>Fabric's save form is the entry's {@code Codec} ({@code persistent(codec)}) — the form every Fabric
 * player file holds; NeoForge keeps its {@code MapCodec} form. {@code copyOnDeath} is an explicit opt-in on
 * Fabric, and the owner-only sync is {@link AttachmentSyncPredicate#targetOnly()}.
 */
public final class ModAttachments {
	private ModAttachments() {
	}

	/** Every attachment, created the moment this class loads, by entry id. */
	private static final Map<String, AttachmentType<?>> REGISTERED = registerAll();

	private static Map<String, AttachmentType<?>> registerAll() {
		Map<String, AttachmentType<?>> registered = new LinkedHashMap<>();
		for (ModPlayerAttachments.PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			if (registered.put(def.id(), create(def)) != null) {
				throw new IllegalStateException("PLAYER_ATTACHMENTS declares attachment id '" + def.id() + "' twice");
			}
		}
		return Map.copyOf(registered);
	}

	private static <T> AttachmentType<T> create(ModPlayerAttachments.PlayerAttachmentDef<T> def) {
		return AttachmentRegistry.create(Industrialization.id(def.id()), builder -> {
			builder.initializer(def::empty).persistent(def.codec());
			if (def.copyOnDeath()) {
				builder.copyOnDeath();
			}
			if (def.syncToOwner()) {
				builder.syncWith(def.streamCodec(), AttachmentSyncPredicate.targetOnly());
			}
		});
	}

	/** The registered attachment of an entry. The cast cannot lie: the entry created the type under its id. */
	@SuppressWarnings("unchecked")
	public static <T> AttachmentType<T> type(ModPlayerAttachments.PlayerAttachmentDef<T> def) {
		AttachmentType<?> type = REGISTERED.get(def.id());
		if (type == null) {
			throw new IllegalStateException("no Fabric attachment for '" + def.id() + "'");
		}
		return (AttachmentType<T>) type;
	}

	/** Binds every entry's server-side store to this loader's accessor. Called once from Fabric init. */
	public static void init() {
		for (ModPlayerAttachments.PlayerAttachmentDef<?> def : ModPlayerAttachments.PLAYER_ATTACHMENTS) {
			bindStore(def);
		}
	}

	private static <T> void bindStore(ModPlayerAttachments.PlayerAttachmentDef<T> def) {
		AttachmentType<T> type = type(def);
		def.bindStore().accept(new PlayerAttachmentAccessor<>() {
			@Override
			public T get(ServerPlayer player) {
				// getAttachedOrCreate installs the default on a read; getAttachedOrElse does not (MOD-483).
				return player.getAttachedOrElse(type, def.empty());
			}

			@Override
			public void set(ServerPlayer player, T value) {
				player.setAttached(type, value);
			}
		});
	}
}
