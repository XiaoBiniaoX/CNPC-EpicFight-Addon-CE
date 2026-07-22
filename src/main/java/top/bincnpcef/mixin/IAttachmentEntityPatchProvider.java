package top.bincnpcef.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;
import yesman.epicfight.world.capabilities.provider.AttachmentEntityPatchProvider;

@Mixin(AttachmentEntityPatchProvider.class)
public interface IAttachmentEntityPatchProvider {
    @Accessor("entitypatch")
    void cnpcef$setEntityPatch(EntityPatch<?> patch);
}
