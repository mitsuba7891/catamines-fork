package me.catalysmrl.catamines.utils.helper;

import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.utils.message.Messages;
import java.util.Locale;

public final class CountdownFormatter {
    private CountdownFormatter() {}
    public static String clock(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", safe / 3600, safe / 60 % 60, safe % 60);
    }
    public static String format(String text, int seconds, Locale locale, CataMine mine) {
        int safe = Math.max(0, seconds);
        boolean spanish = locale.getLanguage().equals("es");
        String unit = spanish ? (safe == 1 ? "segundo" : "segundos") : (safe == 1 ? "second" : "seconds");
        return Messages.colorize(text.replace("%seconds%", Integer.toString(safe))
                .replace("%time%", unit).replace("%formattedseconds%", clock(safe))
                .replace("%formattedtime%", safe + " " + unit)
                .replace("%cm%", Messages.PREFIX).replace("%mine%", mine.getDisplayName())
                .replace("%remainingblocksper%", String.format(Locale.ROOT, "%.2f", mine.getController().getRemainingPercentage()))
                .replace("%resetpercentage%", Double.toString(mine.getController().getResetPercentage())));
    }
}
