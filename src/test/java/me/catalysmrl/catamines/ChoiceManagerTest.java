package me.catalysmrl.catamines;

import me.catalysmrl.catamines.mine.components.manager.choice.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChoiceManagerTest {
    private static final class Entry implements Choice, Identifiable {
        private final String name;
        private double weight;
        Entry(String name, double weight) { this.name = name; this.weight = weight; }
        public String getName() { return name; }
        public double getChance() { return weight; }
    }
    @Test void removedRegionCannotRemainUpcomingOrCurrent() {
        Entry a = new Entry("a", 100), b = new Entry("b", 100);
        var manager = new ChoiceManager<>(List.of(a, b));
        manager.setCurrent(a); manager.setUpcoming(a); manager.remove(a);
        assertTrue(manager.getCurrent().isEmpty());
        assertSame(b, manager.getUpcoming().orElseThrow());
        for (int i = 0; i < 100; i++) { manager.next(); assertSame(b, manager.getCurrent().orElseThrow()); }
    }
    @Test void zeroAndNonFiniteWeightsNeverRegenerate() {
        var manager = new ChoiceManager<>(List.of(new Entry("zero", 0), new Entry("nan", Double.NaN)));
        assertTrue(manager.getUpcoming().isEmpty());
    }
    @Test void editedWeightsAndParentCallbacksAreRespected() {
        Entry a = new Entry("a", 100), b = new Entry("b", 0);
        java.util.ArrayList<Entry> attached = new java.util.ArrayList<>();
        var manager = new ChoiceManager<Entry>(attached::add);
        manager.add(a); manager.add(b);
        a.weight = 0; b.weight = 100;
        assertSame(b, manager.getUpcoming().orElseThrow());
        assertEquals(List.of(a, b), attached);
        assertThrows(UnsupportedOperationException.class, () -> manager.getChoices().clear());
        assertThrows(IllegalArgumentException.class, () -> manager.setUpcoming(new Entry("outside", 100)));
    }
}
