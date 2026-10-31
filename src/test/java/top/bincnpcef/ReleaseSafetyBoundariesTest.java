package top.bincnpcef;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Source constraints only; does not substitute for loading Mixins or running Minecraft. */
class ReleaseSafetyBoundariesTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/top/bincnpcef", path));
    }

    @Test
    void damageLinkHasSourceBoundariesAndDoesNotOverwriteReducedDamage() throws Exception {
        String model = source("common/NpcDamageModel.java");
        assertTrue(model.contains("source.getDirectEntity() == npc"));
        assertTrue(model.contains("!source.is(EpicFightDamageTypeTags.EXECUTION)"));
        assertTrue(model.contains("AddonConfig.NPC_DAMAGE_USE_MELEE_STRENGTH.get()"));
        assertTrue(source("mixin/impl/MixinNpcBaseDamage.java").contains(
                "useMeleeStrength(source) ? damage : patch.getModifiedBaseDamage(damage)"));
        String damage = source("mixin/impl/MixinEpicFightDamageSource.java");
        assertTrue(damage.contains("@At(\"HEAD\")"));
        assertTrue(damage.contains("cir.setReturnValue(baseDamage)"));
        assertFalse(damage.contains("getStrength()"));
    }

    @Test
    void reloadAndBindingKeepThreadAndLifecycleBoundaries() throws Exception {
        String reload = source("common/NpcPatchReloadListener.java");
        assertFalse(reload.contains("putCustomEntityPatch("));
        assertFalse(reload.contains("RenderStorage"));
        assertTrue(reload.contains("thenCompose(barrier::wait).thenAcceptAsync"));
        assertTrue(reload.contains("private static volatile Snapshot server"));
        assertTrue(reload.contains("private static volatile Snapshot client"));
        assertTrue(reload.contains("server = new Snapshot(tempProvider"));
        assertTrue(reload.contains("client = new Snapshot(tempProvider"));
        assertTrue(reload.contains("entity.level().isClientSide() ? client : server"));
        assertTrue(source("client/render/RenderStorage.java").contains("isSameThread()"));
        String display = source("mixin/impl/MixinDataDisplay.java");
        assertTrue(display.contains("NpcPatchReloadListener.providerFor(npc)"));
        assertTrue(display.contains("cnpcef$boundProvider == branch"));
        assertTrue(display.contains("npc.isAddedToLevel() && patch.uninitialized()"));
        assertFalse(display.contains("npc.tickCount > 0"));
    }

    @Test
    void deathRecoveryCoversEarlyReturnsAndPreservesNonDeathQueueAndExplicitPose() throws Exception {
        for (String mixin : new String[]{"MixinEntityNpcInterface", "MixinNpcCorpsePose"}) {
            String body = source("mixin/impl/" + mixin + ".java");
            assertTrue(body.contains("method = \"tick\", at = @At(\"RETURN\")"));
            assertFalse(body.contains("method = \"tick\", at = @At(\"TAIL\")"));
        }
        String recovery = source("common/NpcDeathRecovery.java");
        assertTrue(recovery.contains("npc.currentAnimation == 14"));
        assertTrue(recovery.contains("queuedDeath ? null : next"));
        assertTrue(recovery.contains("npc.getHealth() <= 0.0F || npc.isKilled()"));
        String corpse = source("client/NpcCorpsePose.java");
        assertTrue(corpse.contains("npc.currentAnimation == 14"));
        assertTrue(corpse.contains("next != null && !queuedDeath ? next"));
        assertTrue(corpse.contains("else if (restoredAnimator != animator)"));
        assertTrue(source("mixin/impl/MixinCustomHumanoidMobPatch.java").contains("NpcDeathRecovery.dead(npc)"));
    }
}
