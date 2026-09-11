package com.goodbird.cnpcefaddon.common.patch;

import com.goodbird.cnpcefaddon.common.GroundedFallFix;
import com.goodbird.cnpcefaddon.common.provider.CeNpcPatchProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.shelmarow.combat_evolution.bgm.network.CEMusicNetworkHandler;
import noppes.npcs.ai.EntityAIAttackTarget;
import noppes.npcs.entity.EntityNPCInterface;
import net.shelmarow.combat_evolution.ai.CEDatapackMobPatch;
import net.shelmarow.combat_evolution.ai.CECombatBehaviors;
import net.shelmarow.combat_evolution.ai.iml.ILivingEntityData;
import net.shelmarow.combat_evolution.ai.goal.CEAnimationAttackGoal;
import net.shelmarow.combat_evolution.ai.util.CEPatchUtils;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.gameasset.EpicFightSounds;
import yesman.epicfight.main.EpicFightSharedConstants;

/**
 * 将 CombatEvolution 的完整战斗 patch 适配到 CustomNPCs。
 *
 * <p>继承 CE 原生数据包 patch，保留其行为树、耐力/破防、防御反击、属性、BossBar 与 BGM
 * 全部实现；只补 CNPC 特有的 armature 深拷贝、显示缩放和已落地残留下落速度护栏。
 */
public final class CeNpcPatch extends CEDatapackMobPatch implements INpcPatch {
    /** 战斗欲望攻速修正的固定 UUID，便于重复应用时先移除旧值。 */
    private static final java.util.UUID BATTLE_DESIRE_SPEED_ID =
            java.util.UUID.fromString("7c9f1a52-3d84-4b6e-9a11-8f2e5c7a4d13");

    private final CeNpcPatchProvider npcProvider;
    private float lastDesire = Float.NaN;
    private float ceAttackSpeedBase = 1.0F;
    private float ceAttackSpeedApplied = 1.0F;
    private int shortCounterRemaining;
    private CECombatBehaviors.Behavior<?> lastShortCounterBehavior;
    private CECombatBehaviors.Behavior<?> guardDecisionBehavior;
    private boolean guardDecisionKeep;
    private String livingMotionKey;
    private boolean clientGuardState;
    private final java.util.Set<java.util.UUID> musicSyncedPlayers = new java.util.HashSet<>();

    public CeNpcPatch(CeNpcPatchProvider provider) {
        super(provider);
        this.npcProvider = provider;
    }

    /**
     * 供 patch 分派判据使用：同一 patch 类可对应任意多份数据包 provider
     * （5 份 CE 测试包全是 CeNpcPatch），因此「类型相同」不足以判定可复用，
     * 必须比到 provider 实例。见 findings.md 第七轮验收「第二层根因」。
     */
    public CeNpcPatchProvider getNpcProvider() {
        return this.npcProvider;
    }

    @Override
    public void onConstructed(Mob entityIn) {
        this.original = entityIn;
        this.armature = this.npcProvider.armature.get().deepCopy();
        this.animator = yesman.epicfight.main.EpicFightSharedConstants.getAnimator(this);
        this.initAnimator(this.animator);
        this.animator.postInit();
        // CE 的 initAnimator 只装 7 条裸 biped living 动画；武器 living 动画由
        // modifyLivingMotionByCurrentItem 按 weaponLivingMotions 装配，而 CE 只在
        // onStartTracking / updateHeldItem 才调用它。CNPC 是运行时换 patch，
        // 两个时机都可能已经过去，必须在这里补一次，否则待机与战斗动画全部为空。
        applyCeLivingMotions();
    }

    @Override
    public void initAI() {
        super.initAI();
        applyBattleDesireAttackSpeed();
        lastDesire = com.goodbird.cnpcefaddon.common.BattleDesire.of(this.original);
    }

    @Override
    public void refreshCombatAI() {
        this.initAI();
    }

    @Override
    public void onStartTracking(ServerPlayer player) {
        super.onStartTracking(player);
        // CNPC replaces the EF capability after the entity is already tracked. CE's normal
        // tracking hook therefore ran before this patch existed, so its boss bar never got
        // the player added.
        this.ceBossEvent.addPlayer(player);
    }

