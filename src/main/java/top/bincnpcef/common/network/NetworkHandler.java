package top.bincnpcef.common.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SPDatapackSync.TYPE, SPDatapackSync.STREAM_CODEC, SPDatapackSync::handle);
    }
}
