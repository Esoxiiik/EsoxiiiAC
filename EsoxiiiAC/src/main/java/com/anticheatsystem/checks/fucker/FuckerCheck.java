package com.anticheatsystem.checks.fucker;

import com.anticheatsystem.checks.Check;
import com.anticheatsystem.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sprawdzenie Fucker (Bed Breaker) wykrywa graczy używających cheatów
 * do automatycznego niszczenia łóżek i innych bloków (np. w Bed Wars).
 */
public class FuckerCheck extends Check {
    
    // Mapa czasów ostatniego kliknięcia bloku
    private final Map<UUID, Long> lastBreakAttemptTime = new HashMap<>();
    
    // Mapa liczby kliknięć w krótkim czasie
    private final Map<UUID, Integer> clickCounter = new HashMap<>();
    
    // Mapa ostatnio klikniętych bloków
    private final Map<UUID, Location> lastBreakAttemptLocation = new HashMap<>();
    
    // Czas między kliknięciami wskazujący na potencjalny cheat (w ms)
    private static final long MIN_CLICK_DELAY = 50;
    
    // Maksymalna odległość patrzenia na blok, aby go zniszczyć
    private static final double MAX_BREAK_DISTANCE = 6.0;
    
    // Maksymalny kąt, pod jakim gracz może próbować zniszczyć blok
    private static final double MAX_BREAK_ANGLE = 90.0;
    
    // Próg liczby kliknięć w krótkim czasie
    private static final int CLICK_THRESHOLD = 10;
    
    // Czas resetowania licznika kliknięć (w ms)
    private static final long CLICK_RESET_TIME = 2000;
    
    /**
     * Konstruktor klasy FuckerCheck
     * 
     * @param playerData Dane gracza
     */
    public FuckerCheck(PlayerData playerData) {
        super(playerData, "Fucker");
        
        // Domyślnie włączony
        this.enabled = true;
        
        // Standardowe ustawienia
        this.cancelViolation = true;
        this.notifyViolation = true;
        this.maxViolations = 10;
    }
    
    /**
     * Obsługa zdarzenia zniszczenia bloku
     * 
     * @param event Zdarzenie zniszczenia bloku
     * @return true, jeśli wykryto cheata, false w przeciwnym przypadku
     */
    public boolean onBlockBreak(BlockBreakEvent event) {
        // Nie sprawdzaj, jeśli kontrola jest wyłączona
        if (!enabled) return false;
        
        Player player = event.getPlayer();
        Block block = event.getBlock();
        UUID playerId = player.getUniqueId();
        
        // Ignoruj graczy w trybie kreatywnym
        if (player.getGameMode() == GameMode.CREATIVE) {
            return false;
        }
        
        // Sprawdź odległość i kąt patrzenia na blok
        if (!isLookingAtBlock(player, block)) {
            // Gracz nie patrzy na ten blok - prawdopodobnie cheat
            handleViolation(player, "Fucker", "Niszczenie bloku bez patrzenia");
            
            if (cancelViolation) {
                event.setCancelled(true);
                return true;
            }
            
            return true;
        }
        
        // Sprawdź czy to jest łóżko, skrzynia lub inny ważny blok
        if (isTargetBlock(block.getType())) {
            // Aktualny czas
            long currentTime = System.currentTimeMillis();
            
            // Czas od ostatniego kliknięcia
            long lastTime = lastBreakAttemptTime.getOrDefault(playerId, 0L);
            long timeDiff = currentTime - lastTime;
            
            // Lokalizacja ostatniego kliknięcia
            Location lastLoc = lastBreakAttemptLocation.get(playerId);
            
            // Jeśli czas między kliknięciami jest podejrzanie krótki
            if (timeDiff < MIN_CLICK_DELAY) {
                // Zwiększ licznik kliknięć
                int clicks = clickCounter.getOrDefault(playerId, 0) + 1;
                clickCounter.put(playerId, clicks);
                
                // Jeśli licznik przekroczył próg
                if (clicks > CLICK_THRESHOLD) {
                    // Wykryto potencjalnego cheata Fucker
                    handleViolation(player, "Fucker", "Zbyt szybkie niszczenie bloków (" + clicks + " kliknięć)");
                    
                    // Resetuj licznik
                    clickCounter.put(playerId, 0);
                    
                    if (cancelViolation) {
                        event.setCancelled(true);
                        return true;
                    }
                    
                    return true;
                }
            } else if (timeDiff > CLICK_RESET_TIME) {
                // Resetuj licznik, jeśli minęło dużo czasu
                clickCounter.put(playerId, 0);
            }
            
            // Jeśli poprzedni blok był w innym miejscu, a czas jest bardzo krótki
            if (lastLoc != null && !block.getLocation().equals(lastLoc) && timeDiff < 200) {
                double distance = lastLoc.distance(block.getLocation());
                
                // Jeśli odległość między blokami jest duża, a czas krótki
                if (distance > 2.0) {
                    // Wykryto potencjalnego cheata Fucker (przeskakiwanie między blokami)
                    handleViolation(player, "Fucker", 
                        String.format("Przeskakiwanie między blokami (%.2f bloków w %d ms)", distance, timeDiff));
                    
                    if (cancelViolation) {
                        event.setCancelled(true);
                        return true;
                    }
                    
                    return true;
                }
            }
            
            // Aktualizuj ostatni czas i lokalizację
            lastBreakAttemptTime.put(playerId, currentTime);
            lastBreakAttemptLocation.put(playerId, block.getLocation());
        }
        
        return false;
    }
    
