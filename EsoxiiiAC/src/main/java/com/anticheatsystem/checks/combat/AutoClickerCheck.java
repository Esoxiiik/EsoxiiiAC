package com.anticheatsystem.checks.combat;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

/**
 * Sprawdzenie wykrywające graczy używających auto-clickera
 */
public class AutoClickerCheck extends Check {

    private static final int MAX_ALLOWED_CPS = 20; // Maksymalna liczba kliknięć na sekundę
    private static final int HISTORY_SIZE = 10; // Liczba próbek do analizy
    
    // Przechowuje historię CPS dla każdego gracza
    private final Map<UUID, LinkedList<Integer>> cpsHistory = new HashMap<>();
    
    public AutoClickerCheck(AntiCheatMain plugin) {
        super(plugin, "autoclicker", "combat");
    }
    
    /**
     * Sprawdza, czy gracz używa auto-clickera
     * 
     * @param player Gracz do sprawdzenia
     * @param currentCPS Aktualne kliknięcia na sekundę
     */
    public void checkAutoClicker(Player player, int currentCPS) {
        UUID playerId = player.getUniqueId();
        
        // Pobierz historię CPS dla tego gracza
        LinkedList<Integer> history = cpsHistory.computeIfAbsent(playerId, k -> new LinkedList<>());
        
        // Dodaj aktualny CPS do historii
        history.addLast(currentCPS);
        
        // Ogranicz rozmiar historii
        while (history.size() > HISTORY_SIZE) {
            history.removeFirst();
        }
        
        // Jeśli mamy wystarczająco dużo próbek, przeprowadź analizę
        if (history.size() >= HISTORY_SIZE) {
            // Sprawdź maksymalny CPS
            int maxCPS = history.stream().mapToInt(Integer::intValue).max().orElse(0);
            
            // Sprawdź, czy maksymalny CPS przekracza limit
            if (maxCPS > MAX_ALLOWED_CPS) {
                String details = "max_cps=" + maxCPS + ", limit=" + MAX_ALLOWED_CPS;
                
                // Oblicz poziom naruszenia na podstawie przekroczenia
                int violationLevel = Math.min(maxCPS - MAX_ALLOWED_CPS, maxViolationsPerCheck);
                
                flag(player, violationLevel, details);
                return;
            }
            
            // Sprawdź czy wzorzec kliknięć jest zbyt regularny (typowe dla auto-clickerów)
            checkPattern(player, history);
        }
    }
    
    /**
     * Sprawdza wzorzec kliknięć pod kątem regularności
     */
    private void checkPattern(Player player, LinkedList<Integer> history) {
        // Oblicz średnią
        double mean = history.stream().mapToInt(Integer::intValue).average().orElse(0);
        
        // Oblicz wariancję
        double variance = history.stream()
                .mapToDouble(cps -> Math.pow(cps - mean, 2))
                .average()
                .orElse(0);
        
        // Oblicz odchylenie standardowe
        double stdDev = Math.sqrt(variance);
        
        // Jeśli odchylenie standardowe jest bardzo małe, to wzorzec jest zbyt regularny
        // Co sugeruje auto-clicker (ludzie mają naturalną zmienność)
        if (stdDev < 0.5 && mean > 8) {
            String details = String.format("regularny_wzorzec: mean=%.2f, stdDev=%.2f", mean, stdDev);
            
            // Poziom naruszenia zależy od regularności
            int violationLevel = (int) Math.ceil((1.0 / (stdDev + 0.1)) * 2);
            violationLevel = Math.min(violationLevel, maxViolationsPerCheck);
            
            flag(player, violationLevel, details);
        }
        
        // Sprawdź czy nie ma idealnie stałej liczby kliknięć przez długi czas
        boolean allSame = history.stream().distinct().count() == 1 && history.getFirst() > 5;
        if (allSame) {
            String details = "identyczne_cps=" + history.getFirst() + " przez " + history.size() + " sekund";
            
            // Stała liczba kliknięć jest bardzo podejrzana
            int violationLevel = Math.min(10, maxViolationsPerCheck);
            flag(player, violationLevel, details);
        }
    }
}