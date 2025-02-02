package cum.xiaro.trollhack.module.modules.player

import cum.xiaro.trollhack.event.events.PacketEvent
import cum.xiaro.trollhack.event.listener
import cum.xiaro.trollhack.module.Category
import cum.xiaro.trollhack.module.Module
import cum.xiaro.trollhack.util.atFalse
import net.minecraft.network.play.client.*

internal object PacketCancel : Module(
    name = "PacketCancel",
    description = "Cancels specific packets used for various actions",
    category = Category.PLAYER
) {
    private val all0 = setting("All", false)
    private val all by all0
    private val packetInput by setting("CPacket Input", true, all0.atFalse())
    private val packetPlayer by setting("CPacket Player", true, all0.atFalse())
    private val packetEntityAction by setting("CPacket Entity Action", true, all0.atFalse())
    private val packetUseEntity by setting("CPacket Use Entity", true, all0.atFalse())
    private val packetVehicleMove by setting("CPacket Vehicle Move", true, all0.atFalse())

    private var numPackets = 0

    override fun getHudInfo(): String {
        return numPackets.toString()
    }

    init {
        listener<PacketEvent.Send> {
            if (all
                || it.packet is CPacketInput && packetInput
                || it.packet is CPacketPlayer && packetPlayer
                || it.packet is CPacketEntityAction && packetEntityAction
                || it.packet is CPacketUseEntity && packetUseEntity
                || it.packet is CPacketVehicleMove && packetVehicleMove
            ) {
                it.cancel()
                numPackets++
            }
        }

        onDisable {
            numPackets = 0
        }
    }
}