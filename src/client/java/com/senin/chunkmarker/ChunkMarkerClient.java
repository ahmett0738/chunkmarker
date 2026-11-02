package com.senin.chunkmarker;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

/**
 * Client-only mod: scans every chunk the client receives (so it also works on servers, no extra packets),
 * counts the blocks we care about and draws a big flat icon over chunks that qualify.
 */
public class ChunkMarkerClient implements ClientModInitializer {
	/** Scan results of one chunk. Counts are independent of the settings; the settings are applied when drawing/reporting. */
	public static final class ChunkInfo {
		public final int minX;
		public final int minZ;
		public final int[] counts;

		ChunkInfo(int minX, int minZ, int[] counts) {
			this.minX = minX;
			this.minZ = minZ;
			this.counts = counts;
		}
	}

	private static final class ScanJob {
		final ClientLevel level;
		final LevelChunk chunk;
		final int[] counts = new int[MarkerType.values().length];
		int section = 0;
		boolean done = false;

		ScanJob(ClientLevel level, LevelChunk chunk) {
			this.level = level;
			this.chunk = chunk;
		}
	}

	/** Scan results by chunk (ChunkPos.toLong()). */
	public static final Map<Long, ChunkInfo> DATA = new HashMap<>();

	private static final Map<Long, LevelChunk> LOADED = new HashMap<>();
	private static final ArrayDeque<ScanJob> QUEUE = new ArrayDeque<>();
	/** Bit mask of marker types already reported in chat per chunk, so the chat is never spammed with repeats. */
	private static final Map<Long, Integer> REPORTED = new HashMap<>();
	private static ClientLevel trackedLevel;

	private static final long SCAN_BUDGET_NANOS = 3_000_000L; // max ~3 ms of scanning per tick, so the game never freezes

	private static KeyMapping menuKey;

	@Override
	public void onInitializeClient() {
		Config.get();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("chunkmarker", "main"));
		menuKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.chunkmarker.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));

		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			ensureLevel(level);
			long key = chunk.getPos().toLong();
			LOADED.put(key, chunk);
			QUEUE.add(new ScanJob(level, chunk));
		});

		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			long key = chunk.getPos().toLong();
			LOADED.remove(key);
			DATA.remove(key);
			REPORTED.remove(key);
		});

		ClientTickEvents.END_CLIENT_TICK.register(ChunkMarkerClient::onTick);

		MarkerRenderer.register();
	}

	private static void ensureLevel(ClientLevel level) {
		if (trackedLevel != level) {
			clearAll();
			trackedLevel = level;
		}
	}

	private static void clearAll() {
		DATA.clear();
		LOADED.clear();
		QUEUE.clear();
		REPORTED.clear();
	}

	private static void onTick(Minecraft mc) {
		if (mc.level != trackedLevel) {
			clearAll();
			trackedLevel = mc.level;
		}

		while (menuKey.consumeClick()) {
			if (mc.screen == null) {
				mc.setScreen(new SettingsScreen(null));
			}
		}

		long end = System.nanoTime() + SCAN_BUDGET_NANOS;
		while (!QUEUE.isEmpty() && System.nanoTime() < end) {
			ScanJob job = QUEUE.peek();
			long key = job.chunk.getPos().toLong();
			if (job.level != trackedLevel || LOADED.get(key) != job.chunk) {
				QUEUE.poll(); // chunk was unloaded (or replaced) before we got to it
				continue;
			}
			scanNextSection(job);
			if (job.done) {
				QUEUE.poll();
				ChunkPos pos = job.chunk.getPos();
				ChunkInfo info = new ChunkInfo(pos.getMinBlockX(), pos.getMinBlockZ(), job.counts);
				DATA.put(key, info);
				report(key, info);
			}
		}
	}

	private static void scanNextSection(ScanJob job) {
		LevelChunkSection[] sections = job.chunk.getSections();
		if (job.section >= sections.length) {
			job.done = true;
			return;
		}

		int sectionIndex = job.section++;
		if (job.section >= sections.length) {
			job.done = true; // this is the last section; it is scanned below, then the job is finished
		}

		LevelChunkSection section = sections[sectionIndex];
		if (section == null || section.hasOnlyAir()) {
			return;
		}

		ChunkPos chunkPos = job.chunk.getPos();
		int minX = chunkPos.getMinBlockX();
		int minZ = chunkPos.getMinBlockZ();
		int baseY = job.level.getMinY() + sectionIndex * 16;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int[] counts = job.counts;

		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					BlockState state = section.getBlockState(x, y, z);
					if (state.isAir()) {
						continue;
					}

					Block block = state.getBlock();

					if (block == Blocks.VINE) {
						// A vine counts as fully grown when it hangs down onto a block (the block under it is not air).
						// Only the bottom piece of a hanging column is counted, so one column = 1.
						pos.set(minX + x, baseY + y - 1, minZ + z);
						BlockState below = job.chunk.getBlockState(pos);
						if (!below.isAir() && !below.is(Blocks.VINE)) {
							counts[MarkerType.VINE.ordinal()]++;
						}
					} else if (block == Blocks.BEEHIVE || block == Blocks.BEE_NEST) {
						if (state.getValue(BeehiveBlock.HONEY_LEVEL) >= 5) {
							counts[MarkerType.HIVE.ordinal()]++;
						}
					} else if (block == Blocks.KELP) {
						// Kelp counts when it reached the water surface: nothing but air above the top piece.
						pos.set(minX + x, baseY + y + 1, minZ + z);
						if (job.chunk.getBlockState(pos).isAir()) {
							counts[MarkerType.KELP.ordinal()]++;
						}
					} else if (block == Blocks.AMETHYST_CLUSTER) {
						counts[MarkerType.AMETHYST.ordinal()]++;
					}
				}
			}
		}
	}

	/** The one marker type to draw for this chunk (first match in priority order), or null for none. */
	public static MarkerType shownType(ChunkInfo info) {
		Config config = Config.get();
		for (MarkerType type : MarkerType.values()) {
			if (config.isEnabled(type) && info.counts[type.ordinal()] >= config.getLimit(type)) {
				return type;
			}
		}
		return null;
	}

	/** Called when the settings screen closes: applies new settings to chunks that are already scanned. */
	public static void reevaluateAll() {
		for (Map.Entry<Long, ChunkInfo> entry : DATA.entrySet()) {
			report(entry.getKey(), entry.getValue());
		}
	}

	/** Writes one chat message per chunk for everything that qualifies, but never the same type twice for the same chunk. */
	private static void report(long key, ChunkInfo info) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}

		Config config = Config.get();
		int mask = 0;
		for (MarkerType type : MarkerType.values()) {
			if (config.isEnabled(type) && info.counts[type.ordinal()] >= config.getLimit(type)) {
				mask |= 1 << type.ordinal();
			}
		}

		int already = REPORTED.getOrDefault(key, 0);
		if ((mask & ~already) == 0) {
			return;
		}
		REPORTED.put(key, already | mask);

		StringBuilder found = new StringBuilder();
		for (MarkerType type : MarkerType.values()) {
			if ((mask & (1 << type.ordinal())) != 0) {
				if (found.length() > 0) {
					found.append(", ");
				}
				found.append(type.label).append(": ").append(info.counts[type.ordinal()]);
			}
		}

		Component message = Component.literal("[Chunk Marker] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal("Chunk (" + (info.minX >> 4) + ", " + (info.minZ >> 4) + ") x=" + info.minX
						+ " z=" + info.minZ + " - " + found).withStyle(ChatFormatting.WHITE));
		mc.player.displayClientMessage(message, false);
	}
}
