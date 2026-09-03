package net.kdt.pojavlaunch.chatoverlay;

import java.util.Locale;
import java.util.regex.Pattern;

/** Death-screen / death-message heuristics from log lines. English vanilla + common keys. */
public final class ChatDeath {
    private static final Pattern DEATH = Pattern.compile(
            "you died|death\\.screen|was slain|was shot|was slain by|drowned|"
                    + "hit the ground too hard|fell from a high place|fell off|blew up|"
                    + "went off with a bang|starved to death|burned to death|went up in flames|"
                    + "walked into fire|tried to swim in lava|suffocated|withered away|"
                    + "was squashed|was squished|was killed by|didn't want to live|"
                    + "experienced kinetic energy|was poked to death|was impaled|"
                    + "froze to death|was stung to death|discovered the floor was lava|"
                    + "was doomed to fall|was blown up|left the confines of this world",
            Pattern.CASE_INSENSITIVE);

    public static boolean isSelfDeath(String text, String selfName) {
        if (text == null || text.isEmpty()) return false;
        String t = ChatLogParser.stripSection(text).trim();
        String low = t.toLowerCase(Locale.ROOT);
        if (low.contains("you died") || low.contains("death.screen")) return true;
        if (!DEATH.matcher(low).find()) return false;
        if (selfName == null || selfName.isEmpty()) return true;
        String me = selfName.toLowerCase(Locale.ROOT);
        return low.startsWith(me + " ") || low.startsWith(me + "was") || low.contains(" " + me + " ");
    }
}
