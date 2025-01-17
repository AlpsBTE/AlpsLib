package com.alpsbte.alpslib.libpsterra.core;

import java.util.ArrayList;
import java.util.List;

import com.alpsbte.alpslib.io.config.ConfigNotImplementedException;
import com.alpsbte.alpslib.libpsterra.core.config.PSInitializer;
import com.alpsbte.alpslib.libpsterra.utils.Utils;
import com.alpsbte.alpslib.utils.head.AlpsHeadEventListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.ipvp.canvas.MenuFunctionListener;
import com.alpsbte.alpslib.libpsterra.commands.CMD_CreatePlot;
import com.alpsbte.alpslib.libpsterra.commands.CMD_PastePlot;
import com.alpsbte.alpslib.libpsterra.core.config.ConfigManager;
import com.alpsbte.alpslib.libpsterra.core.config.ConfigPaths;
import com.alpsbte.alpslib.libpsterra.core.config.DataMode;
import com.alpsbte.alpslib.libpsterra.core.plotsystem.PlotCreator;
import com.alpsbte.alpslib.libpsterra.core.plotsystem.PlotPaster;
import com.alpsbte.alpslib.libpsterra.utils.FTPManager;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import org.jetbrains.annotations.NotNull;

public class PSTerraSetup {

    public static JavaPlugin plugin;

    public PlotCreator plotCreator;
    public PlotPaster plotPaster;
    public Connection connection;
    public ConfigManager configManager;

    /**
     * creates/instantiates all the necessary classes and event listeners required for plugin operation
     *
     * @param plugin The plugin instance
     * @throws Exception If an error occurs during setup
     */
    public static PSTerraSetup setupPlugin(@NotNull JavaPlugin plugin, @NotNull String version) throws Exception{
        return setupPlugin(plugin, version, new PSInitializer(plugin));
    }

