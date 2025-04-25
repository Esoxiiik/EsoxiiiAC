package com.anticheatsystem.checks.combat;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Sprawdzenie wykrywające graczy używających aimbot
 */
public class AimbotCheck extends Check {

    // Przechowuje ostatnie rotacje graczy
    private final Map<UUID, List<RotationData>> rotationHistory = new HashMap<>();
    
    // Maksymalna liczba rotacji do przechowywania w historii
    private static final int MAX_HISTORY_SIZE = 20;
    
    public AimbotCheck(AntiCheatMain plugin) {
        super(plugin, "aimbot", "combat");
    }
    
    /**
     * Sprawdza, czy gracz używa aimbot
     * 
     * @param player Gracz do sprawdzenia
     * @param target Cel ataku
     */
    public void checkAimbot(Player player, Entity target) {
        UUID playerId = player.getUniqueId();
        Location playerLoc = player.getLocation();
        
        // Oblicz różnicę kątów między aktualnym skierowaniem gracza a idealnym skierowaniem na cel
        Vector playerDirection = playerLoc.getDirection();
        Vector targetDirection = target.getLocation().toVector().subtract(playerLoc.toVector()).normalize();
        
        // Oblicz kąt między wektorami (w stopniach)
        double angle = Math.toDegrees(Math.acos(playerDirection.dot(targetDirection)));
        
        // Pobierz historię rotacji dla gracza
        List<RotationData> history = rotationHistory.computeIfAbsent(playerId, k -> new ArrayList<>());
        
        // Dodaj aktualną rotację do historii
        RotationData currentData = new RotationData(playerLoc.getYaw(), playerLoc.getPitch(), angle, System.currentTimeMillis());
        history.add(currentData);
        
        // Ogranicz rozmiar historii
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }
        
        // Jeśli mamy wystarczająco dużo próbek, przeprowadź analizę
        if (history.size() >= 5) {
            analyzeRotationPatterns(player, history);
        }
    }
    
    /**
     * Analizuje wzorce rotacji gracza
     */
    private void analyzeRotationPatterns(Player player, List<RotationData> history) {
        // Sprawdź 1: Zbyt precyzyjne celowanie
        checkPrecisionAiming(player, history);
        
        // Sprawdź 2: Nienaturalnie szybkie rotacje
        checkSnapAiming(player, history);
    }
    
    /**
     * Sprawdza, czy gracz ma zbyt precyzyjne celowanie (wskazujące na aimbot)
     */
    private void checkPrecisionAiming(Player player, List<RotationData> history) {
        // Policz, ile razy gracz celował niemal idealnie (mały kąt błędu)
        int precisionHits = 0;
        
        for (RotationData data : history) {
            if (data.angleToTarget < 5.0) { // mniej niż 5 stopni błędu
                precisionHits++;
            }
        }
        
        // Jeśli więcej niż 80% uderzeń było bardzo precyzyjnych
        if (precisionHits >= history.size() * 0.8) {
            String details = String.format("bardzo_precyzyjne_celowanie: %d/%d celnych ataków", 
                    precisionHits, history.size());
            
            // Poziom naruszenia zależy od stopnia precyzji
            int violationLevel = Math.min(5, maxViolationsPerCheck);
            
            flag(player, violationLevel, details);
        }
    }
    
    /**
     * Sprawdza, czy gracz wykonuje nienaturalnie szybkie i precyzyjne rotacje (snap aiming)
     */
    private void checkSnapAiming(Player player, List<RotationData> history) {
        // Szukaj nagłych dużych zmian w rotacji
        int snapCount = 0;
        
        for (int i = 1; i < history.size(); i++) {
            RotationData prev = history.get(i - 1);
            RotationData curr = history.get(i);
            
            // Oblicz różnicę w rotacji
            float yawDiff = Math.abs(angleDifference(curr.yaw, prev.yaw));
            float pitchDiff = Math.abs(curr.pitch - prev.pitch);
            
            // Oblicz czas między pomiarami
            long timeDiff = curr.timestamp - prev.timestamp;
            
            // Oblicz szybkość rotacji w stopniach na sekundę
            double rotationSpeed = (yawDiff + pitchDiff) * 1000.0 / timeDiff;
            
            // Jeśli rotacja jest szybka i celna
            if (rotationSpeed > 500 && curr.angleToTarget < 10.0) {
                snapCount++;
            }
        }
        
        // Jeśli wykryto zbyt wiele nagłych rotacji
        if (snapCount >= 3) {
            String details = String.format("snap_aiming: %d nagłych rotacji", snapCount);
            
            // Poziom naruszenia zależy od liczby nagłych rotacji
            int violationLevel = Math.min(snapCount, maxViolationsPerCheck);
            
            flag(player, violationLevel, details);
        }
    }
    
    /**
     * Oblicza różnicę między dwoma kątami, uwzględniając przejścia przez 0/360
     */
    private float angleDifference(float angle1, float angle2) {
        float diff = Math.abs(angle1 - angle2) % 360;
        return diff > 180 ? 360 - diff : diff;
    }
    
    /**
     * Klasa przechowująca dane o rotacji gracza
     */
    private static class RotationData {
        final float yaw;
        final float pitch;
        final double angleToTarget;
        final long timestamp;
        
        RotationData(float yaw, float pitch, double angleToTarget, long timestamp) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.angleToTarget = angleToTarget;
            this.timestamp = timestamp;
        }
    }
}