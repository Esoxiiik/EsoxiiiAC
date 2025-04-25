package com.anticheatsystem.checks.crasher;

import com.anticheatsystem.AntiCheatMain;
import org.bukkit.entity.Player;

/**
 * Sprawdzenie wykrywające próby crashowania serwera przez nadmierne żądania otwierania książek.
 * Bazuje na analizie ilości pakietów CustomPayload typu "MC|BOpen" lub "MC|BEdit".
 */
public class ServerCrasherA extends ServerCrasherCheck {
    
    private int threshold = 0;
    
    public ServerCrasherA(AntiCheatMain plugin) {
        super(plugin, "A", "Server Crasher");
    }
    
    /**
     * Obsługuje pakiet ruchu (używany do zmniejszenia progu)
     */
    public void handleFlyingPacket() {
        // Zmniejsz próg (aktualnie wykryty poziom zagrożenia)
        this.threshold -= Math.min(this.threshold, 1);
    }
    
    /**
     * Obsługuje pakiet niestandardowych danych
     * 
     * @param player Gracz, który wysłał pakiet
     * @param channel Kanał niestandardowych danych
     */
    public void handleCustomPayload(Player player, String channel) {
        if (channel.equals("MC|BOpen") || channel.equals("MC|BEdit")) {
            // Zwiększ próg o 2 za każdym razem
            this.threshold += 2;
            
            if (this.threshold > 4) {
                // Zgłoś naruszenie
                String details = String.format("Channel: %s", channel);
                flag(player, threshold / 4, details);
                
                // Jeśli próg jest wystarczająco wysoki, blokuj gracza
                if (getViolationLevel(player) > getMaxViolationsPerCheck()) {
                    blockPlayer(player, "Nadmierne pakiety " + channel);
                }
            }
        }
    }
    
    /**
     * Pobiera aktualny poziom naruszeń gracza
     * Metoda pomocnicza do zastąpienia w przyszłości
     */
    private int getViolationLevel(Player player) {
        // To powinna być integracja z systemem naruszeń
        return threshold / 4;
    }
}