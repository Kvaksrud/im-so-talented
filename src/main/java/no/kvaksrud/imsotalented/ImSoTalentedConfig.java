package no.kvaksrud.imsotalented;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import no.kvaksrud.imsotalented.talent.TalentRules;
import no.kvaksrud.imsotalented.talent.TalentRules.EnchantmentStacking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Settings from config/imsotalented.properties, which only the server reads (the client gets the rules it needs when
 * joining). A missing file is written with the defaults and missing keys are added to it. A value that doesn't parse
 * falls back to its default, and one out of range is clamped. A key that isn't a setting is ignored with a warning (and
 * left out when the file is rewritten), and 0.1.0's {@code extra_inventory_pickup} carries over to {@code
 * backpack_pickup}.
 *
 * @param rules          tree level prices and how talents combine with enchantments
 * @param backpackPickup whether picked-up items that don't fit the inventory go into the backpack
 */
public record ImSoTalentedConfig(TalentRules rules, boolean backpackPickup) {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImSoTalented.MOD_ID);

	public static final String FILE_NAME = "imsotalented.properties";
	/** The highest {@code level_cost_base} allowed; a higher one is clamped to it. */
	public static final int MAX_LEVEL_COST_BASE = 1_000_000;

	public static final ImSoTalentedConfig DEFAULTS = new ImSoTalentedConfig(TalentRules.DEFAULTS, true);

	/**
	 * The price keys were renamed with the four trees: {@code cost_base} and {@code cost_growth} priced talent points in
	 * vanilla XP, so a 0.1.0 file's values would mean something else now. Under the new names an old file falls back to
	 * the new defaults, and the rewrite for the missing keys drops the old ones (with a warning, see
	 * {@link #RETIRED_KEYS}).
	 */
	static final String LEVEL_COST_BASE = "level_cost_base";
	static final String LEVEL_COST_GROWTH = "level_cost_growth";
	static final String ENCHANTMENT_STACKING = "enchantment_stacking";
	static final String BACKPACK_PICKUP = "backpack_pickup";
	private static final List<String> KEYS = List.of(LEVEL_COST_BASE, LEVEL_COST_GROWTH, ENCHANTMENT_STACKING, BACKPACK_PICKUP);

	/** 0.1.0's name for {@link #BACKPACK_PICKUP}: read when the new key is missing, so a rename doesn't flip the choice. */
	static final String EXTRA_INVENTORY_PICKUP = "extra_inventory_pickup";

	/** The keys 0.1.0 had that are gone, with what took their place, for the warning about each. */
	private static final Map<String, String> RETIRED_KEYS = Map.of(
			"cost_base", "it priced talents in vanilla XP; " + LEVEL_COST_BASE + " (tree XP for the first tree level) replaces it, so its value doesn't carry over",
			"cost_growth", "it priced talents in vanilla XP; " + LEVEL_COST_GROWTH + " (how much more each tree level takes) replaces it, so its value doesn't carry over",
			"converter_values", "the Experience Converter is gone and nothing replaces it: tree XP is earned by playing",
			EXTRA_INVENTORY_PICKUP, "it is called " + BACKPACK_PICKUP + " now");

	/**
	 * Reads the file, writing it first if it is missing or lacks a setting. Never fails: the defaults stand in. Each key
	 * the file has that isn't a setting gets one warning, and a rewrite is logged.
	 */
	public static ImSoTalentedConfig load(Path file) {
		Properties properties = new Properties();
		boolean exists = Files.exists(file);

		if (exists) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				properties.load(reader);
			} catch (IOException | IllegalArgumentException e) {
				LOGGER.warn("Could not read {}, using the default settings: {}", file, e.toString());
				return DEFAULTS;
			}
		}

		// The 0.1.0 pickup key counts only while the new one is missing; the rewrite then saves it under the new name.
		boolean oldPickup = !properties.containsKey(BACKPACK_PICKUP) && properties.containsKey(EXTRA_INVENTORY_PICKUP);
		TalentRules defaults = DEFAULTS.rules;
		TalentRules rules = new TalentRules(
				readInt(properties, file, LEVEL_COST_BASE, defaults.costBase(), 0, MAX_LEVEL_COST_BASE),
				readDouble(properties, file, LEVEL_COST_GROWTH, defaults.costGrowth(), 1.0, 2.0),
				readStacking(properties, file, defaults.stacking()));
		ImSoTalentedConfig config = new ImSoTalentedConfig(
				rules,
				readBoolean(properties, file, oldPickup ? EXTRA_INVENTORY_PICKUP : BACKPACK_PICKUP, DEFAULTS.backpackPickup));

		List<String> missing = KEYS.stream().filter(key -> !properties.containsKey(key)).toList();
		List<String> unused = properties.stringPropertyNames().stream().filter(key -> !KEYS.contains(key)).sorted().toList();
		boolean rewrite = !missing.isEmpty();

		for (String key : unused) {
			warnUnused(file, key, oldPickup, rewrite);
		}

		if (rewrite && config.save(file)) {
			if (exists) {
				LOGGER.info("Rewrote {} to add {}{}", file, String.join(", ", missing),
						unused.isEmpty() ? "" : " and leave out " + String.join(", ", unused));
			} else {
				LOGGER.info("Wrote {} with the default settings", file);
			}
		}

		return config;
	}

	/** One warning for a key that isn't a setting: what replaced it if it is an old one, and what happens to it. */
	private static void warnUnused(Path file, String key, boolean oldPickup, boolean rewrite) {
		String fate = rewrite ? "It is left out of the rewritten file." : "It is ignored.";

		if (key.equals(EXTRA_INVENTORY_PICKUP)) {
			LOGGER.warn("{} in {} is no longer used: {}{}", key, file, RETIRED_KEYS.get(key),
					oldPickup ? ", and the rewritten file keeps its value under that name." : ", which the file has too. " + fate);
		} else if (RETIRED_KEYS.containsKey(key)) {
			LOGGER.warn("{} in {} is no longer used: {}. {}", key, file, RETIRED_KEYS.get(key), fate);
		} else {
			LOGGER.warn("{} in {} is not a setting (they are {}). {}", key, file, String.join(", ", KEYS), fate);
		}
	}

	/** The settings in the file's key=value form, for the log line saying which ones are in effect. */
	public String summary() {
		return "%s=%d, %s=%s, %s=%s, %s=%s".formatted(
				LEVEL_COST_BASE, this.rules.costBase(),
				LEVEL_COST_GROWTH, this.rules.costGrowth(),
				ENCHANTMENT_STACKING, this.rules.stacking().id(),
				BACKPACK_PICKUP, this.backpackPickup);
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

	/**
	 * Writes every setting with an explanation. A write failure is logged; the settings still apply.
	 *
	 * @return whether the file was written
	 */
	public boolean save(Path file) {
		String text = """
				# I'm So Talented settings. Only the server's copy counts (in singleplayer, this one).
				# Changes take effect the next time a world is opened or the server starts.

				# Each talent tree (Gatherer, Warrior, Guardian, Explorer) has its own XP, earned by doing that tree's
				# activity, and every tree level is one talent point to spend in that tree. Going from level n to level n+1
				# takes ceil(level_cost_base * level_cost_growth^n) tree XP, the same in every tree.
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
				DEFAULTS.rules.costBase(), DEFAULTS.rules.costGrowth(), MAX_LEVEL_COST_BASE,
				LEVEL_COST_BASE, this.rules.costBase(),
				LEVEL_COST_GROWTH, this.rules.costGrowth(),
				DEFAULTS.rules.stacking().id(), ENCHANTMENT_STACKING, this.rules.stacking().id(),
				DEFAULTS.backpackPickup, BACKPACK_PICKUP, this.backpackPickup);

		try {
			Path parent = file.toAbsolutePath().getParent();

			if (parent != null) {
				Files.createDirectories(parent);
			}

			Files.writeString(file, text, StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			LOGGER.warn("Could not write {}: {}", file, e.toString());
			return false;
		}
	}
}