    @Override
    public void onStopTracking(ServerPlayer player) {
        super.onStopTracking(player);
        this.ceBossEvent.removePlayer(player);
    }

    /** Removes CE client state before a datapack reload replaces this patch instance. */
    public void clearReloadState() {
        if (this.original == null || this.original.level().isClientSide()) {
            return;
        }
        for (ServerPlayer player : new java.util.ArrayList<>(this.ceBossEvent.getPlayers())) {
            boolean playing = musicIsPlaying();
            if (playing) {
                CEMusicNetworkHandler.sendRemoveMusicPacket(player, musicId(), true);
            }
            this.ceBossEvent.removePlayer(player);
        }
        // 血条本身也必须失效：CE 只在构造期 initBossBar 里 setVisible，
        // 旧实例若仍 visible，客户端 GUI 会继续渲染它。
        this.ceBossEvent.setVisible(false);
    }

    private net.shelmarow.combat_evolution.bgm.network.CEMusicPacket musicPacket() {
        try {
            var field = net.shelmarow.combat_evolution.ai.CEDatapackMobPatch.class
                    .getDeclaredField("music");
            field.setAccessible(true);
            return (net.shelmarow.combat_evolution.bgm.network.CEMusicPacket) field.get(this);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean musicIsPlaying() {
        try {
            var field = net.shelmarow.combat_evolution.ai.CEDatapackMobPatch.class
                    .getDeclaredField("shouldPlayBGM");
            field.setAccessible(true);
            return field.getBoolean(this);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private java.util.UUID musicId() {
        try {
            var field = net.shelmarow.combat_evolution.ai.CEDatapackMobPatch.class
                    .getDeclaredField("bgmUUID");
            field.setAccessible(true);
            return (java.util.UUID) field.get(this);
        } catch (Throwable ignored) {
            return java.util.UUID.randomUUID();
        }
    }

    /**
     * 按战斗欲望缩放攻速。用独立的 {@code AttributeModifier} 而非改基础值，
     * 保证数据包与装备提供的攻速不被覆盖，且重复调用幂等。
     */
    private void applyBattleDesireAttackSpeed() {
        try {
            var attr = this.original.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
            if (attr == null) {
                return;
            }
            attr.removeModifier(BATTLE_DESIRE_SPEED_ID);
            float factor = com.goodbird.cnpcefaddon.common.BattleDesire.attackSpeedFactor(
                    com.goodbird.cnpcefaddon.common.BattleDesire.of(this.original));
            if (factor != 1.0F) {
                attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        BATTLE_DESIRE_SPEED_ID, "cnpcef battle desire",
                        factor - 1.0F,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        } catch (Throwable ignored) {
            // 属性缺失时保持数据包原攻速
        }
    }

    /**
     * CE 用 {@code original instanceof RangedAttackMob} 判断远程单位并返回 null（源码 :483），
     * 但 CNPC 的 {@code EntityNPCInterface} 无条件实现 {@code RangedAttackMob}（为支持弓箭），
     * 于是近战 NPC 也被判成远程 → builder 恒为 null → 行为树永不挂载 → 无攻击动画、无伤害。
     *
     * <p>这里改按「实际手持物是否远程武器」判断，其余查表逻辑与 CE 完全一致。
     */
    @Override
    protected net.shelmarow.combat_evolution.ai.CECombatBehaviors.Builder<
            yesman.epicfight.world.capabilities.entitypatch.MobPatch<?>> getCustomWeaponMotionBuilder() {
        var itemCap = this.getHoldingItemCapability(net.minecraft.world.InteractionHand.MAIN_HAND);
        var byCategory = this.weaponAttackMotions.get(itemCap.getWeaponCategory());
        if (byCategory != null) {
            var style = itemCap.getStyle(this);
            var common = yesman.epicfight.world.capabilities.item.CapabilityItem.Styles.COMMON;
            if (byCategory.containsKey(style) || byCategory.containsKey(common)) {
                return byCategory.getOrDefault(style, byCategory.get(common));
            }
        }
        // 数据包没有为当前武器分类配置行为树时，一律不接管。
        //
        // 不能像 CE 原版那样回退 DefaultCombatBehavior.FIST：那是硬编码的三段僵尸拳
        // （Animations.ZOMBIE_ATTACK1/2/3）。CNPC 生成时通常空手（category=FIST），
        // 若此刻挂上僵尸拳行为树，它会立刻把僵尸攻击动画播到基础层并让 EntityState.inaction()
        // 持续为真；之后即使装备武器、CE 在 updateHeldItem 里重建行为树，被锁住的动画层也
        // 无法切到数据包配置的动画，表现为「无站姿、无攻击动画，但判定生效（有伤害和格挡）」。
        //
        // 返回 null 时 CE 的 setAIAsInfantry 不挂任何 Goal，NPC 保持 CNPC 原生 AI 与
        // EF 的普通 living 动画；等装备上数据包配置过的武器分类后，重建才会真正挂上行为树。
        return null;
    }

    /** 按当前手持武器装配 CE 的 living 动画；失败时退回 CE 默认动画，不使 patch 构造失败。 */
    public void applyCeLivingMotions() {
        try {
            String newKey = currentLivingMotionKey();
            if (!newKey.equals(livingMotionKey) || hasMissingBaseLivingMotion()) {
                this.modifyLivingMotionByCurrentItem(false);
            }

            // CE rebuilds the living-motion map from item modifiers and datapack overrides. A
            // CNPC can be patched before it has an item, so restore CE's base motions that the
            // rebuild legitimately omitted; otherwise empty-hand NPCs lose even idle/fall/death.
            addLivingMotionIfMissing(LivingMotions.IDLE, Animations.BIPED_IDLE);
            addLivingMotionIfMissing(LivingMotions.WALK, Animations.BIPED_WALK);
            addLivingMotionIfMissing(LivingMotions.RUN, Animations.BIPED_RUN);
            addLivingMotionIfMissing(LivingMotions.CHASE, Animations.BIPED_RUN);
            addLivingMotionIfMissing(LivingMotions.FALL, Animations.BIPED_FALL);
            addLivingMotionIfMissing(LivingMotions.MOUNT, Animations.BIPED_MOUNT);
            addLivingMotionIfMissing(LivingMotions.DEATH, Animations.BIPED_DEATH);
            addLivingMotionIfMissing(LivingMotions.BLOCK, Animations.SWORD_GUARD);
            livingMotionKey = newKey;
            syncCeLivingMotions();
        } catch (Throwable ignored) {
            // 缺武器 capability 等情况保持 CE 的裸 biped 动画
        }
    }

    private void addLivingMotionIfMissing(LivingMotion motion, AssetAccessor<? extends StaticAnimation> animation) {
        if (!hasLivingMotionMapping(motion)) {
            this.getAnimator().addLivingAnimation(motion, animation);
        }
    }

    /**
     * 判断某 living motion 是否已有映射，双端语义一致。
     *
     * <p>踩坑第 49 条：EF 的 {@code ClientAnimator} 覆写 {@code addLivingAnimation}，
     * 按动画的 {@code LayerType} 分流 —— composite 层动画（{@code SWORD_GUARD} 即是）
     * 进 {@code compositeLivingAnimations}，**永远不进 {@code livingAnimations}**。
     * 因此客户端用 {@code getLivingAnimation(BLOCK, null)} 判断恒为 null，
     * 导致每 tick 反复无效添加、格挡动画始终播不上（服务端基类不分流故正常）。
     *
     * <p>{@code getCompositeLivingMotion} 是纯 {@code map.get()}（EF 源码 :190-192），
     * 无 {@code getOrDefault} 回退，可安全用于判空。
     */
    private boolean hasLivingMotionMapping(LivingMotion motion) {
        if (this.getAnimator().getLivingAnimation(motion, null) != null) {
            return true;
        }
        if (!this.isLogicalClient()) {
            return false;
        }
        // 用反射取 composite 映射：CeNpcPatch 位于 common 区，
        // 而 getClientAnimator() / ClientAnimator 均为 @OnlyIn(Dist.CLIENT)，
        // 直接引用会让专用服务端在类校验阶段失败（约法第 22 条）。
        try {
            Object animator = this.getAnimator();
            var method = animator.getClass().getMethod("getCompositeLivingMotion", LivingMotion.class);
            return method.invoke(animator, motion) != null;
        } catch (Throwable ignored) {
            // 非 ClientAnimator 或 animator 尚未就绪：按「未映射」处理，下一 tick 会再试
            return false;
        }
    }

    private boolean hasMissingBaseLivingMotion() {
        // BLOCK 必须纳入：CE 的 modifyLivingMotionByCurrentItem 在 hasChange||forceChange 时
        // 执行 resetLivingAnimations() 后只重装 newLivingAnimations，而数据包多数不配 BLOCK
        // → BLOCK 映射被清空。
        // 该 reset 也可能由 CE 自己的 onStartTracking(forceChange=true) 触发，此时
        // livingMotionKey 未变，若不把 BLOCK 计入缺失判定就永远不会补回来 → 格挡动画永久丢失。
        return !hasLivingMotionMapping(LivingMotions.BLOCK)
                || this.getAnimator().getLivingAnimation(LivingMotions.IDLE, null) == null
                || this.getAnimator().getLivingAnimation(LivingMotions.WALK, null) == null
                || this.getAnimator().getLivingAnimation(LivingMotions.FALL, null) == null
                || this.getAnimator().getLivingAnimation(LivingMotions.DEATH, null) == null;
    }

    private void syncCeLivingMotions() {
        if (this.isLogicalClient() || this.original == null) {
            return;
        }
        yesman.epicfight.network.server.SPChangeLivingMotion packet =
                new yesman.epicfight.network.server.SPChangeLivingMotion(this.original.getId());
        packet.putEntries(this.getAnimator().getLivingAnimations().entrySet());
        yesman.epicfight.network.EpicFightNetworkManager.sendToAllPlayerTrackingThisEntity(packet, this.original);
    }

    private String currentLivingMotionKey() {
        var main = this.getHoldingItemCapability(net.minecraft.world.InteractionHand.MAIN_HAND);
        var off = this.getAdvancedHoldingItemCapability(net.minecraft.world.InteractionHand.OFF_HAND);
        return this.original.getMainHandItem().getItem() + ":" + main.getWeaponCategory() + ":"
                + main.getStyle(this) + ":" + this.original.getOffhandItem().getItem() + ":"
                + off.getWeaponCategory() + ":" + off.getStyle(this);
    }

    @Override
    public void updateHeldItem(yesman.epicfight.world.capabilities.item.CapabilityItem fromCap,
                               yesman.epicfight.world.capabilities.item.CapabilityItem toCap,
                               net.minecraft.world.item.ItemStack from,
                               net.minecraft.world.item.ItemStack to,
                               net.minecraft.world.InteractionHand hand) {
        super.updateHeldItem(fromCap, toCap, from, to, hand);
        applyCeLivingMotions();
        applyBattleDesireAttackSpeed();
    }

    @Override
    public net.minecraft.sounds.SoundEvent getWeaponHitSound(net.minecraft.world.InteractionHand hand) {
        var capability = this.getAdvancedHoldingItemCapability(hand);
        boolean fist = capability.getWeaponCategory()
                == yesman.epicfight.world.capabilities.item.CapabilityItem.WeaponCategories.FIST;
        // 空手沿用 CE/EF 默认音效，持械统一用刀刃命中声。
        return fist ? super.getWeaponHitSound(hand) : EpicFightSounds.BLADE_HIT.get();
    }

    @Override
    public boolean suppressNativeAttack() {
        return this.original != null && this.original.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof CEAnimationAttackGoal<?>);
    }

    @Override
    public OpenMatrix4f getModelMatrix(float partialTicks) {
        float npcScale = this.original instanceof EntityNPCInterface npc && npc.display != null
                ? npc.display.getSize() / 5.0F
                : 1.0F;
        return super.getModelMatrix(partialTicks).scale(npcScale, npcScale, npcScale);
    }

    @Override
    protected void selectGoalToRemove(java.util.Set<Goal> toRemove) {
        super.selectGoalToRemove(toRemove);
        // 只有 CE 确实会挂上自己的行为树时才让位；远程 NPC 的 builder 为 null，
        // 此时若移除 CNPC 原生攻击 AI，NPC 会两套 AI 都没有而彻底不攻击。
        if (this.getCustomWeaponMotionBuilder() == null) {
            return;
        }
        for (var wrapped : this.original.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof EntityAIAttackTarget) {
                toRemove.add(wrapped.getGoal());
            }
        }
    }

    /**
     * 按战斗欲望缩放 CE 行为树打出的伤害。数据包里的 {@code damageMultiplier} 不被改写，
     * 这里只在最终结算前乘上欲望系数（5.0 时为 1.0，等同不介入）。
     */
    @Override
    public yesman.epicfight.api.utils.AttackResult attack(
            yesman.epicfight.world.damagesource.EpicFightDamageSource damageSource,
            net.minecraft.world.entity.Entity target,
            net.minecraft.world.InteractionHand hand) {
        try {
            float factor = com.goodbird.cnpcefaddon.common.BattleDesire.damageFactor(
                    com.goodbird.cnpcefaddon.common.BattleDesire.of(this.original));
            if (factor != 1.0F) {
                damageSource.attachDamageModifier(
                        yesman.epicfight.api.utils.math.ValueModifier.multiplier(factor));
            }
        } catch (Throwable ignored) {
            // 任何意外都退回 CE 原始伤害
        }
        yesman.epicfight.api.utils.AttackResult result = super.attack(damageSource, target, hand);
        // hitSound 的唯一门是 attackResult.resultType.dealtDamage()。
        // 若这里全是 BLOCKED/MISSED，音效不响就与 getWeaponHitSound 无关。
        return result;
    }

    /**
     * 长格挡时长按战斗欲望缩放：欲望高的 NPC 不愿久守，会提前结束防御转入进攻。
     *
     * <p>放在 patch 自己的 tick 里而不是 mixin CE 的行为树选取方法 —— CE 是可选依赖，
     * 若 mixin 直接 target CE 的类，未安装 CE 的用户会因目标类缺失而启动崩溃
     * （同踩坑第 42 条：只崩别人机器的那类问题）。本类只在 CE 存在时才被加载，天然安全。
     */
    private void applyLongGuardDesire() {
        try {
            float desire = com.goodbird.cnpcefaddon.common.BattleDesire.of(this.original);

            // 5.0 为基准，完全不介入 CE 原始防御时长。
            if (desire == com.goodbird.cnpcefaddon.common.BattleDesire.DEFAULT) {
                return;
            }

            for (var wrapped : this.original.goalSelector.getAvailableGoals()) {
                if (!(wrapped.getGoal() instanceof net.shelmarow.combat_evolution.ai.goal.CEAnimationAttackGoal<?> goal)) {
                    continue;
                }

                var current = goal.getCombatBehaviors().getCurrentBehavior();
            if (current == null) {
                if (CEPatchUtils.isGuard(this) && !CEPatchUtils.isInCounter(this)) {
                    stopGuardAnimation();
                    CEPatchUtils.setGuard(this, false);
                    guardDecisionBehavior = null;
                }
                return;
            }

                var type = current.getType();
                boolean isLongGuard =
                        type == net.shelmarow.combat_evolution.ai.CECombatBehaviors.BehaviorType.GUARD
                                || type == net.shelmarow.combat_evolution.ai.CECombatBehaviors.BehaviorType.GUARD_WANDER;

                if (!isLongGuard) {
                    return;
                }

                // 5.0 is deliberately untouched. Outside the midpoint, the configured chance
                // is the actual per-tick chance to keep guarding (0.0=3/4, 10.0=1/5).
                float keep = com.goodbird.cnpcefaddon.common.BattleDesire.longGuardChance(desire);
                if (guardDecisionBehavior != current) {
                    guardDecisionBehavior = current;
                    guardDecisionKeep = this.original.getRandom().nextFloat() < keep;
                }
                if (!guardDecisionKeep) {
                    finishBehavior(goal, current);
                }
                return;
            }
        } catch (Throwable ignored) {
            // 任何意外都退回 CE 原始防御时长
        }
    }

    /**
     * 短格挡穿插：格挡受击后按战斗欲望概率立刻插入 1~3 次攻击。
     *
     * <p>欲望 5.0 及以下概率为 0，完全走 CE 原逻辑；10.0 时概率 0.8、穿插 3 次。
     * 实现方式是清掉当前的防御行为，让行为树在下一 tick 立即重选进攻节点 ——
     * 不自造动画、不改 CE 的反击流程。
     */
    @Override
    public void onGuardHit(net.minecraft.world.damagesource.DamageSource damageSource) {
        super.onGuardHit(damageSource);

        try {
            float desire = com.goodbird.cnpcefaddon.common.BattleDesire.of(this.original);
            float chance = com.goodbird.cnpcefaddon.common.BattleDesire.shortGuardCounterChance(desire);

            if (chance <= 0.0F || this.original.getRandom().nextFloat() >= chance) {
                return;
            }

            int hits = com.goodbird.cnpcefaddon.common.BattleDesire.shortGuardCounterHits(desire);
            if (hits <= 0) {
                return;
            }

            // Explicitly leave the guard when the desire roll selected a short counter. CE's
            // own counter animation is separate from an attack behavior and otherwise leaves
            // the NPC defending forever instead of inserting the requested 1-3 attacks.
            for (var wrapped : this.original.goalSelector.getAvailableGoals()) {
                if (wrapped.getGoal() instanceof CEAnimationAttackGoal<?> goal) {
                    var current = goal.getCombatBehaviors().getCurrentBehavior();
                    if (current != null) {
                        current.resetCooldown();
                        finishBehavior(goal, current);
                    }
                    shortCounterRemaining = hits;
                    lastShortCounterBehavior = null;
                    break;
                }
            }
        } catch (Throwable ignored) {
            // 任何意外都退回 CE 原始格挡行为
        }
    }

    @Override
    public void updateMotion(boolean considerInaction) {
        // CE 与 EF 都以 deltaY < -0.55 判 FALL；复用既有护栏，避免新 CE patch 漏掉已修逻辑。
        GroundedFallFix.clearStaleFallVelocity(this);
        super.updateMotion(considerInaction);
        try {
            // CE's guard is a synchronized living animation. Its stock updateMotion leaves the
            // living motion at IDLE, so the client animator immediately replaces the guard with
            // idle even though the server guard flag is true. BLOCK is a base-layer living
            // motion, not a composite-only motion.
            if (CEPatchUtils.isGuard(this) && !CEPatchUtils.isInCounter(this)) {
                this.currentLivingMotion = LivingMotions.BLOCK;
                this.currentCompositeMotion = LivingMotions.BLOCK;
            }
        } catch (Throwable ignored) {
            // Keep CE's normal motion selection if its optional synced state is unavailable.
        }
    }

    @Override
    public void tick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        clearStaleGuardState();
        if (this.original != null) {
            // AnimationPlayer.tick runs inside super.tick(). CE reads this synced value there,
            // so applying the desire multiplier only after super.tick() is one frame too late.
            applyCeAnimationSpeed(com.goodbird.cnpcefaddon.common.BattleDesire.of(this.original));
        }
        super.tick(event);
        if (this.original == null) {
            return;
        }

        float desire = com.goodbird.cnpcefaddon.common.BattleDesire.of(this.original);
        if (Float.isNaN(lastDesire) || desire != lastDesire) {
            applyBattleDesireAttackSpeed();
            initAI();
            guardDecisionBehavior = null;
            lastDesire = desire;
        }
        if (this.isLogicalClient()) {
            applyCeLivingMotions();
        }
        applyCeAnimationSpeed(desire);
        ensureBossBarTracking();
        clearStaleGuardState();
        syncClientGuardAnimation();
        advanceShortCounter();
        // 服务端无目标时无需再算防御时长；客户端始终执行，以便本地解除残留的格挡状态。
        if (this.getTarget() == null && !this.original.level().isClientSide()) {
            return;
        }
        applyLongGuardDesire();
    }

    private void ensureBossBarTracking() {
        if (this.original.level().isClientSide()) {
            return;
        }
        if (!this.ceBossEvent.isVisible()) {
            return;
        }
        if (!(this.original.level() instanceof ServerLevel level)) {
            return;
        }

        double range = 64.0D * 64.0D;
        boolean hasTarget = this.getTarget() != null;
        if (!hasTarget) {
            musicSyncedPlayers.clear();
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this.original) <= range
                    && !this.ceBossEvent.getPlayers().contains(player)) {
                // onStartTracking also sends an already-active CE music request. A CNPC patch
                // can be created after vanilla tracking has fired, and adding the player directly
                // would otherwise leave BGM silent until the next tracking transition.
                onStartTracking(player);
            }
            if (hasTarget && this.ceBossEvent.getPlayers().contains(player)
                    && musicSyncedPlayers.add(player.getUUID())) {
                // The patch may have been created after the player's tracking callback. Re-run
                // the CE hook once so an already active BGM is delivered to that client.
                onStartTracking(player);
            }
        }
    }

