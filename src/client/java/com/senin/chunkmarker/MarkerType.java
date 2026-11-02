package com.senin.chunkmarker;

import net.minecraft.resources.Identifier;

/** The four things the mod looks for. The order here is also the priority when a chunk qualifies for several. */
public enum MarkerType {
	VINE("Sarmaşık", "textures/block/vine.png", 0xE055B030, 1),
	HIVE("Arı kovanı (seviye 5)", "textures/item/honey_bottle.png", 0xE0FFFFFF, 1),
	KELP("Kelp", "textures/item/kelp.png", 0xE0FFFFFF, 10),
	AMETHYST("Ametist kümesi", "textures/block/amethyst_cluster.png", 0xE0FFFFFF, 1);

	public final String label;
	public final Identifier texture;
	/** ARGB color the texture is multiplied with (the vine texture is gray in vanilla, so it needs a green tint). */
	public final int tint;
	public final int defaultLimit;

	MarkerType(String label, String texturePath, int tint, int defaultLimit) {
		this.label = label;
		this.texture = Identifier.fromNamespaceAndPath("minecraft", texturePath);
		this.tint = tint;
		this.defaultLimit = defaultLimit;
	}
}
