package com.alpsbte.alpslib.libpsterra.core.plotsystem;

import com.alpsbte.alpslib.libpsterra.core.Connection;
import com.alpsbte.alpslib.libpsterra.core.config.ConfigPaths;
import com.alpsbte.alpslib.libpsterra.utils.FTPManager;
import com.alpsbte.alpslib.libpsterra.utils.Utils;
import com.alpsbte.alpslib.libpsterra.utils.WorldEditUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.List;
import java.util.logging.Level;

public class PlotPaster extends Thread {

    private final String serverName;

    public final boolean fastMode;
    private final int pasteInterval;
    public final String worldName;
    private final boolean broadcastMessages;
    private Plugin plugin;
    private Connection connection;
    private final String schematicsPath;

    private boolean consoleOutput;

    public PlotPaster(Plugin plugin, FileConfiguration config, Connection connection, String worldName, String pluginConfigPath, boolean consoleOutput) {
        this.plugin = plugin;
        this.connection = connection;
        this.consoleOutput = consoleOutput;

        this.serverName = config.getString(ConfigPaths.SERVER_NAME);
        this.fastMode = config.getBoolean(ConfigPaths.FAST_MODE);
        this.worldName = worldName;
        this.pasteInterval = config.getInt(ConfigPaths.PASTING_INTERVAL);
        this.broadcastMessages = config.getBoolean(ConfigPaths.BROADCAST_INFO);
        this.schematicsPath = Paths.get(pluginConfigPath, "schematics") + File.separator;
    }

    @Override
    public void run() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            try {

                List<Plot> pastePlots = connection.getCompletedAndUnpastedPlots();
                int pastedPlots = 0;

                for (Plot plot : pastePlots){
                        
                    int plotID = plot.id;
                    try{
                        CityProject city = connection.getCityProject(plot.city_project_id);
                        Server server = connection.getServer(city);
                        String cityServerName = server.name;

                            
                        if (cityServerName.equals(serverName)) {
                            boolean successful = pastePlotSchematic(plotID, city, Bukkit.getWorld(worldName), plot.mc_coordinates, plot.version, fastMode);

                            if(successful)
                                pastedPlots++;
                            else
                                Utils.sendConsoleError("Failed to paste plot #" + plotID + "!", null, consoleOutput);
                        }//endif city is on this server
                        
                    } catch (Exception ex) {
                        Utils.sendConsoleError("An error occurred while pasting plot #" + plotID + "!", ex, consoleOutput);
                    }

                }//endfor plots

                if (broadcastMessages && pastedPlots != 0) {
                    Bukkit.broadcastMessage("§7§l>§a Pasted §6" + pastedPlots + " §aplot" + (pastedPlots > 1 ? "s" : "") + "!");
                }

            } catch (Exception ex) {
                    Utils.sendConsoleError( "An error occurred while getting unpasted plots!", ex, consoleOutput);
            }
        }, 0L, 20L * pasteInterval);
    }

    public boolean pastePlotSchematic(int plotID, CityProject city, World world, Vector mcCoordinates, double plotVersion, boolean fastMode) throws Exception {


        Server server = connection.getServer(city);

        String schematicFileType = ".schematic";
        String schemFileType = ".schem";

        File outlineSchematic = Paths.get(schematicsPath, String.valueOf(server.id), String.valueOf(city.id), plotID + schemFileType).toFile();
        if (!outlineSchematic.exists())
            outlineSchematic = Paths.get(schematicsPath, String.valueOf(server.id), String.valueOf(city.id), plotID + schematicFileType).toFile();



        File completedSchematic = Paths.get(schematicsPath, String.valueOf(server.id), "finishedSchematics", String.valueOf(city.id), plotID + schemFileType).toFile();

        // Download from SFTP or FTP server if enabled
        FTPConfiguration ftpConfiguration = connection.getFTPConfiguration(city);
        if (ftpConfiguration != null) {
            Files.deleteIfExists(completedSchematic.toPath());

            if (!FTPManager.downloadSchematic(FTPManager.getFTPUrl(ftpConfiguration, server, city), completedSchematic)) {
                completedSchematic = Paths.get(schematicsPath, String.valueOf(server.id), "finishedSchematics", String.valueOf(city.id), plotID + schematicFileType).toFile();
                Files.deleteIfExists(completedSchematic.toPath());
                FTPManager.downloadSchematic(FTPManager.getFTPUrl(ftpConfiguration, server, city), completedSchematic);
            }
        }

        if (outlineSchematic.exists() && completedSchematic.exists()) {
            WorldEditUtil.pasteSchematic(outlineSchematic, completedSchematic, world, mcCoordinates, plotVersion, fastMode);

            connection.setPlotPasted(plotID);
        } else {
            Bukkit.getLogger().log(Level.WARNING, "Could not find schematic file(s) of plot #" + plotID + "!");
            return false;
        }

        return true;
    }
}
