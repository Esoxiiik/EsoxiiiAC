package com.anticheatsystem.manager;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.config.ActionStep;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Zarządza naruszeniami wykrytymi przez system anti-cheat
 */
public class ViolationManager {

    private final AntiCheatMain plugin;
    
    // Mapa naruszeń graczy: Nazwa gracza -> (Typ naruszenia -> Liczba naruszeń)
    private final Map<String, Map<String, Integer>> playerViolations = new ConcurrentHashMap<>();
    
    // Mapa podjętych ostatnio działań: Nazwa gracza -> (Poziom działania)
    private final Map<String, Integer> lastActionLevels = new ConcurrentHashMap<>();
    
    // Format daty dla logów
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    public ViolationManager(AntiCheatMain plugin) {
        this.plugin = plugin;
        
        // Załaduj dane o naruszeniach, jeśli plik istnieje
        loadViolationData();
    }
    
    /**
     * Zarejestruj naruszenie dla gracza
     * 
     * @param playerName Nazwa gracza
     * @param checkName Nazwa sprawdzenia (np. "movement.fly")
     * @param violationLevel Poziom naruszenia do dodania
     * @param message Wiadomość wyjaśniająca naruszenie
     * @return Łączna liczba naruszeń dla tego gracza
     */
    public int addViolation(String playerName, String checkName, int violationLevel, String message) {
        // Pobierz mapę naruszeń dla tego gracza
        Map<String, Integer> violations = playerViolations.computeIfAbsent(playerName, k -> new ConcurrentHashMap<>());
        
        // Pobierz aktualną liczbę naruszeń dla tego sprawdzenia
        int currentViolations = violations.getOrDefault(checkName, 0);
        
        // Dodaj nowe naruszenia
        violations.put(checkName, currentViolations + violationLevel);
        
        // Zapisz naruszenie do pliku, jeśli włączone
        if (plugin.getConfigManager().isLogViolations()) {
            logViolation(playerName, checkName, violationLevel, message);
        }
        
        // Powiadom administratorów
        plugin.notifyAdmins(playerName + " naruszył " + checkName + ": " + message + 
                " (+" + violationLevel + ", łącznie: " + (currentViolations + violationLevel) + ")");
        
        // Wykonaj odpowiednie działanie na podstawie całkowitej liczby naruszeń
        processActions(playerName);
        
        // Zwróć łączną liczbę naruszeń dla wszystkich sprawdzeń
        return getTotalViolations(playerName);
    }
    
    /**
     * Zapisz naruszenie do pliku dziennika
     */
    private void logViolation(String playerName, String checkName, int violationLevel, String message) {
        try {
            File logsDir = new File(plugin.getDataFolder(), "logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            
            String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            File logFile = new File(logsDir, date + ".log");
            
            try (FileWriter fw = new FileWriter(logFile, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {
                
                String logEntry = String.format("[%s] %s - %s (+%d): %s",
                        dateFormat.format(new Date()),
                        playerName,
                        checkName,
                        violationLevel,
                        message);
                
                out.println(logEntry);
            }
        } catch (IOException e) {
            plugin.getPluginLogger().log(Level.WARNING, "Nie można zapisać naruszenia do pliku dziennika", e);
        }
    }
    
    /**
     * Wykonaj odpowiednie działania na podstawie liczby naruszeń gracza
     */
    private void processActions(String playerName) {
        // Pobierz łączną liczbę naruszeń dla gracza
        int totalViolations = getTotalViolations(playerName);
        
        // Pobierz najwyższy poziom akcji, który został już wykonany
        int lastActionLevel = lastActionLevels.getOrDefault(playerName, 0);
        
        // Znajdź odpowiednie działanie dla aktualnej liczby naruszeń
        ActionStep action = plugin.getConfigManager().getActionForViolations(totalViolations);
        
        if (action != null) {
            // Sprawdź, czy to działanie jest wyższego poziomu niż ostatnio wykonane
            if (action.getViolations() > lastActionLevel) {
                // Wykonaj komendę
                String command = action.createCommand(playerName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                
                // Zaktualizuj poziom ostatniego działania
                lastActionLevels.put(playerName, action.getViolations());
                
                // Zapisz informację do dziennika
                plugin.getPluginLogger().info("Wykonano działanie przeciwko graczowi " + playerName + 
                        ": " + action.getAction() + " (naruszenia: " + totalViolations + ")");
            }
        }
    }
    
    /**
     * Pobierz łączną liczbę naruszeń dla gracza
     */
    public int getTotalViolations(String playerName) {
        Map<String, Integer> violations = playerViolations.get(playerName);
        if (violations == null) {
            return 0;
        }
        
        // Sumuj wszystkie naruszenia
        return violations.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    /**
     * Pobierz mapę naruszeń dla określonego gracza
     */
    public Map<String, Integer> getPlayerViolations(String playerName) {
        return new HashMap<>(playerViolations.getOrDefault(playerName, new HashMap<>()));
    }
    
    /**
     * Resetuj wszystkie naruszenia dla określonego gracza
     */
    public void resetPlayerViolations(String playerName) {
        playerViolations.remove(playerName);
        lastActionLevels.remove(playerName);
        plugin.getPluginLogger().info("Zresetowano naruszenia dla gracza " + playerName);
    }
    
    /**
     * Resetuj wszystkie naruszenia dla wszystkich graczy
     */
    public void resetAllViolations() {
        playerViolations.clear();
        lastActionLevels.clear();
        plugin.getPluginLogger().info("Zresetowano naruszenia dla wszystkich graczy");
    }
    
    /**
     * Zapisz dane o naruszeniach do pliku
     */
    public void saveViolationData() {
        try {
            File dataFile = new File(plugin.getDataFolder(), "violations.dat");
            
            try (FileOutputStream fos = new FileOutputStream(dataFile);
                 ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                
                // Zapisz mapę naruszeń i ostatnich działań
                oos.writeObject(new HashMap<>(playerViolations));
                oos.writeObject(new HashMap<>(lastActionLevels));
            }
        } catch (IOException e) {
            plugin.getPluginLogger().log(Level.WARNING, "Nie można zapisać danych o naruszeniach", e);
        }
    }
    
    /**
     * Załaduj dane o naruszeniach z pliku
     */
    @SuppressWarnings("unchecked")
    private void loadViolationData() {
        try {
            File dataFile = new File(plugin.getDataFolder(), "violations.dat");
            
            if (dataFile.exists()) {
                try (FileInputStream fis = new FileInputStream(dataFile);
                     ObjectInputStream ois = new ObjectInputStream(fis)) {
                    
                    // Wczytaj mapę naruszeń i ostatnich działań
                    Map<String, Map<String, Integer>> loadedViolations = 
                            (Map<String, Map<String, Integer>>) ois.readObject();
                    Map<String, Integer> loadedActionLevels = 
                            (Map<String, Integer>) ois.readObject();
                    
                    // Dodaj wczytane dane do naszych map
                    playerViolations.putAll(loadedViolations);
                    lastActionLevels.putAll(loadedActionLevels);
                    
                    plugin.getPluginLogger().info("Wczytano dane o naruszeniach dla " + 
                            loadedViolations.size() + " graczy");
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            plugin.getPluginLogger().log(Level.WARNING, "Nie można wczytać danych o naruszeniach", e);
        }
    }
    
    /**
     * Sprawdź, czy gracz ma zbyt wiele naruszeń i powinien zostać ukarany
     */
    public boolean hasTooManyViolations(String playerName) {
        int total = getTotalViolations(playerName);
        return total >= plugin.getConfigManager().getMaxViolations();
    }
}