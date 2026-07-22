package top.bincnpcef.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.bincnpcef.common.NpcPatchReloadListener;

import java.util.ArrayList;
import java.util.List;

public record SPDatapackSync(List<ResourceLocation> modelIds, List<CompoundTag> tags) implements CustomPacketPayload {
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
        List<ResourceLocation> ids = new ArrayList<>();
        List<CompoundTag> tags = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(buf.readResourceLocation());
            tags.add(buf.readNbt());
        }
        return new SPDatapackSync(ids, tags);
    }

    public static void handle(SPDatapackSync msg, IPayloadContext context) {
        // 在客户端主线程处理
        Minecraft.getInstance().execute(() -> {
            NpcPatchReloadListener.processServerPacket(msg.modelIds, msg.tags);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
