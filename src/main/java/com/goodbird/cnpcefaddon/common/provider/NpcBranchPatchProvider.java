package com.goodbird.cnpcefaddon.common.provider;

import com.goodbird.cnpcefaddon.common.ResLocPredicate;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import yesman.epicfight.api.data.reloader.MobPatchReloadListener;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NpcBranchPatchProvider extends MobPatchReloadListener.AbstractMobPatchProvider {
    /** 未标注归属的条目（客户端同步表、临时表）统一记为该 owner。 */
    public static final String OWNER_UNKNOWN = "unknown";

    protected List<Pair<ResLocPredicate, MobPatchReloadListener.AbstractMobPatchProvider>> providers = Lists.newArrayList();
    /**
     * 每个资源键当前条目的归属 reloader。
     *
     * <p>三个 reloader（普通 / 高级 / CE）共写本注册表，各自只按自己的 `OWNED_KEYS` 撤回。
     * 但旧实现的 {@code removeProvider} 只按键删除，无法辨认「该键当前的值是不是自己写的」：
     * 同名键被后跑的 reloader 覆盖后，先跑者下一轮仍会把它删掉，于是那个键在本轮
     * 重新注册前一直缺失，若后跑者本轮解析失败就永久消失。加 owner 后删除是幂等且定向的。
     */
    protected final Map<ResourceLocation, String> owners = new HashMap<>();
    protected MobPatchReloadListener.AbstractMobPatchProvider defaultProvider;
    public NpcBranchPatchProvider(){
        defaultProvider = new MobPatchReloadListener.NullPatchProvider();
    }

    public EntityPatch<?> get(Entity entity) {
        for(Pair<ResLocPredicate, MobPatchReloadListener.AbstractMobPatchProvider> pair : providers){
            if(pair.getFirst().predicate(entity)){
                return pair.getSecond().get(entity);
            }
        }
        return this.defaultProvider.get(entity);
    }

    public void addProvider(ResourceLocation resLoc, MobPatchReloadListener.AbstractMobPatchProvider newProv){
        addProvider(resLoc, newProv, OWNER_UNKNOWN);
    }

    /** 覆盖语义与旧版一致（同键后写覆盖），只是额外记录归属。 */
    public void addProvider(ResourceLocation resLoc, MobPatchReloadListener.AbstractMobPatchProvider newProv, String owner){
        providers.removeIf(pair -> resLoc.equals(pair.getFirst().resourceLocation));
        providers.add(new Pair<>(new ResLocPredicate(resLoc), newProv));
        owners.put(resLoc, owner == null ? OWNER_UNKNOWN : owner);
    }

    /** Retracts a single patch, used by reload listeners that co-own this registry. */
    public void removeProvider(ResourceLocation resLoc){
        providers.removeIf(pair -> resLoc.equals(pair.getFirst().resourceLocation));
        owners.remove(resLoc);
    }

    /**
     * 只在该键当前仍归 {@code owner} 时撤回，避免删掉别的 reloader 后写的同名条目。
     *
     * @return 是否真的删除了条目
     */
    public boolean removeProviderIfOwnedBy(ResourceLocation resLoc, String owner){
        String current = owners.get(resLoc);
        if (current == null || !current.equals(owner)) {
            return false;
        }
        providers.removeIf(pair -> resLoc.equals(pair.getFirst().resourceLocation));
        owners.remove(resLoc);
        return true;
    }

    /** 该键当前的归属，未注册时返回 {@code null}。 */
    public String getOwner(ResourceLocation resLoc){
        return owners.get(resLoc);
    }

    public void clear(){
        providers.clear();
        owners.clear();
    }

    public void resetProviders(List<Pair<ResLocPredicate, MobPatchReloadListener.AbstractMobPatchProvider>> newProviders) {
        resetProviders(newProviders, OWNER_UNKNOWN);
    }

    /**
     * 整体换入一份新表。防御性复制：旧实现直接接管调用方的 List，调用方之后再改动
     * 那个列表就会绕过提交路径改到共享注册表。
     */
    public void resetProviders(List<Pair<ResLocPredicate, MobPatchReloadListener.AbstractMobPatchProvider>> newProviders, String owner) {
        this.providers = Lists.newArrayList(newProviders);
        this.owners.clear();
        String resolved = owner == null ? OWNER_UNKNOWN : owner;
        for (Pair<ResLocPredicate, MobPatchReloadListener.AbstractMobPatchProvider> pair : this.providers) {
            this.owners.put(pair.getFirst().resourceLocation, resolved);
        }
    }

    public List<Pair<ResLocPredicate, MobPatchReloadListener.AbstractMobPatchProvider>> getProviders() {
        return this.providers;
    }
}
