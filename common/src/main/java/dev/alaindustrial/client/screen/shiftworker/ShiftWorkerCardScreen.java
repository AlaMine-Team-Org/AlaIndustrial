package dev.alaindustrial.client.screen.shiftworker;

import static dev.alaindustrial.client.screen.shiftworker.ShiftWorkerCardLayout.*;

import dev.alaindustrial.client.screen.GuiStyle;
import dev.alaindustrial.entity.ShiftWorker;
import dev.alaindustrial.network.ShiftWorkerCardPayload;
import dev.alaindustrial.registry.ModMobs;
import dev.alaindustrial.settlement.record.RecordNumber;
import dev.alaindustrial.settlement.shift.ShiftWorkerProfile;
import dev.alaindustrial.settlement.shift.ShiftWorkerRecord;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * A shift worker's card (MOD-854): a plain screen, opened by a card the server sends opening — a right click on the
 * worker or a row of the town core's roster ({@link ShiftWorkerCardClient}). Three tabs: «Card» (the worker, its
 * battery, health, rank and level, stats with «+»), «Personal file» (biography and service marks) and «Number» (when
 * it was issued and the numbers it held before).
 *
 * <p><b>Read-only unless the server says otherwise.</b> The «+», rename and respec controls appear only when the card
 * says the viewer owns the worker's settlement, it is alive and the action can succeed now; the server checks every
 * action again.
 * A dead record is grey and still under a stamp — «record closed», or «awaiting return» when permadeath is off.
 *
 * <p><b>Live while open.</b> The card of a living worker is asked for again about once a second; a card that arrives
 * for the same number — the reply to that, or to an action — updates this screen in place, keeping the tab and a
 * name being typed. The model is the loaded worker itself when the client has it, otherwise a body dressed in the
 * card's look.
 */
public final class ShiftWorkerCardScreen extends Screen {

	/** Longest name the field takes; the server cuts at its own, longer limit. */
	public static final int NAME_LIMIT = 16;
	/** Ticks between two refresh requests of a living worker's card. */
	private static final int REFRESH_TICKS = 20;

	/** The three tabs, in header order. */
	public enum Tab {
		CARD("card"), FILE("file"), NUMBER("number");

		private final String key;

		Tab(String key) {
			this.key = key;
		}

		Component title() {
			return Component.translatable(ShiftWorkerCardText.UI + "tab." + key);
		}
	}

	private ShiftWorkerCardPayload card;
	private Tab tab = Tab.CARD;
	private boolean editing;
	private String draft = "";
	private @Nullable EditBox nameBox;
	/** What the controls were built for; a card that changes it rebuilds them. */
	private String controls = "";
	private int ticks;
	private int panelX;
	private int panelY;
	private int panelH;
	/** Which free house «Relocate» moves the worker into, as an index into the card's list (MOD-862). */
	private int relocateChoice;
	private @Nullable ShiftWorker dressedBody;
	private String dressedFor = "";

	public ShiftWorkerCardScreen(ShiftWorkerCardPayload card) {
		super(ShiftWorkerCardText.title(card));
		this.card = card;
	}

