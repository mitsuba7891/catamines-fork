package me.catalysmrl.catamines.mine.components.composition.drop;

import me.catalysmrl.catamines.api.serialization.DeserializationException;
import me.catalysmrl.catamines.api.serialization.SectionSerializable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public class CataMineItem implements SectionSerializable, Cloneable {

    private ItemStack item;
    private double chance = 100;
    private boolean fortune;

    public CataMineItem(ItemStack item) {
        this.item = java.util.Objects.requireNonNull(item).clone();
    }

    @Override
    public void serialize(ConfigurationSection section) {
        section.set("item", item.clone());
        section.set("chance", chance);
        section.set("fortune", fortune);
    }

    public static CataMineItem deserialize(ConfigurationSection section) throws DeserializationException {
        ItemStack item = section.getItemStack("item");
        // Read maps written by the unfinished v3 serializer as well as Bukkit's tagged ItemStack form.
        if (item == null && section.isConfigurationSection("item")) {
            try { item = ItemStack.deserialize(section.getConfigurationSection("item").getValues(false)); }
            catch (RuntimeException e) { throw new DeserializationException("Invalid legacy item map", e); }
        }
        if (item == null) throw new DeserializationException("Could not deserialize item");

        double chance = section.getDouble("chance", 100d);
        boolean fortune = section.getBoolean("fortune", false);

        CataMineItem mineItem = new CataMineItem(item);
        mineItem.setChance(chance);
        mineItem.setFortune(fortune);

        return mineItem;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        if (!Double.isFinite(chance) || chance < 0 || chance > 100) throw new IllegalArgumentException("Drop chance must be 0-100");
        this.chance = chance;
    }

    public boolean isFortune() {
        return fortune;
    }

    public void setFortune(boolean fortune) {
        this.fortune = fortune;
    }

    @Override
    public CataMineItem clone() {
        CataMineItem copy = new CataMineItem(item);
        copy.chance = chance;
        copy.fortune = fortune;
        return copy;
    }
}
