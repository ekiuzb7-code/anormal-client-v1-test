package com.lucidclient.module.impl;

import com.lucidclient.LucidClient;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.KeybindSetting;
import com.lucidclient.module.setting.StringSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * FakeStats — fakes the Money / Shards / Playtime / Rank values shown in the
 * DonutSMP scoreboard sidebar, client-side cosmetic only.
 *
 * <h3>What it does</h3>
 * When enabled on DonutSMP, the stat lines in the scoreboard sidebar (right
 * side of the screen) show fake values you choose instead of your real
 * balance/playtime/rank. The server still tracks your real stats — this only
 * changes what the sidebar renders locally, for screenshots/recordings/privacy.
 *
 * <h3>How it works</h3>
 * DonutSMP's scoreboard uses fake colour-code score holders (no team
 * prefix/suffix), so each visible line (e.g. {@code "§6$ §f99.4K§"}) is drawn
 * as a single {@link Text} by {@code DrawContext.drawText} inside
 * {@code InGameHud.renderScoreboardSidebar}. Vanilla passes colour {@code -1}
 * to that {@code drawText} call, so the on-screen colour is driven entirely by
 * the {@code Text}'s own {@link Style} tree (NOT by a colour argument). The
 * existing NameProtect {@code @ModifyArg} hook already intercepts every such
 * {@code drawText} call; FakeStats chains a second transform in that same
 * hook.
 *
 * <p><b>Icon colour preservation (the hard part).</b> DonutSMP can encode the
 * icon's colour two different ways, and we must handle both:
 * <ul>
 *   <li><b>Per-sibling {@link Style}</b> — the {@code $} is one styled sibling
 *       (gold) and the {@code 99.4K} is another styled sibling (white).</li>
 *   <li><b>Embedded {@code §}-bytes</b> — the whole line is one flat
 *       {@code Text.literal("§6$ §f99.4K§")} whose colour lives in the
 *       {@code §}-bytes inside the raw string (root style is empty).</li>
 * </ul>
 * Flattening the tree via {@link Text#getString()} and rebuilding with only
 * the root style (the previous approach) silently dropped the colour in BOTH
 * cases — the icon rendered white, because the colour lived in the siblings'
 * Styles / the §-bytes that the rebuild discarded.
 *
 * <p>The fix walks the {@code Text} tree once via
 * {@link StringVisitable#visit(StringVisitable.StyledVisitor, Style)}, which
 * hands us each leaf as {@code (effective merged Style, raw leaf string)}.
 * The raw leaf string preserves any embedded {@code §}-bytes; the effective
 * Style carries whatever colour DonutSMP assigned to that leaf. We then
 * rebuild leaf-by-leaf, keeping each leaf's own Style verbatim — so the icon
 * leaf keeps its colour whether it came from a sibling Style or from a
 * {@code §}-byte. Only the value leaf's <i>content</i> is swapped for the fake
 * number; its Style is reused so the fake number inherits the colour the
 * original value had.
 *
 * <h3>Settings</h3>
 *   Toggle Key, Money, Shards, Playtime, Rank.
 *
 * Toggle key works even when the module is disabled (same pattern as
 * NameProtect / FakePay / SkinProtect).
 */
public class FakeStats extends Module {

    // ── Static state read by the sidebar @ModifyArg hook (mirrors
    //    NameProtect.isActive()). A static flag lets the hook no-op cheaply
    //    when the module is off.
    private static volatile boolean active = false;

    /** Stat label tokens (lowercased) used to match sidebar lines. */
    private static final String LABEL_MONEY    = "money";
    private static final String LABEL_SHARDS   = "shards";
    private static final String LABEL_PLAYTIME = "playtime";
    private static final String LABEL_RANK     = "rank";

    /**
     * DonutSMP sidebar line markers. Confirmed in-game (15:43:09 sidebar dump):
     * the sidebar shows standalone icon-prefixed values with <b>no</b> text
     * labels and <b>no</b> {@code :} separators, e.g. {@code "$ 99.4K"},
     * {@code "★ 225"}, {@code "⌚ 1d 21h"}. We match by the distinctive lead
     * character/icon so the spoof survives whatever {@code §} colour codes
     * DonutSMP wraps the line in.
     */
    private static final String ICON_MONEY    = "$";
    private static final String ICON_SHARDS   = "★";
    private static final String ICON_PLAYTIME = "⌚";

    // ── Fake values, synced each tick from the StringSettings so live
    //    edits in the ClickGUI take effect immediately. Defaults are JUST
    //    the value / digits — the icon + its colour come from the real
    //    DonutSMP sidebar line at replace time (see rebuildFromSegments),
    //    so the customization only carries the number.
    private static volatile String fakeMoney    = "1,337,420";
    private static volatile String fakeShards   = "99,999";
    private static volatile String fakePlaytime = "2h 30m";
private static volatile String fakeRank     =
"Donut+++";

    /** Shared RNG for jittering the fake balance's last three digits. */
    private static final java.util.Random RANDOM = new java.util.Random();

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to enable/disable fake stats. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final StringSetting money = addSetting(
        new StringSetting("Money", "Fake Money number shown in the sidebar. Only the digits — the original '$' icon + its colour come from the real sidebar line, so do NOT enter the icon here.",
            "1,337,420"));
    private final StringSetting shards = addSetting(
        new StringSetting("Shards", "Fake Shards number shown in the sidebar. Only the digits — the original '★' icon + its colour come from the real sidebar line.",
            "99,999"));
    private final StringSetting playtime = addSetting(
        new StringSetting("Playtime", "Fake Playtime shown in the sidebar. Only the value — the original '⌚' icon + its colour come from the real sidebar line.",
            "2h 30m"));
    private final StringSetting rank = addSetting(
        new StringSetting("Rank", "Fake Rank text shown in the sidebar (e.g. Donut+++).",
            "Donut+++"));

    /** Toggle key edge detection (matches Sprint's onTickAlways pattern). */
    private boolean wasKeyDown = false;

    public FakeStats() {
        super("Fake Stats",
            "Fakes Money, Shards, Playtime, and Rank in the DonutSMP scoreboard sidebar. Client-side only.",
            Category.DONUT);
    }

    @Override
    protected void onEnable() {
        wasKeyDown = false;
        syncFakeValues();
        active = true;
    }

    @Override
    protected void onDisable() {
        active = false;
    }

    /**
     * onTickAlways — toggle key works even when the module is disabled,
     * matching NameProtect / FakePay / Sprint. Also syncs the fake values
     * from the StringSettings each tick so live edits in the ClickGUI take
     * effect immediately.
     */
    @Override
    public void onTickAlways(MinecraftClient client) {
        if (active) syncFakeValues();

        if (client.player == null || client.getWindow() == null) return;
        int key = toggleKey.getKey();
        if (key < 0) return;

        long handle = client.getWindow().getHandle();
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

    /** Copy the current StringSetting values into the static fields the hook reads. */
    private void syncFakeValues() {
        fakeMoney    = money.get();
        fakeShards   = shards.get();
        fakePlaytime = playtime.get();
        fakeRank     = rank.get();
    }

    // ════════════════════════════════════════════════════════════
    //  Sidebar @ModifyArg read surface — static, no module instance required
    // ════════════════════════════════════════════════════════════

    /** Called by the mixin hook to check if stat faking is active. */
    public static boolean isActive() { return active; }

    /**
     * The configured fake rank text, read by the tab-list {@code PlayerListHud}
     * mixin ({@link com.lucidclient.mixin.PlayerListHudMixin}) to prepend
     * {@code "[rank] "} to the local player's TAB entry on DonutSMP.
     * Returns the live-synced {@link #fakeRank} value (so ClickGUI edits take
     * effect immediately). May be empty when the user cleared the setting.
     */
    public static String getFakeRank() { return fakeRank == null ? "" : fakeRank; }

    // ════════════════════════════════════════════════════════════
    //  /balance (/bal, /money) fake — cancel the outgoing command
    //  and echo a fake balance line locally, in the real DonutSMP
    //  chat format ("&7You have &a${amount}&7."). Same
    //  cancel-and-inject pattern as FakePay.
    // ════════════════════════════════════════════════════════════

    /**
     * Called for every outgoing chat command (the string has NO leading '/').
     * Registered once at init in {@code LucidClient} as a defence-in-depth
     * first layer (cancels before the packet is even built). Never reached if
     * the chat-screen event doesn't fire — the {@code ClientConnectionMixin}
     * packet hook catches it in that case.
     *
     * <p>Match rules — only a bare SELF balance check is faked:
     * <ul>
     *   <li>module active AND on DonutSMP,</li>
     *   <li>first token is {@code balance} / {@code bal} / {@code money}
     *       (case-insensitive — the real DonutSMP aliases),</li>
     *   <li>NO player argument: {@code /bal Steve} checks someone else's real
     *       balance, so that falls through to the server untouched.</li>
     * </ul>
     * Returns {@code false} to cancel the command (server never sees it).
     */
    public static boolean onSendCommand(String command) {
        return !maybeCancelFakeBalance(command);   // true = pass through
    }

    /**
     * Single matcher shared by BOTH intercept layers: the Fabric
     * {@code ALLOW_COMMAND} event handler ({@link #onSendCommand}, command has
     * NO leading '/') and the {@code ClientConnectionMixin} packet hook on
     * {@code ChatCommandSignedC2SPacket} (command MAY carry the leading '/').
     * Returns {@code true} when the command was a bare self-balance and has
     * been faked (caller must cancel the outgoing command); {@code false} to
     * pass through untouched.
     */
    public static boolean maybeCancelFakeBalance(String raw) {
        if (!active) return false;
        if (raw == null) return false;
        String s = raw.trim();
        if (s.startsWith("/")) s = s.substring(1);
        String[] parts = s.split("\\s+");
        if (parts.length == 0) return false;
        String head = parts[0].toLowerCase(Locale.ROOT);
        if (!"balance".equals(head) && !"bal".equals(head) && !"money".equals(head)) return false;
        if (parts.length > 1) return false;   // /bal <someone> → real balance
        if (!isOnDonutSMP()) return false;
        LucidClient.LOGGER.info("[FakeStats] intercepting /" + head + " (donut, active)");
        showFakeBalance();
        return true;
    }

    /**
     * Echo the fake balance into the local chat HUD exactly like DonutSMP's
     * reply: {@code "You have "} (white), {@code $} (green) and the amount
     * (white) as a FULL written-out number with thousands commas
     * ({@code 1,400,000,000}) — no abbreviation (the scoreboard abbreviates,
     * {@code /bal} does not). Local only; no packet leaves the client.
     */
    private static void showFakeBalance() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.inGameHud == null || client.player == null) return;
            String amountText = fakeMoney == null || fakeMoney.isEmpty() ? "0" : fakeMoney;
            Double parsed = parseBalanceAmount(amountText);
            String displayed = (parsed != null)
                ? formatBalance(scrambleBalance((long) Math.round(parsed)))
                : amountText.replace(" ", "").trim();
            // "$" renders directly against the number — no space between them;
            // tabs/odd whitespace are stripped so the pair is always tight.
            MutableText message = Text.literal("You have ").formatted(Formatting.WHITE)
                .append(Text.literal("$").formatted(Formatting.GREEN))
                .append(Text.literal(displayed).formatted(Formatting.WHITE));
            client.inGameHud.getChatHud().addMessage(message);
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid FakeStats showFakeBalance threw", t);
        }
    }

    /**
     * Jitter the ones / tens / hundreds digits (each 1-9, never 0) so the fake
     * balance never ends in a clean "000" that would look hand-entered. When
     * the amount is in the billions, ALSO spoof the hundred-thousands digit
     * (10^5) the same way, so a big balance doesn't look pre-typed either.
     * Amounts below one thousand are left untouched — there are no hundreds,
     * tens and ones digits available without changing the value's magnitude.
     */
    private static long scrambleBalance(long amount) {
        if (amount < 1000L) return amount;
        long base = amount / 1000L * 1000L;
        long result = base
            + (1 + RANDOM.nextInt(9)) * 100L
            + (1 + RANDOM.nextInt(9)) * 10L
            + (1 + RANDOM.nextInt(9));
        if (amount >= 1_000_000_000L) {
            long pos = 100_000L;   // hundred-thousands digit
            long digit = (result / pos) % 10;
            result = result - digit * pos + (1 + RANDOM.nextInt(9)) * pos;
        }
        return result;
    }

    /**
     * Parse a balance string: strips {@code $}, thousands commas and spaces,
     * honours a trailing {@code k} / {@code m} / {@code b} suffix
     * (case-insensitive, x1_000 / x1_000_000 / x1_000_000_000), and returns
     * {@code null} for anything unparseable. {@code "1,337,420"} → 1337420,
     * {@code "2.5b"} → 2500000000.
     */
    private static Double parseBalanceAmount(String raw) {
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
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Format a raw amount the way the {@code /bal} reply shows it: a FULL
     * written-out number with thousands commas, no abbreviation. Whole
     * amounts print as integers ({@code 1400000000} → {@code 1,400,000,000});
     * fractional amounts keep up to two decimals with trailing zeros trimmed.
     */
    private static String formatBalance(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.format(Locale.US, "%,d", (long) value);
        }
        String s = String.format(Locale.US, "%,.2f", value);
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') end--;
        if (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }

    /**
     * Format an amount the abbreviated way the real DonutSMP sidebar does —
     * {@code B} / {@code M} / {@code k} by magnitude ({@code 1.4b},
     * {@code 2.25M}, {@code 99.4K}), whole numbers plain, else up to two
     * decimals with trailing zeros trimmed. Used for the {@code /bal} reply
     * and the post-pay balance so every surface shows the same style as the
     * scoreboard.
     */
    private static String formatMoneyAbbrev(double value) {
        if (value >= 1_000_000_000.0) return trimMoney(value / 1_000_000_000.0) + "b";
        if (value >= 1_000_000.0) return trimMoney(value / 1_000_000.0) + "m";
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
     * Deduct a payment amount from the FAKE money balance (sidebar + /bal).
     * Called by {@link FakePay} right after a fake {@code /pay} succeeds, so
     * the fake wallet visibly spends money while the real balance is never
     * touched (nothing reaches the server). The balance is clamped at 0 —
     * a negative fake balance looks broken.
     *
     * <p>Updates BOTH the static {@link #fakeMoney} the sidebar hook reads
     * and the live {@code Money} StringSetting, so the per-tick
     * {@link #syncFakeValues()} does not revert the deduction and the
     * ClickGUI shows the reduced balance.
     */
    public static void applyPayment(double amount) {
        if (amount <= 0) return;
        try {
            String text = fakeMoney;
            Double parsed = parseBalanceAmount(text);
            double current = parsed != null ? parsed : 0.0;
            double remaining = Math.max(0.0, current - amount);
            // Same abbreviated style as the scoreboard / real DonutSMP sidebar.
            String updated = formatMoneyAbbrev(remaining);
            fakeMoney = updated;
            try {
                FakeStats mod = LucidClient.getInstance().getModuleManager().getModule(FakeStats.class);
                if (mod != null) mod.money.setValue(updated);
            } catch (Throwable ignored) {
                // Module not registered yet / manager unavailable — the static
                // field is still updated; setting sync resumes on next tick.
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid FakeStats applyPayment threw", t);
        }
    }

    /**
     * If the given sidebar line matches a known stat, return a rebuilt
     * {@link Text} with the value replaced by the corresponding fake value.
     * Returns {@code null} when nothing matched (so the caller leaves the
     * original text untouched).
     *
     * <p><b>DonutSMP format (confirmed in-game):</b> the sidebar shows
     * standalone icon-prefixed values with <i>no</i> text labels and <i>no</i>
     * {@code :} separators — e.g. {@code "§6$ §f99.4K§"}, {@code "★ 225 §"},
     * {@code "⌚ 1d 21h §"}. Every line ends with a lone trailing {@code §}.
     * We match by the distinctive lead icon, then fall back to the lowercased
     * label token (for any future server that does use {@code "Money:"} style).
     *
     * <p><b>Guarantee:</b> the icon glyph retains the original line's leading
     * icon colour — only the digits/value after the icon are swapped. This
     * holds whether DonutSMP encoded the colour as a per-sibling {@link Style}
     * or as embedded {@code §}-bytes in a flat literal, because the rebuild
     * walks the tree via {@link StringVisitable.StyledVisitor} and preserves
     * each leaf's effective Style verbatim.
     *
     * <p>Static so the {@code ScoreboardEntryMixin} hook can call it without
     * holding a module instance.
     */
    public static Text replaceStatsInText(Text line) {
        if (!active) return null;
        if (line == null) return null;
        if (!isOnDonutSMP()) return null;

        String original = line.getString();
        if (original == null || original.isEmpty()) return null;

        String stripped = stripFormatting(original);
        String lower = stripped.toLowerCase(Locale.ROOT);

        String fakeValue = null;
        String iconChar = null;  // the lead icon ("$", "★", "⌚") we match on
        // Values are shown VERBATIM as configured — the user keeps the
        // abbreviated style ("1.4b") that the real DonutSMP sidebar uses.
        if (stripped.startsWith(ICON_MONEY) || lower.startsWith(ICON_MONEY)) {
            fakeValue = fakeMoney;  iconChar = ICON_MONEY;
        } else if (stripped.startsWith(ICON_SHARDS) || lower.startsWith(ICON_SHARDS)) {
            fakeValue = fakeShards; iconChar = ICON_SHARDS;
        } else if (stripped.startsWith(ICON_PLAYTIME) || lower.startsWith(ICON_PLAYTIME)) {
            fakeValue = fakePlaytime; iconChar = ICON_PLAYTIME;
        }

        // Fallback: label-token match (for any server using "Money:" / "Rank:")
        // — no icon. Use the legacy string-rewrap path.
        if (fakeValue == null) {
            if (lower.contains(LABEL_MONEY))         fakeValue = fakeMoney;
            else if (lower.contains(LABEL_SHARDS))   fakeValue = fakeShards;
            else if (lower.contains(LABEL_PLAYTIME)) fakeValue = fakePlaytime;
            else if (lower.contains(LABEL_RANK))     fakeValue = fakeRank;

            if (fakeValue == null || fakeValue.isEmpty()) return null;
            return Text.literal(wrapWithOriginalColor(original, fakeValue))
                .setStyle(line.getStyle());
        }

        if (fakeValue.isEmpty()) return null;

        // ── Primary path: walk the Text tree leaf-by-leaf via StyledVisitor.
        //    Each leaf arrives as (effective merged Style, raw leaf string).
        //    The raw string preserves any embedded §-bytes; the Style carries
        //    whatever colour DonutSMP assigned to that leaf. Rebuilding
        //    leaf-by-leaf with each leaf's own Style guarantees the icon
        //    keeps its original colour — value content swapped, icon + its
        //    colour untouched — in BOTH the sibling-Style case and the
        //    flat-§-byte case. ────────────────────────────────────────────
        List<Seg> segs = collectSegments(line);
        if (segs != null && !segs.isEmpty()) {
            Text rebuilt = rebuildFromSegments(segs, fakeValue, iconChar);
            if (rebuilt != null) return rebuilt;
        }

        // ── Last resort: flat §-string rebuild on the whole flattened string.
        //    Reached only if the StyledVisitor walk somehow produced nothing
        //    useful (e.g. an empty tree). replaceValueKeepIcon preserves every
        //    §-code surrounding the icon, so even here the icon keeps its
        //    §-colour. ──────────────────────────────────────────────────────
        String rebuiltStr = replaceValueKeepIcon(original, fakeValue, iconChar);
        if (rebuiltStr == null) {
            // Couldn't confidently locate the icon — leave the line un-faked
            // rather than risk a miscoloured glyph. Correctness over coverage.
            return null;
        }
        return Text.literal(rebuiltStr).setStyle(line.getStyle());
    }

    // ────────────────────────────────────────────────────────────
    //  Tree walk via StringVisitable.StyledVisitor
    // ────────────────────────────────────────────────────────────

    /** One leaf of the walked Text tree: the effective (parent-merged) Style
     *  and the raw leaf string (which may contain embedded §-bytes). */
    private static final class Seg {
        final Style style;
        final String text;
        Seg(Style s, String t) { style = s; text = t; }
    }

    /**
     * Walk the whole {@link Text} tree once, collecting every leaf in order
     * as {@code (effective merged Style, raw leaf string)}. The visitor
     * always returns {@code Optional.empty()} so the walk never short-
     * circuits and visits every leaf.
     *
     * <p>The effective Style is the Style the renderer actually applies to
     * that leaf (the leaf's own Style merged up the tree with the root's
     * Style), which is exactly what we want to preserve verbatim per leaf.
     */
    private static List<Seg> collectSegments(Text line) {
        if (line == null) return null;
        List<Seg> out = new ArrayList<>();
        StringVisitable.StyledVisitor<Void> visitor = (style, asString) -> {
            if (asString != null && !asString.isEmpty()) {
                out.add(new Seg(style, asString));
            }
            return Optional.empty();
        };
        // visit applies the root style to each leaf's style before invoking
        // the visitor, so 'style' is the effective merged style.
        line.visit(visitor, line.getStyle());
        return out;
    }

    /**
     * Rebuild a {@link MutableText} from the walked segments, swapping only
     * the value-carrying leaf's <i>content</i> for the fake number while
     * keeping every leaf's own {@link Style} verbatim — so the icon leaf
     * keeps its original colour regardless of how DonutSMP encoded it.
     *
     * <p>Per-segment rules (segments walked in document order):
     * <ul>
     *   <li><b>Icon-only leaf</b> (carries the icon and nothing else, e.g.
     *       the gold {@code "$"} sibling) — kept <i>verbatim</i> (raw
     *       string + its Style), so its colour is untouched.</li>
     *   <li><b>Mixed icon+value leaf</b> (the icon and the digits share one
     *       raw string, e.g. {@code "§6$ §f99.4K§"} in a flat literal) —
     *       handed to {@link #replaceValueKeepIcon}, which swaps only the
     *       digits while preserving every surrounding {@code §}-byte, so the
     *       icon keeps its {@code §}-colour. The leaf's Style is reused.</li>
     *   <li><b>Pure value leaf</b> after the icon (e.g. the white
     *       {@code "99.4K"} sibling) — content replaced with the fake number,
     *       Style reused so the fake number inherits the original value's
     *       colour.</li>
     *   <li><b>Blank / §-only / whitespace leaf</b> (e.g. DonutSMP's trailing
     *       lone {@code §}) — kept verbatim.</li>
     *   <li><b>Any text before the icon</b> — kept verbatim (rare).</li>
     * </ul>
     *
     * @return the rebuilt MutableText, or {@code null} if no value leaf was
     *         actually swapped (the caller then falls back / leaves the line
     *         un-faked)
     */
    private static Text rebuildFromSegments(List<Seg> segs, String fakeValue, String iconChar) {
        String fakeNumber = stripIconAndWhitespace(fakeValue, iconChar);

        MutableText result = Text.empty();
        boolean seenIcon = false;
        boolean replacedAny = false;

        for (Seg seg : segs) {
            String s = seg.text;
            boolean hasIcon = !seenIcon && containsIcon(s, iconChar);

            if (hasIcon) {
                seenIcon = true;
                // Does this leaf ALSO carry the value, or just the icon?
                String leftover = stripFormatting(s).replace(iconChar, "").trim();
                if (leftover.isEmpty()) {
                    // Icon-only leaf — keep verbatim (raw string + its Style).
                    result.append(Text.literal(s).setStyle(seg.style));
                    continue;
                }
                // Mixed icon+value leaf — swap only the digits, preserving
                // every §-byte so the icon keeps its §-colour.
                String replaced = replaceValueKeepIcon(s, fakeValue, iconChar);
                result.append(Text.literal(replaced != null ? replaced : s).setStyle(seg.style));
                if (replaced != null) replacedAny = true;
                continue;
            }

            if (isBlankOrFormatting(s)) {
                // whitespace / lone-§ / §-code-only leaf — keep verbatim.
                result.append(Text.literal(s).setStyle(seg.style));
                continue;
            }

            if (seenIcon) {
                // Pure value leaf after the icon — swap content, reuse its
                // Style so the fake number inherits the original value colour.
                result.append(Text.literal(fakeNumber).setStyle(seg.style));
                replacedAny = true;
            } else {
                // Text before the icon (rare) — keep verbatim.
                result.append(Text.literal(s).setStyle(seg.style));
            }
        }
        return replacedAny ? result : null;
    }

    // ────────────────────────────────────────────────────────────
    //  String-level helpers (operate on RAW leaf strings, §-bytes
    //  preserved — used by the mixed-leaf + last-resort paths)
    // ────────────────────────────────────────────────────────────

    /**
     * Rebuild a raw leaf string, replacing ONLY the value digits while leaving
     * the icon and every surrounding {@code §}-code untouched, so the icon's
     * on-screen colour stays exactly as provided.
     *
     * <p>Example: with {@code original = "§6$ §f99.4K§"} and
     * {@code fakeValue = "1,337,420"} this returns
     * {@code "§6$ §f1,337,420§"}. The leading {@code §6} (gold) colouring the
     * {@code $} icon is preserved verbatim; the {@code §f} that recolours the
     * value to white is preserved verbatim; only the digits are swapped.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Locate the icon in {@code original} (skipping any leading
     *       {@code §X} colour-code pairs so a leading colour code is not
     *       mistaken for the icon).
     *   <li>Keep {@code original[0..iconEnd]} verbatim as the prefix — this
     *       preserves every {@code §}-code up to and including the icon.
     *   <li>Walk forward from {@code iconEnd} skipping whitespace and
     *       {@code §X} code pairs, collecting them into a "restyle" buffer.
     *       The first non-whitespace non-§-code char is where the value
     *       starts.
     *   <li>From the END of {@code original}, walk backward keeping any
     *       trailing lone {@code §} and any trailing {@code §X} pairs — the
     *       result is the suffix (DonutSMP's lone-§ terminator, etc.).
     *   <li>Strip the icon + leading whitespace from the user-supplied
     *       {@code fakeValue} (so users who set {@code "$ 1,337,420"} don't
     *       get a duplicated {@code $}.
     *   <li>Return {@code prefix + restyle + fakeNumber + suffix}.
     * </ol>
     *
     * @return the rebuilt string, or {@code null} if the icon wasn't found
     *         in {@code original} (so the caller can fall back).
     */
    private static String replaceValueKeepIcon(String original, String fakeValue, String iconChar) {
        if (original == null || original.isEmpty()) return null;

        // ── Step 1: locate the icon in original (skipping §X pairs) ──
        int iconPos = -1;
        for (int i = 0; i < original.length(); i++) {
            char c = original.charAt(i);
            if (c == '\u00A7') {
                if (i + 1 < original.length()) {
                    i++;  // skip the §X pair
                }
                continue;
            }
            if (original.startsWith(iconChar, i)) {
                iconPos = i;
                break;
            }
        }
        if (iconPos < 0) return null;
        int iconEnd = iconPos + iconChar.length();
        String prefix = original.substring(0, iconEnd);

        // ── Step 2: walk forward from iconEnd over whitespace + §X codes ──
        // Collect them as "restyle" — these preserve the whitespace between
        // the icon and the value, plus the colour-code that recolours the
        // value (e.g. "§f" between "$" and "99.4K").
        int valueStart = iconEnd;
        StringBuilder restyle = new StringBuilder();
        while (valueStart < original.length()) {
            char c = original.charAt(valueStart);
            if (c == '\u00A7' && valueStart + 1 < original.length()) {
                restyle.append(original, valueStart, valueStart + 2);
                valueStart += 2;
            } else if (c == '\u00A7') {
                // Lone trailing § right after the icon → value is empty.
                restyle.append(c);
                valueStart++;
            } else if (Character.isWhitespace(c)) {
                restyle.append(c);
                valueStart++;
            } else {
                break;
            }
        }

        // ── Step 3: walk backward from end keeping trailing §X / lone § ──
        // DonutSMP ends every sidebar line with a lone "§" — usually
        // preceded by a single trailing space (e.g. "★ 225 §", "⌚ 1d 21h §").
        // Keep both §-codes AND trailing whitespace in the suffix so the
        // rebuilt line preserves the same on-screen terminator spacing.
        // Inner whitespace in the VALUE (like "1d 21h") is safe: the backward
        // walk stops at the first non-whitespace non-§ value char it meets
        // (here 'h') and never reaches past it into the inner space.
        int suffixStart = original.length();
        while (suffixStart > valueStart) {
            char c = original.charAt(suffixStart - 1);
            if (c == '\u00A7') {
                suffixStart -= 1;
            } else if (c == ' ' || c == '\t') {
                suffixStart -= 1;
            } else {
                break;
            }
        }
        String suffix = original.substring(suffixStart);

        // ── Step 4: cleaner for the user's fake value ──
        String fakeNumber = stripIconAndWhitespace(fakeValue, iconChar);

        // ── Step 5: assemble prefix + restyle + fakeNumber + suffix ──
        return prefix + restyle.toString() + fakeNumber + suffix;
    }

    /**
     * Strip a leading icon (e.g. {@code "$"}) and any leading whitespace
     * from {@code value}. Defensive: handles old configs that stored the
     * icon-prefixed value (e.g. {@code "$ 1,337,420"}) by trimming down to
     * the bare number/text the spoof actually swaps in.
     */
    private static String stripIconAndWhitespace(String value, String iconChar) {
        String v = value;
        if (v == null) return "";
        if (iconChar != null && v.startsWith(iconChar)) {
            v = v.substring(iconChar.length());
        }
        while (v.length() > 0
            && (v.charAt(0) == ' ' || v.charAt(0) == '\t')) {
            v = v.substring(1);
        }
        return v;
    }

    /**
     * True if {@code s} is empty, pure whitespace, or contains only
     * {@code §}- colour/format codes (including a lone trailing {@code §}).
     * Such leaves carry no visible text and are kept verbatim during the
     * rebuild.
     */
    private static boolean isBlankOrFormatting(String s) {
        if (s == null) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00A7') {
                if (i + 1 < s.length()) i++;  // skip the §X pair
                continue;
            }
            if (Character.isWhitespace(c)) continue;
            return false;
        }
        return true;
    }

    /**
     * True if {@code s} contains the icon char, skipping any {@code §X}
     * colour-code pairs so we don't false-positive on a {@code §} byte.
     */
    private static boolean containsIcon(String s, String iconChar) {
        if (s == null || iconChar == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00A7') {
                if (i + 1 < s.length()) i++;
                continue;
            }
            if (s.startsWith(iconChar, i)) return true;
        }
        return false;
    }

    /**
     * Extract the leading {@code §X} colour codes and the trailing lone
     * {@code §} from {@code original} and wrap {@code fakeValue} with them,
     * so the rebuilt line keeps the same colour and spacing as the original.
     *
     * <p>Used by the label-token branch (lines like {@code "Money: $1,234"}
     * which have no icon-prefix match). The icon branch doesn't reach here.
     */
    private static String wrapWithOriginalColor(String original, String fakeValue) {
        StringBuilder leading = new StringBuilder();
        int i = 0;
        // Capture any leading "§X" colour/format code pairs.
        while (i + 1 < original.length() && original.charAt(i) == '§') {
            leading.append(original, i, i + 2);
            i += 2;
        }
        // Capture a single trailing lone "§" (DonutSMP ends every line with one).
        boolean trailingSection = original.endsWith("§");
        return leading.toString() + fakeValue + (trailingSection ? "§" : "");
    }

    /** Remove §-colour codes (and the char following each §) for matching. */
    private static String stripFormatting(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                i++; // skip the § and the following format code char
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * True when the current server looks like DonutSMP. Same detection as
     * NameProtect / FakePay: server address or saved-server name contains
     * "donut" (case-insensitive). Returns false off-server.
     */
    private static boolean isOnDonutSMP() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return false;
            ServerInfo entry = client.getCurrentServerEntry();
            if (entry == null) return false;

            if (entry.address != null
                && entry.address.toLowerCase(Locale.ROOT).contains("donut")) {
                return true;
            }
            if (entry.name != null
                && entry.name.toLowerCase(Locale.ROOT).contains("donut")) {
                return true;
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid FakeStats server-detection threw", t);
        }
        return false;
    }
}
