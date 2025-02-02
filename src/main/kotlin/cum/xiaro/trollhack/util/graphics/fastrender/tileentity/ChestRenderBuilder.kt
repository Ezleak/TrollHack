package cum.xiaro.trollhack.util.graphics.fastrender.tileentity

import cum.xiaro.trollhack.util.graphics.fastrender.model.Model
import cum.xiaro.trollhack.util.graphics.fastrender.model.tileentity.ModelChest
import cum.xiaro.trollhack.util.graphics.fastrender.model.tileentity.ModelLargeChest
import cum.xiaro.trollhack.util.interfaces.Helper
import net.minecraft.block.BlockChest
import net.minecraft.client.renderer.GLAllocation
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.ITextureObject
import net.minecraft.client.renderer.texture.TextureUtil
import net.minecraft.tileentity.TileEntityChest
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL33.glVertexAttribDivisor
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.util.*

class ChestRenderBuilder(
    builtPosX: Double,
    builtPosY: Double,
    builtPosZ: Double
) : ITileEntityRenderBuilder<TileEntityChest> {
    private val smallChest = SmallChestRenderBuilder(builtPosX, builtPosY, builtPosZ)
    private val largeChest = LargeChestRenderBuilder(builtPosX, builtPosY, builtPosZ)

    override fun add(tileEntity: TileEntityChest) {
        if (tileEntity.hasWorld()) {
            val block = tileEntity.blockType

            if (block is BlockChest && tileEntity.blockMetadata == 0) {
                block.checkForSurroundingChests(tileEntity.world, tileEntity.pos, tileEntity.world.getBlockState(tileEntity.pos))
            }
        }

        tileEntity.checkForAdjacentChests()

        if (tileEntity.adjacentChestXNeg == null && tileEntity.adjacentChestZNeg == null) {
            if (tileEntity.adjacentChestXPos == null && tileEntity.adjacentChestZPos == null) {
                smallChest.add(tileEntity)
            } else {
                largeChest.add(tileEntity)
            }
        }
    }

    override fun build() {
        smallChest.build()
        largeChest.build()
    }

    override fun upload(): ITileEntityRenderBuilder.Renderer {
        return Renderer(smallChest.upload(), largeChest.upload())
    }

    private class Renderer(
        private val smallChest: ITileEntityRenderBuilder.Renderer,
        private val largeChest: ITileEntityRenderBuilder.Renderer
    ) : ITileEntityRenderBuilder.Renderer {
        override fun render(renderPosX: Double, renderPosY: Double, renderPosZ: Double) {
            smallChest.render(renderPosX, renderPosY, renderPosZ)
            largeChest.render(renderPosX, renderPosY, renderPosZ)
        }

        override fun destroy() {
            smallChest.destroy()
            largeChest.destroy()
        }
    }

    open class SmallChestRenderBuilder(
        builtPosX: Double,
        builtPosY: Double,
        builtPosZ: Double
    ) : AbstractTileEntityRenderBuilder<TileEntityChest>(builtPosX, builtPosY, builtPosZ) {
        override fun add(tileEntity: TileEntityChest) {
            val pos = tileEntity.pos
            val posX = (pos.x + 0.5 - builtPosX).toFloat()
            val posY = (pos.y - builtPosY).toFloat()
            val posZ = (pos.z + 0.5 - builtPosZ).toFloat()

            floatArrayList.add(posX)
            floatArrayList.add(posY)
            floatArrayList.add(posZ)

            putTileEntityLightMapUV(tileEntity)

            when (getTileEntityBlockMetadata(tileEntity)) {
                2 -> {
                    byteArrayList.add(2)
                }
                4 -> {
                    byteArrayList.add(1)
                }
                5 -> {
                    byteArrayList.add(-1)
                }
                else -> {
                    byteArrayList.add(0)
                }
            }

            when {
                isChristmas -> {
                    byteArrayList.add(2)
                }
                tileEntity.chestType == BlockChest.Type.TRAP -> {
                    byteArrayList.add(1)
                }
                else -> {
                    byteArrayList.add(0)
                }
            }

            shortArrayList.add((tileEntity.prevLidAngle * 65535.0f).toInt().toShort())
            shortArrayList.add((tileEntity.lidAngle * 65535.0f).toInt().toShort())

            size++
        }

        override fun uploadBuffer(buffer: ByteBuffer): AbstractTileEntityRenderBuilder.Renderer {
            return upload(buffer, model, Texture.instance.texture.glTextureId)
        }

        protected fun upload(buffer: ByteBuffer, model: Model, texture: Int): AbstractTileEntityRenderBuilder.Renderer {
            val vaoID = glGenVertexArrays()
            val vboID = glGenBuffers()

            glBindBuffer(GL_ARRAY_BUFFER, vboID)
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STREAM_DRAW)

            glBindVertexArray(vaoID)

            glVertexAttribPointer(4, 3, GL_FLOAT, false, 20, 0L) // 12
            glVertexAttribPointer(5, 2, GL_UNSIGNED_BYTE, true, 20, 12L) // 2

            glVertexAttribIPointer(6, 1, GL_BYTE, 20, 14L) // 1
            glVertexAttribIPointer(7, 1, GL_UNSIGNED_BYTE, 20, 15L) // 1
            glVertexAttribPointer(8, 1, GL_UNSIGNED_SHORT, true, 20, 16L) // 2
            glVertexAttribPointer(9, 1, GL_UNSIGNED_SHORT, true, 20, 18L) // 2

            glVertexAttribDivisor(4, 1)
            glVertexAttribDivisor(5, 1)
            glVertexAttribDivisor(6, 1)
            glVertexAttribDivisor(7, 1)
            glVertexAttribDivisor(8, 1)
            glVertexAttribDivisor(9, 1)

            model.attachVBO()

            glEnableVertexAttribArray(4)
            glEnableVertexAttribArray(5)
            glEnableVertexAttribArray(6)
            glEnableVertexAttribArray(7)
            glEnableVertexAttribArray(8)
            glEnableVertexAttribArray(9)

            glBindBuffer(GL_ARRAY_BUFFER, 0)
            glBindVertexArray(0)

            return Renderer(shader, vaoID, vboID, model.modelSize, size, builtPosX, builtPosY, builtPosZ, texture)
        }

        override fun buildBuffer(): ByteBuffer {
            val buffer = GLAllocation.createDirectByteBuffer(size * 20)

            var floatIndex = 0
            var shortIndex = 0
            var byteIndex = 0

            for (i in 0 until size) {
                buffer.putFloat(floatArrayList.getFloat(floatIndex++))
                buffer.putFloat(floatArrayList.getFloat(floatIndex++))
                buffer.putFloat(floatArrayList.getFloat(floatIndex++))
                buffer.put(byteArrayList.getByte(byteIndex++))
                buffer.put(byteArrayList.getByte(byteIndex++))
                buffer.put(byteArrayList.getByte(byteIndex++))
                buffer.put(byteArrayList.getByte(byteIndex++))
                buffer.putShort(shortArrayList.getShort(shortIndex++))
                buffer.putShort(shortArrayList.getShort(shortIndex++))
            }

            buffer.flip()

            return buffer
        }

        private class Renderer(
            shader: Shader,
            vaoID: Int,
            vboID: Int,
            modelSize: Int,
            size: Int,
            builtPosX: Double,
            builtPosY: Double,
            builtPosZ: Double,
            private val texture: Int
        ) : AbstractTileEntityRenderBuilder.Renderer(shader, vaoID, vboID, modelSize, size, builtPosX, builtPosY, builtPosZ), Helper {
            override fun preRender() {
                GlStateManager.bindTexture(texture)
            }

            override fun postRender() {
                GlStateManager.bindTexture(0)
            }
        }

        protected companion object {
            private val model = ModelChest().apply { init() }

            val shader = Shader("/assets/trollhack/shaders/tileentity/Chest.vsh", "/assets/trollhack/shaders/tileentity/Default.fsh")

            val isChristmas = Calendar.getInstance().let { calendar ->
                calendar[2] + 1 == 12 && calendar[5] >= 24 && calendar[5] <= 26
            }
        }

        private class Texture private constructor(
            val hash: Int
        ) : Helper {
            val texture: ITextureObject

            init {
                val images = textures.map {
                    val iResource = mc.resourceManager.getResource(it)
                    TextureUtil.readBufferedImage(iResource.inputStream)
                }

                val firstImage = images[0]
                val size = firstImage.height
                val finalImage = BufferedImage(firstImage.width, size * 4, firstImage.type)
                val graphics = finalImage.createGraphics()

                for (i in images.indices) {
                    val src = images[i]
                    graphics.drawImage(src, 0, size * i, null)
                }

                graphics.dispose()

                texture = DynamicTexture(finalImage)
            }

            companion object : Helper {
                private val textures = arrayOf(
                    ResourceLocation("textures/entity/chest/normal.png"),
                    ResourceLocation("textures/entity/chest/trapped.png"),
                    ResourceLocation("textures/entity/chest/christmas.png")
                )

                var instance = Texture(getTextureHash())
                    private set
                    get() {
                        val newHash = getTextureHash()
                        if (newHash != field.hash) {
                            glDeleteTextures(field.texture.glTextureId)
                            field = Texture(newHash)
                        }

                        return field
                    }

                private fun getTextureHash(): Int {
                    var result = 1

                    for (element in textures) {
                        val textureObject = mc.renderEngine.getTexture(element)
                        @Suppress("UNNECESSARY_SAFE_CALL")
                        result = 31 * result + (textureObject?.hashCode() ?: 0)
                    }

                    return result
                }
            }
        }
    }

    class LargeChestRenderBuilder(
        builtPosX: Double,
        builtPosY: Double,
        builtPosZ: Double
    ) : SmallChestRenderBuilder(builtPosX, builtPosY, builtPosZ) {
        override fun add(tileEntity: TileEntityChest) {
            val pos = tileEntity.pos
            var posX = (pos.x + 0.5 - builtPosX).toFloat()
            val posY = (pos.y - builtPosY).toFloat()
            var posZ = (pos.z + 0.5 - builtPosZ).toFloat()

            if (tileEntity.adjacentChestZPos != null) posZ += 0.5f else posX += 0.5f

            floatArrayList.add(posX)
            floatArrayList.add(posY)
            floatArrayList.add(posZ)

            putTileEntityLightMapUV(tileEntity)

            when (getTileEntityBlockMetadata(tileEntity)) {
                2 -> {
                    byteArrayList.add(2)
                }
                4 -> {
                    byteArrayList.add(1)
                }
                5 -> {
                    byteArrayList.add(-1)
                }
                else -> {
                    byteArrayList.add(0)
                }
            }

            when {
                isChristmas -> {
                    byteArrayList.add(2)
                }
                tileEntity.chestType == BlockChest.Type.TRAP -> {
                    byteArrayList.add(1)
                }
                else -> {
                    byteArrayList.add(0)
                }
            }

            shortArrayList.add((tileEntity.prevLidAngle * 65535.0f).toInt().toShort())
            shortArrayList.add((tileEntity.lidAngle * 65535.0f).toInt().toShort())

            size++
        }

        override fun uploadBuffer(buffer: ByteBuffer): AbstractTileEntityRenderBuilder.Renderer {
            return upload(buffer, model, Texture.instance.texture.glTextureId)
        }

        private companion object {
            val model = ModelLargeChest().apply { init() }
        }

        private class Texture private constructor(
            val hash: Int
        ) : Helper {
            val texture: ITextureObject

            init {
                val images = textures.map {
                    val iResource = mc.resourceManager.getResource(it)
                    TextureUtil.readBufferedImage(iResource.inputStream)
                }

                val firstImage = images[0]
                val size = firstImage.height
                val finalImage = BufferedImage(firstImage.width, size * 4, firstImage.type)
                val graphics = finalImage.createGraphics()

                for (i in images.indices) {
                    val src = images[i]
                    graphics.drawImage(src, 0, size * i, null)
                }

                graphics.dispose()

                texture = DynamicTexture(finalImage)
            }

            companion object : Helper {
                private val textures = arrayOf(
                    ResourceLocation("textures/entity/chest/normal_double.png"),
                    ResourceLocation("textures/entity/chest/trapped_double.png"),
                    ResourceLocation("textures/entity/chest/christmas_double.png")
                )

                var instance = Texture(getTextureHash())
                    private set
                    get() {
                        val newHash = getTextureHash()
                        if (newHash != field.hash) {
                            glDeleteTextures(field.texture.glTextureId)
                            field = Texture(newHash)
                        }

                        return field
                    }

                private fun getTextureHash(): Int {
                    var result = 1

                    for (element in textures) {
                        val textureObject = mc.renderEngine.getTexture(element)
                        @Suppress("UNNECESSARY_SAFE_CALL")
                        result = 31 * result + (textureObject?.hashCode() ?: 0)
                    }

                    return result
                }
            }
        }
    }
}