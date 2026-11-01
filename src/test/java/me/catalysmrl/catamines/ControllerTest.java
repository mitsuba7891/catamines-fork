package me.catalysmrl.catamines;

import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.mine.components.manager.choice.ChoiceManager;
import me.catalysmrl.catamines.mine.components.manager.controller.CataMineController;
import me.catalysmrl.catamines.mine.components.region.CataMineRegion;
import me.catalysmrl.catamines.utils.helper.CountdownFormatter;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ControllerTest {
    private CataMine mine;
    private CataMineController controller;
    @BeforeEach void setup() {
        mine = mock(CataMine.class);
        ChoiceManager<CataMineRegion> regions = new ChoiceManager<>();
        CataMineRegion region = mock(CataMineRegion.class);
        when(region.getName()).thenReturn("region"); when(region.getChance()).thenReturn(100d);
        regions.add(region); regions.setCurrent(region);
        when(mine.getRegionManager()).thenReturn(regions);
        controller = new CataMineController(mine);
        when(mine.getController()).thenReturn(controller);
    }
    @Test void timerRegeneratesOnExactZeroAndNotOneSecondLate() {
        controller.setResetDelay(3);
        controller.tick(); controller.tick(); verify(mine, never()).reset(any());
        controller.tick(); verify(mine).reset(null);
    }
    @Test void pendingResetFreezesTimerAndDoesNotQueueDuplicates() {
        controller.setResetDelay(1); controller.setResetting(true);
        for (int i = 0; i < 100; i++) controller.tick();
        assertEquals(1, controller.getCountdown()); verify(mine, never()).reset(any());
    }
    @Test void percentageUsesRemainingNonAirBlocksAndSurvivesPersistence() {
        controller.setResetMode(CataMineController.ResetMode.PERCENTAGE);
        controller.setResetPercentage(20); controller.resetCompleted(10);
        for (int i = 0; i < 7; i++) controller.blockBroken();
        controller.tick(); verify(mine, never()).reset(any());
        controller.blockBroken(); controller.tick(); verify(mine).reset(null);
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        controller.serialize(yaml);
        var restored = CataMineController.deserialize(yaml, mine);
        assertEquals(2, restored.getBlockCount()); assertEquals(10, restored.getTotalBlocks());
    }
    @Test void combinedModeResetsWhenEitherConditionIsMet() {
        controller.setResetMode(CataMineController.ResetMode.TIME_PERCENTAGE);
        controller.setResetDelay(30); controller.resetCompleted(10); controller.setBlockCount(1);
        controller.tick(); verify(mine).reset(null);
    }
    @Test void failedResetRetriesWithoutPermanentlyStoppingMine() {
        controller.setResetDelay(1); controller.resetFailed();
        for (int i = 0; i < 5; i++) controller.tick();
        verify(mine, never()).reset(any()); controller.tick(); verify(mine).reset(null);
    }
    @Test void disabledDelayAndInvalidPercentagesAreSafe() {
        controller.setResetDelay(0); controller.tick(); verify(mine, never()).reset(any());
        assertThrows(IllegalArgumentException.class, () -> controller.setResetPercentage(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> controller.setResetPercentage(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> controller.setResetDelay(-2));
    }
    @Test void numericSecondsAndLocalizedUnitDoNotGetConfused() {
        when(mine.getDisplayName()).thenReturn("Test");
        assertEquals("5 segundos", CountdownFormatter.format("%seconds% %time%", 5, java.util.Locale.forLanguageTag("es"), mine));
        assertEquals("1 second", CountdownFormatter.format("%seconds% %time%", 1, java.util.Locale.ENGLISH, mine));
        assertEquals("01:01:01", CountdownFormatter.clock(3661));
    }
}
