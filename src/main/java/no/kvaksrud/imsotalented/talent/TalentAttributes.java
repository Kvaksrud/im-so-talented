package no.kvaksrud.imsotalented.talent;

import java.util.function.ToDoubleFunction;

import no.kvaksrud.imsotalented.ImSoTalented;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Talents that are plain attribute modifiers: health, armor, speed, jumping, falling — and, from the
 * mobility batch, step height (Step Assist) and both interaction ranges (Reach). The modifiers are
 * permanent (saved with the player), so max health is already right when health is loaded on login;
 * otherwise it would be clamped to 20. Vanilla doesn't carry modifiers over a death, so they're applied
 * again on respawn.
 *
 * <p>Reach rides on the two range attributes because 26.3's {@code Player.blockInteractionRange()} and
 * {@code entityInteractionRange()} are pure reads of them (defaults 4.5/3.0), and the creative/spectator
 * extended reach is itself a transient modifier on the same attributes — a permanent ADD_VALUE modifier
 * therefore lands additively on top of whatever the current state gives, never replacing it.
 */
public final class TalentAttributes {
	private static final Bonus[] BONUSES = {
			new Bonus(Talent.MORE_HEALTH, Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, TalentValues::extraHealth),
			new Bonus(Talent.STRONGER_ARMOR, Attributes.ARMOR, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, TalentValues::armorBonus),
			new Bonus(Talent.STRONGER_ARMOR, Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, TalentValues::armorBonus),
			new Bonus(Talent.FASTER_RUNNING, Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, TalentValues::speedBonus),
			new Bonus(Talent.HIGHER_JUMPING, Attributes.JUMP_STRENGTH, AttributeModifier.Operation.ADD_VALUE, TalentValues::jumpStrengthBonus),
			new Bonus(Talent.SAFE_FALLING, Attributes.SAFE_FALL_DISTANCE, AttributeModifier.Operation.ADD_VALUE, TalentValues::extraSafeFall),
			new Bonus(Talent.SAFE_FALLING, Attributes.FALL_DAMAGE_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, level -> -TalentValues.fallDamageReduction(level)),
			// Mobility: 26.3 reads step-up and both interaction ranges off attributes (vanilla 0.6 / 4.5 / 3.0), so
			// Step Assist and Reach are plain ADD_VALUE rows — no mixin needed for either.
			new Bonus(Talent.STEP_ASSIST, Attributes.STEP_HEIGHT, AttributeModifier.Operation.ADD_VALUE, TalentValues::stepBonus),
			new Bonus(Talent.REACH, Attributes.BLOCK_INTERACTION_RANGE, AttributeModifier.Operation.ADD_VALUE, TalentValues::reachBonus),
			new Bonus(Talent.REACH, Attributes.ENTITY_INTERACTION_RANGE, AttributeModifier.Operation.ADD_VALUE, TalentValues::entityReachBonus),
	};

	private TalentAttributes() {
	}

	/**
	 * Sets every talent attribute modifier to match the player's effective levels — 0 while a toggleable
	 * talent is toggled off, so toggling off removes its modifiers and toggling back on re-adds them —
	 * removing those at level 0.
	 */
	public static void apply(ServerPlayer player) {
		TalentData data = Talents.data(player);

		for (Bonus bonus : BONUSES) {
			AttributeInstance attribute = player.getAttribute(bonus.attribute);

			if (attribute == null) {
				continue;
			}

			int level = data.effectiveLevel(bonus.talent);
			Identifier id = ImSoTalented.id(bonus.talent.id());

			if (level <= 0) {
				attribute.removeModifier(id);
			} else {
				attribute.addOrReplacePermanentModifier(new AttributeModifier(id, bonus.amount.applyAsDouble(level), bonus.operation));
			}
		}
	}

	private record Bonus(Talent talent, Holder<Attribute> attribute, AttributeModifier.Operation operation, ToDoubleFunction<Integer> amount) {
	}
}
