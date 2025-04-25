package com.anticheatsystem;

import com.anticheatsystem.commands.AntiCheatCommand;
import com.anticheatsystem.config.ConfigManager;
import com.anticheatsystem.listeners.PlayerListener;
import com.anticheatsystem.manager.CheckManager;
import com.anticheatsystem.manager.ViolationManager;
import com.anticheatsystem.protections.AntiCrash;
import com.anticheatsystem.protections.PacketLimiter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Główna klasa pluginu AntiCheat
 */
public class AntiCheatMain extends JavaPlugin {

    private ConfigManager configManager;
    private ViolationManager violationManager;
    private CheckManager checkManager;
    private List<String> adminNotifications = new ArrayList<>();
    
    @Override
    public void onEnable() {
        // Zapisz domyślną konfigurację, jeśli nie istnieje
        saveDefaultConfig();
        
        // Inicjalizuj managerów
        configManager = new ConfigManager(this);
        violationManager = new ViolationManager(this);
        checkManager = new CheckManager(this);
        
        // Zarejestruj listenery
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new AntiCrash(this), this);
        getServer().getPluginManager().registerEvents(new PacketLimiter(this), this);
        
        // Zarejestruj komendę
        getCommand("anticheat").setExecutor(new AntiCheatCommand(this));
        getCommand("anticheat").setTabCompleter(new AntiCheatCommand(this));
        
        // Uruchom zadanie wysyłające powiadomienia
        startNotificationTask();
        
        getLogger().info("AntiCheat został pomyślnie uruchomiony!");
    }
    
    @Override
    public void onDisable() {
        // Zapisz dane o naruszeniach
        if (violationManager != null) {
            violationManager.saveViolationData();
        }
        
        getLogger().info("AntiCheat został wyłączony!");
    }
    
    /**
     * Pobiera menedżera konfiguracji
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * Pobiera menedżera naruszeń
     */
    public ViolationManager getViolationManager() {
        return violationManager;
    }
    
    /**
     * Pobiera menedżera sprawdzeń
     */
    public CheckManager getCheckManager() {
        return checkManager;
    }
    
    /**
     * Pobiera logger pluginu
     */
    public Logger getPluginLogger() {
        return getLogger();
    }
    
    /**
     * Wysyła sformatowaną wiadomość
     */
    public void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.RED + "[AntiCheat] " + ChatColor.WHITE + message);
    }
    
    /**
     * Dodaje powiadomienie dla administratorów do kolejki
     */
    public void notifyAdmins(String message) {
        String formattedMessage = ChatColor.RED + "[AntiCheat] " + ChatColor.WHITE + message;
        
        // Dodaj do kolejki powiadomień
        synchronized (adminNotifications) {
            adminNotifications.add(formattedMessage);
        }
        
        // Zapisz do dziennika
        getLogger().info("[Admin] " + message);
    }
    
    /**
     * Uruchamia zadanie okresowo wysyłające powiadomienia administratorom
     */
    private void startNotificationTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            List<String> notifications;
            
            // Pobierz i wyczyść kolejkę powiadomień
            synchronized (adminNotifications) {
                if (adminNotifications.isEmpty()) {
                    return;
                }
                
                notifications = new ArrayList<>(adminNotifications);
                adminNotifications.clear();
            }
            
            // Wyślij powiadomienia do administratorów
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("anticheat.notify")) {
                    for (String notification : notifications) {
                        player.sendMessage(notification);
                    }
                }
            }
        }, 20L, 20L); // Co sekundę
    }
}