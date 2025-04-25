package com.anticheatsystem.checks.step;

import com.anticheatsystem.checks.Check;
import com.anticheatsystem.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

/**
 * Podstawowa klasa sprawdzania Step (nierealistyczne wchodzenie po blokach)
 * Wykrywa potencjalne cheaty pozwalające graczom wchodzić wyżej niż normalnie
 * (np. Step, Spider).
 */
public class StepCheck extends Check {

    // Maksymalna wysokość kroku w normalnej grze
    private static final double MAX_STEP_HEIGHT = 0.6;
    
    // Maksymalna wysokość kroku z przesiadaniem (np. na schody)
    private static final double MAX_STEP_HEIGHT_WITH_SLABS = 1.0;
    
    // Bufor historii lokacji
    private Location lastGroundLocation;
    private Location lastLocation;
    private boolean wasOnGround;
    private long lastStepViolation;
    
    /**
     * Konstruktor klasy StepCheck
     * 
     * @param playerData Dane gracza
     */
    public StepCheck(PlayerData playerData) {
        super(playerData, "Step");
        
        // Domyślnie włączony
        this.enabled = true;
        
        // Standardowe ustawienia
        this.cancelViolation = true;
        this.notifyViolation = true;
        this.maxViolations = 5;
    }
    
    /**
     * Obsługa ruchu gracza (wersja oparta na zdarzeniach)
     * 
     * @param event Zdarzenie ruchu gracza
     * @return true, jeśli wykryto cheata, false w przeciwnym przypadku
     */
    @Override
    public boolean onMove(PlayerMoveEvent event) {
        // Nie sprawdzaj, jeśli kontrola jest wyłączona
        if (!enabled) return false;
        
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        
        // Jeśli to pierwsze wywołanie lub gracz teleportuje się, zapisz lokację i zakończ
        if (lastLocation == null || from.distanceSquared(to) > 100) {
            lastLocation = to.clone();
            lastGroundLocation = player.isOnGround() ? to.clone() : null;
            wasOnGround = player.isOnGround();
            return false;
        }
        
        // Ignoruj graczy w trybie kreatywnym, latających lub w wodzie
        if (player.getGameMode() == GameMode.CREATIVE || 
            player.getGameMode() == GameMode.SPECTATOR ||
            player.getAllowFlight() || 
            player.isFlying() ||
            player.isInWater() ||
            player.isInLava()) {
            
            lastLocation = to.clone();
            lastGroundLocation = player.isOnGround() ? to.clone() : null;
            wasOnGround = player.isOnGround();
            return false;
        }
        
        // Oblicz delta Y (zmianę wysokości)
        double deltaY = to.getY() - from.getY();
        
        // Sprawdź, czy gracz jest teraz na ziemi, ale wcześniej nie był
        boolean onGround = player.isOnGround();
        
        // Analiza ruchu gracza przechodzącego z stanu "nie na ziemi" do "na ziemi"
        if (!wasOnGround && onGround && deltaY > 0) {
            // Maksymalna dozwolona wysokość kroku
            double maxAllowedStep = MAX_STEP_HEIGHT;
            
            // Sprawdź, czy w pobliżu są schody, płyty lub półbloki, które pozwalają na wyższy krok
            if (hasSlabNear(to)) {
                maxAllowedStep = MAX_STEP_HEIGHT_WITH_SLABS;
            }
            
            // Sprawdź, czy zmiana wysokości jest podejrzana
            if (deltaY > maxAllowedStep) {
                // Sprawdź, czy gracz nie jest wypychany przez tłok lub inne mechanizmy
                if (!isLegitimateElevation(player, from, to)) {
                    // Wykryto potencjalnego cheata Step
                    handleViolation(player, "Step", String.format("deltaY=%.2f, max=%.2f", deltaY, maxAllowedStep));
                    
                    // Ustaw znacznik czasowy naruszenia
                    lastStepViolation = System.currentTimeMillis();
                    
                    // Jeśli włączone jest anulowanie, cofnij gracza
                    if (cancelViolation) {
                        // Cofnij gracza do ostatniej bezpiecznej pozycji na ziemi
                        if (lastGroundLocation != null) {
                            teleportToGround(player, lastGroundLocation);
                            return true;
                        } else {
                            // Jeśli nie mamy bezpiecznej pozycji, po prostu anuluj ruch
                            event.setCancelled(true);
                            return true;
                        }
                    }
                    
                    return true;
                }
            }
        }
        
        // Aktualizuj ostatnią lokację
        lastLocation = to.clone();
        
        // Aktualizuj ostatnią lokację na ziemi
        if (onGround) {
            lastGroundLocation = to.clone();
        }
        
        // Zapamiętaj stan "na ziemi"
        wasOnGround = onGround;
        
        return false;
    }
    
