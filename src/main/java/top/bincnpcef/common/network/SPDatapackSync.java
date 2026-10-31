package top.bincnpcef.common.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.bincnpcef.common.NpcPatchReloadListener;
import top.bincnpcef.client.render.RenderStorage;

import java.util.ArrayList;
import java.util.List;

public record SPDatapackSync(List<ResourceLocation> modelIds, List<CompoundTag> tags) implements CustomPacketPayload {
    public SPDatapackSync {
        if (modelIds.size() != tags.size()) throw new IllegalArgumentException("Mismatched NPC model/tag lists");
        modelIds = List.copyOf(modelIds);
        tags = tags.stream().map(CompoundTag::copy).toList();
    }
    public static final Type<SPDatapackSync> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("cnpcef", "datapack_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SPDatapackSync> STREAM_CODEC = StreamCodec.of(
        SPDatapackSync::write,
        SPDatapackSync::read
    );

    private static void write(RegistryFriendlyByteBuf buf, SPDatapackSync msg) {
        buf.writeInt(msg.modelIds.size());
        for (int i = 0; i < msg.modelIds.size(); i++) {
            buf.writeResourceLocation(msg.modelIds.get(i));
            buf.writeNbt(msg.tags.get(i));
        }
    }

    private static SPDatapackSync read(RegistryFriendlyByteBuf buf) {
        int count = buf.readInt();
        if (count < 0 || count > buf.readableBytes()) throw new IllegalArgumentException("Invalid NPC model count");
        List<ResourceLocation> ids = new ArrayList<>();
        List<CompoundTag> tags = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(buf.readResourceLocation());
            CompoundTag tag = buf.readNbt();
            if (tag == null) throw new IllegalArgumentException("Missing NPC model NBT");
            tags.add(tag);
        }
        return new SPDatapackSync(ids, tags);
    }

    public static void handle(SPDatapackSync msg, IPayloadContext context) {
        // 在客户端主线程处理
        context.enqueueWork(() -> {
            NpcPatchReloadListener.processServerPacket(msg.modelIds, msg.tags);
            RenderStorage.registerRenderers(NpcPatchReloadListener.clientTags());
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
