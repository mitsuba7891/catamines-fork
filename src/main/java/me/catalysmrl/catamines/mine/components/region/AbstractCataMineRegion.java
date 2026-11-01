package me.catalysmrl.catamines.mine.components.region;

import me.catalysmrl.catamines.api.mine.CataMine;
import me.catalysmrl.catamines.api.mine.Flag;
import me.catalysmrl.catamines.api.mine.PropertyHolder;
import me.catalysmrl.catamines.api.serialization.DeserializationException;
import me.catalysmrl.catamines.api.rewards.Reward;
import me.catalysmrl.catamines.mine.components.MineFlags;
import me.catalysmrl.catamines.mine.components.composition.CataMineComposition;
import me.catalysmrl.catamines.mine.components.manager.choice.ChoiceManager;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractCataMineRegion implements CataMineRegion, Cloneable {

    protected CataMine mine;

    protected String name;
    protected double chance = 100.0d;
    protected ChoiceManager<CataMineComposition> compositionManager;
    protected List<Reward> rewards = new ArrayList<>();

    protected MineFlags flags = new MineFlags();

    public AbstractCataMineRegion(String name) {
        this.name = name;
        this.compositionManager = new ChoiceManager<>(composition -> composition.setRegion(this));
    }

    @Override
    public void serialize(ConfigurationSection section) {
        section.set("region-type", getType().toString());
        section.set("name", name);
        section.set("chance", chance);
        section.set("current-composition", compositionManager.getCurrent().map(CataMineComposition::getName).orElse(null));
        flags.serialize(section.createSection("flags"));
        if (!rewards.isEmpty()) {
            ConfigurationSection rewardsSection = section.createSection("rewards");
            for (Reward r : rewards) r.serialize(rewardsSection.createSection(r.getId()));
        }
    }

    public void serializeCompositions(ConfigurationSection compositionsSection) {
        for (int i = 0; i < compositionManager.getChoices().size(); i++) {
            compositionManager.getChoices().get(i).serialize(compositionsSection.createSection("composition-" + i));
        }
    }

    public static List<CataMineComposition> deserializeCompositions(ConfigurationSection compositionsSection)
            throws DeserializationException {
        List<CataMineComposition> compositionList = new ArrayList<>();
        for (String key : compositionsSection.getKeys(false)) {
            ConfigurationSection compositionSection = compositionsSection.getConfigurationSection(key);
            if (compositionSection == null)
                continue;
            compositionList.add(CataMineComposition.deserialize(compositionSection));
        }

        return compositionList;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        if (!Double.isFinite(chance) || chance < 0) throw new IllegalArgumentException("Invalid region weight");
        this.chance = chance;
    }

    @Override
    public ChoiceManager<CataMineComposition> getCompositionManager() {
        return compositionManager;
    }

    @Override
    public PropertyHolder getParent() {
        return mine;
    }

    public void setMine(CataMine mine) {
        this.mine = mine;
    }

    protected void cancelPendingReset() {
        if (mine != null && mine.getPlugin() != null && mine.getPlugin().getMineManager() != null) {
            mine.getPlugin().getMineManager().cancelReset(mine);
        }
    }

    @Override
    public void fill() {
        if (mine == null) throw new IllegalStateException("Region has no owning mine");
        mine.getPlugin().getMineManager().resetRegion(this);
    }

    protected void deserializeCommon(ConfigurationSection section) throws DeserializationException {
        flags = MineFlags.deserialize(section.getConfigurationSection("flags"));
        ConfigurationSection rewardSection = section.getConfigurationSection("rewards");
        if (rewardSection != null) {
            for (String id : rewardSection.getKeys(false)) {
                addReward(Reward.deserialize(id, rewardSection.getConfigurationSection(id)));
            }
        }
        ConfigurationSection compositions = section.getConfigurationSection("compositions");
        if (compositions != null) deserializeCompositions(compositions).forEach(compositionManager::add);
        compositionManager.get(section.getString("current-composition", "")).ifPresent(compositionManager::setCurrent);
    }

    @Override
    public <T> T getFlag(Flag<T> flag) {
        return flags.get(flag);
    }

    @Override
    public <T> void setFlag(Flag<T> flag, T value) {
        flags.set(flag, value);
    }

    @Override
    public boolean hasFlag(Flag<?> flag) {
        return flags.get(flag) != null;
    }

    @Override
    public List<Reward> getRewards() {
        return rewards;
    }

    @Override
    public void addReward(Reward reward) {
        if (reward != null && !rewards.contains(reward)) {
            rewards.add(reward);
        }
    }

    @Override
    public void removeReward(Reward reward) {
        rewards.remove(reward);
    }

    @Override
    public AbstractCataMineRegion clone() {
        try {
            AbstractCataMineRegion clone = (AbstractCataMineRegion) super.clone();
            clone.flags = this.flags.clone();
            clone.rewards = new ArrayList<>(this.rewards);
            clone.compositionManager = new ChoiceManager<>(composition -> composition.setRegion(clone));
            for (CataMineComposition comp : this.compositionManager.getChoices()) {
                CataMineComposition copy = comp.clone();
                clone.compositionManager.add(copy);
                if (compositionManager.getCurrent().orElse(null) == comp) clone.compositionManager.setCurrent(copy);
                if (compositionManager.getUpcoming().orElse(null) == comp) clone.compositionManager.setUpcoming(copy);
            }
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public String toString() {
        return "AbstractCataMineRegion{" +
                "name='" + name + '\'' +
                ", chance=" + chance +
                ", compositionManager=" + compositionManager +
                '}';
    }
}
