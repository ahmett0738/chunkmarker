package com.senin.chunkmarker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** Settings, saved to config/chunkmarker.json. Index = MarkerType.ordinal(). */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MAX_LIMIT = 9999;

	public boolean[] enabled;
	public int[] limit;

	private static Config instance;

	public static Config get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("chunkmarker.json");
	}

	private static Config load() {
		Config config = null;
		try {
			Path path = file();
			if (Files.exists(path)) {
				config = GSON.fromJson(Files.readString(path), Config.class);
			}
		} catch (Exception e) {
			System.err.println("[chunkmarker] Could not read config, using defaults: " + e);
		}
		if (config == null) {
			config = new Config();
		}
		config.sanitize();
		return config;
	}

	/** Makes sure both arrays exist, have the right length and valid values (also fixes hand-edited files). */
	private void sanitize() {
		int n = MarkerType.values().length;
		boolean[] newEnabled = new boolean[n];
		int[] newLimit = new int[n];
		for (int i = 0; i < n; i++) {
			newEnabled[i] = enabled == null || i >= enabled.length || enabled[i];
			int value = (limit != null && i < limit.length) ? limit[i] : MarkerType.values()[i].defaultLimit;
			newLimit[i] = Math.max(1, Math.min(MAX_LIMIT, value));
		}
		enabled = newEnabled;
		limit = newLimit;
	}

	public boolean isEnabled(MarkerType type) {
		return enabled[type.ordinal()];
	}

	public int getLimit(MarkerType type) {
		return limit[type.ordinal()];
	}

	public void setEnabled(MarkerType type, boolean value) {
		enabled[type.ordinal()] = value;
	}

	public void changeLimit(MarkerType type, int delta) {
		limit[type.ordinal()] = Math.max(1, Math.min(MAX_LIMIT, limit[type.ordinal()] + delta));
	}

	public void save() {
		try {
			Files.writeString(file(), GSON.toJson(this));
		} catch (IOException e) {
			System.err.println("[chunkmarker] Could not save config: " + e);
		}
	}
}