    private void syncClientGuardAnimation() {
        if (!this.isLogicalClient()) {
            return;
        }

        boolean guarding = CEPatchUtils.isGuard(this) && !CEPatchUtils.isInCounter(this);
        AssetAccessor<? extends StaticAnimation> guard = this.getAnimator()
                .getLivingAnimation(LivingMotions.BLOCK, Animations.SWORD_GUARD);

        if (!guarding) {
            if (clientGuardState && guard != null) {
                this.getAnimator().stopPlaying(guard);
            }
            clientGuardState = false;
            return;
        }

        if (guard == null) {
            return;
        }

        var player = this.getAnimator().getPlayerFor(null);
        if (player == null || player.getAnimation().isEmpty()) {
            this.getAnimator().playAnimation(guard, 0.0F);
            clientGuardState = true;
            return;
        }

        var current = player.getAnimation().get().getRealAnimation();
        var idle = this.getAnimator().getLivingAnimation(LivingMotions.IDLE, Animations.BIPED_IDLE);
        boolean alreadyGuard = current == guard;
        boolean canReplace = current == idle || player.isEnd();
        // 基础层若正播别的未结束动画（受击、CE 反击、攻击连段），本帧不抢占，下一帧再试。
        if (!alreadyGuard && canReplace) {
            this.getAnimator().playAnimation(guard, 0.0F);
        }
        // clientGuardState 表示「格挡状态成立」，不表示动画已播上；
        // !guarding 分支依赖它来决定是否 stopPlaying，故此处无条件置 true。
        clientGuardState = true;
    }

