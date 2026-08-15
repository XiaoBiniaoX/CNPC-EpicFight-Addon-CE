package com.goodbird.cnpcefaddon.mixin.impl;

import com.nameless.indestructible.world.ai.goal.AdvancedCombatGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.PrintStream;

/**
 * Removes a debug print that stutters every NPC attack chain.
 * <p>
 * {@code AdvancedCombatGoal.tick()} ends its "continue the current series" branch with
 * {@code System.out.println("process execute")}. That runs once per proceeded behavior, on the
 * server thread, and Forge redirects {@code System.out} into Log4j -- so every swing pays for
 * a synchronous console write plus a line in both latest.log and debug.log. Earlier logs from
 * this instance contain hundreds of them in a single session. With several NPCs fighting it
 * lands on exactly the tick the next move should start.
 * <p>
 * The redirect swallows the call and nothing else; the behavior still executes normally.
 * {@code require = 0} keeps this harmless if a future Indestructible build drops the print.
 */
@Mixin(AdvancedCombatGoal.class)
public class MixinAdvancedCombatGoal {

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/io/PrintStream;println(Ljava/lang/String;)V",
                    remap = false
            ),
            require = 0
    )
    private void cnpcef$dropDebugPrint(PrintStream stream, String message) {
        // intentionally empty
    }
}
