package me.catalysmrl.catamines;

import me.catalysmrl.catamines.command.CommandManager;
import me.catalysmrl.catamines.listeners.BlockListeners;
import me.catalysmrl.catamines.managers.MineManager;
import me.catalysmrl.catamines.utils.helper.CompatibilityProvider;
import me.catalysmrl.catamines.utils.message.LocaleBootstrap;
import me.catalysmrl.catamines.utils.placeholders.CataMinePlaceHolders;
import me.catalysmrl.catamines.api.rewards.RewardManager;
import me.catalysmrl.catamines.api.rewards.parser.RewardParser;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class CataMines extends JavaPlugin {

    private static CataMines INSTANCE;

    public static CataMines getInstance() {
        return INSTANCE;
    }

    private MineManager mineManager;
    private CommandManager commandManager;
    private RewardManager rewardManager;
    private RewardParser rewardParser;

    @Override
    public void onLoad() {
        INSTANCE = this;

        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        CompatibilityProvider.checkCompatibility();

        new LocaleBootstrap(this).init();

        mineManager = new MineManager(this);
        
        // Setup Reward Engine
        rewardManager = new RewardManager();
        rewardParser = new RewardParser(rewardManager);
        
        // Register default Handlers
        rewardManager.registerAction("actionbar", new me.catalysmrl.catamines.mine.rewards.actions.ActionBarAction());
        rewardManager.registerAction("command", new me.catalysmrl.catamines.mine.rewards.actions.CommandAction());
        rewardManager.registerAction("giveitem", new me.catalysmrl.catamines.mine.rewards.actions.GiveItemAction());
        
        rewardManager.registerTargeter("trigger", new me.catalysmrl.catamines.mine.rewards.targeters.TriggerTargeter());
        rewardManager.registerTargeter("playersinradius", new me.catalysmrl.catamines.mine.rewards.targeters.PlayersInRadiusTargeter());
        
        rewardManager.registerCondition("haspermission", new me.catalysmrl.catamines.mine.rewards.conditions.PermissionCondition());

        registerCommands();
        registerListeners();

        if (CompatibilityProvider.isPapiEnabled()) {
            new CataMinePlaceHolders(this).register();
        }

        setupMetrics();
    }

    @Override
    public void onDisable() {
        commandManager = null;

        // Properly disable MineManager
        if (mineManager != null) mineManager.shutDown();
        mineManager = null;
        INSTANCE = null;
    }

    private void setupMetrics() {
        final Metrics metrics = new Metrics(this, 12889);
        metrics.addCustomChart(new SimplePie("we_implementation",
                () -> CompatibilityProvider.isFaweEnabled() ? "FastAsyncWorldEdit" : "WorldEdit"));

        metrics.addCustomChart(new SingleLineChart("mines", () -> mineManager.getMines().size()));
    }

    private void registerCommands() {
        commandManager = new CommandManager(this);

        PluginCommand command = getCommand("catamines");
        if (command == null) {
            getLogger().severe("***************************************");
            getLogger().severe("Could not register commands. All plugin");
            getLogger().severe("functions may still work apart from commands");
            getLogger().severe("***************************************");
            return;
        }

        command.setExecutor(commandManager);
    }

    private void registerListeners() {
        getLogger().info("Registering listeners");
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new BlockListeners(mineManager), this);
        pm.registerEvents(new me.catalysmrl.catamines.gui.GuiListener(), this);
        pm.registerEvents(new me.catalysmrl.catamines.mine.rewards.listeners.RewardListener(this), this);
    }

    public MineManager getMineManager() {
        return mineManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public RewardParser getRewardParser() {
        return rewardParser;
    }
}
