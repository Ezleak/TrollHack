package cum.xiaro.trollhack.gui.rgui.windows

import cum.xiaro.trollhack.util.extension.fastCeil
import cum.xiaro.trollhack.util.extension.fastFloor
import cum.xiaro.trollhack.util.extension.sumOfFloat
import cum.xiaro.trollhack.gui.AbstractTrollGui
import cum.xiaro.trollhack.gui.rgui.Component
import cum.xiaro.trollhack.gui.rgui.InteractiveComponent
import cum.xiaro.trollhack.module.modules.client.GuiSetting
import cum.xiaro.trollhack.util.TickTimer
import cum.xiaro.trollhack.util.graphics.GlStateUtils
import cum.xiaro.trollhack.util.math.vector.Vec2f
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11.*
import kotlin.math.max
import kotlin.math.min

open class ListWindow(
    name: CharSequence,
    posX: Float,
    posY: Float,
    width: Float,
    height: Float,
    saveToConfig: SettingGroup,
    vararg childrenIn: Component
) : TitledWindow(name, posX, posY, width, height, saveToConfig) {
    val children = ArrayList<Component>()

    override val minWidth = 80.0f
    override val minHeight = 200.0f
    override val maxWidth = 200.0f
    override val maxHeight get() = mc.displayHeight.toFloat()
    override val resizable: Boolean get() = hoveredChild == null

    private val lineSpace = 3.0f
    var hoveredChild: Component? = null
        private set(value) {
            if (value == field) return
            (field as? InteractiveComponent)?.onLeave(AbstractTrollGui.getRealMousePos())
            (value as? InteractiveComponent)?.onHover(AbstractTrollGui.getRealMousePos())
            field = value
        }

    private val scrollTimer = TickTimer()
    private var scrollSpeed = 0.0f

    private var scrollProgress = 0.0f
        set(value) {
            prevScrollProgress = field
            field = value
        }
    private var prevScrollProgress = 0.0f
    private val renderScrollProgress
        get() = prevScrollProgress + (scrollProgress - prevScrollProgress) * mc.renderPartialTicks

    private var doubleClickTime = -1L

    init {
        children.addAll(childrenIn)
        updateChild()
    }

    private fun updateChild() {
        var y = (if (draggableHeight != height) draggableHeight else 0.0f) + lineSpace
        for (child in children) {
            if (!child.visible) continue
            child.posX = lineSpace * 1.618f
            child.posY = y
            child.width = width - lineSpace * 3.236f
            y += child.height + lineSpace
        }
    }

    override fun onDisplayed() {
        super.onDisplayed()
        for (child in children) child.onDisplayed()
    }

    override fun onClosed() {
        super.onClosed()
        for (child in children) child.onClosed()
    }

    override fun onGuiInit() {
        super.onGuiInit()
        for (child in children) child.onGuiInit()
        updateChild()
    }

    override fun onResize() {
        super.onResize()
        updateChild()
    }

    override fun onTick() {
        super.onTick()
        if (children.isEmpty()) return
        val lastVisible = children.lastOrNull { it.visible }
        val maxScrollProgress = lastVisible?.let { max(it.posY + it.height + lineSpace - height, 0.01f) }
            ?: draggableHeight

        scrollProgress = (scrollProgress + scrollSpeed)
        scrollSpeed *= 0.5f
        if (scrollTimer.tick(100L)) {
            if (scrollProgress < 0.0) {
                scrollSpeed = scrollProgress * -0.25f
            } else if (scrollProgress > maxScrollProgress) {
                scrollSpeed = (scrollProgress - maxScrollProgress) * -0.25f
            }
        }

        updateChild()
        for (child in children) child.onTick()
    }

    override fun onRender(absolutePos: Vec2f) {
        super.onRender(absolutePos)

        if (renderMinimizeProgress != 0.0f) {
            renderChildren {
                it.onRender(absolutePos.plus(it.renderPosX, it.renderPosY - renderScrollProgress))
            }
        }
    }

    override fun onPostRender(absolutePos: Vec2f) {
        super.onPostRender(absolutePos)

        if (renderMinimizeProgress != 0.0f) {
            renderChildren {
                it.onPostRender(absolutePos.plus(it.renderPosX, it.renderPosY - renderScrollProgress))
            }
        }
    }

    private fun renderChildren(renderBlock: (Component) -> Unit) {
        GlStateUtils.scissor(
            ((renderPosX + lineSpace * 1.618) * GuiSetting.scaleFactor - 0.5f).fastFloor(),
            mc.displayHeight - ((renderPosY + renderHeight) * GuiSetting.scaleFactor - 0.5f).fastFloor(),
            ((renderWidth - lineSpace * 3.236) * GuiSetting.scaleFactor + 1.0f).fastCeil(),
            ((renderHeight - draggableHeight) * GuiSetting.scaleFactor).fastCeil()
        )
        glEnable(GL_SCISSOR_TEST)
        glTranslatef(0.0f, -renderScrollProgress, 0.0f)

        mc.profiler.startSection("childrens")
        for (child in children) {
            if (!child.visible) continue
            if (child.renderPosY + child.renderHeight - renderScrollProgress < draggableHeight) continue
            if (child.renderPosY - renderScrollProgress > renderHeight) continue
            glPushMatrix()
            glTranslatef(child.renderPosX, child.renderPosY, 0.0f)
            renderBlock(child)
            glPopMatrix()
        }
        mc.profiler.endSection()

        glDisable(GL_SCISSOR_TEST)
    }

    override fun onMouseInput(mousePos: Vec2f) {
        super.onMouseInput(mousePos)
        val relativeMousePos = mousePos.minus(posX, posY - renderScrollProgress)
        if (Mouse.getEventDWheel() != 0) {
            scrollTimer.reset()
            scrollSpeed -= Mouse.getEventDWheel() * 0.1f
            updateHovered(relativeMousePos)
        }
        if (mouseState != MouseState.DRAG) {
            updateHovered(relativeMousePos)
        }
        if (!minimized) (hoveredChild as? InteractiveComponent)?.let {
            it.onMouseInput(getRelativeMousePos(mousePos, it))
        }
    }

    private fun updateHovered(relativeMousePos: Vec2f) {
        hoveredChild = if (relativeMousePos.y < draggableHeight || relativeMousePos.x < lineSpace || relativeMousePos.x > renderWidth - lineSpace) null
        else children.firstOrNull { it.visible && relativeMousePos.y in it.posY..it.posY + it.height }
    }

    override fun onLeave(mousePos: Vec2f) {
        super.onLeave(mousePos)
        hoveredChild = null
    }

    override fun onClick(mousePos: Vec2f, buttonId: Int) {
        super.onClick(mousePos, buttonId)

        handleDoubleClick(mousePos, buttonId)

        if (!minimized) (hoveredChild as? InteractiveComponent)?.let {
            it.onClick(getRelativeMousePos(mousePos, it), buttonId)
        }
    }

    override fun onRelease(mousePos: Vec2f, buttonId: Int) {
        super.onRelease(mousePos, buttonId)
        if (!minimized) (hoveredChild as? InteractiveComponent)?.let {
            it.onRelease(getRelativeMousePos(mousePos, it), buttonId)
        }
    }

    override fun onDrag(mousePos: Vec2f, clickPos: Vec2f, buttonId: Int) {
        super.onDrag(mousePos, clickPos, buttonId)
        if (!minimized) (hoveredChild as? InteractiveComponent)?.let {
            it.onDrag(getRelativeMousePos(mousePos, it), clickPos, buttonId)
        }
    }

    override fun onKeyInput(keyCode: Int, keyState: Boolean) {
        super.onKeyInput(keyCode, keyState)
        if (!minimized) (hoveredChild as? InteractiveComponent)?.onKeyInput(keyCode, keyState)
    }

    private fun handleDoubleClick(mousePos: Vec2f, buttonId: Int) {
        if (!visible || buttonId != 0 || mousePos.y - posY >= draggableHeight) {
            doubleClickTime = -1L
            return
        }

        val currentTime = System.currentTimeMillis()

        doubleClickTime = if (currentTime - doubleClickTime > 500L) {
            currentTime
        } else {
            val sum = children.filter(Component::visible).sumOfFloat { it.height + lineSpace }
            val targetHeight = max(height, sum + draggableHeight + lineSpace)
            val maxHeight = scaledDisplayHeight - 2.0f

            height = min(targetHeight, scaledDisplayHeight - 2.0f)
            posY = min(posY, maxHeight - targetHeight)

            -1L
        }
    }

    private fun getRelativeMousePos(mousePos: Vec2f, component: InteractiveComponent) =
        mousePos.minus(posX, posY - renderScrollProgress).minus(component.posX, component.posY)
}