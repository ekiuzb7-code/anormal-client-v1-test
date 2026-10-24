package com.anormal.client.module.impl.player;

import com.anormal.client.lucid.StringSetting;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * Rewrites your own nametag: a custom nickname plus a fake rank, visible only to you.
 *
 * <p><b>Why it draws the tag itself.</b> Vanilla places the label from the entity render state,
 * which is only rebuilt while the entity passes the visibility check - so at running or elytra
 * speed the plate lags a tick behind the head and reads as sliding / delayed. The tag is therefore
 * drawn in the HUD pass from {@code getLerpedPos(tickDelta)}, recomputed every frame, so it stays
 * locked to the head at any speed. Vanilla's own tag is suppressed for the local player so the two
 * never overlap.</p>
 */
public class NameShower extends Module {

    public final StringSetting nickname = new StringSetting("Nickname",
            "The name shown above your head (empty = your real name)", "");

    public final StringSetting rank = new StringSetting("Rank",
            "Rank text, e.g. MVP+ / VIP / OWNER", "MVP+");

    public final BooleanSetting showNametag = new BooleanSetting("Show Nametag",
            "Master switch for the tag above your head", true);

    public final BooleanSetting showRank = new BooleanSetting("Show Rank",
            "Include the rank in the nametag", true);

    public final ModeSetting rankStyle = new ModeSetting("Rank Style",
            "How the rank is laid out around the name", "[Rank] Name",
            "[Rank] Name", "Rank | Name", "Name [Rank]", "Rank Name");

    public final BooleanSetting boldRank = new BooleanSetting("Bold Rank",
            "Draw the rank in bold", true);

    public final ColorSetting nameColor = new ColorSetting("Name Color",
            "Colour of your nickname", ColorUtils.rgba(255, 255, 255, 255));

    public final ColorSetting rankColor = new ColorSetting("Rank Color",
            "Colour of the rank text", ColorUtils.rgba(255, 200, 0, 255));

    public final NumberSetting size = new NumberSetting("Size",
            "Scale of the tag", 1.0, 0.5, 2.5, 0.05);

    public final BooleanSetting background = new BooleanSetting("Background",
            "Dark plate behind the text", true);

    public final BooleanSetting shadow = new BooleanSetting("Text Shadow",
            "Draw a drop shadow under the text", true);

    public final NumberSetting heightOffset = new NumberSetting("Height Offset",
            "How far above the head the tag floats", 0.4, 0.0, 1.5, 0.05);

    private static NameShower instance;

    public NameShower() {
        super("NameShower",
                "Shows a custom nickname and a fake rank above your own character",
                Category.PLAYER);
        addSetting(nickname);
        addSetting(rank);
        addSetting(showNametag);
        addSetting(showRank);
        addSetting(rankStyle);
        addSetting(boldRank);
        addSetting(nameColor);
        addSetting(rankColor);
        addSetting(size);
        addSetting(background);
        addSetting(shadow);
        addSetting(heightOffset);
        instance = this;
    }

    public static NameShower getInstance() {
        return instance;
    }

    /**
     * Builds the tag for the local player.
     *
     * @return the text to draw, or {@code null} when there is nothing to show
     */
    public static Text buildLabel(PlayerEntity player) {
        NameShower module = instance;
        if (module == null || !module.isEnabled()) return null;
        if (!module.showNametag.isEnabled()) return null;

        String nick = module.nickname.get();
        if (nick == null || nick.isBlank()) {
            nick = player.getGameProfile().name();
        }
        if (nick == null || nick.isBlank()) return null;

        MutableText nameText = Text.literal(nick)
                .styled(style -> style.withColor(module.nameColor.getColor()));

        String rankText = module.rank.get();
        if (!module.showRank.isEnabled() || rankText == null || rankText.isBlank()) {
            return nameText;
        }

        MutableText rankPart = Text.literal(rankText)
                .styled(style -> style
                        .withColor(module.rankColor.getColor())
                        .withBold(module.boldRank.isEnabled()));

        MutableText bracketOpen = Text.literal("[").styled(s -> s.withColor(module.rankColor.getColor()));
        MutableText bracketClose = Text.literal("]").styled(s -> s.withColor(module.rankColor.getColor()));
        MutableText space = Text.literal(" ");

        return switch (module.rankStyle.getValue()) {
            case "Rank | Name" -> Text.empty()
                    .append(rankPart)
                    .append(Text.literal(" | ").styled(s -> s.withColor(0x808080)))
                    .append(nameText);
            case "Name [Rank]" -> Text.empty()
                    .append(nameText)
                    .append(space)
                    .append(bracketOpen)
                    .append(rankPart)
                    .append(bracketClose);
            case "Rank Name" -> Text.empty()
                    .append(rankPart)
                    .append(space)
                    .append(nameText);
            default -> Text.empty()
                    .append(bracketOpen)
                    .append(rankPart)
                    .append(bracketClose)
                    .append(space)
                    .append(nameText);
        };
    }

    /** True when the HUD pass owns the tag, so vanilla's own label must stay hidden. */
    public static boolean drawsOwnTag() {
        NameShower module = instance;
        return module != null && module.isEnabled() && module.showNametag.isEnabled();
    }

    // ------------------------------------------------------------------
    //  HUD pass - recomputed from the interpolated position every frame
    // ------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext ctx, float tickDelta) {
        if (mc.player == null || !isEnabled() || !showNametag.isEnabled()) return;

        // In first person the camera sits inside your own head - nothing to label.
        if (mc.options.getPerspective().isFirstPerson()) return;

        Text label = buildLabel(mc.player);
        if (label == null || label.getString().isEmpty()) return;

        Vec3d anchor = ProjectionUtil.lerpPos(mc.player, tickDelta)
                .add(0.0, mc.player.getHeight() + heightOffset.getValue(), 0.0);

        int[] screen = ProjectionUtil.project(anchor, tickDelta);
        if (screen == null) return;

        // Behind the camera is already filtered by project(); this drops the points that land
        // far outside the window (looking away, extreme FOV) so no label is painted off-screen.
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        if (screen[0] < -64 || screen[0] > screenW + 64
                || screen[1] < -64 || screen[1] > screenH + 64) {
            return;
        }

        var matrices = ctx.getMatrices();
        float scale = size.getValue().floatValue();

        int textWidth = mc.textRenderer.getWidth(label);
        int textHeight = mc.textRenderer.fontHeight;

        // Centre on the anchor by half the SCALED width, so the tag does not drift sideways
        // when the model turns or the text changes length.
        int drawX = screen[0] - Math.round(textWidth * scale / 2.0f);
        int drawY = screen[1] - Math.round(textHeight * scale);

        matrices.pushMatrix();
        matrices.translate(drawX, drawY);
        matrices.scale(scale, scale);

        if (background.isEnabled()) {
            int pad = 2;
            ctx.fill(-pad, -pad, textWidth + pad, textHeight + pad, 0x66000000);
        }

        ctx.drawText(mc.textRenderer, label, 0, 0, 0xFFFFFFFF, shadow.isEnabled());

        matrices.popMatrix();
    }
}
