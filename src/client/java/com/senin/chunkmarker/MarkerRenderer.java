package com.senin.chunkmarker;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

/** Draws one flat 16x16-block icon at y=62 over every chunk that qualifies. Uses the "see through" text render type, so it shows through terrain. */
public final class MarkerRenderer {
	private static final float ICON_Y = 62.0F;
	private static final int FULL_BRIGHT = 15728880;

	private MarkerRenderer() {
	}

	public static void register() {
		WorldRenderEvents.END_MAIN.register(MarkerRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		if (ChunkMarkerClient.DATA.isEmpty()) {
			return;
		}

		Vec3 camera = context.worldState().cameraRenderState.pos;
		PoseStack matrices = context.matrices();
		MultiBufferSource buffers = context.consumers();

		matrices.pushPose();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		Matrix4f matrix = matrices.last().pose();

		// One pass per marker type, so each texture's buffer is opened only once.
		for (MarkerType type : MarkerType.values()) {
			VertexConsumer consumer = null;

			for (Map.Entry<Long, ChunkMarkerClient.ChunkInfo> entry : ChunkMarkerClient.DATA.entrySet()) {
				ChunkMarkerClient.ChunkInfo info = entry.getValue();
				if (ChunkMarkerClient.shownType(info) != type) {
					continue;
				}

				if (consumer == null) {
					consumer = buffers.getBuffer(RenderTypes.textSeeThrough(type.texture));
				}

				float x0 = info.minX;
				float z0 = info.minZ;
				float x1 = x0 + 16.0F;
				float z1 = z0 + 16.0F;

				// Flat quad, north is up when you look down on it.
				consumer.addVertex(matrix, x0, ICON_Y, z0).setColor(type.tint).setUv(0.0F, 0.0F).setLight(FULL_BRIGHT);
				consumer.addVertex(matrix, x0, ICON_Y, z1).setColor(type.tint).setUv(0.0F, 1.0F).setLight(FULL_BRIGHT);
				consumer.addVertex(matrix, x1, ICON_Y, z1).setColor(type.tint).setUv(1.0F, 1.0F).setLight(FULL_BRIGHT);
				consumer.addVertex(matrix, x1, ICON_Y, z0).setColor(type.tint).setUv(1.0F, 0.0F).setLight(FULL_BRIGHT);
			}
		}

		matrices.popPose();
	}
}
