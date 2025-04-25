package com.anticheatsystem.manager;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import com.anticheatsystem.checks.combat.AimbotCheck;
import com.anticheatsystem.checks.combat.AutoClickerCheck;
import com.anticheatsystem.checks.combat.KillAuraCheck;
import com.anticheatsystem.checks.combat.ReachCheck;
import com.anticheatsystem.checks.misc.DisablerCheck;
import com.anticheatsystem.checks.movement.FlyCheck;
import com.anticheatsystem.checks.movement.JesusCheck;
import com.anticheatsystem.checks.movement.SpeedCheck;
import com.anticheatsystem.checks.movement.TeleportCheck;
import com.anticheatsystem.checks.movement.TimerCheck;
import com.anticheatsystem.checks.player.InventoryCheck;
import com.anticheatsystem.checks.player.NukerCheck;
import com.anticheatsystem.checks.player.ScaffoldCheck;
import com.anticheatsystem.checks.player.XRayCheck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Zarządza wszystkimi sprawdzeniami (checks) systemu anti-cheat
 */
public class CheckManager {

    private final AntiCheatMain plugin;
    private final Map<String, Check> checks = new HashMap<>();
    private final Map<String, List<Check>> categoryChecks = new HashMap<>();
    
    public CheckManager(AntiCheatMain plugin) {
        this.plugin = plugin;
        registerChecks();
    }
    
    /**
     * Zarejestruj wszystkie sprawdzenia
     */
    private void registerChecks() {
        // Rejestracja sprawdzeń ruchu
        registerCheck(new FlyCheck(plugin));
        registerCheck(new SpeedCheck(plugin));
        registerCheck(new TeleportCheck(plugin));
        registerCheck(new JesusCheck(plugin));
        registerCheck(new TimerCheck(plugin));
        
        // Rejestracja sprawdzeń walki
        registerCheck(new AimbotCheck(plugin));
        registerCheck(new AutoClickerCheck(plugin));
        registerCheck(new ReachCheck(plugin));
        registerCheck(new KillAuraCheck(plugin));
        
        // Rejestracja sprawdzeń gracza
        registerCheck(new XRayCheck(plugin));
        registerCheck(new InventoryCheck(plugin));
        registerCheck(new ScaffoldCheck(plugin));
        registerCheck(new NukerCheck(plugin));
        
        // Rejestracja sprawdzeń różnych
        registerCheck(new DisablerCheck(plugin));
        
        plugin.getPluginLogger().info("Zarejestrowano " + checks.size() + " sprawdzeń");
    }
    
    /**
     * Rejestruje nowe sprawdzenie
     * 
     * @param check Sprawdzenie do zarejestrowania
     */
    public void registerCheck(Check check) {
        String checkName = check.getName();
        String checkCategory = check.getCategory();
        
        // Dodaj do głównej mapy
        checks.put(checkName, check);
        
        // Dodaj do mapy kategorii
        List<Check> categoryList = categoryChecks.computeIfAbsent(checkCategory, k -> new ArrayList<>());
        categoryList.add(check);
        
        plugin.getPluginLogger().info("Zarejestrowano sprawdzenie: " + checkName);
    }
    
    /**
     * Pobierz określone sprawdzenie po nazwie
     * 
     * @param name Nazwa sprawdzenia (np. "movement.fly")
     * @return Sprawdzenie lub null jeśli nie znaleziono
     */
    public Check getCheck(String name) {
        return checks.get(name);
    }
    
    /**
     * Pobierz wszystkie sprawdzenia
     * 
     * @return Lista wszystkich sprawdzeń
     */
    public List<Check> getAllChecks() {
        return new ArrayList<>(checks.values());
    }
    
    /**
     * Pobierz wszystkie sprawdzenia dla określonej kategorii
     * 
     * @param category Nazwa kategorii (np. "movement")
     * @return Lista sprawdzeń w tej kategorii
     */
    public List<Check> getChecksByCategory(String category) {
        return categoryChecks.getOrDefault(category, new ArrayList<>());
    }
    
    /**
     * Sprawdź, czy określone sprawdzenie jest włączone
     * 
     * @param name Nazwa sprawdzenia
     * @return true jeśli sprawdzenie jest włączone, false w przeciwnym razie
     */
    public boolean isCheckEnabled(String name) {
        return plugin.getConfigManager().isCheckEnabled(name);
    }
    
    /**
     * Przeładuj wszystkie sprawdzenia
     */
    public void reloadChecks() {
        for (Check check : getAllChecks()) {
            check.reload();
        }
        plugin.getPluginLogger().info("Przeładowano wszystkie sprawdzenia");
    }
}