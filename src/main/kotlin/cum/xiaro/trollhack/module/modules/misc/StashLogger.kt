package cum.xiaro.trollhack.module.modules.misc

import cum.xiaro.trollhack.util.extension.synchronized
import cum.xiaro.trollhack.event.events.TickEvent
import cum.xiaro.trollhack.event.safeListener
import cum.xiaro.trollhack.manager.managers.WaypointManager
import cum.xiaro.trollhack.module.Category
import cum.xiaro.trollhack.module.Module
import cum.xiaro.trollhack.module.modules.movement.AutoWalk
import cum.xiaro.trollhack.util.BaritoneUtils
import cum.xiaro.trollhack.util.TickTimer
import cum.xiaro.trollhack.util.TimeUnit
import cum.xiaro.trollhack.util.atTrue
import cum.xiaro.trollhack.util.math.CoordinateConverter.asString
import cum.xiaro.trollhack.util.text.MessageSendUtils
import cum.xiaro.trollhack.util.threads.defaultScope
import cum.xiaro.trollhack.util.threads.onMainThread
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.init.SoundEvents
import net.minecraft.tileentity.*
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import kotlin.math.roundToInt

internal object StashLogger : Module(
    name = "StashLogger",
    category = Category.MISC,
    description = "Logs storage units in render distance."
) {
    private val saveToWaypoints by setting("Save To Waypoints", true)
    private val logToChat by setting("Log To Chat", true)
    private val playSound by setting("Play Sound", true)
    private val logChests0 = setting("Chests", true)
    private val logChests by logChests0
    private val chestDensity by setting("Min Chests", 5, 1..20, 1, logChests0.atTrue())
    private val logShulkers0 = setting("Shulkers", true)
    private val logShulkers by logShulkers0
    private val shulkerDensity by setting("Min Shulkers", 1, 1..20, 1, logShulkers0.atTrue())
    private val logDroppers0 = setting("Droppers", true)
    private val logDroppers by logDroppers0
    private val dropperDensity by setting("Min Droppers", 5, 1..20, 1, logDroppers0.atTrue())
    private val logDispensers0 = setting("Dispensers", true)
    private val logDispensers by logDispensers0
    private val dispenserDensity by setting("Min Dispensers", 5, 1..20, 1, logDispensers0.atTrue())
    private val logHoppers0 = setting("Hoppers", true)
    private val logHoppers by logHoppers0
    private val hopperDensity by setting("Min Hoppers", 5, 1..20, 1, logHoppers0.atTrue())
    private val disableAutoWalk by setting("Disable Auto Walk", false, description = "Disables AutoWalk when a stash is found")
    private val cancelBaritone by setting("Cancel Baritone", false, description = "Cancels Baritone when a stash is found")

    private val chunkData = LinkedHashMap<Long, ChunkStats>()
    private val knownPositions = HashSet<BlockPos>()
    private val timer = TickTimer(TimeUnit.SECONDS)

    init {
        safeListener<TickEvent.Post> {
            if (!timer.tickAndReset(3L)) return@safeListener

            defaultScope.launch {
                coroutineScope {
                    launch {
                        world.loadedTileEntityList.toList().forEach(::logTileEntity)
                        notification()
                    }
                }
            }
        }
    }

    private suspend fun notification() {
        var found = false

        for (chunkStats in chunkData.values) {
            if (!chunkStats.hot) continue

            chunkStats.hot = false
            val center = chunkStats.center()
            val string = chunkStats.toString()

            if (saveToWaypoints) {
                WaypointManager.add(center, string)
            }

            if (playSound) {
                onMainThread {
                    mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                }
            }

            if (logToChat) {
                val positionString = center.asString()
                MessageSendUtils.sendNoSpamChatMessage("$chatName $positionString $string")
            }

            found = found || true
        }

        if (found) {
            if (disableAutoWalk && AutoWalk.isEnabled) AutoWalk.disable()
            if (cancelBaritone && (BaritoneUtils.isPathing || BaritoneUtils.isActive)) BaritoneUtils.cancelEverything()
        }
    }

    private fun logTileEntity(tileEntity: TileEntity) {
        if (!checkTileEntityType(tileEntity)) return
        if (!knownPositions.add(tileEntity.pos)) return

        val chunk = ChunkPos.asLong(tileEntity.pos.x shr 4, tileEntity.pos.z shr 4)
        val chunkStats = chunkData.getOrPut(chunk, ::ChunkStats)

        chunkStats.add(tileEntity)
    }

    private fun checkTileEntityType(tileEntity: TileEntity) =
        logChests && tileEntity is TileEntityChest
            || logShulkers && tileEntity is TileEntityShulkerBox
            || logDroppers && tileEntity is TileEntityDropper
            || logDispensers && tileEntity is TileEntityDispenser
            || logHoppers && tileEntity is TileEntityHopper

    private class ChunkStats {
        var chests = 0; private set
        var shulkers = 0; private set
        var droppers = 0; private set
        var dispensers = 0; private set
        var hoppers = 0; private set

        var hot = false

        private val tileEntities = ArrayList<TileEntity>().synchronized()

        fun add(tileEntity: TileEntity) {
            when (tileEntity) {
                is TileEntityChest -> chests++
                is TileEntityShulkerBox -> shulkers++
                is TileEntityDropper -> droppers++
                is TileEntityDispenser -> dispensers++
                is TileEntityHopper -> hoppers++
                else -> return
            }

            tileEntities.add(tileEntity)

            if (chests >= chestDensity
                || shulkers >= shulkerDensity
                || droppers >= dropperDensity
                || dispensers >= dispenserDensity
                || hoppers >= hopperDensity) {
                hot = true
            }
        }

        fun center(): BlockPos {
            var x = 0.0
            var y = 0.0
            var z = 0.0
            val size = tileEntities.size

            for (tileEntity in tileEntities) {
                x += tileEntity.pos.x
                y += tileEntity.pos.y
                z += tileEntity.pos.z
            }

            x /= size
            y /= size
            z /= size

            return BlockPos(x.roundToInt(), y.roundToInt(), z.roundToInt())
        }

        override fun toString(): String {
            return "($chests chests, $shulkers shulkers, $droppers droppers, $dispensers dispensers, $hoppers hoppers)"
        }
    }
}
