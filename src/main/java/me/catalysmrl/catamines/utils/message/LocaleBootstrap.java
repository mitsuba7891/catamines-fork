package me.catalysmrl.catamines.utils.message;

import me.catalysmrl.catamines.CataMines;
import java.nio.file.Files;

public class LocaleBootstrap {
    private final CataMines plugin;
    public LocaleBootstrap(CataMines plugin) { this.plugin = plugin; }
    public void init() {
        for (String language : new String[]{"en", "es"}) {
            String resource = "lang/messages_" + language + ".yml";
            if (!Files.exists(plugin.getDataPath().resolve(resource))) plugin.saveResource(resource, false);
        }
        Messages.PREFIX = plugin.getConfig().getString("prefix", Messages.PREFIX);
        LocaleManager manager = new LocaleManager(plugin);
        manager.loadLocales(plugin.getDataPath().resolve("lang"));
        MessageService.init(manager);
    }
}
