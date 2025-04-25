package com.anticheatsystem.checks.combat;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sprawdzenie wykrywające graczy z niewłaściwym zasięgiem ataków
 */
public class ReachCheck extends Check {

    // Maksymalny zasięg ataku w trybie przetrwania
    private static final double MAX_REACH = 3.1D; // Vanilla: ~3.0
    
    // Tolerancja dla opóźnienia sieciowego
    private static final double LAG_COMPENSATION = 0.5D;
    
    // Przechowuje zasięgi ataków poszczególnych graczy
    private final Map<UUID, double[]> reachHistory = new HashMap<>();
    
    public ReachCheck(AntiCheatMain plugin) {
        super(plugin, "reach", "combat");
    }
    
    /**
     * Sprawdza, czy gracz atakuje z niedozwolonego zasięgu
     * 
     * @param player Atakujący gracz
     * @param target Cel ataku
     */
    public void checkReach(Player player, Entity target) {
        // Ignoruj graczy w trybie kreatywnym
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        
        // Oblicz odległość między graczem a celem
        double distance = player.getLocation().distance(target.getLocation());
        
        // Pobierz identyfikator gracza
        UUID playerId = player.getUniqueId();
        
        // Pobierz historię zasięgów gracza
        double[] history = reachHistory.getOrDefault(playerId, new double[5]);
        
        // Przesuń historię
        System.arraycopy(history, 0, history, 1, history.length - 1);
        history[0] = distance;
        
        // Zapisz zaktualizowaną historię
        reachHistory.put(playerId, history);
        
        // Oblicz średnią z ostatnich ataków
        double average = 0;
        int count = 0;
        for (double d : history) {
            if (d > 0) {
                average += d;
                count++;
            }
        }
        
        if (count > 0) {
            average /= count;
        }
        
        // Maksymalny dozwolony zasięg z uwzględnieniem tolerancji sieciowej
        double maxAllowedReach = MAX_REACH + (LAG_COMPENSATION / sensitivity);
        
        // Sprawdź, czy zasięg przekracza maksymalny dozwolony
        if (distance > maxAllowedReach) {
            String details = String.format("distance=%.2f, max=%.2f, avg=%.2f", 
                    distance, maxAllowedReach, average);
            
            // Ustal poziom naruszenia na podstawie przekroczenia
            int violationLevel = (int) Math.ceil((distance - maxAllowedReach) * 2);
            
            // Ogranicz do maksymalnej wartości dla tego sprawdzenia
            violationLevel = Math.min(violationLevel, maxViolationsPerCheck);
            
            flag(player, violationLevel, details);
        }
        // Sprawdź również średnią z historii, aby wykryć graczy, którzy czasami używają reach hacka
        else if (average > maxAllowedReach && count >= 3) {
            String details = String.format("avg_distance=%.2f, max=%.2f", 
                    average, maxAllowedReach);
            
            // Ustal poziom naruszenia na podstawie przekroczenia
            int violationLevel = (int) Math.ceil((average - maxAllowedReach) * 2);
            
            // Ogranicz do maksymalnej wartości dla tego sprawdzenia
            violationLevel = Math.min(violationLevel, maxViolationsPerCheck);
            
            flag(player, violationLevel, details);
        }
    }
}