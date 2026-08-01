package com.goodbird.cnpcefaddon.client;

/**
 * Marks the stretch of rendering that happens inside a Custom NPCs GUI entity preview.
 * <p>
 * {@code CustomGuiEntityDisplay.drawEntity} is the single entry point for every NPC preview
 * in Custom NPCs -- the NPC inventory screen, the model creation tabs, dialogs, quests, the
 * companion screens and scripted GUIs all funnel into it. Everything that decides whether an
 * NPC should be hidden, ghosted or drawn by Epic Fight instead of the vanilla model resolves
 * against the local player and the NPC's availability conditions, which are world concepts:
 * inside an editor GUI they made the preview disappear.
 * <p>
 * Only the render thread touches this, so a plain counter is enough. It is a counter rather
 * than a flag because {@code drawEntity} recurses when riders are shown.
 */
public final class GuiRenderContext {

    private static int depth;

    private GuiRenderContext() {
    }

    public static void push() {
        depth++;
    }

    public static void pop() {
        if (depth > 0) {
            depth--;
        }
    }

    /** True while a GUI entity preview is being drawn. */
    public static boolean isActive() {
        return depth > 0;
    }
}
