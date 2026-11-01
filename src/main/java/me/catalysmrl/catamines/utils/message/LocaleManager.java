package me.catalysmrl.catamines.utils.message;

import me.catalysmrl.catamines.CataMines;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Disk overrides -> regional/language fallback -> bundled English, per message key. */
public class LocaleManager {
    private final CataMines plugin;
    private final Map<Locale, YamlConfiguration> localeFiles = new HashMap<>();
    private final Map<Locale, YamlConfiguration> bundled = new HashMap<>();

    public LocaleManager(CataMines plugin) { this.plugin = plugin; }

    public void loadLocales(Path folder) {
        localeFiles.clear();
        bundled.clear();
        for (String code : new String[]{"en", "es"}) {
            try (var input = plugin.getResource("lang/messages_" + code + ".yml")) {
                if (input != null) bundled.put(parseLocale(code), YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8)));
            } catch (Exception e) { plugin.getLogger().warning("Could not load bundled language " + code + ": " + e.getMessage()); }
        }
        try {
            Files.createDirectories(folder);
            try (var files = Files.newDirectoryStream(folder, "messages_*.yml")) {
                for (Path path : files) {
                    try {
                        YamlConfiguration config = new YamlConfiguration();
                        config.load(path.toFile());
                        localeFiles.put(parseLocale(path.getFileName().toString()), config);
                    } catch (Exception e) { plugin.getLogger().warning("Invalid language file " + path + ": " + e.getMessage()); }
                }
            }
        } catch (Exception e) { plugin.getLogger().warning("Could not load languages: " + e.getMessage()); }
    }

    private Object lookup(Locale locale, String key, boolean list) {
        Locale language = Locale.forLanguageTag(locale.getLanguage());
        for (YamlConfiguration config : Arrays.asList(localeFiles.get(locale), localeFiles.get(language),
                bundled.get(locale), bundled.get(language), localeFiles.get(Locale.ENGLISH), bundled.get(Locale.ENGLISH))) {
            if (config == null) continue;
            Object value = config.get(key);
            if (list ? value instanceof List<?> : value instanceof String) return value;
        }
        return null;
    }

    public String getMessage(Locale locale, String key) {
        Object value = lookup(locale, key, false);
        return expandPrefix(value == null ? "&cMissing message: " + key : value.toString());
    }

    public List<String> getList(Locale locale, String key) {
        Object value = lookup(locale, key, true);
        if (!(value instanceof List<?> list)) return List.of("&cMissing list: " + key);
        return list.stream().map(Object::toString).map(this::expandPrefix).toList();
    }

    private String expandPrefix(String text) {
        return text.replaceAll("%p(?![A-Za-z])", java.util.regex.Matcher.quoteReplacement(Messages.PREFIX));
    }
    public String getFormatted(Locale locale, String key, Object... replacements) {
        return format(getMessage(locale, key), replacements);
    }
    private static String format(String text, Object... replacements) {
        for (int i = 0; i < replacements.length; i++) text = text.replace("{" + i + "}", String.valueOf(replacements[i]));
        return Messages.colorize(text);
    }
    public List<String> getFormattedList(Locale locale, String key, Object... replacements) {
        return getList(locale, key).stream().map(text -> format(text, replacements)).toList();
    }

    public Locale getLocale(CommandSender sender) {
        String configured = plugin.getConfig().getString("language", "EN");
        if (configured.equalsIgnoreCase("AUTO")) return sender instanceof Player player ? player.locale() : Locale.ENGLISH;
        if (configured.equalsIgnoreCase("CN")) configured = "zh-CN";
        return parseLocale(configured);
    }

    public static Locale parseLocale(String value) {
        String code = value.replaceFirst("^messages_", "").replaceFirst("\\.yml$", "");
        return Locale.forLanguageTag(code.replace('_', '-').toLowerCase(Locale.ROOT));
    }
}
