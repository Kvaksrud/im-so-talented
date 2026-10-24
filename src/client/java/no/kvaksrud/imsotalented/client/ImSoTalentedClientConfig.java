package no.kvaksrud.imsotalented.client;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

import no.kvaksrud.imsotalented.ImSoTalented;
import no.kvaksrud.imsotalented.talent.TalentTree;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Personal settings from config/imsotalented-client.properties, kept on this computer only (the server's settings
 * are in {@code imsotalented.properties}). A missing file or key reads as the default; a value that doesn't parse is
 * logged and reads as the default too.
 *
 * @param pinnedTree the tree whose XP bar always shows above the hotbar, or null for none
 */
public record ImSoTalentedClientConfig(@Nullable TalentTree pinnedTree) {
	private static final Logger LOGGER = LoggerFactory.getLogger(ImSoTalented.MOD_ID);

	public static final String FILE_NAME = "imsotalented-client.properties";
	public static final ImSoTalentedClientConfig DEFAULTS = new ImSoTalentedClientConfig(null);

	static final String PINNED_TREE = "pinned_tree";
	private static final String NONE = "none";

	/** Reads the file. Never fails: the defaults stand in. */
	public static ImSoTalentedClientConfig load(Path file) {
		if (!Files.exists(file)) {
			return DEFAULTS;
		}

		Properties properties = new Properties();

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (IOException | IllegalArgumentException e) {
			LOGGER.warn("Could not read {}, using the default settings: {}", file, e.toString());
			return DEFAULTS;
		}

		return new ImSoTalentedClientConfig(readTree(properties, file));
	}

	private static @Nullable TalentTree readTree(Properties properties, Path file) {
		String text = properties.getProperty(PINNED_TREE);

		if (text == null || text.isBlank() || text.trim().equalsIgnoreCase(NONE)) {
			return null;
		}

		Optional<TalentTree> tree = TalentTree.byId(text.trim().toLowerCase(Locale.ROOT));

		if (tree.isEmpty()) {
			LOGGER.warn("{} in {} must be gatherer, warrior, guardian, explorer or none, not \"{}\". Using none.", PINNED_TREE, file, text);
		}

		return tree.orElse(null);
	}

	/** Writes every setting with an explanation. A write failure is logged; the settings still apply. */
	public void save(Path file) {
		String text = """
				# I'm So Talented settings for this computer only. The game writes this file when you change them.

				# The talent tree whose XP bar always shows above the hotbar: gatherer, warrior, guardian, explorer, or
				# none. Pin or unpin a tree with the pin button in its tab of the talent screen.
				%s=%s
				""".formatted(PINNED_TREE, this.pinnedTree == null ? NONE : this.pinnedTree.id());

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