    /**
     * Obsługa zdarzenia interakcji gracza z blokiem
     * 
     * @param event Zdarzenie interakcji
     * @return true, jeśli wykryto cheata, false w przeciwnym przypadku
     */
    public boolean onPlayerInteract(PlayerInteractEvent event) {
        // Nie sprawdzaj, jeśli kontrola jest wyłączona
        if (!enabled) return false;
        
        // Interesują nas tylko interakcje z blokami
        if (event.getClickedBlock() == null) {
            return false;
        }
        
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        UUID playerId = player.getUniqueId();
        
        // Ignoruj graczy w trybie kreatywnym
        if (player.getGameMode() == GameMode.CREATIVE) {
            return false;
        }
        
        // Sprawdź odległość i kąt patrzenia na blok
        if (!isLookingAtBlock(player, block)) {
            // Gracz nie patrzy na ten blok - prawdopodobnie cheat
            handleViolation(player, "Fucker", "Interakcja z blokiem bez patrzenia");
            
            if (cancelViolation) {
                event.setCancelled(true);
                return true;
            }
            
            return true;
        }
        
        // Sprawdź czy to jest łóżko, skrzynia lub inny ważny blok
        if (isTargetBlock(block.getType())) {
            // Aktualny czas
            long currentTime = System.currentTimeMillis();
            
            // Czas od ostatniego kliknięcia
            long lastTime = lastBreakAttemptTime.getOrDefault(playerId, 0L);
            long timeDiff = currentTime - lastTime;
            
            // Jeśli czas między kliknięciami jest podejrzanie krótki
            if (timeDiff < MIN_CLICK_DELAY) {
                // Zwiększ licznik kliknięć
                int clicks = clickCounter.getOrDefault(playerId, 0) + 1;
                clickCounter.put(playerId, clicks);
                
                // Jeśli licznik przekroczył próg
                if (clicks > CLICK_THRESHOLD) {
                    // Wykryto potencjalnego cheata Fucker
                    handleViolation(player, "Fucker", "Zbyt szybkie klikanie bloków (" + clicks + " kliknięć)");
                    
                    // Resetuj licznik
                    clickCounter.put(playerId, 0);
                    
                    if (cancelViolation) {
                        event.setCancelled(true);
                        return true;
                    }
                    
                    return true;
                }
            } else if (timeDiff > CLICK_RESET_TIME) {
                // Resetuj licznik, jeśli minęło dużo czasu
                clickCounter.put(playerId, 0);
            }
            
            // Aktualizuj ostatni czas i lokalizację
            lastBreakAttemptTime.put(playerId, currentTime);
            lastBreakAttemptLocation.put(playerId, block.getLocation());
        }
        
        return false;
    }
    
    /**
     * Sprawdza, czy gracz patrzy na dany blok (odległość i kąt)
     * 
     * @param player Gracz
     * @param block Blok
     * @return true, jeśli gracz patrzy na blok, false w przeciwnym przypadku
     */
    private boolean isLookingAtBlock(Player player, Block block) {
        // Lokalizacja oczu gracza
        Location eyeLocation = player.getEyeLocation();
        
        // Wektor kierunku patrzenia
        Vector direction = eyeLocation.getDirection().normalize();
        
        // Wektor od oczu gracza do środka bloku
        Vector toBlock = block.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(eyeLocation.toVector());
        
        // Odległość od gracza do bloku
        double distance = toBlock.length();
        
        // Jeśli blok jest za daleko
        if (distance > MAX_BREAK_DISTANCE) {
            return false;
        }
        
        // Kąt między wektorem kierunku patrzenia a wektorem do bloku
        double angle = Math.toDegrees(direction.angle(toBlock.normalize()));
        
        // Jeśli kąt jest zbyt duży
        return angle <= MAX_BREAK_ANGLE;
    }
    
    /**
     * Sprawdza, czy dany typ materiału jest celem dla Fucker/BedBreaker
     * 
     * @param material Typ materiału
     * @return true, jeśli jest to cel, false w przeciwnym przypadku
     */
    private boolean isTargetBlock(Material material) {
        // Sprawdź czy to jest łóżko
        if (material.name().contains("BED")) {
            return true;
        }
        
        // Sprawdź czy to jest skrzynia
        if (material.name().contains("CHEST")) {
            return true;
        }
        
        // Sprawdź czy to jest ważny blok
        switch (material) {
            case CAKE:
            case END_PORTAL_FRAME:
            case ENDER_CHEST:
            case BEACON:
            case DRAGON_EGG:
            case SPAWNER:
            case ENCHANTING_TABLE:
            case BREWING_STAND:
            case ANVIL:
            case SHULKER_BOX:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Czyści dane dla gracza (np. przy wyjściu z serwera)
     * 
     * @param player Gracz
     */
    public void clearPlayerData(Player player) {
        UUID playerId = player.getUniqueId();
        lastBreakAttemptTime.remove(playerId);
        clickCounter.remove(playerId);
        lastBreakAttemptLocation.remove(playerId);
    }
}