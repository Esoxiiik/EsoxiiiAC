package com.anticheatsystem.checks.fly;

import com.anticheatsystem.AntiCheatMain;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Sprawdzenie wykrywające latanie poprzez analizę trwale unoszącego się Y
 * i braku zmiany współrzędnej Y przy braku kontaktu z gruntem.
 */
public class FlyC extends FlyCheck {
    
    private Double lastY = null;
    private int lastBypassTick = -10;
    private int threshold = 0;
    private int currentTick = 0;
    
    public FlyC(AntiCheatMain plugin) {
        super(plugin, "C", "Fly");
    }
    
    /**
     * Obsługuje ruch gracza
     * 
     * @param player Gracz do sprawdzenia
     * @param location Aktualna lokalizacja
     * @param isOnGround Czy gracz twierdzi, że jest na gruncie
     */
    public void handleMovement(Player player, Location location, boolean isOnGround) {
        // Zwiększ licznik ticków
        currentTick++;
        
        // Sprawdź czy gracz powinien być sprawdzony
        if (!shouldCheck(player)) {
            lastY = location.getY();
            return;
        }
        
        if (lastY != null) {
            double y = location.getY();
            
            // Sprawdź czy gracz unosi się bez zmiany Y
            if (lastY == y && y > 0.0 && !isOnGround && !playerHasRecentActions(player)) {
                
                // Sprawdź czy nie ma żadnych bloków wokół gracza, które mogłyby
                // tłumaczyć utrzymywanie stałej wysokości
                if (!hasBlocksNearby(player, y) && currentTick - 20 > lastBypassTick) {
                    threshold++;
                    
                    if (threshold > getSafetyThreshold(player)) {
                        String details = String.format("Y: %.2f", y);
                        flag(player, threshold, details);
                    }
                } else {
                    // Gracz ma bloki w pobliżu lub inne wyjaśnienie
                    threshold = 0;
                    lastBypassTick = currentTick;
                }
            } else {
                // Y się zmienia, więc to nie jest stałe unoszenie się
                threshold = 0;
            }
        }
        
        // Zapisujemy ostatnie Y
        lastY = location.getY();
    }
    
    /**
     * Oblicza bezpieczny próg dla gracza bazując na jego opóźnieniu
     * 
     * @param player Gracz do sprawdzenia
     * @return Bezpieczny próg naruszeń
     */
    private int getSafetyThreshold(Player player) {
        if (player.getPing() > 200) {
            return 4; // Większy próg dla graczy z większym pingiem
        }
        return 1;
    }
    
    /**
     * Sprawdza, czy gracz wykonał niedawno jakieś akcje, które mogłyby
     * wyjaśnić unoszenie się (np. wylanie wiadra wody)
     * 
     * @param player Gracz do sprawdzenia
     * @return true jeśli gracz wykonał takie akcje
     */
    private boolean playerHasRecentActions(Player player) {
        // W pełnej implementacji należałoby sprawdzić różne akcje
        // jak używanie wiadra z wodą, stawianie bloków pod sobą itp.
        
        // Dla uproszczenia sprawdzamy tylko czy gracz nie trzyma wiadra
        Material mainHand = player.getInventory().getItemInMainHand().getType();
        Material offHand = player.getInventory().getItemInOffHand().getType();
        
        return mainHand.toString().contains("BUCKET") || offHand.toString().contains("BUCKET");
    }
    
    /**
     * Sprawdza, czy wokół gracza znajdują się bloki które mogłyby
     * wyjaśniać jego utrzymywanie stałej wysokości
     * 
     * @param player Gracz do sprawdzenia
     * @param y Aktualna wysokość gracza
     * @return true jeśli znaleziono bloki mogące wyjaśniać pozycję
     */
    private boolean hasBlocksNearby(Player player, double y) {
        World world = player.getWorld();
        Location center = player.getLocation();
        
        // Sprawdź bloki w obszarze wokół gracza
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int yOffset = -1; yOffset <= 2; yOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    // Pomiń centrum (gracza)
                    if (xOffset == 0 && yOffset == 0 && zOffset == 0) continue;
                    
                    Location checkLoc = center.clone().add(xOffset, yOffset, zOffset);
                    Material material = checkLoc.getBlock().getType();
                    
                    // Jeśli znaleziono blok, który mógłby wspierać gracza
                    if (isSupportingBlock(material)) {
                        return true;
                    }
                }
            }
        }
        
        // Sprawdź czy gracz jest w pobliżu specjalnych encji
        return player.getNearbyEntities(1.5, 1.5, 1.5).stream()
                .anyMatch(entity -> entity.getType().name().equals("BOAT") 
                        || entity.getType().name().equals("MINECART")
                        || entity.getType().name().contains("SHULKER"));
    }
    
    /**
     * Sprawdza, czy dany materiał jest blokiem wspierającym (np. drabina, woda)
     * 
     * @param material Materiał do sprawdzenia
     * @return true jeśli materiał może wspierać gracza
     */
    private boolean isSupportingBlock(Material material) {
        String name = material.toString();
        
        return name.contains("LADDER") || name.contains("VINE") || name.contains("WATER") 
                || name.contains("BUBBLE") || name.contains("SCAFFOLDING") 
                || name.contains("HONEY") || name.contains("COBWEB");
    }
}