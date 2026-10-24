package no.kvaksrud.imsotalented;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import no.kvaksrud.imsotalented.talent.TalentRules;
import no.kvaksrud.imsotalented.talent.TalentRules.EnchantmentStacking;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImSoTalentedConfigTest {
	@TempDir
	Path dir;

	private Path file() {
		return this.dir.resolve("config").resolve(ImSoTalentedConfig.FILE_NAME);
	}

	private void write(String text) throws IOException {
		Files.createDirectories(this.file().getParent());
		Files.writeString(this.file(), text, StandardCharsets.UTF_8);
	}

	@Test
	void theDefaultsAreTheDesignsPrices() {
		assertEquals(new TalentRules(25, 1.18, EnchantmentStacking.HIGHEST), ImSoTalentedConfig.DEFAULTS.rules());
		assertTrue(ImSoTalentedConfig.DEFAULTS.backpackPickup());
	}

	@Test
	void missingFileIsWrittenWithTheDefaults() throws IOException {
		assertEquals(ImSoTalentedConfig.DEFAULTS, ImSoTalentedConfig.load(this.file()));

		String text = Files.readString(this.file());
		assertTrue(text.contains("\ncost_base=25\n"), text);
		assertTrue(text.contains("\ncost_growth=1.18\n"), text);
		assertTrue(text.contains("\nenchantment_stacking=highest\n"), text);
		assertTrue(text.contains("\nbackpack_pickup=true\n"), text);
		assertFalse(text.contains("converter_values"), "the Experience Converter is gone: " + text);
		assertEquals(ImSoTalentedConfig.DEFAULTS, ImSoTalentedConfig.load(this.file()), "the written file reads back as the defaults");
	}

	@Test
	void readsEverySetting() throws IOException {
		this.write("""
				cost_base = 40
				cost_growth=1.5
				enchantment_stacking=ADD
				backpack_pickup=no
				""");

		ImSoTalentedConfig config = ImSoTalentedConfig.load(this.file());
		assertEquals(new TalentRules(40, 1.5, EnchantmentStacking.ADD), config.rules());
		assertFalse(config.backpackPickup());
	}

	@Test
	void badValuesFallBackOrAreClamped() throws IOException {
		this.write("""
				cost_base=lots
				cost_growth=-4
				enchantment_stacking=max
				backpack_pickup=maybe
				""");

		ImSoTalentedConfig config = ImSoTalentedConfig.load(this.file());
		assertEquals(25, config.rules().costBase(), "not a number: default");
		assertEquals(1.0, config.rules().costGrowth(), "below 1.0: clamped to the range floor");
		assertEquals(EnchantmentStacking.HIGHEST, config.rules().stacking());
		assertTrue(config.backpackPickup());
	}

	@Test
	void costBaseIsClampedToItsRange() throws IOException {
		this.write("cost_base=-5\n");
		assertEquals(0, ImSoTalentedConfig.load(this.file()).rules().costBase(), "below 0: every level free");

		this.write("cost_base=99999999999\n");
		assertEquals(ImSoTalentedConfig.MAX_POINTS, ImSoTalentedConfig.load(this.file()).rules().costBase());
	}

	@Test
	void growthAboveTheRangeIsClamped() throws IOException {
		this.write("cost_growth=4.5\n");
		assertEquals(2.0, ImSoTalentedConfig.load(this.file()).rules().costGrowth(), "above 2.0: clamped to the range ceiling");
	}

	@Test
	void growthThatDoesNotParseFallsBack() throws IOException {
		this.write("cost_growth=fast\n");
		assertEquals(1.18, ImSoTalentedConfig.load(this.file()).rules().costGrowth(), "not a number: default");

		this.write("cost_growth=NaN\n");
		assertEquals(1.18, ImSoTalentedConfig.load(this.file()).rules().costGrowth(), "not a real number: default");
	}

	@Test
	void missingKeysAreAddedToTheFile() throws IOException {
		this.write("cost_base=12\n");
		ImSoTalentedConfig config = ImSoTalentedConfig.load(this.file());
		assertEquals(12, config.rules().costBase());

		String text = Files.readString(this.file());
		assertTrue(text.contains("\ncost_base=12\n"), "keeps the value that was there: " + text);
		assertTrue(text.contains("\ncost_growth=1.18\n"), "adds the missing ones: " + text);
		assertTrue(text.contains("\nbackpack_pickup=true\n"), "adds the missing ones: " + text);
	}

	@Test
	void anOldFileLosesTheSettingsThatAreGone() throws IOException {
		this.write("""
				cost_base=10
				cost_growth=1.07
				enchantment_stacking=add
				converter_values=minecraft:diamond=40
				""");

		ImSoTalentedConfig config = ImSoTalentedConfig.load(this.file());
		assertEquals(new TalentRules(10, 1.07, EnchantmentStacking.ADD), config.rules(), "the prices someone chose are kept");

		String text = Files.readString(this.file());
		assertFalse(text.contains("converter_values"), "rewritten without the converter: " + text);
		assertTrue(text.contains("\nbackpack_pickup="), "and with the backpack setting: " + text);
	}
}
