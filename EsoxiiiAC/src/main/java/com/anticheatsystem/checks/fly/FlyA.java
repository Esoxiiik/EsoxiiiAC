package com.anticheatsystem.checks.fly;

import com.anticheatsystem.AntiCheatMain;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * Sprawdzenie wykrywające latanie poprzez analizę bloków pod graczem.
 * Wykrywa sytuacje, gdy gracz znajduje się na ziemi, choć pod nim nie ma
 * żadnych solidnych bloków.
 */
public class FlyA extends FlyCheck {
    
    // Zbiór materiałów, które są w pełni przepuszczalne (powietrze, trawa, kwiaty itp.)
    private static final Set<Material> FULLY_PASSABLE = initPassableMaterials();
    
    private int threshold = 0;
    private int lastBypassTick = -10;
    private int currentTick = 0;
    
    public FlyA(AntiCheatMain plugin) {
        super(plugin, "A", "Fly");
    }
    
    /**
     * Inicjalizuje zbiór materiałów, które są w pełni przepuszczalne
     */
    private static Set<Material> initPassableMaterials() {
        Set<Material> passable = new HashSet<>();
        
        // Dodaj podstawowe przepuszczalne materiały
        passable.add(Material.AIR);
        passable.add(Material.CAVE_AIR);
        passable.add(Material.VOID_AIR);
        passable.add(Material.GRASS);
        passable.add(Material.TALL_GRASS);
        
        // W pełnej implementacji należy dodać wszystkie przepuszczalne materiały
        
        return passable;
    }
    
    /**
     * Obsługuje sprawdzenie dla gracza na podstawie jego lokalizacji
     * 
     * @param player Gracz do sprawdzenia
     * @param isOnGround Informacja, czy klient twierdzi, że gracz jest na ziemi
     */
    public void handleMovement(Player player, boolean isOnGround) {
        // Zwiększamy licznik ticków
        currentTick++;
        
        // Sprawdź, czy gracz powinien być sprawdzony
        if (!shouldCheck(player)) {
            return;
        }
        
        // Jeśli gracz twierdzi, że jest na ziemi
        if (isOnGround) {
            Location location = player.getLocation();
            World world = player.getWorld();
            
            // Utwórz regionu do sprawdzenia (pod nogami gracza)
            Location checkLoc = location.clone().subtract(0, 0.5, 0);
            
            // Sprawdź, czy blok pod graczem jest przepuszczalny
            if (isFullyPassable(checkLoc.getBlock()) && !playerHasNearbyEntities(player)) {
                int pingSafetyBuffer = Math.max(2, (int)(player.getPing() / 50));
                
                // Sprawdź czy minęło wystarczająco dużo czasu od ostatniego "bezpiecznego" stanu
                if (currentTick - lastBypassTick > pingSafetyBuffer) {
                    threshold++;
                    
                    if (threshold > pingSafetyBuffer) {
                        // Oblicz VL na podstawie surowego thresholdu
                        double vl = threshold > 10 ? 1.0 : 0.5;
                        String details = String.format("T: %d, G: %b", threshold, isOnGround);
                        flag(player, (int)vl, details);
                    }
                } else {
                    threshold = 0;
                }
            } else {
                // Gracz ma pod sobą solidny blok lub encję - to legalne
                threshold = 0;
                lastBypassTick = currentTick;
            }
        } else {
            // Gracz nie twierdzi, że jest na ziemi, więc to nie jest naruszenie tego sprawdzenia
            threshold = 0;
        }
    }
    
    /**
     * Sprawdza, czy blok jest w pełni przepuszczalny (można przez niego przejść)
     */
    private boolean isFullyPassable(Block block) {
        return FULLY_PASSABLE.contains(block.getType());
    }
    
    /**
     * Sprawdza, czy gracz ma w pobliżu encje, które mogłyby powodować legalne "lewitowanie"
     */
    private boolean playerHasNearbyEntities(Player player) {
        // Sprawdź czy są w pobliżu łódki, minecrafty, shulkery itp.
        return player.getNearbyEntities(2.5, 2.5, 2.5).stream()
                .anyMatch(entity -> entity.getType().name().equals("BOAT") 
                        || entity.getType().name().equals("MINECART")
                        || entity.getType().name().equals("SHULKER"));
    }
}