package no.kvaksrud.imsotalented;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import no.kvaksrud.imsotalented.talent.TalentRules;
import no.kvaksrud.imsotalented.talent.TalentRules.EnchantmentStacking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Settings from config/imsotalented.properties, which only the server reads (the client gets the rules it needs when
 * joining). A missing file is written with the defaults and missing keys are added to it. A value that doesn't parse
 * falls back to its default, and one out of range is clamped.
 *
 * @param rules          tree level prices and how talents combine with enchantments
 * @param backpackPickup whether picked-up items that don't fit the inventory go into the backpack
 */
public record ImSoTalentedConfig(TalentRules rules, boolean backpackPickup) {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImSoTalented.MOD_ID);

	public static final String FILE_NAME = "imsotalented.properties";
	public static final int MAX_POINTS = 1_000_000;

	public static final ImSoTalentedConfig DEFAULTS = new ImSoTalentedConfig(TalentRules.DEFAULTS, true);

	static final String COST_BASE = "cost_base";
	static final String COST_GROWTH = "cost_growth";
	static final String ENCHANTMENT_STACKING = "enchantment_stacking";
	static final String BACKPACK_PICKUP = "backpack_pickup";
	private static final List<String> KEYS = List.of(COST_BASE, COST_GROWTH, ENCHANTMENT_STACKING, BACKPACK_PICKUP);

	/** Reads the file, writing it first if it is missing or lacks a setting. Never fails: the defaults stand in. */
	public static ImSoTalentedConfig load(Path file) {
		Properties properties = new Properties();

		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				properties.load(reader);
			} catch (IOException | IllegalArgumentException e) {
				LOGGER.warn("Could not read {}, using the default settings: {}", file, e.toString());
				return DEFAULTS;
			}
		}

		TalentRules defaults = DEFAULTS.rules;
		TalentRules rules = new TalentRules(
				readInt(properties, file, COST_BASE, defaults.costBase(), 0, MAX_POINTS),
				readDouble(properties, file, COST_GROWTH, defaults.costGrowth(), 1.0, 2.0),
				readStacking(properties, file, defaults.stacking()));
		ImSoTalentedConfig config = new ImSoTalentedConfig(
				rules,
				readBoolean(properties, file, BACKPACK_PICKUP, DEFAULTS.backpackPickup));

		if (!properties.stringPropertyNames().containsAll(KEYS)) {
			config.save(file);
		}

		return config;
	}

	private static int readInt(Properties properties, Path file, String key, int fallback, int min, int max) {
		String text = properties.getProperty(key);

		if (text == null) {
			return fallback;
		}

		long value;

		try {
			value = Long.parseLong(text.trim());
		} catch (NumberFormatException e) {
			LOGGER.warn("{} in {} is not a whole number: \"{}\". Using {}.", key, file, text, fallback);
			return fallback;
		}

		long clamped = Math.clamp(value, min, max);

		if (clamped != value) {
			LOGGER.warn("{} in {} must be from {} to {}, so {} counts as {}.", key, file, min, max, text.trim(), clamped);
		}

		return (int) clamped;
	}

	private static double readDouble(Properties properties, Path file, String key, double fallback, double min, double max) {
		String text = properties.getProperty(key);

		if (text == null) {
			return fallback;
		}

		double value;

		try {
			value = Double.parseDouble(text.trim());
		} catch (NumberFormatException e) {
			LOGGER.warn("{} in {} is not a number: \"{}\". Using {}.", key, file, text, fallback);
			return fallback;
		}

		if (Double.isNaN(value) || Double.isInfinite(value)) {
			LOGGER.warn("{} in {} must be a real number, not \"{}\". Using {}.", key, file, text, fallback);
			return fallback;
		}

		double clamped = Math.clamp(value, min, max);

		if (clamped != value) {
			LOGGER.warn("{} in {} must be from {} to {}, so {} counts as {}.", key, file, min, max, text.trim(), clamped);
		}

		return clamped;
	}

	private static boolean readBoolean(Properties properties, Path file, String key, boolean fallback) {
		String text = properties.getProperty(key);

		if (text == null) {
			return fallback;
		}

		return switch (text.trim().toLowerCase(Locale.ROOT)) {
			case "true", "yes", "on" -> true;
			case "false", "no", "off" -> false;
			default -> {
				LOGGER.warn("{} in {} must be true or false, not \"{}\". Using {}.", key, file, text, fallback);
				yield fallback;
			}
		};
	}

	private static EnchantmentStacking readStacking(Properties properties, Path file, EnchantmentStacking fallback) {
		String text = properties.getProperty(ENCHANTMENT_STACKING);

		if (text == null) {
			return fallback;
		}

		return EnchantmentStacking.byId(text).orElseGet(() -> {
			LOGGER.warn("{} in {} must be highest or add, not \"{}\". Using {}.", ENCHANTMENT_STACKING, file, text, fallback.id());
			return fallback;
		});
	}

	/** Writes every setting with an explanation. A write failure is logged; the settings still apply. */
	public void save(Path file) {
		String text = """
				# I'm So Talented settings. Only the server's copy counts (in singleplayer, this one).
				# Changes take effect the next time a world is opened or the server starts.

				# Each talent tree (Gatherer, Warrior, Guardian, Explorer) has its own XP, earned by doing that tree's
				# activity, and every tree level is one talent point to spend in that tree. Going from level n to level n+1
				# takes ceil(cost_base * cost_growth^n) tree XP, the same in every tree.
				# Defaults: %d and %s. Allowed: 0 to %d, and 1.0 to 2.0.
				%s=%d
				%s=%s

				# How a talent combines with the same enchantment on your gear (Faster Mining with Efficiency, More Luck with
				# Fortune, Looting and Luck of the Sea, Careful Hands with Unbreaking):
				#   highest - the higher of the two counts, so gear keeps its level: a Fortune I pick with More Luck III
				#             mines like Fortune III, and a Fortune III pick with More Luck II stays Fortune III
				#   add     - both together: a Fortune III pick with More Luck III mines like Fortune VI
				# Default: %s
				%s=%s

				# Whether items you pick up that don't fit your inventory go into your backpack (the Backpack talent).
				# Default: %s
				%s=%s
				""".formatted(
				DEFAULTS.rules.costBase(), DEFAULTS.rules.costGrowth(), MAX_POINTS,
				COST_BASE, this.rules.costBase(),
				COST_GROWTH, this.rules.costGrowth(),
				DEFAULTS.rules.stacking().id(), ENCHANTMENT_STACKING, this.rules.stacking().id(),
				DEFAULTS.backpackPickup, BACKPACK_PICKUP, this.backpackPickup);

		try {
			Path parent = file.toAbsolutePath().getParent();

			if (parent != null) {
				Files.createDirectories(parent);
			}

			Files.writeString(file, text, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.warn("Could not write {}: {}", file, e.toString());
		}
	}
}