    /**
     * creates/instantiates all the necessary classes and event listeners required for plugin operation
     *
     * @param javaPlugin The plugin instance
     * @param version The version of the plugin
     * @param psInitializer The plugin initializer that can be used by other plugins to initialize the plugin
     * @throws Exception If an error occurs during setup
     */
    public static PSTerraSetup setupPlugin(@NotNull JavaPlugin javaPlugin, @NotNull String version, @NotNull PSInitializer psInitializer) throws RuntimeException, ConfigNotImplementedException, ClassNotFoundException {
        plugin = javaPlugin;

        boolean consoleOutput = psInitializer.isDefaultInitializer() || psInitializer.isConsoleOutput();

        PSTerraSetup result = new PSTerraSetup();

        String successPrefix = ChatColor.DARK_GRAY + "[" + ChatColor.DARK_GREEN + "✔" + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY;
        String errorPrefix = ChatColor.DARK_GRAY + "[" + ChatColor.RED + "X" + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY;

        Utils.sendConsoleMessage(ChatColor.GOLD + "--------------- Plot-System-Terra V" + version + " ----------------", consoleOutput);
        Utils.sendConsoleMessage(ChatColor.DARK_GREEN + "Starting plugin...", consoleOutput);
        Utils.sendConsoleMessage(" ", consoleOutput);

        // Check for required dependencies, if it returns false disable plugin
        if (!DependencyManager.checkForRequiredDependencies(javaPlugin)) {
            Utils.sendConsoleMessage(errorPrefix + "Could not load required dependencies.", consoleOutput);
            Utils.sendConsoleMessage(ChatColor.YELLOW + "Missing Dependencies:", consoleOutput);

            boolean finalConsoleOutput = consoleOutput;
            DependencyManager.missingDependencies.forEach(dependency -> Utils.sendConsoleMessage(ChatColor.YELLOW + " - " + dependency, finalConsoleOutput));
            throw new RuntimeException("Could not load required dependencies");
        }
        Utils.sendConsoleMessage(successPrefix + "Successfully loaded required dependencies.", consoleOutput);

        // Load config
        result.configManager = new ConfigManager(javaPlugin, psInitializer);
        Utils.sendConsoleMessage(successPrefix + "Successfully loaded configuration file.", consoleOutput);
        result.configManager.reloadConfigs();
        FileConfiguration configFile = result.configManager.getConfig();

        // Set up PSInitializer with config file if default initializer is used
        if(psInitializer.isDefaultInitializer()){
            psInitializer = new PSInitializer(plugin, configFile);
            consoleOutput = psInitializer.isConsoleOutput();
        }


        // Initialize connection

        FTPManager.setDebugMode(psInitializer.isDevMode());
        Utils.sendConsoleMessage(successPrefix + "datamode:"+ configFile.getString(ConfigPaths.DATA_MODE), consoleOutput);
        String dataMode = configFile.getString(ConfigPaths.DATA_MODE);
        if(dataMode == null)
            throw new RuntimeException("Data Mode not set in config.yml file.");
        if(dataMode.equalsIgnoreCase(DataMode.DATABASE.toString())){

            String URL = configFile.getString(ConfigPaths.DATABASE_URL);
            String name = configFile.getString(ConfigPaths.DATABASE_NAME);
            String username = configFile.getString(ConfigPaths.DATABASE_USERNAME);
            String password = configFile.getString(ConfigPaths.DATABASE_PASSWORD);
            String teamApiKey = configFile.getString(ConfigPaths.API_KEY);
            
            result.connection = new DatabaseConnection(URL, name, username, password, teamApiKey);// DatabaseConnection.InitializeDatabase();
            Utils.sendConsoleMessage(successPrefix + "Successfully initialized database connection.", consoleOutput);
        }else{
            String teamApiKey = psInitializer.getAPIKey();
            String apiHost = psInitializer.getAPIHost();
            int apiPort = psInitializer.getAPIPort();

            result.connection = new NetworkAPIConnection(apiHost, apiPort, teamApiKey);
            Utils.sendConsoleMessage(successPrefix + "Successfully initialized API connection.", consoleOutput);
        }
        if (result.connection == null)
            throw new RuntimeException("Connection initialization failed");


        //check FTP
        if(psInitializer.isDevMode()) {
            try {
                FTPManager.testSFTPConnection_VFS2(result.connection, consoleOutput);
                Utils.sendConsoleMessage(successPrefix + "Successfully tested FTP connection.", consoleOutput);
            }catch (Exception e){
                Utils.sendConsoleMessage(errorPrefix + "Could not test FTP connection.", consoleOutput);
                e.printStackTrace();
            }
        }

        // Register event listeners
        javaPlugin.getServer().getPluginManager().registerEvents(new MenuFunctionListener(), javaPlugin);
        javaPlugin.getServer().getPluginManager().registerEvents(new AlpsHeadEventListener(), javaPlugin);
        Utils.sendConsoleMessage(successPrefix + "Successfully registered event listeners.", consoleOutput);

        result.plotCreator = new PlotCreator(javaPlugin, configFile, result.connection, psInitializer.getAbsolutePluginConfigPath());

        // Start checking for plots to paste
        String worldName = psInitializer.getWorldName();
        if(worldName == null)
            worldName = configFile.getString(ConfigPaths.WORLD_NAME);
        if(worldName == null)
            throw new RuntimeException("World Name not set in config file.");

        result.plotPaster = new PlotPaster(javaPlugin, configFile, result.connection, worldName, psInitializer.getAbsolutePluginConfigPath(), consoleOutput);
        result.plotPaster.start();



        // Register commands

        javaPlugin.getCommand("createplot").setExecutor(new CMD_CreatePlot(psInitializer, result.plotCreator, result.connection, configFile));
        javaPlugin.getCommand("pasteplot").setExecutor(new CMD_PastePlot(result.plotPaster, result.connection, configFile));

        Utils.sendConsoleMessage(successPrefix + "Successfully registered commands.", consoleOutput);


        return result;
    }

    public static JavaPlugin getPlugin() {
        return plugin;
    }

    public static class DependencyManager {

        // List with all missing dependencies
        private final static List<String> missingDependencies = new ArrayList<>();

        /**
         * Check for all required dependencies and inform in console about missing dependencies
         * @return True if all dependencies are present
         */
        private static boolean checkForRequiredDependencies(Plugin plugin) {
            PluginManager pluginManager = plugin.getServer().getPluginManager();

            if (!pluginManager.isPluginEnabled("WorldEdit")) {
                missingDependencies.add("WorldEdit (V6.1.9)");
            }

            if (!pluginManager.isPluginEnabled("HeadDatabase")) {
                missingDependencies.add("HeadDatabase");
            }

            return missingDependencies.isEmpty();
        }

        /**
         * Checks if the plugin "FastAsyncWorldEdit" is enabled.
         *
         * @return True if FastAsyncWorldEdit is enabled, false if it is not enabled
         */
        public static boolean isFastAsyncWorldEditEnabled() {
            return getPlugin().getServer().getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
        }

        /**
         * @return World Edit instance
         */
        public static WorldEdit getWorldEdit() {
            return WorldEdit.getInstance();
        }

        /**
         * @return World Edit Plugin
         */
        public static WorldEditPlugin getWorldEditPlugin() {
            return (WorldEditPlugin) Bukkit.getServer().getPluginManager().getPlugin("WorldEdit");
        }
    }
};
