package me.catalysmrl.catamines.managers.blockmanagers;

import org.bukkit.plugin.java.JavaPlugin;

/** FAWE may batch its edits internally; plugin state and Bukkit callbacks stay on the server thread. */
public class FastAsyncBlockApplicationManager extends BukkitBlockApplicationManager {

    public FastAsyncBlockApplicationManager(JavaPlugin plugin) {
        super(plugin);
    }

}
