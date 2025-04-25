package com.anticheatsystem.checks.badpackets;

import com.anticheatsystem.AntiCheatMain;
import org.bukkit.entity.Player;

/**
 * Wykrywa nieprawidłowe wartości kierunku (pitch), które są niemożliwe do osiągnięcia
 * w normalnej grze. Najczęściej występują przy użyciu killeraurę czy innych cheatów.
 */
public class BadPacketsB extends BadPacketsCheck {
    
    public BadPacketsB(AntiCheatMain plugin) {
        super(plugin, "B", "Invalid Direction");
    }
    
    /**
     * Sprawdza pakiet ruchu pod kątem nieprawidłowych wartości pitch
     * 
     * @param player Gracz, który wysłał pakiet
     * @param pitch Wartość pitch z pakietu
     * @param isTeleporting Czy gracz jest w trakcie teleportacji
     */
    public void handleFlyingPacket(Player player, float pitch, boolean isTeleporting) {
        if (!isTeleporting) {
            if (Math.abs(pitch) > 90.0F) {
                String details = String.format("Pitch: %.2f", pitch);
                flag(player, 1, details);
            }
        }
    }
}