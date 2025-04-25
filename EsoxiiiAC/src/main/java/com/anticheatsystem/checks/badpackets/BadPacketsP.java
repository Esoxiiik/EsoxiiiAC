package com.anticheatsystem.checks.badpackets;

import com.anticheatsystem.AntiCheatMain;
import org.bukkit.entity.Player;

/**
 * Wykrywa nietypowe sekwencje pakietów USE_ENTITY, które mogą wskazywać na używanie
 * cheatów typu KillAura.
 */
public class BadPacketsP extends BadPacketsCheck {
    
    private boolean attack = false;
    private boolean interactAt = false;
    private boolean interact = false;
    
    public BadPacketsP(AntiCheatMain plugin) {
        super(plugin, "P", "KillAura");
    }
    
    /**
     * Obsługuje pakiet interakcji z encją
     * 
     * @param player Gracz, który wysłał pakiet
     * @param action Typ akcji (0 = INTERACT, 1 = ATTACK, 2 = INTERACT_AT)
     */
    public void handleUseEntityPacket(Player player, int action) {
        if (action == 1) { // ATTACK
            if (!this.attack && (this.interact || this.interactAt)) {
                StringBuilder builder = new StringBuilder().append("Attack [");
                String add;
                if (this.interactAt) {
                    add = "Interact At ";
                } else {
                    add = "";
                }

                builder.append(add);
                if (this.interact) {
                    add = "Interact";
                } else {
                    add = "";
                }

                String details = builder.append(add).append("]").toString();
                flag(player, 1, details);
                this.interact = this.interactAt = false;
            }

            this.attack = true;
        } else if (action == 0) { // INTERACT
            this.interact = true;
        } else if (action == 2) { // INTERACT_AT
            if (!this.interactAt && this.interact) {
                flag(player, 1, "Interact");
                this.interact = false;
            }

            this.interactAt = true;
        }
    }
    
    /**
     * Resetuje stan sprawdzenia przy nowym pakiecie ruchu
     */
    public void handleFlyingPacket() {
        this.attack = this.interactAt = this.interact = false;
    }
}