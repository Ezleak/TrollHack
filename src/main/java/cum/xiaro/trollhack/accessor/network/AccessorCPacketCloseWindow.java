package cum.xiaro.trollhack.accessor.network;

import net.minecraft.network.play.client.CPacketCloseWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CPacketCloseWindow.class)
public interface AccessorCPacketCloseWindow {

    @Accessor("windowId")
    int trollGetWindowID();

}