	/**
	 * Shows {@code card}: updates the open card of the same number in place, or opens a new screen. The default hook
	 * of {@link ShiftWorkerCardClient#onOpenCard}.
	 */
	public static void show(ShiftWorkerCardPayload card) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof ShiftWorkerCardScreen open && open.card.number().equals(card.number())) {
			open.update(card);
			return;
		}
		minecraft.gui.setScreen(new ShiftWorkerCardScreen(card));
	}

	/** The card shown now. */
	public ShiftWorkerCardPayload card() {
		return card;
	}

	/** The open tab. */
	public Tab tab() {
		return tab;
	}

	/** Opens {@code next}; the tab buttons and the L3 stand both go through here. */
	public void selectTab(Tab next) {
		if (next != tab) {
			tab = next;
			refreshControls();
		}
	}

	private void update(ShiftWorkerCardPayload next) {
		card = next;
		if (!canEdit()) {
			editing = false;
		}
		refreshControls();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		ticks++;
		ShiftWorkerCardPayload latest = ShiftWorkerCardClient.latestCard();
		if (latest != null && latest != card && latest.number().equals(card.number())) {
			update(latest);
		}
		if (card.alive() && ticks % REFRESH_TICKS == 0) {
			ShiftWorkerCardClient.requestCard(card.number());
		}
	}

	// ── controls ────────────────────────────────────────────────────────────────────────────────

	private boolean canEdit() {
		return card.viewerIsOwner() && card.alive();
	}

	private boolean canInvest(ShiftWorkerProfile.Stat stat) {
		return canEdit() && card.freePoints() > 0 && card.count(stat) < card.statCap() && card.active(stat);
	}

	/** The state the controls depend on; when it changes they are built again. */
	private String controlsKey() {
		StringBuilder key = new StringBuilder().append(tab).append(editing).append(canEdit()).append(card.canRespec())
				.append(card.freeHouses()).append(relocateChoice).append(card.work());
		for (ShiftWorkerProfile.Stat stat : ShiftWorkerProfile.Stat.values()) {
			key.append(canInvest(stat) ? '+' : '-');
		}
		return key.toString();
	}

	private void refreshControls() {
		if (!controlsKey().equals(controls) && this.minecraft != null && this.font != null) {
			rememberDraft();
			rebuildWidgets();
		}
	}

	private void rememberDraft() {
		if (nameBox != null) {
			draft = nameBox.getValue();
		}
	}

	@Override
	protected void init() {
		int panelW = Math.min(this.width - 24, PANEL_W);
		panelH = Math.min(this.height - 24, PANEL_H);
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;
		nameBox = null;
		int right = panelX + panelW - 6;
		Component closeLabel = Component.translatable("gui.alaindustrial.dashboard.close");
		right = headerButton(closeLabel, right, this::onClose, true);
		Tab[] tabs = Tab.values();
		for (int i = tabs.length - 1; i >= 0; i--) {
			Tab each = tabs[i];
			right = headerButton(each.title(), right, () -> selectTab(each), each != tab);
		}
		initNameControls();
		if (tab == Tab.CARD) {
			initCardControls();
		}
		controls = controlsKey();
	}

	/** One header button right-aligned at {@code right}; returns the right edge for the next one. */
	private int headerButton(Component label, int right, Runnable action, boolean active) {
		int w = this.font.width(label) + 12;
		Button button = addRenderableWidget(Button.builder(label, b -> action.run())
				.bounds(right - w, panelY + HEADER_BUTTON_Y, w, HEADER_BUTTON_H).build());
		button.active = active;
		return right - w - 2;
	}

	private void initNameControls() {
		if (!canEdit()) {
			return;
		}
		int x = panelX + LEFT_X;
		int y = panelY + NAME_Y;
		if (!editing) {
			addRenderableWidget(Button.builder(Component.translatable(ShiftWorkerCardText.UI + "rename.button"),
					b -> startEditing()).bounds(x + LEFT_W - NAME_H, y, NAME_H, NAME_H)
					.tooltip(Tooltip.create(Component.translatable(ShiftWorkerCardText.UI + "rename.tooltip")))
					.build());
			return;
		}
		EditBox box = new EditBox(this.font, x, y, LEFT_W - NAME_H - 2, NAME_H,
				Component.translatable(ShiftWorkerCardText.UI + "rename.tooltip"));
		box.setMaxLength(NAME_LIMIT);
		box.setValue(draft);
		nameBox = addRenderableWidget(box);
		addRenderableWidget(Button.builder(Component.translatable(ShiftWorkerCardText.UI + "confirm.button"),
				b -> confirmName()).bounds(x + LEFT_W - NAME_H, y, NAME_H, NAME_H)
				.tooltip(Tooltip.create(Component.translatable(ShiftWorkerCardText.UI + "confirm.tooltip")))
				.build());
		setInitialFocus(box);
	}

	private void initCardControls() {
		boolean alive = card.alive();
		int x = panelX + RIGHT_X;
		int rightEdge = x + rightWidth() - 6;
		for (ShiftWorkerProfile.Stat stat : ShiftWorkerProfile.Stat.values()) {
			if (canInvest(stat)) {
				addRenderableWidget(Button.builder(Component.literal("+"),
						b -> ShiftWorkerCardClient.invest(card.number(), stat))
						.bounds(rightEdge - SMALL_BUTTON, panelY + statRowY(alive, stat.ordinal()), SMALL_BUTTON,
								SMALL_BUTTON)
						.tooltip(Tooltip.create(Component.translatable(ShiftWorkerCardText.UI + "invest.tooltip")))
						.build());
			}
		}
		initRelocateControls(x, rightEdge);
		initWorkControl(rightEdge);
		if (card.canRespec()) {
			Component label = Component.translatable(ShiftWorkerCardText.UI + "respec",
					ShiftWorkerCardText.amount(card.respecCost()));
			// Exactly as wide as the label in the active language, never narrower. It shares the «free points»
			// row when both fit; otherwise it moves to the title row of the module, whose right side is free.
			int w = this.font.width(label) + 10;
			int freeW = this.font.width(
					Component.translatable(ShiftWorkerCardText.UI + "free_points", card.freePoints()));
			boolean sameRow = freeW + 4 + w <= rightEdge - (x + 8);
			int by = panelY + rankTop(alive) + (sameRow ? RANK_FREE - 3 : 3);
			addRenderableWidget(Button.builder(label, b -> ShiftWorkerCardClient.respec(card.number()))
					.bounds(rightEdge - w, by, w, SMALL_BUTTON)
					.tooltip(Tooltip.create(Component.translatable(ShiftWorkerCardText.UI + "respec.tooltip")))
					.build());
		}
	}

	/** «Off work» or «Back to work» on the work row (MOD-888), for a worker that works or was taken off work. */
	private void initWorkControl(int rightEdge) {
		if (!canEdit() || card.work() == ShiftWorkerCardPayload.WORK_NONE) {
			return;
		}
		boolean off = card.work() == ShiftWorkerCardPayload.WORK_OFF;
		String key = ShiftWorkerCardText.UI + (off ? "back_to_work" : "off_work");
		int y = panelY + workRowY() - 2;
		addRenderableWidget(Button.builder(Component.translatable(key),
				b -> ShiftWorkerCardClient.toggleWork(card.number()))
				.bounds(rightEdge - WORK_BUTTON_W, y, WORK_BUTTON_W, SMALL_BUTTON)
				.tooltip(Tooltip.create(Component.translatable(key + ".tooltip")))
				.build());
	}

	/** «Relocate» and the arrow that picks the house, on the home row (MOD-862, R102). */
	private void initRelocateControls(int x, int rightEdge) {
		if (!canEdit() || card.freeHouses().isEmpty()) {
			return;
		}
		ShiftWorkerCardPayload.House target = card.freeHouses().get(Math.floorMod(relocateChoice,
				card.freeHouses().size()));
		int y = panelY + homeRowY() - 2;
		addRenderableWidget(Button.builder(Component.translatable(ShiftWorkerCardText.UI + "relocate"),
				b -> ShiftWorkerCardClient.relocate(card.number(), target.index()))
				.bounds(rightEdge - SMALL_BUTTON - 2 - RELOCATE_W, y, RELOCATE_W, SMALL_BUTTON)
				.tooltip(Tooltip.create(Component.translatable(ShiftWorkerCardText.UI + "relocate.tooltip",
						ShiftWorkerCardText.house(target))))
				.build());
		Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
			relocateChoice++;
			refreshControls();
		}).bounds(rightEdge - SMALL_BUTTON, y, SMALL_BUTTON, SMALL_BUTTON)
				.tooltip(Tooltip.create(Component.translatable(ShiftWorkerCardText.UI + "relocate.next")))
				.build());
		next.active = card.freeHouses().size() > 1;
	}

	private void startEditing() {
		editing = true;
		draft = card.name();
		refreshControls();
	}

	private void confirmName() {
		if (nameBox != null) {
			ShiftWorkerCardClient.rename(card.number(), nameBox.getValue().trim());
		}
		editing = false;
		refreshControls();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (editing && nameBox != null && nameBox.isFocused()) {
			if (event.isConfirmation()) {
				confirmName();
				return true;
			}
			if (event.isEscape()) {
				editing = false;
				refreshControls();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	// ── drawing ─────────────────────────────────────────────────────────────────────────────────

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(g, mouseX, mouseY, partialTick);
		int panelW = Math.min(this.width - 24, PANEL_W);
		GuiStyle.panel(g, panelX, panelY, panelW, panelH);
		g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + HEADER, GuiStyle.HEADER_BAR);
		g.fill(panelX + 1, panelY + HEADER, panelX + panelW - 1, panelY + HEADER + 1, GuiStyle.PANEL_LO);
		ShiftWorkerCardPages pages = new ShiftWorkerCardPages(this.font, g, card, panelX, panelY);
		int titleRoom = firstHeaderButtonX() - panelX - 14;
		pages.line(ShiftWorkerCardText.title(card), panelX + 8, panelY + 7, Math.max(20, titleRoom),
				GuiStyle.HEADER_TEXT);
		pages.drawLeftColumn(editing, canEdit() ? LEFT_W - NAME_H - 4 : LEFT_W);
		int bottom = panelY + panelH - 12;
		switch (tab) {
			case CARD -> pages.drawCardTab();
			case FILE -> pages.drawFileTab(bottom);
			case NUMBER -> pages.drawNumberTab(bottom);
		}
		drawModel(g, pages, mouseX, mouseY);
	}

	private int firstHeaderButtonX() {
		int lowest = panelX + Math.min(this.width - 24, PANEL_W);
		for (var child : children()) {
			if (child instanceof Button button && button.getY() == panelY + HEADER_BUTTON_Y) {
				lowest = Math.min(lowest, button.getX());
			}
		}
		return lowest;
	}

	/** The worker in its frame: alive it follows the mouse, dead it stands still, greyed, under its stamp. */
	private void drawModel(GuiGraphicsExtractor g, ShiftWorkerCardPages pages, int mouseX, int mouseY) {
		int x0 = panelX + LEFT_X;
		int y0 = panelY + MODEL_TOP;
		int x1 = x0 + LEFT_W;
		int y1 = panelY + MODEL_BOTTOM;
		g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, GuiStyle.SLOT_EDGE);
		int rows = Math.max(1, y1 - y0);
		for (int i = 0; i < rows; i++) {
			g.fill(x0, y0 + i, x1, y0 + i + 1, lerp(GuiStyle.MODEL_BACK_TOP, GuiStyle.MODEL_BACK_BOTTOM,
					(float) i / rows));
		}
		LivingEntity body = body();
		boolean alive = card.alive();
		if (body != null) {
			int lookX = alive ? mouseX : (x0 + x1) / 2;
			int lookY = alive ? mouseY : y0 + (y1 - y0) / 3;
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, x0 + 2, y0 + 4, x1 - 2, y1 - 4,
					(y1 - y0) / 3, 0.0625F, lookX, lookY, body);
		}
		g.nextStratum();
		if (!alive) {
			g.fill(x0, y0, x1, y1, GuiStyle.CLOSED_WASH);
		}
		Optional<Component> stamp = pages.stamp();
		stamp.ifPresent(text -> pages.drawStamp(text, (x0 + x1) / 2, (y0 + y1) / 2, LEFT_W));
	}

	/** The loaded worker when this client has it and it is alive; otherwise a body dressed in the card's look. */
	private @Nullable LivingEntity body() {
		Minecraft minecraft = this.minecraft;
		if (minecraft == null || minecraft.level == null) {
			return null;
		}
		if (card.alive() && card.entityId() >= 0
				&& minecraft.level.getEntity(card.entityId()) instanceof LivingEntity loaded) {
			return loaded;
		}
		String key = card.number() + '|' + card.look() + '|' + card.slim();
		if (dressedBody == null || !key.equals(dressedFor)) {
			// A body made only to be looked at, with an id of its own: the renderer reads it (26.3 throws otherwise).
			ShiftWorker worker = ShiftWorker.displayBody(ModMobs.SHIFT_WORKER.type(), minecraft.level);
			if (worker != null) {
				RecordNumber.parse(card.number()).ifPresent(number -> worker.showRecord(ShiftWorkerRecord.issued(
						number, UUID.randomUUID(), card.look(), card.slim(), card.settlement())));
			}
			dressedBody = worker;
			dressedFor = key;
		}
		return dressedBody;
	}

	private static int lerp(int a, int b, float t) {
		int out = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int ca = (a >>> shift) & 0xFF;
			int cb = (b >>> shift) & 0xFF;
			out |= ((int) (ca + (cb - ca) * t) & 0xFF) << shift;
		}
		return out;
	}
}