    /**
     * Obsługa pakietu ruchu (wersja packet-based)
     * 
     * @param player Gracz
     * @param from Lokacja początkowa
     * @param to Lokacja docelowa
     * @param onGround Czy gracz jest na ziemi
     * @return true, jeśli wykryto cheata, false w przeciwnym przypadku
     */
    public boolean handleMovePacket(Player player, Location from, Location to, boolean onGround) {
        // Nie sprawdzaj, jeśli kontrola jest wyłączona
        if (!enabled) return false;
        
        // Jeśli to pierwsze wywołanie lub gracz teleportuje się, zapisz lokację i zakończ
        if (lastLocation == null || from.distanceSquared(to) > 100) {
            lastLocation = to.clone();
            lastGroundLocation = onGround ? to.clone() : null;
            wasOnGround = onGround;
            return false;
        }
        
        // Ignoruj graczy w trybie kreatywnym, latających lub w wodzie
        if (player.getGameMode() == GameMode.CREATIVE || 
            player.getGameMode() == GameMode.SPECTATOR ||
            player.getAllowFlight() || 
            player.isFlying() ||
            player.isInWater() ||
            player.isInLava()) {
            
            lastLocation = to.clone();
            lastGroundLocation = onGround ? to.clone() : null;
            wasOnGround = onGround;
            return false;
        }
        
        // Oblicz delta Y (zmianę wysokości)
        double deltaY = to.getY() - from.getY();
        
        // Analiza ruchu gracza przechodzącego z stanu "nie na ziemi" do "na ziemi"
        if (!wasOnGround && onGround && deltaY > 0) {
            // Maksymalna dozwolona wysokość kroku
            double maxAllowedStep = MAX_STEP_HEIGHT;
            
            // Sprawdź, czy w pobliżu są schody, płyty lub półbloki, które pozwalają na wyższy krok
            if (hasSlabNear(to)) {
                maxAllowedStep = MAX_STEP_HEIGHT_WITH_SLABS;
            }
            
            // Sprawdź, czy zmiana wysokości jest podejrzana
            if (deltaY > maxAllowedStep) {
                // Sprawdź, czy gracz nie jest wypychany przez tłok lub inne mechanizmy
                if (!isLegitimateElevation(player, from, to)) {
                    // Wykryto potencjalnego cheata Step
                    handleViolation(player, "Step", String.format("deltaY=%.2f, max=%.2f", deltaY, maxAllowedStep));
                    
                    // Ustaw znacznik czasowy naruszenia
                    lastStepViolation = System.currentTimeMillis();
                    
                    // Jeśli włączone jest anulowanie, cofnij gracza
                    if (cancelViolation) {
                        // Cofnij gracza do ostatniej bezpiecznej pozycji na ziemi
                        if (lastGroundLocation != null) {
                            teleportToGround(player, lastGroundLocation);
                            return true;
                        }
                    }
                    
                    return true;
                }
            }
        }
        
        // Aktualizuj ostatnią lokację
        lastLocation = to.clone();
        
        // Aktualizuj ostatnią lokację na ziemi
        if (onGround) {
            lastGroundLocation = to.clone();
        }
        
        // Zapamiętaj stan "na ziemi"
        wasOnGround = onGround;
        
        return false;
    }
    
    /**
     * Sprawdza, czy w pobliżu gracza znajdują się półbloki, schody lub inne bloki
     * pozwalające na wyższy krok
     * 
     * @param location Lokacja do sprawdzenia
     * @return true, jeśli znaleziono odpowiednie bloki, false w przeciwnym przypadku
     */
    private boolean hasSlabNear(Location location) {
        // Sprawdź bloki w promieniu 1 bloku od gracza na tej samej wysokości
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = location.getBlock().getRelative(x, 0, z);
                
                // Sprawdź typ bloku
                Material type = block.getType();
                String typeName = type.name();
                
                // Sprawdź czy to schody, płyty lub półbloki
                if (typeName.contains("SLAB") || 
                    typeName.contains("STAIR") || 
                    typeName.contains("STEP") ||
                    typeName.contains("CARPET") ||
                    type == Material.SOUL_SAND) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Sprawdza, czy zmiana wysokości jest uzasadniona innymi mechanizmami gry
     * (np. wypychanie przez tłok, efekt skoku)
     * 
     * @param player Gracz
     * @param from Lokacja początkowa
     * @param to Lokacja docelowa
     * @return true, jeśli zmiana wysokości jest uzasadniona, false w przeciwnym przypadku
     */
    private boolean isLegitimateElevation(Player player, Location from, Location to) {
        // Sprawdź, czy gracz ma efekt skoku
        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.JUMP)) {
            return true;
        }
        
        // Sprawdź, czy w pobliżu są tłoki
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block block = to.getBlock().getRelative(x, y, z);
                    
                    // Sprawdź czy to tłok
                    Material type = block.getType();
                    if (type == Material.PISTON || 
                        type == Material.STICKY_PISTON || 
                        type == Material.PISTON_HEAD) {
                        return true;
                    }
                }
            }
        }
        
        // Sprawdź, czy dystans w poziomie jest bardzo mały - może to wskazywać
        // na wspinanie się po ścianie, co jest możliwe w niektórych przypadkach
        double horizontalDistance = Math.sqrt(
            Math.pow(to.getX() - from.getX(), 2) + 
            Math.pow(to.getZ() - from.getZ(), 2)
        );
        
        // Jeśli ruch poziomy jest bardzo mały, to może być wspinanie się
        if (horizontalDistance < 0.05) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Teleportuje gracza bezpiecznie na ziemię
     * 
     * @param player Gracz
     * @param location Lokacja docelowa
     */
    private void teleportToGround(Player player, Location location) {
        // Ustawienie wektora prędkości na zero aby zatrzymać ruch
        player.setVelocity(new Vector(0, 0, 0));
        
        // Teleportuj gracza do bezpiecznej lokacji
        player.teleport(location);
    }
}