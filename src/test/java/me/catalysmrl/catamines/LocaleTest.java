package me.catalysmrl.catamines;

import me.catalysmrl.catamines.utils.message.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocaleTest {
    @TempDir Path folder;
    private CataMines plugin;
    private YamlConfiguration config;
    @BeforeEach void setup() {
        plugin = mock(CataMines.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("locale-test"));
        when(plugin.getDataPath()).thenReturn(folder);
        when(plugin.getResource(anyString())).thenAnswer(call -> getClass().getClassLoader().getResourceAsStream(call.getArgument(0)));
    }
    @Test void regionalOverrideFallsBackPerKeyAndThenToEnglish() throws Exception {
        Files.writeString(folder.resolve("messages_es_ES.yml"), "catamines:\n  gui:\n    mines: 'Minas editadas'\n");
        var manager = new LocaleManager(plugin); manager.loadLocales(folder);
        assertEquals("Minas editadas", manager.getMessage(Locale.forLanguageTag("es-ES"), "catamines.gui.mines"));
        assertTrue(manager.getMessage(Locale.forLanguageTag("es-MX"), "catamines.gui.mines").contains("Minas"));
        assertFalse(manager.getMessage(Locale.FRENCH, "catamines.gui.mines").contains("Missing"));
    }
    @Test void fixedLanguageAndPlayerAutomaticLocaleAreDifferentModes() {
        var manager = new LocaleManager(plugin);
        Player player = mock(Player.class); when(player.locale()).thenReturn(Locale.FRENCH);
        config.set("language", "ES"); assertEquals("es", manager.getLocale(player).getLanguage());
        config.set("language", "AUTO"); assertEquals(Locale.FRENCH, manager.getLocale(player));
        assertEquals(Locale.forLanguageTag("pt-BR"), LocaleManager.parseLocale("messages_pt_BR.yml"));
    }
    @Test void initializationNeverOverwritesExistingTranslation() throws Exception {
        Files.createDirectories(folder.resolve("lang"));
        Path english = folder.resolve("lang/messages_en.yml");
        Files.writeString(english, "catamines:\n  gui:\n    mines: 'My edited title'\n");
        new LocaleBootstrap(plugin).init();
        assertTrue(Files.readString(english).contains("My edited title"));
        verify(plugin, never()).saveResource("lang/messages_en.yml", true);
        verify(plugin, never()).saveResource("lang/messages_en.yml", false);
    }
    @Test void everyMessageConstantHasEnglishAndSpanishTranslation() throws Exception {
        var manager = new LocaleManager(plugin); manager.loadLocales(folder);
        for (var field : Message.class.getFields()) {
            if (field.getType() != Message.class) continue;
            String key = ((Message) field.get(null)).getKey();
            for (Locale locale : List.of(Locale.ENGLISH, Locale.forLanguageTag("es"))) {
                assertFalse(manager.getMessage(locale, key).contains("Missing") && manager.getList(locale, key).getFirst().contains("Missing"), key);
            }
        }
    }
}
