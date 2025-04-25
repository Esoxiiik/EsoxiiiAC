package com.anticheatsystem.checks;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.config.CheckConfig;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Abstrakcyjna klasa bazowa dla wszystkich sprawdzeń anti-cheat
 */
public abstract class Check {

    protected final AntiCheatMain plugin;
    protected final String name;
    protected final String category;
    protected final String fullName;
    
    // Flaga wskazująca, czy sprawdzenie jest aktualnie włączone
    protected boolean enabled;
    
    // Czułość sprawdzenia (wyższa wartość = mniej fałszywych alarmów)
    protected int sensitivity;
    
    // Maksymalna liczba naruszeń dla tego sprawdzenia
    protected int maxViolationsPerCheck;
    
    // Czy to sprawdzenie powinno cofać gracza po wykryciu oszustwa
    protected boolean setbackEnabled;
    
    // Mapa do przechowywania ostatnich bezpiecznych lokalizacji graczy
    private static final Map<UUID, Location> safeLocations = new HashMap<>();
    
    // Cooldown na setback dla każdego gracza (aby zapobiec zbyt częstym cofnięciom)
    private static final Map<UUID, Long> setbackCooldowns = new HashMap<>();
    
    // Domyślny czas cooldownu setbacka w milisekundach
    private static final long DEFAULT_SETBACK_COOLDOWN = 1000; // 1 sekunda
    
    /**
     * Konstruktor dla sprawdzenia
     * 
     * @param plugin Instancja głównego pluginu
     * @param name Nazwa sprawdzenia (np. "fly")
     * @param category Kategoria sprawdzenia (np. "movement")
     */
    public Check(AntiCheatMain plugin, String name, String category) {
        this.plugin = plugin;
        this.name = name;
        this.category = category;
        this.fullName = category + "." + name;
        
        // Załaduj konfigurację
        reload();
    }
    
    /**
     * Przeładuj konfigurację tego sprawdzenia
     */
    public void reload() {
        CheckConfig config = plugin.getConfigManager().getCheckConfig(fullName);
        this.enabled = config.isEnabled();
        this.sensitivity = config.getSensitivity();
        this.maxViolationsPerCheck = config.getMaxViolationsPerCheck();
        this.setbackEnabled = config.isSetbackEnabled();
    }
    
    /**
     * Zgłoś naruszenie dla gracza
     * 
     * @param player Gracz, który naruszył zasady
     * @param violationLevel Liczba punktów naruszenia do dodania
     * @param message Szczegóły naruszenia
     * @return Łączna liczba naruszeń gracza po dodaniu tego
     */
    protected int flag(Player player, int violationLevel, String message) {
        // Sprawdź, czy check jest włączony
        if (!isEnabled()) {
            return 0;
        }
        
        // Sprawdź, czy świat jest wyłączony
        if (plugin.getConfigManager().isWorldExempt(player.getWorld().getName())) {
            return 0;
        }
        
        // Sprawdź uprawnienia do omijania (nowy system)
        if (hasAnyBypassPermission(player)) {
            return 0;
        }
        
        // Zgłoś naruszenie
        int totalVl = plugin.getViolationManager().addViolation(player.getName(), fullName, 
                violationLevel, message);
        
        // Wykonaj setback, jeśli jest włączony i osiągnięto odpowiedni VL
        if (setbackEnabled && totalVl >= 3) {
            performSetback(player);
        }
        
        return totalVl;
    }
    
    /**
     * Aktualizuje bezpieczną pozycję dla gracza.
     * Powinna być wywoływana, gdy gracz jest w legalnej pozycji.
     * 
     * @param player Gracz
     */
    protected void updateSafeLocation(Player player) {
        // Zapisz bezpieczną lokalizację tylko jeśli gracz jest na ziemi
        if (player.isOnGround()) {
            Location loc = player.getLocation().clone();
            safeLocations.put(player.getUniqueId(), loc);
        }
    }
    
    /**
     * Cofa gracza do ostatniej bezpiecznej pozycji
     * 
     * @param player Gracz do cofnięcia
     * @return true jeśli setback został wykonany, false w przeciwnym razie
     */
    protected boolean performSetback(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Sprawdź cooldown
        long currentTime = System.currentTimeMillis();
        if (setbackCooldowns.containsKey(playerId)) {
            long lastSetback = setbackCooldowns.get(playerId);
            if (currentTime - lastSetback < DEFAULT_SETBACK_COOLDOWN) {
                return false; // Jeszcze nie minął cooldown
            }
        }
        
        // Sprawdź czy mamy bezpieczną lokalizację
        if (!safeLocations.containsKey(playerId)) {
            // Jeśli nie mamy bezpiecznej lokalizacji, spróbuj użyć aktualnej, ale z zerowaniem prędkości
            player.setVelocity(new Vector(0, 0, 0));
            return false;
        }
        
        // Wykonaj setback
        Location safeLocation = safeLocations.get(playerId);
        player.teleport(safeLocation);
        player.setVelocity(new Vector(0, 0, 0));
        
        // Aktualizuj cooldown
        setbackCooldowns.put(playerId, currentTime);
        
        return true;
    }
    
    /**
     * Sprawdza, czy gracz ma jakiekolwiek uprawnienia do omijania tego sprawdzenia
     */
    private boolean hasAnyBypassPermission(Player player) {
        // Sprawdź ogólne uprawnienie omijania
        if (player.hasPermission("esoxiiiac.bypass")) {
            return true;
        }
        
        // Sprawdź uprawnienie dla kategorii
        if (player.hasPermission("esoxiiiac.bypass." + category)) {
            return true;
        }
        
        // Sprawdź uprawnienie dla konkretnego checka
        if (player.hasPermission("esoxiiiac.bypass." + category + "." + name)) {
            return true;
        }
        
        // Sprawdź stare uprawnienia (dla kompatybilności wstecznej)
        if (player.hasPermission("anticheat.bypass")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Sprawdź, czy to sprawdzenie jest włączone
     */
    public boolean isEnabled() {
        return enabled && plugin.getConfigManager().isEnabled();
    }
    
    /**
     * Pobierz nazwę tego sprawdzenia
     */
    public String getName() {
        return fullName;
    }
    
    /**
     * Pobierz krótką nazwę tego sprawdzenia (bez kategorii)
     */
    public String getShortName() {
        return name;
    }
    
    /**
     * Pobierz kategorię tego sprawdzenia
     */
    public String getCategory() {
        return category;
    }
    
    /**
     * Pobierz czułość tego sprawdzenia
     */
    public int getSensitivity() {
        return sensitivity;
    }
    
    /**
     * Pobierz maksymalną liczbę naruszeń dla tego sprawdzenia
     */
    public int getMaxViolationsPerCheck() {
        return maxViolationsPerCheck;
    }
    
    /**
     * Sprawdza, czy setback jest włączony dla tego sprawdzenia
     */
    public boolean isSetbackEnabled() {
        return setbackEnabled;
    }
    
    /**
     * Włącza lub wyłącza funkcję setback dla tego sprawdzenia
     * 
     * @param enabled true aby włączyć, false aby wyłączyć
     */
    public void setSetbackEnabled(boolean enabled) {
        this.setbackEnabled = enabled;
    }
}