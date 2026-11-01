package me.catalysmrl.catamines.mine.components.composition.drop;

import me.catalysmrl.catamines.mine.components.composition.CataMineBlock;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public final class DropTable {
    private DropTable() {}

    /** Each entry is an independent percentage roll; SINGLE stops after the first successful entry. */
    public static List<ItemStack> roll(CataMineBlock block, int fortune, RandomGenerator random) {
        List<ItemStack> result = new ArrayList<>();
        for (CataMineItem entry : block.getItems()) {
            if (entry.getChance() <= 0 || random.nextDouble(100) >= entry.getChance()) continue;
            ItemStack item = entry.getItem().clone();
            if (item.getType().isAir() || item.getAmount() <= 0) continue;
            int multiplier = entry.isFortune() ? Math.max(1, random.nextInt(Math.clamp(fortune, 0, 10) + 2)) : 1;
            int remaining = Math.clamp((long) item.getAmount() * multiplier, 1, 4096);
            while (remaining > 0) {
                ItemStack stack = item.clone();
                stack.setAmount(Math.min(remaining, stack.getMaxStackSize()));
                remaining -= stack.getAmount();
                result.add(stack);
            }
            if (block.getDropType() == CataMineBlock.DropType.SINGLE) break;
        }
        return result;
    }
}
