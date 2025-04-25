package com.anticheatsystem.checks.fly;

import com.anticheatsystem.AntiCheatMain;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Sprawdzenie wykrywające latanie poprzez analizę zmian położenia gracza
 * względem podłoża. Wykrywa przypadki, gdy gracz twierdzi że jest na ziemi,
 * ale nie znajduje się na pełnym bloku.
 */
public class FlyB extends FlyCheck {
    
    private int lastBypassTick = -10;
    private int threshold = 0;
    private int currentTick = 0;
    
    public FlyB(AntiCheatMain plugin) {
        super(plugin, "B", "Fly");
    }
    
    /**
     * Obsługuje sprawdzenie ruchu gracza
     * 
     * @param player Gracz do sprawdzenia
     * @param from Poprzednia lokalizacja gracza
     * @param to Aktualna lokalizacja gracza
     * @param isFromOnGround Czy poprzednia lokalizacja była na ziemi
     * @param isToOnGround Czy aktualna lokalizacja jest na ziemi
     */
    public void handleMovement(Player player, Location from, Location to, 
                              boolean isFromOnGround, boolean isToOnGround) {
        // Zwiększamy licznik ticków
        currentTick++;
        
        // Sprawdź, czy gracz powinien być sprawdzony
        if (!shouldCheck(player)) {
            return;
        }
        
        // Sprawdź tylko gdy obie lokalizacje są na ziemi
        if (isFromOnGround && isToOnGround) {
            // Sprawdź czy Y nie jest wielokrotnością 0.5 (normalny blok)
            // Gracze na normalnym bloku powinni mieć Y kończące się na .0
            if (from.getY() != to.getY() && !isOnGroundValue(from.getY())) {
                // Weryfikacja dodatkowa - czy pod graczem na pewno nie ma bloku
                if (currentTick > 200 && !isOnGroundValue(to.getY()) && verifyNoBlocksBelow(player)) {
                    if (currentTick - 10 > lastBypassTick) {
                        // Sprawdź dodatkowe warunki wykluczające
                        if (!hasJumpBoost(player) && !isNearSpecialEntities(player)) {
                            threshold++;
                            
                            double deltaY = to.getY() - from.getY();
                            String details = String.format("Delta: %s", deltaY);
                            flag(player, threshold, details);
                        }
                    }
                } else {
                    threshold = 0;
                    lastBypassTick = currentTick;
                }
            }
        }
        
        // Powoli zmniejszaj threshold, jeśli nie ma naruszeń
        if (threshold > 0 && currentTick % 20 == 0) {
            threshold--;
        }
    }
    
    /**
     * Sprawdza, czy wartość Y odpowiada pozycji na bloku
     * 
     * @param y Wartość Y do sprawdzenia
     * @return true jeśli wartość odpowiada staniu na bloku
     */
    private boolean isOnGroundValue(double y) {
        // Sprawdź czy y kończy się na .0 (blok pełny)
        return Math.abs(y - Math.floor(y)) < 0.001;
    }
    
    /**
     * Sprawdza, czy pod graczem rzeczywiście nie ma bloków
     * 
     * @param player Gracz do sprawdzenia
     * @return true jeśli pod graczem nie ma bloków przez kilka kratek w dół
     */
    private boolean verifyNoBlocksBelow(Player player) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        
        // Sprawdź bloki pod graczem (do 2 bloków w dół)
        for (int i = 1; i <= 2; i++) {
            Location checkLoc = loc.clone().subtract(0, i, 0);
            Material material = checkLoc.getBlock().getType();
            
            // Jeśli jest jakiś blok pod graczem, to zwracamy false
            if (material != Material.AIR && material != Material.CAVE_AIR && material != Material.VOID_AIR) {
                return false;
            }
        }
        
        // Nie znaleziono bloków pod graczem
        return true;
    }
    
    /**
     * Sprawdza, czy gracz ma efekt skoku
     */
    private boolean hasJumpBoost(Player player) {
        return player.hasPotionEffect(org.bukkit.potion.PotionEffectType.JUMP);
    }
    
    /**
     * Sprawdza, czy gracz jest w pobliżu specjalnych encji
     */
    private boolean isNearSpecialEntities(Player player) {
        return player.getNearbyEntities(2.5, 2.5, 2.5).stream()
                .anyMatch(entity -> entity.getType().name().equals("BOAT") 
                        || entity.getType().name().equals("MINECART")
                        || entity.getType().name().contains("SHULKER"));
    }
}