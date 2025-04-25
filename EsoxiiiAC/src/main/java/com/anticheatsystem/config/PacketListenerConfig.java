package com.anticheatsystem.config;

import com.anticheatsystem.AntiCheatMain;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Klasa zarządzająca konfiguracją nasłuchiwania pakietów
 */
public class PacketListenerConfig {
    
    private final AntiCheatMain plugin;
    private final File configFile;
    private FileConfiguration config;
    
    // Ustawienia nasłuchiwania pakietów
    private boolean packetListeningEnabled = false;
    private boolean debugMode = false;
    
    // Mapowanie pakietów do włączenia/wyłączenia
    private final Map<String, Boolean> enabledPackets = new HashMap<>();
    
    /**
     * Konstruktor konfiguracji nasłuchiwania pakietów
     * 
     * @param plugin Główna instancja pluginu
     */
    public PacketListenerConfig(AntiCheatMain plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "packet_config.yml");
        
        // Załaduj konfigurację
        loadConfig();
    }
    
    /**
     * Ładuje lub tworzy plik konfiguracyjny
     */
    public void loadConfig() {
        if (!configFile.exists()) {
            // Utwórz katalog, jeśli nie istnieje
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            
            // Inicjalizuj nową konfigurację
            config = new YamlConfiguration();
            
            // Ustaw domyślne wartości
            config.set("packet_listener.enabled", false);
            config.set("packet_listener.debug_mode", false);
            
            // Domyślna konfiguracja pakietów
            ConfigurationSection packetsSection = config.createSection("packet_listener.packets");
            packetsSection.set("POSITION", true);
            packetsSection.set("POSITION_LOOK", true);
            packetsSection.set("LOOK", true);
            packetsSection.set("FLYING", true);
            packetsSection.set("USE_ENTITY", true);
            packetsSection.set("BLOCK_DIG", true);
            packetsSection.set("BLOCK_PLACE", true);
            packetsSection.set("ARM_ANIMATION", true);
            packetsSection.set("ABILITIES", true);
            packetsSection.set("CUSTOM_PAYLOAD", true);
            packetsSection.set("TRANSACTION", true);
            packetsSection.set("ENTITY_ACTION", true);
            packetsSection.set("KEEP_ALIVE", true);
            
            // Zapisz plik
            try {
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Nie można zapisać domyślnej konfiguracji pakietów: " + e.getMessage());
            }
        } else {
            // Załaduj istniejącą konfigurację
            config = YamlConfiguration.loadConfiguration(configFile);
        }
        
        // Wczytaj ustawienia
        packetListeningEnabled = config.getBoolean("packet_listener.enabled", false);
        debugMode = config.getBoolean("packet_listener.debug_mode", false);
        
        // Wczytaj konfigurację pakietów
        ConfigurationSection packetsSection = config.getConfigurationSection("packet_listener.packets");
        if (packetsSection != null) {
            for (String packetType : packetsSection.getKeys(false)) {
                enabledPackets.put(packetType, packetsSection.getBoolean(packetType, true));
            }
        }
    }
    
    /**
     * Zapisuje konfigurację do pliku
     */
    public void saveConfig() {
        try {
            config.set("packet_listener.enabled", packetListeningEnabled);
            config.set("packet_listener.debug_mode", debugMode);
            
            // Zapisz konfigurację pakietów
            for (Map.Entry<String, Boolean> entry : enabledPackets.entrySet()) {
                config.set("packet_listener.packets." + entry.getKey(), entry.getValue());
            }
            
            // Zapisz plik
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie można zapisać konfiguracji pakietów: " + e.getMessage());
        }
    }
    
    /**
     * Włącza lub wyłącza nasłuchiwanie pakietów
     * 
     * @param enabled True aby włączyć, false aby wyłączyć
     */
    public void setPacketListeningEnabled(boolean enabled) {
        this.packetListeningEnabled = enabled;
        saveConfig();
    }
    
    /**
     * Włącza lub wyłącza tryb debugowania
     * 
     * @param enabled True aby włączyć, false aby wyłączyć
     */
    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
        saveConfig();
    }
    
    /**
     * Włącza lub wyłącza nasłuchiwanie konkretnego pakietu
     * 
     * @param packetType Typ pakietu
     * @param enabled True aby włączyć, false aby wyłączyć
     */
    public void setPacketEnabled(String packetType, boolean enabled) {
        enabledPackets.put(packetType, enabled);
        saveConfig();
    }
    
    /**
     * Sprawdza, czy nasłuchiwanie pakietów jest włączone
     * 
     * @return True jeśli włączone, false w przeciwnym razie
     */
    public boolean isPacketListeningEnabled() {
        return packetListeningEnabled;
    }
    
    /**
     * Sprawdza, czy tryb debugowania jest włączony
     * 
     * @return True jeśli włączony, false w przeciwnym razie
     */
    public boolean isDebugMode() {
        return debugMode;
    }
    
    /**
     * Sprawdza, czy nasłuchiwanie danego pakietu jest włączone
     * 
     * @param packetType Typ pakietu
     * @return True jeśli włączone, false w przeciwnym razie
     */
    public boolean isPacketEnabled(String packetType) {
        return enabledPackets.getOrDefault(packetType, true);
    }
    
    /**
     * Pobiera mapę włączonych/wyłączonych pakietów
     * 
     * @return Mapa pakietów
     */
    public Map<String, Boolean> getEnabledPackets() {
        return new HashMap<>(enabledPackets);
    }
}