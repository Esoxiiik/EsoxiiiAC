package com.anticheatsystem.checks.movement;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Sprawdzenie wykrywające graczy, którzy nieprawidłowo teleportują się
 * (np. za pomocą hacków czy modów)
 */
public class TeleportCheck extends Check {

    // Maksymalna dozwolona odległość teleportacji bez odpowiednich uprawnień
    private static final double MAX_TELEPORT_DISTANCE = 25.0;
    
    public TeleportCheck(AntiCheatMain plugin) {
        super(plugin, "teleport", "movement");
    }
    
    /**
     * Sprawdza, czy gracz teleportuje się na niedozwoloną odległość
     * 
     * @param player Gracz do sprawdzenia
     * @param lastLocation Ostatnia znana lokalizacja gracza
     * @param newLocation Nowa lokalizacja gracza
     * @param timeDelta Czas w ms od ostatniego ruchu
     */
    public void checkTeleport(Player player, Location lastLocation, Location newLocation, long timeDelta) {
        // Ignoruj graczy w trybie kreatywnym lub obserwatora
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        
        // Ignoruj graczy z pozwoleniem na teleportację
        if (player.hasPermission("minecraft.command.tp") || player.hasPermission("essentials.tp")) {
            return;
        }
        
        // Ignoruj, jeśli player jest w trybie lotu
        if (player.isFlying() || player.getAllowFlight()) {
            return;
        }
        
        // Upewnij się, że lokalizacje są z tego samego świata
        if (!lastLocation.getWorld().equals(newLocation.getWorld())) {
            return;
        }
        
        // Oblicz odległość między poprzednią a aktualną lokalizacją
        double distance = lastLocation.distance(newLocation);
        
        // Oblicz maksymalną możliwą odległość przemieszczenia w tym czasie
        // Przy założeniu szybkości poruszania się około 10 bloków na sekundę
        double maxDistance = (timeDelta / 1000.0) * 10.0;
        
        // Dodaj trochę zapasu na lag i inne czynniki
        maxDistance = Math.max(maxDistance, MAX_TELEPORT_DISTANCE);
        
        // Jeśli gracz przemieścił się dalej niż to możliwe
        if (distance > maxDistance) {
            String details = String.format("dist=%.2f, max=%.2f, time=%d ms", 
                    distance, maxDistance, timeDelta);
            
            // Ustal poziom naruszenia na podstawie przekroczenia
            int violationLevel = (int) Math.ceil((distance / maxDistance) * 5);
            
            // Ogranicz do maksymalnej wartości dla tego sprawdzenia
            violationLevel = Math.min(violationLevel, maxViolationsPerCheck);
            
            // Zgłoś naruszenie
            flag(player, violationLevel, details);
            
            // Jeśli gracz znacznie przekroczył limit, teleportuj go z powrotem
            if (distance > maxDistance * 2) {
                player.teleport(lastLocation, PlayerTeleportEvent.TeleportCause.UNKNOWN);
            }
        }
    }
}