    private void finishBehavior(CEAnimationAttackGoal<?> goal, CECombatBehaviors.Behavior<?> behavior) {
        ((CECombatBehaviors.Behavior) behavior).stopGuardAndWander(this);
        goal.clearCurrentBehavior(behavior);
        if (guardDecisionBehavior == behavior) {
            guardDecisionBehavior = null;
        }
    }

    private void clearStaleGuardState() {
        try {
            CEAnimationAttackGoal<?> goal = findAttackGoal();
            CECombatBehaviors.Behavior<?> current = goal == null ? null : goal.getCombatBehaviors().getCurrentBehavior();
            boolean currentGuard = current != null
                    && (current.getType() == CECombatBehaviors.BehaviorType.GUARD
                    || current.getType() == CECombatBehaviors.BehaviorType.GUARD_WANDER);

            if (currentGuard && !this.isLogicalClient() && this.getTarget() == null) {
                finishBehavior(goal, current);
                current = null;
            } else if (currentGuard && !CEPatchUtils.isGuard(this) && !CEPatchUtils.isInCounter(this)) {
                finishBehavior(goal, current);
                current = null;
            }

            if (!this.isLogicalClient() && this.getTarget() == null) {
                stopGuardAnimation();
                CEPatchUtils.setGuard(this, false);
                CEPatchUtils.setInCounter(this, false);
            } else if (isGuardAnimationPlaying() && !CEPatchUtils.isGuard(this) && !CEPatchUtils.isInCounter(this)) {
                stopGuardAnimation();
            }
        } catch (Throwable ignored) {
            // Optional CE state must never break the normal NPC tick.
        }
    }

