package com.anticheatsystem.checks.movement;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * Sprawdzenie wykrywające nielegalne latanie w trybie przetrwania
 */
public class FlyCheck extends Check {

    private static final double GRAVITY = 0.08D;
    private static final double AIR_FRICTION = 0.98D;
    
    public FlyCheck(AntiCheatMain plugin) {
        super(plugin, "fly", "movement");
    }
    
    /**
     * Sprawdza, czy gracz używa niedozwolonego latania
     * 
     * @param player Gracz do sprawdzenia
     * @param from Poprzednia lokalizacja
     * @param to Nowa lokalizacja
     * @param timeDelta Czas w ms od ostatniego ruchu
     */
    public void checkFly(Player player, Location from, Location to, long timeDelta) {
        // Ignoruj, jeśli gracze mają pozwolenie na latanie
        if (player.getAllowFlight() || player.getGameMode() == GameMode.CREATIVE 
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        
        // Ignoruj, jeśli gracz ma efekty eliksirów wpływające na ruch
        if (player.hasPotionEffect(PotionEffectType.LEVITATION) || 
                player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            return;
        }
        
        // Pobierz zmiany w pozycji
        double deltaY = to.getY() - from.getY();
        
        // Ignoruj małe zmiany w pozycji
        if (Math.abs(deltaY) < 0.05) {
            return;
        }
        
        // Sprawdź, czy gracz znajduje się w wodzie lub lawie (co tłumaczy powolne opadanie)
        Block block = to.getBlock();
        Material blockType = block.getType();
        if (blockType == Material.WATER || blockType == Material.LAVA) {
            return;
        }
        
        // Sprawdź, czy gracz jest na drabinie lub pnączach
        if (blockType == Material.LADDER || blockType == Material.VINE) {
            return;
        }
        
        // Sprawdź, czy gracz jest na ziemi lub w pobliżu bloku
        if (isNearGround(player)) {
            return;
        }
        
        // Sprawdź, czy zmiana w pozycji Y jest zgodna z grawitacją
        boolean suspiciousY = false;
        
        // Jeśli gracz się wznosi (deltaY > 0)
        if (deltaY > 0) {
            // Jeśli gracz nie jest na początku skoku (velocity Y <= 0), to może to być oszustwo
            if (player.getVelocity().getY() <= 0) {
                suspiciousY = true;
            }
        } 
        // Jeśli gracz spada (deltaY < 0)
        else {
            // Oblicz przewidywaną prędkość spadania na podstawie grawitacji
            double expectedVelocityY = player.getVelocity().getY() * AIR_FRICTION - GRAVITY;
            
            // Jeśli faktyczna zmiana pozycji jest znacznie mniejsza niż przewidywana
            // (gracz spada zbyt wolno), może to być oszustwo
            if (Math.abs(deltaY) < Math.abs(expectedVelocityY) * 0.8) {
                suspiciousY = true;
            }
        }
        
        // Zgłoś naruszenie, jeśli wykryto podejrzany ruch w osi Y
        if (suspiciousY) {
            // Dostosuj czułość - im wyższa czułość, tym mniej fałszywych alarmów
            double threshold = 0.1 * sensitivity;
            
            // Jeśli różnica między faktycznym a oczekiwanym ruchem jest znaczna
            double expectedVelocityY = player.getVelocity().getY() * AIR_FRICTION - GRAVITY;
            double diff = Math.abs(deltaY - expectedVelocityY);
            
            if (diff > threshold) {
                String details = String.format("deltaY=%.2f, expectedY=%.2f, diff=%.2f", 
                        deltaY, expectedVelocityY, diff);
                
                // Ustal poziom naruszenia na podstawie różnicy
                int violationLevel = (int) Math.ceil(diff * 10);
                
                // Ogranicz do maksymalnej wartości dla tego sprawdzenia
                violationLevel = Math.min(violationLevel, maxViolationsPerCheck);
                
                flag(player, violationLevel, details);
            }
        }
    }
    
    /**
     * Sprawdza, czy gracz jest na ziemi lub w pobliżu bloku
     */
    private boolean isNearGround(Player player) {
        Location loc = player.getLocation();
        
        // Sprawdź, czy gracz jest na ziemi
        if (player.isOnGround()) {
            return true;
        }
        
        // Sprawdź, czy pod graczem jest blok
        for (int i = 1; i <= 2; i++) {
            Block blockBelow = loc.clone().subtract(0, i, 0).getBlock();
            if (!blockBelow.getType().isAir()) {
                return true;
            }
        }
        
        // Sprawdź bloki wokół gracza (poziomo)
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                
                Block blockAround = loc.clone().add(x, 0, z).getBlock();
                if (!blockAround.getType().isAir()) {
                    return true;
                }
            }
        }
        
        return false;
    }
}