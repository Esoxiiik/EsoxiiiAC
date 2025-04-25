package com.anticheatsystem.checks.player;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sprawdzenie wykrywające graczy oszukujących w inwentarzu
 * (np. używających cheaty creative inventory)
 */
public class InventoryCheck extends Check {

    // Mapa przechowująca czas ostatniego kliknięcia w inwentarzu
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    
    // Mapa przechowująca licznik podejrzanych kliknięć
    private final Map<UUID, Integer> suspiciousClicks = new HashMap<>();
    
    // Minimalny czas między kliknięciami (ms)
    private static final long MIN_CLICK_INTERVAL = 50;
    
    public InventoryCheck(AntiCheatMain plugin) {
        super(plugin, "inventory", "player");
    }
    
    /**
     * Sprawdza, czy gracz oszukuje w inwentarzu
     * 
     * @param player Gracz do sprawdzenia
     * @param event Zdarzenie kliknięcia w inwentarzu
     */
    public void checkInventory(Player player, InventoryClickEvent event) {
        // Ignoruj graczy w trybie kreatywnym
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        
        // Sprawdź 1: Zbyt szybkie klikanie w inwentarzu
        checkClickSpeed(player, playerId);
        
        // Sprawdź 2: Nielegalne przedmioty w inwentarzu
        checkIllegalItems(player, event.getInventory());
        
        // Sprawdź 3: Kliknięcia poza granicami inwentarza
        checkOutOfBoundsClick(player, event);
    }
    
    /**
     * Sprawdza, czy gracz klika zbyt szybko w inwentarzu
     */
    private void checkClickSpeed(Player player, UUID playerId) {
        long currentTime = System.currentTimeMillis();
        
        // Pobierz czas ostatniego kliknięcia
        long lastTime = lastClickTime.getOrDefault(playerId, 0L);
        
        // Zaktualizuj czas ostatniego kliknięcia
        lastClickTime.put(playerId, currentTime);
        
        // Jeśli kliknięcie było zbyt szybkie
        if (lastTime > 0 && currentTime - lastTime < MIN_CLICK_INTERVAL) {
            // Zwiększ licznik podejrzanych kliknięć
            int suspiciousCount = suspiciousClicks.getOrDefault(playerId, 0) + 1;
            suspiciousClicks.put(playerId, suspiciousCount);
            
            // Jeśli gracz ma zbyt wiele podejrzanych kliknięć
            if (suspiciousCount >= 5) {
                String details = String.format("fast_clicks=%d, interval=%d ms", 
                        suspiciousCount, currentTime - lastTime);
                
                // Zgłoś naruszenie
                flag(player, Math.min(suspiciousCount / 2, maxViolationsPerCheck), details);
                
                // Resetuj licznik
                suspiciousClicks.put(playerId, 0);
            }
        } else {
            // Jeśli kliknięcie było normalne, zmniejsz licznik podejrzanych kliknięć
            int suspiciousCount = suspiciousClicks.getOrDefault(playerId, 0);
            if (suspiciousCount > 0) {
                suspiciousClicks.put(playerId, suspiciousCount - 1);
            }
        }
    }
    
    /**
     * Sprawdza, czy gracz ma nielegalne przedmioty w inwentarzu
     */
    private void checkIllegalItems(Player player, Inventory inventory) {
        // W trybie survival, sprawdź, czy gracz ma przedmioty, których nie powinien mieć
        if (player.getGameMode() == GameMode.SURVIVAL) {
            for (ItemStack item : inventory.getContents()) {
                if (item != null) {
                    // Sprawdź, czy przedmiot jest nielegalny (przykłady)
                    if (item.getType().name().contains("COMMAND") || 
                            item.getType().name().contains("BARRIER") ||
                            item.getType().name().contains("BEDROCK") && !player.hasPermission("anticheat.bypass.items")) {
                        
                        String details = "illegal_item=" + item.getType().name();
                        
                        // Zgłoś naruszenie
                        flag(player, maxViolationsPerCheck, details);
                        
                        // Usuń nielegalny przedmiot
                        inventory.remove(item);
                    }
                    
                    // Sprawdź, czy przedmiot ma nielegalne enchanty (zbyt wysokie poziomy)
                    if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
                        item.getItemMeta().getEnchants().forEach((enchant, level) -> {
                            if (level > enchant.getMaxLevel() && !player.hasPermission("anticheat.bypass.enchants")) {
                                String details = String.format("illegal_enchant=%s:%d (max:%d)", 
                                        enchant.getKey().getKey(), level, enchant.getMaxLevel());
                                
                                // Zgłoś naruszenie
                                flag(player, maxViolationsPerCheck / 2, details);
                            }
                        });
                    }
                }
            }
        }
    }
    
    /**
     * Sprawdza, czy gracz klika poza granicami inwentarza
     */
    private void checkOutOfBoundsClick(Player player, InventoryClickEvent event) {
        // Jeśli slot jest poza granicami inwentarza
        if (event.getSlot() < 0 || event.getRawSlot() < 0 || 
                event.getSlot() >= event.getInventory().getSize() && 
                event.getInventory().getType() != InventoryType.PLAYER) {
            
            String details = String.format("out_of_bounds: slot=%d, rawSlot=%d, size=%d", 
                    event.getSlot(), event.getRawSlot(), event.getInventory().getSize());
            
            // Zgłoś naruszenie
            flag(player, maxViolationsPerCheck, details);
            
            // Anuluj zdarzenie
            event.setCancelled(true);
        }
    }
}