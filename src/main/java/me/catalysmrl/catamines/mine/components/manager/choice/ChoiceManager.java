package me.catalysmrl.catamines.mine.components.manager.choice;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.concurrent.ThreadLocalRandom;

public class ChoiceManager<T extends Identifiable & Choice> {

    private T current;
    private T upcoming;

    private final List<T> choices = new ArrayList<>();
    private final Consumer<T> onAdd;

    public ChoiceManager() {
        this(choice -> {});
    }

    public ChoiceManager(Consumer<T> onAdd) {
        this.onAdd = Objects.requireNonNull(onAdd);
    }

    public ChoiceManager(List<T> choices) {
        this();
        choices.forEach(this::add);
    }

    public void next() {
        if (choices.contains(upcoming)) current = upcoming;
        upcoming = null;
        // Weights are mutable. Recompute instead of caching a stale total after
        // a removal or a chance edit, which previously left deleted regions queued.
        double max = choices.stream().mapToDouble(Choice::getChance)
                .filter(weight -> Double.isFinite(weight) && weight > 0).sum();
        if (!Double.isFinite(max) || max <= 0) return;
        double randomValue = ThreadLocalRandom.current().nextDouble(max);
        double offset = 0;

        for (T choice : choices) {
            if (!Double.isFinite(choice.getChance()) || choice.getChance() <= 0) continue;
            offset += choice.getChance();
            if (randomValue < offset) {
                upcoming = choice;
                return;
            }
        }
    }

    public void add(T choice) {
        Objects.requireNonNull(choice);
        if (contains(choice.getName())) throw new IllegalArgumentException("Duplicate choice: " + choice.getName());
        onAdd.accept(choice);
        choices.add(choice);
        if (upcoming == null && Double.isFinite(choice.getChance()) && choice.getChance() > 0)
            upcoming = choice;
    }

    public void remove(T choice) {
        choices.remove(choice);
        if (current == choice) current = null;
        if (upcoming == choice) {
            upcoming = null;
            chooseUpcoming();
        }
    }

    private void chooseUpcoming() {
        T active = current;
        next();
        current = active;
    }

    public Optional<T> get(String name) {
        return getChoices().stream().filter(choice -> choice.getName().equals(name)).findFirst();
    }

    public Optional<T> getCurrent() {
        return choices.contains(current) ? Optional.of(current) : Optional.empty();
    }

    public void setCurrent(T current) {
        if (current != null && !choices.contains(current)) throw new IllegalArgumentException("Unknown current choice");
        this.current = current;
    }

    public Optional<T> getUpcoming() {
        if (!choices.contains(upcoming) || !Double.isFinite(upcoming.getChance()) || upcoming.getChance() <= 0) chooseUpcoming();
        return Optional.ofNullable(upcoming);
    }

    public void setUpcoming(T upcoming) {
        if (upcoming != null && !choices.contains(upcoming)) throw new IllegalArgumentException("Unknown upcoming choice");
        this.upcoming = upcoming;
    }

    public List<T> getChoices() {
        return java.util.Collections.unmodifiableList(choices);
    }
    
    public boolean contains(String name) {
        return get(name).isPresent();
    }

    @Override
    public String toString() {
        return "ChoiceManager{" +
                "current=" + current +
                ", upcoming=" + upcoming +
                ", choices=" + choices +
                '}';
    }
}
