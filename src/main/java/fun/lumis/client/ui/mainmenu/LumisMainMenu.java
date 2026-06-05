package fun.lumis.client.ui.mainmenu;

import fun.lumis.api.QClient;
import fun.lumis.api.utils.animation.AnimationUtils;
import fun.lumis.api.utils.animation.Easings;
import fun.lumis.api.utils.render.RenderUtils;
import fun.lumis.api.utils.render.fonts.msdf.Font;
import fun.lumis.api.utils.render.fonts.msdf.Fonts;
import fun.lumis.client.ui.altmanager.AltManagerScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Главное меню Lumis: фоновое изображение во весь экран и колонка кнопок по центру
 * (SinglePlayer, MultiPlayer, AltManager, Settings, Quit).
 */
public class LumisMainMenu extends Screen implements QClient {

    private static final Identifier BACKGROUND_TEXTURE = Identifier.of("lumis", "textures/mainmenu/mainmenu.png");

    private static final int TEXT = 0xFFFFFFFF;
    private static final int SUBTEXT = 0xFF9A9AA8;
    private static final int BTN_BG = 0xFF181820;
    private static final int BTN_BG_HOVER = 0xFF24202F;
    private static final int BORDER = 0x1AFFFFFF;

    private static final float BTN_W = 214f;
    private static final float BTN_H = 40f;
    private static final float BTN_GAP = 12f;

    private final List<MenuButton> buttons = new ArrayList<>();

    public LumisMainMenu() {
        super(Text.literal("Lumis"));
        buttons.add(new MenuButton("SinglePlayer", () -> mc.setScreen(new SelectWorldScreen(this))));
        buttons.add(new MenuButton("MultiPlayer", () -> mc.setScreen(new MultiplayerScreen(this))));
        buttons.add(new MenuButton("AltManager", () -> mc.setScreen(new AltManagerScreen(this))));
        buttons.add(new MenuButton("Settings", () -> mc.setScreen(new OptionsScreen(this, mc.options))));
        buttons.add(new MenuButton("Quit", () -> mc.scheduleStop()));
    }

    private Font font(int size) {
        return Fonts.getFont("sf_regular", size);
    }

    private void layoutButtons() {
        float totalH = buttons.size() * BTN_H + (buttons.size() - 1) * BTN_GAP;
        float startY = (height - totalH) / 2f;
        float x = (width - BTN_W) / 2f;
        for (int i = 0; i < buttons.size(); i++) {
            MenuButton b = buttons.get(i);
            b.x = x;
            b.y = startY + i * (BTN_H + BTN_GAP);
            b.w = BTN_W;
            b.h = BTN_H;
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Фон рисуется в render().
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack ms = context.getMatrices();
        layoutButtons();

        // Фоновое изображение на весь экран
        RenderUtils.drawImage(ms, BACKGROUND_TEXTURE, 0, 0, width, height, 0xFFFFFFFF);

        // Затемнение поверх фона для читаемости кнопок
        RenderUtils.drawGradientRect(ms, 0, 0, width, height, 0x88000000, 0x88000000);

        // Кнопки по центру
        for (MenuButton b : buttons) {
            renderButton(ms, b, mouseX, mouseY, delta);
        }

        // Анимированная приветственная надпись над колонкой кнопок
        renderGreeting(ms);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderGreeting(MatrixStack ms) {
        String name = (mc != null && mc.getSession() != null) ? mc.getSession().getUsername() : null;
        if (name == null || name.isEmpty()) name = "Player";

        String text = "Glad to see you, " + name;
        Font f = font(26);
        if (f == null) return;

        float x = (width - f.getStringWidth(text)) / 2f;
        float topButtonY = buttons.isEmpty() ? height / 2f : buttons.get(0).y;
        float y = topButtonY - 44f;

        // Бегущий градиент: фиолетовый -> бирюзовый
        f.drawAnimatedGradientStringHorizontal(ms, text, x, y, 0xFF7D5CFF, 0xFF15C79B, 1.0f);
    }

    private void renderButton(MatrixStack ms, MenuButton b, int mouseX, int mouseY, float delta) {
        boolean hovered = mouseX >= b.x && mouseX <= b.x + b.w && mouseY >= b.y && mouseY <= b.y + b.h;
        b.hover.update(hovered ? 1f : 0f);
        float h = b.hover.getValue();

        float lift = h * 4f;
        float bx = b.x;
        float by = b.y - lift;

        int bg = mixColor(BTN_BG, BTN_BG_HOVER, h);
        RenderUtils.drawShadow(ms, bx, by, b.w, b.h, 10f, 14f, (int) (0x40 * h) << 24);
        RenderUtils.drawRoundedRect(ms, bx, by, b.w, b.h, 10f, bg);
        RenderUtils.drawRoundedRectOutline(ms, bx, by, b.w, b.h, 10f, 1f, BORDER, BORDER, BORDER, BORDER);

        int textColor = mixColor(0xFFCFCFE0, TEXT, h);
        Font f = font(17);
        drawV(f, ms, b.label, bx + 18f, by + b.h / 2f, textColor);
    }

    private void drawV(Font f, MatrixStack ms, String text, float x, float centerY, int color) {
        if (f == null) return;
        f.drawString(ms, text, x, centerY - f.getHeight() / 4f, color);
    }

    private static int mixColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ca = (int) (aa + (ba - aa) * t);
        int cr = (int) (ar + (br - ar) * t);
        int cg = (int) (ag + (bg - ag) * t);
        int cb = (int) (ab + (bb - ab) * t);
        return (ca << 24) | (cr << 16) | (cg << 8) | cb;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            layoutButtons();
            for (MenuButton b : buttons) {
                float lift = b.hover.getValue() * 4f;
                float by = b.y - lift;
                if (mouseX >= b.x && mouseX <= b.x + b.w && mouseY >= by && mouseY <= by + b.h) {
                    b.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static final class MenuButton {
        final String label;
        final Runnable action;
        final AnimationUtils hover = new AnimationUtils(0f, 12f, Easings.CUBIC_OUT);
        float x, y, w, h;

        MenuButton(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }
}
