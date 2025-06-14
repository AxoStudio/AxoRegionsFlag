package com.axo.axoregionsflag;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AxoRegionsFlag extends JavaPlugin implements Listener {

    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<String, Region> regions = new HashMap<>();
    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        loadRegions();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("axoregionsflag").setExecutor(new RegionCommandExecutor());
        getLogger().info("Wtyczka AxoRegionsFlag włączona.");
    }

    @Override
    public void onDisable() {
        saveRegions();
        getLogger().info("Wtyczka AxoRegionsFlag wyłączona.");
    }

    private void loadRegions() {
        ConfigurationSection regionsSection = config.getConfigurationSection("regions");
        if (regionsSection == null) return;

        for (String regionName : regionsSection.getKeys(false)) {
            ConfigurationSection regionSection = regionsSection.getConfigurationSection(regionName);
            if (regionSection == null) continue;

            ConfigurationSection pos1Section = regionSection.getConfigurationSection("pos1");
            ConfigurationSection pos2Section = regionSection.getConfigurationSection("pos2");
            if (pos1Section == null || pos2Section == null) continue;

            Location loc1 = new Location(
                    Bukkit.getWorld("world"),
                    pos1Section.getDouble("x"),
                    pos1Section.getDouble("y"),
                    pos1Section.getDouble("z")
            );
            Location loc2 = new Location(
                    Bukkit.getWorld("world"),
                    pos2Section.getDouble("x"),
                    pos2Section.getDouble("y"),
                    pos2Section.getDouble("z")
            );
            String command = regionSection.getString("command", "");
            int cooldown = regionSection.getInt("cooldown", 600);

            regions.put(regionName, new Region(loc1, loc2, command, cooldown));
        }
    }

    private void saveRegions() {
        config.set("regions", null);
        for (Map.Entry<String, Region> entry : regions.entrySet()) {
            String regionName = entry.getKey();
            Region region = entry.getValue();

            config.set("regions." + regionName + ".pos1.x", region.getPos1().getX());
            config.set("regions." + regionName + ".pos1.y", region.getPos1().getY());
            config.set("regions." + regionName + ".pos1.z", region.getPos1().getZ());
            config.set("regions." + regionName + ".pos2.x", region.getPos2().getX());
            config.set("regions." + regionName + ".pos2.y", region.getPos2().getY());
            config.set("regions." + regionName + ".pos2.z", region.getPos2().getZ());
            config.set("regions." + regionName + ".command", region.getCommand());
            config.set("regions." + regionName + ".cooldown", region.getCooldown());
        }
        saveConfig();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;

        for (Map.Entry<String, Region> entry : regions.entrySet()) {
            Region region = entry.getValue();
            if (region.isInRegion(to)) {
                long currentTime = System.currentTimeMillis();
                Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
                Long lastUsed = playerCooldowns.getOrDefault(entry.getKey(), 0L);

                if (currentTime - lastUsed >= region.getCooldown() * 1000L) {
                    String command = ChatColor.translateAlternateColorCodes('&', region.getCommand().replace("%player%", player.getName()));
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    playerCooldowns.put(entry.getKey(), currentTime);
                }
            }
        }
    }

    private class Region {
        private final Location pos1;
        private final Location pos2;
        private final String command;
        private final int cooldown;

        public Region(Location pos1, Location pos2, String command, int cooldown) {
            this.pos1 = pos1;
            this.pos2 = pos2;
            this.command = command;
            this.cooldown = cooldown;
        }

        public Location getPos1() { return pos1; }
        public Location getPos2() { return pos2; }
        public String getCommand() { return command; }
        public int getCooldown() { return cooldown; }

        public boolean isInRegion(Location loc) {
            double minX = Math.min(pos1.getX(), pos2.getX());
            double maxX = Math.max(pos1.getX(), pos2.getX());
            double minY = Math.min(pos1.getY(), pos2.getY());
            double maxY = Math.max(pos1.getY(), pos2.getY());
            double minZ = Math.min(pos1.getZ(), pos2.getZ());
            double maxZ = Math.max(pos1.getZ(), pos2.getZ());

            return loc.getX() >= minX && loc.getX() <= maxX &&
                    loc.getY() >= minY && loc.getY() <= maxY &&
                    loc.getZ() >= minZ && loc.getZ() <= maxZ &&
                    loc.getWorld().equals(pos1.getWorld());
        }
    }

    private class RegionCommandExecutor implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Komenda tylko dla graczy!");
                return true;
            }

            if (!player.hasPermission("axoregionsflag.admin")) {
                player.sendMessage(ChatColor.RED + "Brak uprawnień!");
                return true;
            }

            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Użycie: /axoregionsflag <pos1|pos2|create|remove|list> [args]");
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "pos1":
                    pos1.put(player.getUniqueId(), player.getLocation());
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aUstawiono pos1 na " + player.getLocation().toVector()));
                    break;
                case "pos2":
                    pos2.put(player.getUniqueId(), player.getLocation());
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aUstawiono pos2 na " + player.getLocation().toVector()));
                    break;
                case "create":
                    if (args.length < 3) {
                        player.sendMessage(ChatColor.RED + "Użycie: /axoregionsflag create <nazwa> <komenda>");
                        return true;
                    }
                    Location loc1 = pos1.get(player.getUniqueId());
                    Location loc2 = pos2.get(player.getUniqueId());
                    if (loc1 == null || loc2 == null) {
                        player.sendMessage(ChatColor.RED + "Najpierw ustaw pos1 i pos2!");
                        return true;
                    }
                    String regionName = args[1];
                    if (regions.containsKey(regionName)) {
                        player.sendMessage(ChatColor.RED + "Region o tej nazwie już istnieje!");
                        return true;
                    }
                    StringBuilder commandStr = new StringBuilder();
                    for (int i = 2; i < args.length; i++) {
                        commandStr.append(args[i]).append(" ");
                    }
                    regions.put(regionName, new Region(loc1, loc2, commandStr.toString().trim(), 600));
                    saveRegions();
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aStworzono region " + regionName));
                    break;
                case "remove":
                    if (args.length != 2) {
                        player.sendMessage(ChatColor.RED + "Użycie: /axoregionsflag remove <nazwa>");
                        return true;
                    }
                    if (regions.remove(args[1]) != null) {
                        saveRegions();
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aUsunięto region " + args[1]));
                    } else {
                        player.sendMessage(ChatColor.RED + "Region nie istnieje!");
                    }
                    break;
                case "list":
                    if (regions.isEmpty()) {
                        player.sendMessage(ChatColor.YELLOW + "Brak regionów.");
                    } else {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aRegiony:"));
                        for (String name : regions.keySet()) {
                            player.sendMessage(ChatColor.YELLOW + "- " + name);
                        }
                    }
                    break;
                default:
                    player.sendMessage(ChatColor.RED + "Nieznana akcja! Użycie: /axoregionsflag <pos1|pos2|create|remove|list>");
            }
            return true;
        }
    }
}