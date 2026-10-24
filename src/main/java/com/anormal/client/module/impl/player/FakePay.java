package com.anormal.client.module.impl.player;

import com.anormal.client.lucid.LucidClient;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * FakePay — DonutSMP {@code /pay} spoofer (client-side, cosmetic only).
 *
 * <h3>What it does</h3>
 * When enabled on a DonutSMP server, sending {@code /pay <target> <amount>}
 * is intercepted <b>before it reaches the server</b> and silently cancelled.
 * A fake payment confirmation is then injected into the local chat HUD
 * (and optionally the action bar) so it looks like the payment went through,
 * but no money ever leaves your balance and nothing is sent to the server.
 *
 * <h3>How it works (glazed-style trigger + validation)</h3>
 * Uses Fabric's {@code fabric-message-api-v1} {@link
 * net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents}:
 *   - {@code ALLOW_COMMAND} returns {@code false} for a valid {@code /pay},
 *     which prevents the packet from being sent.
 *   - The command is tokenised ({@code trim()} + split on whitespace) and must
 *     read {@code pay <player> <amount>} — the literal {@code "pay"} is matched
 *     case-insensitively. The amount must parse as a real positive money value
 *     (see {@link #parseMoney}); anything malformed (bare {@code /pay}, a
 *     missing amount, a non-numeric or zero/negative amount) is <b>passed
 *     through untouched</b> so the server answers it with its own usage/error
 *     message, exactly as a real failed payment would.
 *
 * <h3>Message format</h3>
 * Mimics glazed's DonutSMP payment line rather than a bare EssentialsX string —
 * white text with a green dollar sign, amount abbreviated like a scoreboard
 * balance line ({@code 1.5k}, {@code 2.25M}, {@code 3B}):
 * <pre>
 *   §fYou paid &lt;target&gt; §a$§f &lt;amount&gt;
 * </pre>
 * {@link Formatting#WHITE} and {@link Formatting#GREEN} are the exact RGB
 * values glazed uses (0xFFFFFF / 0x55FF55). Optionally the same line is also
 * mirrored to the action bar above the hotbar, and the arrow-hit "ding"
 * ({@code ENTITY_ARROW_HIT_PLAYER}, the hit-marker sound vanilla plays when
 * your arrow strikes another player) is played at full volume/pitch.
 *
 * <h3>Money parsing / formatting</h3>
 * Ported from glazed's {@code MoneyFmt} util so amounts like {@code $1,500},
 * {@code 2.5k} or {@code 10m} in the typed command are understood, and the
 * echoed amount is abbreviated (B / M / k, whole numbers plain, otherwise up
 * to two decimals with trailing zeros trimmed).
 *
 * <h3>Server scope</h3>
 * Only active when the current server's address (or saved-server name)
 * contains {@code "donut"} — so the mod never interferes with {@code /pay}
 * (or any other command beginning with "pay") on other servers, singleplayer,
 * or in the lobby. Detection uses {@link MinecraftClient#getCurrentServerEntry()}
 * which returns {@code null} off-server, so we naturally no-op there.
 *
 * <h3>Settings</h3>
 *   Toggle Key, Sound, Action Bar.
 *
 * Sound and Action Bar default to on (glazed's defaults). Toggle key works
 * even when the module is disabled (same pattern as Sprint / Hitboxes /
 * Freecam).
 */
public class FakePay extends Module {

    // ── Static state read by the Fabric event handler (registered in
    //    LucidClient.onInitializeClient). A static flag mirrors the
    //    Freecam.isActive() pattern so the handler can no-op cheaply when
    //    the module is off.
    private static volatile boolean active = false;

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to enable/disable fake pay. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final BooleanSetting sound = addSetting(
        new BooleanSetting("Sound", "Play the arrow-hit ding when a payment is faked.",
            true));
    private final BooleanSetting actionBar = addSetting(
        new BooleanSetting("Action Bar", "Also show the payment message above the hotbar.",
            true));

    /** Toggle key edge detection (matches Sprint's onTickAlways pattern). */
    private boolean wasKeyDown = false;

    public FakePay() {
        super("Fake Pay",
            "Fakes /pay on DonutSMP instead of sending it — cancel the command and echo a local \"You paid\" line, ding and all.",
            Category.MISC);
    }

    @Override
    public void onEnable() {
        wasKeyDown = false;
        active = true;
    }

    @Override
    public void onDisable() {
        active = false;
    }

    /**
     * onTickAlways — toggle key works even when the module is disabled,
     * matching Sprint / Hitboxes / Freecam.
     */
    @Override
    public void onTickAlways() {
        if (mc.player == null || mc.getWindow() == null) return;
        int key = toggleKey.getKey();
        if (key < 0) return;

        long handle = mc.getWindow().getHandle();
        boolean isDown;
        if (key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_MOUSE_BUTTON_8) {
            isDown = GLFW.glfwGetMouseButton(handle, key) == GLFW.GLFW_PRESS;
        } else {
            isDown = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        }

        if (isDown && !wasKeyDown) {
            setEnabled(!isEnabled());
        }
        wasKeyDown = isDown;
    }

    // ════════════════════════════════════════════════════════════
    //  Fabric event handler — invoked from LucidClient.onInitializeClient
    //  via ClientSendMessageEvents.ALLOW_COMMAND.register(FakePay::onSendCommand).
    //  Registered there (not here) because the event must be wired exactly
    //  once at init time; the module's static {@link #active} flag gates it.
    // ════════════════════════════════════════════════════════════

    /**
     * Called for every outgoing chat command (the string has NO leading '/').
     * Mirrors glazed's {@code handlePay} contract: if this returns
     * {@code false} the command is cancelled and the fake payment has been
     * shown; {@code true} means "not mine" and the command is sent normally.
     *
     * <p>Only a well-formed {@code pay <player> <amount>} with a positive,
     * parseable amount is spoofed — everything else (bare {@code /pay},
     * missing/malformed amounts, any other command, any other server) falls
     * through to the server so its real response is indistinguishable from
     * normal play.
     *
     * <p>Static + gated on {@link #isActive()} so the registration in
     * {@code LucidClient} is a single no-op pass-through when the module is off.
     */
    public static boolean onSendCommand(String command) {
        if (!active) return true;
        if (command == null) return true;

        // Tokenise and require exactly the shape "pay <player> <amount> ...".
        // The "pay" literal is matched case-insensitively (glazed semantics),
        // so "/PAY Steve 100" is faked just like "/pay Steve 100".
        String[] parts = command.trim().split("\\s+");
        if (parts.length < 3 || !"pay".equals(parts[0].toLowerCase(Locale.ROOT))) return true;

        // Only on DonutSMP — never touch /pay elsewhere.
        if (!isOnDonutSMP()) return true;

        String user = parts[1];
        Double amount = parseMoney(parts[2]);

        // Malformed / zero / negative amount → let the server reject it.
        if (amount == null || amount <= 0) return true;

        // Inject the fake payment locally (chat + optional action bar + ding).
        showFakePayment(user, amount);

        // Deduct from the FAKE stats balance too — the sidebar + /bal drop by
        // the paid amount while the real balance is left untouched (nothing
        // ever reaches the server). Clamped at 0 by applyPayment.
        FakeStats.applyPayment(amount);

        // Cancel — do NOT send the command to the server.
        return false;
    }

    /** Fetch the live module instance for reading settings from static context. */
    private static FakePay instance() {
        return com.anormal.client.module.ModuleManager.getModule(FakePay.class);
    }

    /**
     * Build and display the fake payment confirmation (glazed's message
     * construction): {@code You paid <user> $ <amount>} — white text with a
     * green dollar sign, the amount abbreviated via {@link #formatMoney}.
     *
     * <p>Shown in chat, optionally mirrored to the action bar above the
     * hotbar, and optionally accompanied by the arrow-hit "ding" at full
     * volume and pitch. Everything is local — no packet leaves the mc.
     */
    private static void showFakePayment(String user, double amount) {
        try {
            
            if (mc.inGameHud == null || mc.player == null) return;

            MutableText message = Text.literal("You paid " + user + " ").formatted(Formatting.WHITE)
                .append(Text.literal("$").formatted(Formatting.GREEN))
                .append(Text.literal(" " + formatMoney(amount)).formatted(Formatting.WHITE));

            mc.inGameHud.getChatHud().addMessage(message);

            FakePay module = instance();
            boolean overlay = module == null || module.actionBar.get();
            boolean ding = module == null || module.sound.get();
            if (overlay) {
                mc.inGameHud.setOverlayMessage(message, false);
            }
            if (ding) {
                mc.player.playSound(SoundEvents.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.0f);
            }
        } catch (Throwable t) {
            // Never let a chat injection crash the game — log and move on.
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakePay failed to inject fake message", t);
        }
    }

    // ── Money parsing / formatting (ported from glazed's MoneyFmt) ──

    /**
     * Parse a user-typed money amount: strips {@code $}, thousands commas and
     * spaces, honours a trailing {@code k} / {@code m} / {@code b} suffix
     * (case-insensitive, x1000 / x1_000_000 / x1_000_000_000), and returns
     * {@code null} for anything unparseable. "$1,500" → 1500, "2.5k" → 2500.
     */
    private static Double parseMoney(String raw) {
        if (raw == null) return null;
        String s = raw.replace("$", "").replace(",", "").replace(" ", "");
        if (s.isEmpty()) return null;

        double multiplier = 1.0;
        char last = Character.toLowerCase(s.charAt(s.length() - 1));
        if (last == 'k' || last == 'm' || last == 'b') {
            multiplier = last == 'k' ? 1_000.0 : last == 'm' ? 1_000_000.0 : 1_000_000_000.0;
            s = s.substring(0, s.length() - 1);
            if (s.isEmpty()) return null;
        }

        try {
            return Double.parseDouble(s) * multiplier;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Format an amount the way glazed's MoneyFmt does — abbreviated to
     * {@code B} / {@code M} / {@code k} by magnitude (e.g. 1.5k, 2.25M, 3B),
     * whole numbers rendered plain, everything else capped at two decimals
     * with trailing zeros trimmed (2500 → "2.5k", 1500 → "1.5k", 500 → "500").
     */
    private static String formatMoney(double value) {
        if (value >= 1_000_000_000.0) return trimMoney(value / 1_000_000_000.0) + "B";
        if (value >= 1_000_000.0) return trimMoney(value / 1_000_000.0) + "M";
        if (value >= 1_000.0) return trimMoney(value / 1_000.0) + "k";
        return trimMoney(value);
    }

    /** Whole values as integers; otherwise two decimals with trailing zeros trimmed. */
    private static String trimMoney(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        String s = String.format(Locale.US, "%.2f", value);
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') end--;
        if (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }

    /**
     * True when the current server looks like DonutSMP. Checks the server
     * address (the IP/domain the user connected to) and the saved-server
     * name for the substring "donut" (case-insensitive). Returns false when
     * not connected to any server (singleplayer/lobby) so the mod stays inert.
     */
    private static boolean isOnDonutSMP() {
        try {
            
            if (mc == null) return false;
            ServerInfo entry = mc.getCurrentServerEntry();
            if (entry == null) return false; // singleplayer / not connected

            if (entry.address != null
                && entry.address.toLowerCase(Locale.ROOT).contains("donut")) {
                return true;
            }
            if (entry.name != null
                && entry.name.toLowerCase(Locale.ROOT).contains("donut")) {
                return true;
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid FakePay server-detection threw", t);
        }
        return false;
    }

    /** Called by the mixin/event registration to check if the module is active. */
    public static boolean isActive() { return active; }
}