    private void stopGuardAnimation() {
        AssetAccessor<? extends StaticAnimation> guard =
                this.getAnimator().getLivingAnimation(LivingMotions.BLOCK, Animations.SWORD_GUARD);
        if (guard == null) {
            return;
        }
        if (this.isLogicalClient()) {
            this.getAnimator().stopPlaying(guard);
        } else {
            this.stopPlaying(guard);
        }
    }

    private boolean isGuardAnimationPlaying() {
        try {
            var player = this.getAnimator().getPlayerFor(null);
            if (player == null || player.getAnimation().isEmpty()) {
                return false;
            }
            var current = player.getAnimation().get().getRealAnimation();
            var guard = this.getAnimator().getLivingAnimation(LivingMotions.BLOCK, Animations.SWORD_GUARD);
            return current == guard;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private CEAnimationAttackGoal<?> findAttackGoal() {
        for (var wrapped : this.original.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof CEAnimationAttackGoal<?> goal) {
                return goal;
            }
        }
        return null;
    }

    private void advanceShortCounter() {
        if (shortCounterRemaining <= 0) {
            return;
        }
        CEAnimationAttackGoal<?> goal = findAttackGoal();
        if (goal == null) {
            shortCounterRemaining = 0;
            return;
        }
        CECombatBehaviors.Behavior<?> current = goal.getCombatBehaviors().getCurrentBehavior();
        if (current == null) {
            return;
        }
        if (current.getType() == CECombatBehaviors.BehaviorType.GUARD
                || current.getType() == CECombatBehaviors.BehaviorType.GUARD_WANDER) {
            if (!CEPatchUtils.isInCounter(this)) {
                // Put this guard root on its normal cooldown before clearing it. Otherwise a
                // high-priority guard root can be selected again immediately and starve the
                // short counter indefinitely.
                current.resetCooldown();
                finishBehavior(goal, current);
            }
            return;
        }
        if ((current.getType() == CECombatBehaviors.BehaviorType.ANIMATION
                || current.getType() == CECombatBehaviors.BehaviorType.CUSTOM)
                && current != lastShortCounterBehavior) {
            lastShortCounterBehavior = current;
            if (--shortCounterRemaining <= 0) {
                lastShortCounterBehavior = null;
            }
        }
    }

    /**
     * 战斗欲望对<b>动画速度</b>的缩放已统一收口到 {@code MixinAttackAnimationSpeed}
     * （{@code AttackAnimation.getPlaySpeed}），此处不再重复缩放 CE 自己的
     * {@code ILivingEntityData.attackSpeed}：两处并存会让 CE NPC 被乘两遍
     * （欲望 0 得到 0.25 倍而非 0.5 倍）。
     *
     * <p>本方法保留为空实现，只把 CE 的 attackSpeed 恢复成 CE/JSON 原值，
     * 以便旧存档里已被上一版本改写过的运行时状态回到基准；欲望对<b>攻击频率</b>的缩放
     * 仍由 {@link #applyBattleDesireAttackSpeed()} 通过 {@code ATTACK_SPEED} 属性负责。
     */
    private void applyCeAnimationSpeed(float desire) {
        try {
            ILivingEntityData data = (ILivingEntityData) (Object) this;
            if (!data.combat_evolution$getCanModifySpeed()) {
                ceAttackSpeedBase = 1.0F;
                ceAttackSpeedApplied = 1.0F;
                return;
            }
            // 仅在本 patch 曾写入过缩放值时还原，避免覆盖 CE 行为树/JSON 自己设定的速度。
            float current = data.combat_evolution$getAttackSpeed();
            if (ceAttackSpeedApplied != 0.0F
                    && Math.abs(current - ceAttackSpeedApplied) < 0.0001F
                    && Math.abs(ceAttackSpeedBase - current) > 0.0001F) {
                data.combat_evolution$setAttackSpeed(ceAttackSpeedBase);
                ceAttackSpeedApplied = ceAttackSpeedBase;
            }
        } catch (Throwable ignored) {
            // CE animation playback keeps its JSON speed if the optional state is unavailable.
        }
    }
}
