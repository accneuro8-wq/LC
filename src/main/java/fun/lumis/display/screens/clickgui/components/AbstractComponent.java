package fun.lumis.display.screens.clickgui.components;

import fun.lumis.utils.display.interfaces.QuickImports;
import fun.lumis.utils.client.interfaces.ResizableMovable;

public abstract class AbstractComponent implements Component, QuickImports, ResizableMovable {
    public float x, y, width, height;

    // Поля для логики перетаскивания
    protected boolean dragging;
    protected float dragX, dragY;

    public double scroll = 0;
    public double smoothedScroll = 0;

    @Override
    public ResizableMovable position(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    @Override
    public ResizableMovable size(float width, float height) {
        this.width = width;
        this.height = height;
        return this;
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHover(mouseX, mouseY) && button == 0) {
            this.dragging = true;
            this.dragX = (float) (x - mouseX);
            this.dragY = (float) (y - mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragging = false;
        return false;
    }
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.dragging) {
            this.x = (float) (mouseX + dragX);
            this.y = (float) (mouseY + dragY);
            return true;
        }
        return false;
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) { return false; }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    @Override
    public boolean charTyped(char chr, int modifiers) { return false; }
}