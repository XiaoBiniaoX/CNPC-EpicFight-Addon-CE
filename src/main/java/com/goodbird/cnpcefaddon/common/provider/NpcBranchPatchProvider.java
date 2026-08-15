package com.goodbird.cnpcefaddon.common.provider;

import com.goodbird.cnpcefaddon.common.ResLocPredicate;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import yesman.epicfight.api.data.reloader.MobPatchReloadListener;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;
import java.util.List;

public class NpcBranchPatchProvider extends MobPatchReloadListener.AbstractMobPatchProvider {
    protected List<Pair<ResLocPredicate, MobPatchReloadListener.AbstractMobPatchProvider>> providers = Lists.newArrayList();
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
        providers.removeIf(pair -> resLoc.equals(pair.getFirst().resourceLocation));
        providers.add(new Pair<>(new ResLocPredicate(resLoc), newProv));
    }

    /** Retracts a single patch, used by reload listeners that co-own this registry. */
    public void removeProvider(ResourceLocation resLoc){
        providers.removeIf(pair -> resLoc.equals(pair.getFirst().resourceLocation));
    }

    public void clear(){
        providers.clear();
    }

    public void resetProviders(List<Pair<ResLocPredicate, MobPatchReloadListener.AbstractMobPatchProvider>> newProviders) {
        this.providers = newProviders;
    }

    public List<Pair<ResLocPredicate, MobPatchReloadListener.AbstractMobPatchProvider>> getProviders() {
        return this.providers;
    }
}
