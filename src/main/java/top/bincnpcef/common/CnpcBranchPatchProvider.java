package top.bincnpcef.common;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.bincnpcef.api.IDataDisplay;
import yesman.epicfight.api.data.reloader.MobPatchReloadListener;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;

import java.util.HashMap;
import java.util.Map;

public class CnpcBranchPatchProvider extends MobPatchReloadListener.AbstractMobPatchProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("CNPC-EF-Addon");
    private static final ResourceLocation FORCED_ARMATURE = ResourceLocation.parse("epicfight:entity/biped");

    private final Map<ResourceLocation, MobPatchReloadListener.AbstractMobPatchProvider> providers = new HashMap<>();

    public void addProvider(ResourceLocation rl, MobPatchReloadListener.AbstractMobPatchProvider provider) {
        providers.put(rl, provider);
    }

    public void clear() {
        providers.clear();
    }

    public boolean hasProvider(ResourceLocation rl) {
        return providers.containsKey(rl);
    }

    @Override
    public EntityPatch<?> get(Entity entity) {
        if (entity instanceof EntityNPCInterface npc) {
            ResourceLocation efModel = ((IDataDisplay) (Object) npc.display).cnpcef$getEFModel();
            if (efModel != null) {
                MobPatchReloadListener.AbstractMobPatchProvider provider = providers.get(efModel);
                if (provider != null) {
                    EntityPatch<?> patch = provider.get(entity);
                    // EF 的 readData 只在 Entity.load 时被调用（从存档加载）。
                    // 新建的 NPC 不会调用 readData，导致 initAttributes 不执行，
                    // IMPACT/ARMOR_NEGATION 等属性保持默认值（0.5/0.0）。
                    // 主动调用 readData 确保属性被正确设置。
                    if (patch != null && NpcPatchReloadListener.TAGMAP.containsKey(efModel)) {
                        CompoundTag tag = NpcPatchReloadListener.TAGMAP.get(efModel).copy();
                        tag.putString("armature", FORCED_ARMATURE.toString());
                        try {
                            patch.readData(tag);
                        } catch (Exception e) {
                            LOGGER.error("CnpcBranchPatchProvider: readData failed for entity={}, efModel={}",
                                npc.getName().getString(), efModel, e);
                        }
                    }
                    return patch;
                }
            }
        }
        return null;
    }
}
