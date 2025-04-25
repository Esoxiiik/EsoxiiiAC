package com.anticheatsystem.checks.movement;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Sprawdzenie wykrywające graczy poruszających się zbyt szybko
 */
public class SpeedCheck extends Check {

    // Maksymalne normalne prędkości dla różnych typów ruchu
    private static final double MAX_WALK_SPEED = 0.22D;
    private static final double MAX_SPRINT_SPEED = 0.33D;
    private static final double MAX_SPRINT_JUMP_SPEED = 0.65D;
    private static final double MAX_ICE_SPEED = 1.0D;
    private static final double MAX_WATER_SPEED = 0.16D;
    
    public SpeedCheck(AntiCheatMain plugin) {
        super(plugin, "speed", "movement");
    }
    
    /**
     * Sprawdza, czy gracz porusza się zbyt szybko
     * 
     * @param player Gracz do sprawdzenia
     * @param from Poprzednia lokalizacja
     * @param to Nowa lokalizacja
     * @param timeDelta Czas w ms od ostatniego ruchu
     */
    public void checkSpeed(Player player, Location from, Location to, long timeDelta) {
        // Ignoruj graczy w trybie kreatywnym lub obserwatora
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        
        // Ignoruj graczy, którzy są w stanie latać
        if (player.getAllowFlight() || player.isFlying()) {
            return;
        }
        
        // Oblicz prędkość horyzontalną (tylko X i Z)
        double deltaX = to.getX() - from.getX();
        double deltaZ = to.getZ() - from.getZ();
        double horizontalSpeed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        
        // Dostosuj prędkość do czasu, jeśli jest bardzo duży odstęp czasowy
        if (timeDelta > 100) {
            horizontalSpeed = horizontalSpeed * (50.0 / timeDelta);
        }
        
        // Pobierz maksymalną dozwoloną prędkość dla aktualnych warunków
        double maxAllowedSpeed = getMaxAllowedSpeed(player, from, to);
        
        // Dostosuj maksymalną prędkość na podstawie efektów mikstur
        maxAllowedSpeed *= getPotionSpeedMultiplier(player);
        
        // Dostosuj maksymalną prędkość na podstawie ewentualnego ice slide boost
        if (isIceSliding(player, from)) {
            maxAllowedSpeed = Math.max(maxAllowedSpeed, MAX_ICE_SPEED);
        }
        
        // Sprawdź, czy prędkość przekracza dozwoloną wartość
        if (horizontalSpeed > maxAllowedSpeed) {
            // Współczynnik przekroczenia prędkości
            double overSpeedFactor = horizontalSpeed / maxAllowedSpeed;
            
            // Dostosuj do czułości
            double threshold = 1.0 + (1.0 / sensitivity);
            
            // Jeśli przekroczenie jest znaczne
            if (overSpeedFactor > threshold) {
                String details = String.format("speed=%.2f, maxAllowed=%.2f, factor=%.2f", 
                        horizontalSpeed, maxAllowedSpeed, overSpeedFactor);
                
                // Ustal poziom naruszenia na podstawie różnicy
                int violationLevel = (int) Math.ceil((overSpeedFactor - 1.0) * 10);
                
                // Ogranicz do maksymalnej wartości dla tego sprawdzenia
                violationLevel = Math.min(violationLevel, maxViolationsPerCheck);
                
                flag(player, violationLevel, details);
            }
        }
    }
    
    /**
     * Pobiera maksymalną dozwoloną prędkość dla aktualnych warunków
     */
    private double getMaxAllowedSpeed(Player player, Location from, Location to) {
        // Sprawdź, czy gracz jest w wodzie
        if (from.getBlock().getType() == Material.WATER || to.getBlock().getType() == Material.WATER) {
            return MAX_WATER_SPEED;
        }
        
        // Sprawdź, czy gracz skacze
        if (!player.isOnGround() && to.getY() > from.getY()) {
            return MAX_SPRINT_JUMP_SPEED;
        }
        
        // Sprawdź, czy gracz biegnie
        if (player.isSprinting()) {
            return MAX_SPRINT_SPEED;
        }
        
        // Domyślna prędkość chodzenia
        return MAX_WALK_SPEED;
    }
    
    /**
     * Pobiera mnożnik prędkości na podstawie efektów mikstur
     */
    private double getPotionSpeedMultiplier(Player player) {
        double multiplier = 1.0;
        
        // Sprawdź efekt Speed
        PotionEffect speedEffect = player.getPotionEffect(PotionEffectType.SPEED);
        if (speedEffect != null) {
            int amplifier = speedEffect.getAmplifier() + 1; // +1, bo poziomy zaczynają się od 0
            multiplier += 0.2 * amplifier; // +20% per poziom
        }
        
        // Sprawdź efekt Slowness
        PotionEffect slownessEffect = player.getPotionEffect(PotionEffectType.SLOW);
        if (slownessEffect != null) {
            int amplifier = slownessEffect.getAmplifier() + 1;
            multiplier -= 0.15 * amplifier; // -15% per poziom
            
            // Upewnij się, że mnożnik nie jest ujemny
            if (multiplier < 0.1) {
                multiplier = 0.1;
            }
        }
        
        return multiplier;
    }
    
    /**
     * Sprawdza, czy gracz ślizga się po lodzie
     */
    private boolean isIceSliding(Player player, Location location) {
        // Sprawdź blok pod graczem
        Block block = location.clone().subtract(0, 1, 0).getBlock();
        return block.getType() == Material.ICE || block.getType() == Material.PACKED_ICE || 
               block.getType() == Material.BLUE_ICE;
    }
}