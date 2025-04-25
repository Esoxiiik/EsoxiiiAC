package com.anticheatsystem.checks.velocity;

import com.anticheatsystem.AntiCheatMain;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Sprawdzenie wykrywające ignorowanie pionowego odbicia (vertical velocity),
 * charakterystyczne dla niektórych cheatów typu NoKnockback.
 */
public class VelocityC extends VelocityCheck {
    
    public VelocityC(AntiCheatMain plugin) {
        super(plugin, "C", "NoVelocity");
    }
    
    /**
     * Obsługuje ruch gracza
     * 
     * @param player Gracz
     * @param from Poprzednia lokalizacja
     * @param to Aktualna lokalizacja
     */
    public void handleMovement(Player player, Location from, Location to) {
        // Sprawdź, czy gracz ma aktywne odbicie
        if (!hasActiveVelocity(player)) {
            return;
        }
        
        // Pobierz ostatnie odbicie
        Vector lastVelocity = getLastVelocity(player);
        if (lastVelocity == null || lastVelocity.getY() <= 0.0) {
            return; // Brak pionowego odbicia
        }
        
        // Oblicz oczekiwaną zmianę Y na podstawie czasu, który upłynął
        long velocityTick = getLastVelocityTick(player);
        long timeDiff = System.currentTimeMillis() - velocityTick;
        
        // Sprawdź, czy gracz poruszył się w pionie
        double deltaY = to.getY() - from.getY();
        
        // Jeśli deltaY > 0, to gracz porusza się w górę, co jest oczekiwane
        if (deltaY > 0.0) {
            // Wszystko jest w porządku, gracz reaguje na odbicie
            return;
        }
        
        // Gracz nie porusza się w górę, mimo że powinien - możliwy NoKnockback
        // Dajemy trochę czasu na reakcję (3 ticki)
        if (timeDiff > 150) { // 3 ticki x 50ms = 150ms
            String details = String.format("Ticks: %d | MaxPingTicks: %d", 
                    timeDiff / 50, player.getPing() / 50);
            flag(player, 1, details);
            
            // Resetujemy odbicie, żeby nie flagować wielokrotnie
            player.removeMetadata("esoxiiiac.lastvelocity", plugin);
            player.removeMetadata("esoxiiiac.velocitytick", plugin);
        }
    }
    
    /**
     * Metoda wywoływana, gdy gracz otrzymuje obrażenia
     * 
     * @param player Gracz
     * @param velocity Wektor odbicia
     */
    public void handleDamage(Player player, Vector velocity) {
        // Zapisz informację o odbiciu
        if (velocity.getY() > 0.0) {
            trackVelocity(player, velocity.clone());
        }
    }
}