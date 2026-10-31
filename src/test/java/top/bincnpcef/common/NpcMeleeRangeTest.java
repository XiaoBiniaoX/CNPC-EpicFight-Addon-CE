package top.bincnpcef.common;

import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;
import yesman.epicfight.api.animation.TransformSheet;
import yesman.epicfight.api.animation.property.AnimationProperty.ActionAnimationProperty;
import yesman.epicfight.api.animation.property.AnimationProperty.AttackAnimationProperty;
import yesman.epicfight.api.animation.property.MoveCoordFunctions;
import yesman.epicfight.api.animation.types.AirSlashAnimation;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.DashAttackAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Real EF types/conditions without loading a world; Mixin wiring below is a source constraint only. */
class NpcMeleeRangeTest {
    private static AttackAnimation melee() {
        return new AttackAnimation(0.1F, "test:melee", null, phase());
    }

    private static AttackAnimation.Phase phase() {
        return new AttackAnimation.Phase(0, 0.1F, 0.2F, 0.3F, 0.4F, 1,
                InteractionHand.MAIN_HAND, (yesman.epicfight.api.animation.Joint) null, null);
    }

    @Test
    void rangeUsesLiveValuesAndKeepsCnpcSizeFloor() {
        double range = NpcMeleeRange.rangeSqr(2, 0.6F, 0.6F);
        assertEquals((double) (4.0F + 0.6F), range);
        assertTrue(2 * 2 <= range);
        assertFalse(5 * 5 <= range);
        assertFalse(6 * 6 <= range);
        assertTrue(5 * 5 <= NpcMeleeRange.rangeSqr(5, 0.6F, 0.6F));
        assertEquals((double) (16.0F + 0.6F), NpcMeleeRange.rangeSqr(0, 2, 0.6F));
        assertTrue(6 * 6 <= NpcMeleeRange.rangeSqr(2, 3, 0.6F));
        assertFalse(Double.NaN <= range);
    }

    @Test
    void ordinaryAttacksAreLimitedButSpecialTypesAndAuthoredMovementAreNot() {
        assertTrue(NpcMeleeRange.isOrdinaryMelee(melee()));
        assertFalse(NpcMeleeRange.isOrdinaryMelee(new StaticAnimation(0.1F, false, "test:dodge", null)));
        assertFalse(NpcMeleeRange.isOrdinaryMelee(new DashAttackAnimation(0.1F, "test:dash", null, phase())));
        assertFalse(NpcMeleeRange.isOrdinaryMelee(new AirSlashAnimation(0.1F, "test:airslash", null, phase())));
        AttackAnimation dash = melee();
        dash.addProperty(ActionAnimationProperty.COORD_SET_BEGIN, MoveCoordFunctions.RAW_COORD);
        dash.addProperty(ActionAnimationProperty.COORD_SET_TICK, null);
        assertFalse(NpcMeleeRange.isOrdinaryMelee(dash));
        for (var property : List.of(ActionAnimationProperty.MOVE_VERTICAL, AttackAnimationProperty.FIXED_MOVE_DISTANCE)) {
            AttackAnimation special = melee();
            special.addProperty(property, true);
            assertFalse(NpcMeleeRange.isOrdinaryMelee(special));
        }
        AttackAnimation moving = melee();
        moving.addProperty(ActionAnimationProperty.STOP_MOVEMENT, false);
        assertFalse(NpcMeleeRange.isOrdinaryMelee(moving));
        AttackAnimation authored = melee();
        authored.addProperty(ActionAnimationProperty.COORD, new TransformSheet());
        assertFalse(NpcMeleeRange.isOrdinaryMelee(authored));
        AttackAnimation tracking = melee();
        tracking.addProperty(ActionAnimationProperty.COORD_SET_TICK, MoveCoordFunctions.TRACE_TARGET_LOCATION_ROTATION);
        assertFalse(NpcMeleeRange.isOrdinaryMelee(tracking));
    }

    @Test
    void extraConditionPreservesOriginalConditionsAndDoesNotConsumeRejectedCombo() throws Exception {
        AtomicBoolean inRange = new AtomicBoolean(false);
        AtomicBoolean originalCondition = new AtomicBoolean(true);
        var behavior = CombatBehaviors.Behavior.builder().emptyBehavior()
                .custom(patch -> inRange.get()).custom(patch -> originalCondition.get());
        var series = CombatBehaviors.BehaviorSeries.builder().weight(1).cooldown(10)
                .canBeInterrupted(false).nextBehavior(behavior).nextBehavior(behavior);
        var builtSeries = series.build();
        assertFalse(builtSeries.canBeSelected(null));
        inRange.set(true);
        assertTrue(builtSeries.canBeSelected(null));
        originalCondition.set(false);
        assertFalse(builtSeries.canBeSelected(null));
        var pointer = builtSeries.getClass().getDeclaredField("nextBehaviorPointer");
        pointer.setAccessible(true);
        assertEquals(0, pointer.getInt(builtSeries));
        var cooldown = builtSeries.getClass().getDeclaredField("cooldown");
        cooldown.setAccessible(true);
        assertEquals(0, cooldown.getInt(builtSeries));

        var combat = CombatBehaviors.builder().newBehaviorSeries(series).build(null);
        var active = CombatBehaviors.class.getDeclaredField("currentBehaviorPointer");
        active.setAccessible(true);
        active.setInt(combat, 0); // Emulate a selected series without constructing a game entity.
        originalCondition.set(true);
        assertNotNull(combat.tryProceed());
        inRange.set(false);
        assertNull(combat.tryProceed());
        assertFalse(combat.hasActivatedMove());
    }

    @Test
    void mixinAddsASelectionPredicateWithoutChangingPlaybackOrGoalTick() throws Exception {
        String mixin = Files.readString(Path.of("src/main/java/top/bincnpcef/mixin/impl/MixinCombatBehaviorMeleeRange.java"));
        assertTrue(mixin.contains("method = \"animationBehavior\", at = @At(\"RETURN\")"));
        assertTrue(mixin.contains("custom(patch -> NpcMeleeRange.canSelect(patch, motion))"));
        assertFalse(mixin.contains("cancellable = true"));
        assertFalse(mixin.contains("playAnimation"));
        String helper = Files.readString(Path.of("src/main/java/top/bincnpcef/common/NpcMeleeRange.java"));
        assertTrue(helper.contains("instanceof EntityNPCInterface npc"));
        assertTrue(helper.contains("npc.stats.melee.getRange()"));
        assertTrue(helper.contains("target.getBoundingBox().minY"));
        assertTrue(helper.contains("animation instanceof RangedAttackAnimation"));
        assertTrue(Files.readString(Path.of("src/main/resources/cnpcef.mixins.json"))
                .contains("impl.MixinCombatBehaviorMeleeRange"));
    }
}
