package com.senin.chunkmarker;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Opened with Right Shift. Per marker type: on/off switch and the amount needed before a chunk is marked. */
public class SettingsScreen extends Screen {
	private static final int ROW_HEIGHT = 28;
	private static final int TOP = 56;

	private final Screen parent;

	public SettingsScreen(Screen parent) {
		super(Component.literal("Chunk Marker"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config config = Config.get();
		int left = this.width / 2 - 150;
		MarkerType[] types = MarkerType.values();

		for (int i = 0; i < types.length; i++) {
			final MarkerType type = types[i];
			int y = TOP + i * ROW_HEIGHT;

			this.addRenderableWidget(Button.builder(toggleText(type), button -> {
				config.setEnabled(type, !config.isEnabled(type));
				button.setMessage(toggleText(type));
				config.save();
			}).pos(left, y).size(150, 20).build());

			this.addRenderableWidget(Button.builder(Component.literal("-10"), button -> change(type, -10))
					.pos(left + 160, y).size(28, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal("-1"), button -> change(type, -1))
					.pos(left + 190, y).size(24, 20).build());
			// The current number is drawn between these two in render().
			this.addRenderableWidget(Button.builder(Component.literal("+1"), button -> change(type, 1))
					.pos(left + 256, y).size(24, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal("+10"), button -> change(type, 10))
					.pos(left + 282, y).size(28, 20).build());
		}

		this.addRenderableWidget(Button.builder(Component.literal("Kapat"), button -> this.onClose())
				.pos(this.width / 2 - 60, TOP + types.length * ROW_HEIGHT + 16).size(120, 20).build());
	}

	private static Component toggleText(MarkerType type) {
		return Component.literal(type.label + ": " + (Config.get().isEnabled(type) ? "AÇIK" : "KAPALI"));
	}

	private static void change(MarkerType type, int delta) {
		Config config = Config.get();
		config.changeLimit(type, delta);
		config.save();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);

		graphics.drawCenteredString(this.font, "Chunk Marker", this.width / 2, 16, 0xFFFFFFFF);
		graphics.drawCenteredString(this.font, "Bir chunk, en az bu kadar bulunca işaretlenir:", this.width / 2, 32, 0xFFAAAAAA);

		Config config = Config.get();
		int left = this.width / 2 - 150;
		MarkerType[] types = MarkerType.values();
		for (int i = 0; i < types.length; i++) {
			int y = TOP + i * ROW_HEIGHT + 6;
			graphics.drawCenteredString(this.font, String.valueOf(config.getLimit(types[i])), left + 235, y, 0xFFFFFFFF);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}

	@Override
	public void removed() {
		Config.get().save();
		ChunkMarkerClient.reevaluateAll();
	}
}
