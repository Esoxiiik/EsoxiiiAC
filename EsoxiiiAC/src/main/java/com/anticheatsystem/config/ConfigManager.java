package com.anticheatsystem.config;

import com.anticheatsystem.AntiCheatMain;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Zarządza konfiguracją pluginu
 */
public class ConfigManager {

    private final AntiCheatMain plugin;
    
    // Przechowuje ogólne ustawienia
    private boolean enabled;
    private int maxViolations;
    private boolean logViolations;
    
    // Przechowuje nazwy wyłączonych światów
    private final List<String> exemptWorlds = new ArrayList<>();
    
    // Przechowuje konfiguracje sprawdzeń
    private final Map<String, CheckConfig> checkConfigs = new HashMap<>();
    
    // Przechowuje akcje dla różnych poziomów naruszeń
    private final List<ActionStep> actions = new ArrayList<>();
    
    public ConfigManager(AntiCheatMain plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    /**
     * Ładuje konfigurację z pliku
     */
    public void loadConfig() {
        // Pobierz konfigurację z pliku
        FileConfiguration config = plugin.getConfig();
        
        // Załaduj ogólne ustawienia
        enabled = config.getBoolean("enabled", true);
        maxViolations = config.getInt("max-violations", 50);
        logViolations = config.getBoolean("log-violations", true);
        
        // Załaduj wyłączone światy
        exemptWorlds.clear();
        exemptWorlds.addAll(config.getStringList("exempt-worlds"));
        
        // Załaduj konfiguracje sprawdzeń
        loadCheckConfigs(config);
        
        // Załaduj akcje
        loadActions(config);
        
        plugin.getPluginLogger().info("Konfiguracja załadowana pomyślnie");
    }
    
    /**
     * Przeładowuje konfigurację z pliku
     */
    public void reloadConfig() {
        plugin.reloadConfig();
        loadConfig();
    }
    
    /**
     * Ładuje konfiguracje sprawdzeń
     */
    private void loadCheckConfigs(FileConfiguration config) {
        checkConfigs.clear();
        
        ConfigurationSection checksSection = config.getConfigurationSection("checks");
        if (checksSection == null) {
            plugin.getPluginLogger().warning("Brak sekcji 'checks' w konfiguracji");
            return;
        }
        
        // Przetwórz kategorie sprawdzeń
        for (String category : checksSection.getKeys(false)) {
            ConfigurationSection categorySection = checksSection.getConfigurationSection(category);
            
            if (categorySection != null) {
                // Przetwórz poszczególne sprawdzenia w kategorii
                for (String check : categorySection.getKeys(false)) {
                    ConfigurationSection checkSection = categorySection.getConfigurationSection(check);
                    
                    if (checkSection != null) {
                        String fullName = category + "." + check;
                        boolean checkEnabled = checkSection.getBoolean("enabled", true);
                        int sensitivity = checkSection.getInt("sensitivity", 3);
                        int maxViolationsPerCheck = checkSection.getInt("max-violations", 10);
                        
                        // Dodaj konfigurację sprawdzenia
                        checkConfigs.put(fullName, new CheckConfig(
                                fullName, checkEnabled, sensitivity, maxViolationsPerCheck));
                    }
                }
            }
        }
    }
    
    /**
     * Ładuje akcje dla różnych poziomów naruszeń
     */
    private void loadActions(FileConfiguration config) {
        actions.clear();
        
        List<?> actionsList = config.getList("actions");
        if (actionsList == null) {
            plugin.getPluginLogger().warning("Brak sekcji 'actions' w konfiguracji");
            return;
        }
        
        for (Object obj : actionsList) {
            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> actionMap = (Map<String, Object>) obj;
                
                if (actionMap.containsKey("violations") && actionMap.containsKey("action")) {
                    int violations = (int) actionMap.get("violations");
                    String action = (String) actionMap.get("action");
                    
                    actions.add(new ActionStep(violations, action));
                }
            }
        }
        
        // Sortuj akcje według liczby naruszeń (od najmniejszej do największej)
        actions.sort((a1, a2) -> Integer.compare(a1.getViolations(), a2.getViolations()));
    }
    
    /**
     * Sprawdza, czy cały system jest włączony
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Pobiera maksymalną liczbę naruszeń przed podjęciem poważnych działań
     */
    public int getMaxViolations() {
        return maxViolations;
    }
    
    /**
     * Sprawdza, czy naruszenia mają być logowane do pliku
     */
    public boolean isLogViolations() {
        return logViolations;
    }
    
    /**
     * Sprawdza, czy świat jest wyłączony z sprawdzeń
     */
    public boolean isWorldExempt(String worldName) {
        return exemptWorlds.contains(worldName);
    }
    
    /**
     * Sprawdza, czy określone sprawdzenie jest włączone
     */
    public boolean isCheckEnabled(String checkName) {
        CheckConfig config = checkConfigs.get(checkName);
        return config != null && config.isEnabled() && enabled;
    }
    
    /**
     * Pobiera konfigurację sprawdzenia
     */
    public CheckConfig getCheckConfig(String checkName) {
        // Jeśli konfiguracja istnieje, zwróć ją
        if (checkConfigs.containsKey(checkName)) {
            return checkConfigs.get(checkName);
        }
        
        // W przeciwnym razie zwróć domyślną konfigurację
        plugin.getPluginLogger().warning("Brak konfiguracji dla sprawdzenia " + checkName + ". Używam domyślnej.");
        return new CheckConfig(checkName, true, 3, 10);
    }
    
    /**
     * Pobiera akcję dla określonej liczby naruszeń
     */
    public ActionStep getActionForViolations(int violations) {
        ActionStep lastMatchingAction = null;
        
        // Znajdź najwyższą akcję, która pasuje do liczby naruszeń
        for (ActionStep action : actions) {
            if (violations >= action.getViolations()) {
                lastMatchingAction = action;
            } else {
                // Akcje są posortowane, więc jeśli ta nie pasuje, to następne też nie będą pasować
                break;
            }
        }
        
        return lastMatchingAction;
    }
}