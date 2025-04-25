package com.anticheatsystem.checks.phase;

import com.anticheatsystem.checks.Check;
import com.anticheatsystem.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

/**
 * Sprawdzenie Phase wykrywa graczy próbujących przechodzić przez ściany i inne stałe bloki
 */
public class PhaseCheck extends Check {
    
    // Cache bloków, przez które można przejść
    private static final Set<Material> PASSABLE_MATERIALS = new HashSet<>();
    
    // Ostatnia bezpieczna lokacja
    private Location lastSafeLocation;
    
    // Licznik naruszeń fazy w krótkim czasie
    private int phaseViolationsCount = 0;
    
    // Czas ostatniego naruszenia
    private long lastViolationTime = 0;
    
    // Próg czasu resetowania naruszeń (ms)
    private static final long VIOLATION_RESET_TIME = 5000;
    
    // Inicjalizacja statyczna - materiały, przez które można przejść
    static {
        // Powietrze, woda, lawa, itp.
        PASSABLE_MATERIALS.add(Material.AIR);
        PASSABLE_MATERIALS.add(Material.CAVE_AIR);
        PASSABLE_MATERIALS.add(Material.VOID_AIR);
        PASSABLE_MATERIALS.add(Material.WATER);
        PASSABLE_MATERIALS.add(Material.LAVA);
        
        // Rośliny i dekoracje
        PASSABLE_MATERIALS.add(Material.GRASS);
        PASSABLE_MATERIALS.add(Material.TALL_GRASS);
        PASSABLE_MATERIALS.add(Material.SEAGRASS);
        PASSABLE_MATERIALS.add(Material.TALL_SEAGRASS);
        PASSABLE_MATERIALS.add(Material.DEAD_BUSH);
        PASSABLE_MATERIALS.add(Material.VINE);
        
        // Kwiaty
        PASSABLE_MATERIALS.add(Material.DANDELION);
        PASSABLE_MATERIALS.add(Material.POPPY);
        PASSABLE_MATERIALS.add(Material.BLUE_ORCHID);
        PASSABLE_MATERIALS.add(Material.ALLIUM);
        PASSABLE_MATERIALS.add(Material.AZURE_BLUET);
        PASSABLE_MATERIALS.add(Material.RED_TULIP);
        PASSABLE_MATERIALS.add(Material.ORANGE_TULIP);
        PASSABLE_MATERIALS.add(Material.WHITE_TULIP);
        PASSABLE_MATERIALS.add(Material.PINK_TULIP);
        PASSABLE_MATERIALS.add(Material.OXEYE_DAISY);
        PASSABLE_MATERIALS.add(Material.CORNFLOWER);
        PASSABLE_MATERIALS.add(Material.LILY_OF_THE_VALLEY);
        PASSABLE_MATERIALS.add(Material.WITHER_ROSE);
        
        // Guziki, dźwignie, znaki, itp.
        PASSABLE_MATERIALS.add(Material.STONE_BUTTON);
        PASSABLE_MATERIALS.add(Material.OAK_BUTTON);
        PASSABLE_MATERIALS.add(Material.SPRUCE_BUTTON);
        PASSABLE_MATERIALS.add(Material.BIRCH_BUTTON);
        PASSABLE_MATERIALS.add(Material.JUNGLE_BUTTON);
        PASSABLE_MATERIALS.add(Material.ACACIA_BUTTON);
        PASSABLE_MATERIALS.add(Material.DARK_OAK_BUTTON);
        PASSABLE_MATERIALS.add(Material.LEVER);
        PASSABLE_MATERIALS.add(Material.OAK_SIGN);
        PASSABLE_MATERIALS.add(Material.SPRUCE_SIGN);
        PASSABLE_MATERIALS.add(Material.BIRCH_SIGN);
        PASSABLE_MATERIALS.add(Material.JUNGLE_SIGN);
        PASSABLE_MATERIALS.add(Material.ACACIA_SIGN);
        PASSABLE_MATERIALS.add(Material.DARK_OAK_SIGN);
        PASSABLE_MATERIALS.add(Material.OAK_WALL_SIGN);
        PASSABLE_MATERIALS.add(Material.SPRUCE_WALL_SIGN);
        PASSABLE_MATERIALS.add(Material.BIRCH_WALL_SIGN);
        PASSABLE_MATERIALS.add(Material.JUNGLE_WALL_SIGN);
        PASSABLE_MATERIALS.add(Material.ACACIA_WALL_SIGN);
        PASSABLE_MATERIALS.add(Material.DARK_OAK_WALL_SIGN);
        PASSABLE_MATERIALS.add(Material.LADDER);
        
        // Płyty naciskowe
        PASSABLE_MATERIALS.add(Material.STONE_PRESSURE_PLATE);
        PASSABLE_MATERIALS.add(Material.OAK_PRESSURE_PLATE);
        PASSABLE_MATERIALS.add(Material.SPRUCE_PRESSURE_PLATE);
        PASSABLE_MATERIALS.add(Material.BIRCH_PRESSURE_PLATE);
        PASSABLE_MATERIALS.add(Material.JUNGLE_PRESSURE_PLATE);
        PASSABLE_MATERIALS.add(Material.ACACIA_PRESSURE_PLATE);
        PASSABLE_MATERIALS.add(Material.DARK_OAK_PRESSURE_PLATE);
        PASSABLE_MATERIALS.add(Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        PASSABLE_MATERIALS.add(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
        
        // Dywany, śnieg, itp.
        PASSABLE_MATERIALS.add(Material.WHITE_CARPET);
        PASSABLE_MATERIALS.add(Material.ORANGE_CARPET);
        PASSABLE_MATERIALS.add(Material.MAGENTA_CARPET);
        PASSABLE_MATERIALS.add(Material.LIGHT_BLUE_CARPET);
        PASSABLE_MATERIALS.add(Material.YELLOW_CARPET);
        PASSABLE_MATERIALS.add(Material.LIME_CARPET);
        PASSABLE_MATERIALS.add(Material.PINK_CARPET);
        PASSABLE_MATERIALS.add(Material.GRAY_CARPET);
        PASSABLE_MATERIALS.add(Material.LIGHT_GRAY_CARPET);
        PASSABLE_MATERIALS.add(Material.CYAN_CARPET);
        PASSABLE_MATERIALS.add(Material.PURPLE_CARPET);
        PASSABLE_MATERIALS.add(Material.BLUE_CARPET);
        PASSABLE_MATERIALS.add(Material.BROWN_CARPET);
        PASSABLE_MATERIALS.add(Material.GREEN_CARPET);
        PASSABLE_MATERIALS.add(Material.RED_CARPET);
        PASSABLE_MATERIALS.add(Material.BLACK_CARPET);
        PASSABLE_MATERIALS.add(Material.SNOW);
        
        // Tory
        PASSABLE_MATERIALS.add(Material.RAIL);
        PASSABLE_MATERIALS.add(Material.POWERED_RAIL);
        PASSABLE_MATERIALS.add(Material.DETECTOR_RAIL);
        PASSABLE_MATERIALS.add(Material.ACTIVATOR_RAIL);
        
        // Inne
        PASSABLE_MATERIALS.add(Material.COBWEB);
        PASSABLE_MATERIALS.add(Material.TORCH);
        PASSABLE_MATERIALS.add(Material.WALL_TORCH);
        PASSABLE_MATERIALS.add(Material.REDSTONE_TORCH);
        PASSABLE_MATERIALS.add(Material.REDSTONE_WALL_TORCH);
        PASSABLE_MATERIALS.add(Material.REDSTONE_WIRE);
        PASSABLE_MATERIALS.add(Material.TRIPWIRE);
        PASSABLE_MATERIALS.add(Material.TRIPWIRE_HOOK);
    }
    
    /**
     * Konstruktor sprawdzenia Phase
     * 
     * @param playerData Dane gracza
     */
    public PhaseCheck(PlayerData playerData) {
        super(playerData, "Phase");
        
        // Domyślnie włączony
        this.enabled = true;
        
        // Standardowe ustawienia
        this.cancelViolation = true;
        this.notifyViolation = true;
        this.maxViolations = 3;
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
        
        // Ignoruj graczy w trybie kreatywnym, spectator lub z uprawnieniami do latania
        if (player.getGameMode() == GameMode.CREATIVE || 
            player.getGameMode() == GameMode.SPECTATOR ||
            player.getAllowFlight() || 
            player.isFlying()) {
            
            // Aktualizuj ostatnią bezpieczną lokację
            lastSafeLocation = to.clone();
            return false;
        }
        
        // Sprawdź, czy gracz teleportuje się (duża odległość)
        if (from.distanceSquared(to) > 100) {
            // Aktualizuj ostatnią bezpieczną lokację i zakończ
            lastSafeLocation = to.clone();
            return false;
        }
        
        // Jeśli to pierwsze wywołanie, zapisz lokację i zakończ
        if (lastSafeLocation == null) {
            lastSafeLocation = to.clone();
            return false;
        }
        
        // Sprawdź, czy gracz przechodzi przez stały blok
        if (isPassingThroughSolidBlock(from, to)) {
            // Zwiększ licznik naruszeń
            phaseViolationsCount++;
            
            // Ustaw czas naruszenia
            lastViolationTime = System.currentTimeMillis();
            
            // Wykryto potencjalnego cheata Phase
            handleViolation(player, "Phase", "Przechodzenie przez blok");
            
            // Jeśli włączone jest anulowanie, cofnij gracza
            if (cancelViolation) {
                // Teleportuj gracza do ostatniej bezpiecznej lokacji
                teleportToSafe(player);
                return true;
            }
            
            return true;
        } else {
            // Sprawdź, czy minął czas resetowania naruszeń
            if (System.currentTimeMillis() - lastViolationTime > VIOLATION_RESET_TIME) {
                phaseViolationsCount = 0;
            }
            
            // Aktualizuj ostatnią bezpieczną lokację, jeśli jest bezpieczna
            if (isSafeLocation(to)) {
                lastSafeLocation = to.clone();
            }
        }
        
        return false;
    }
    
    /**
     * Obsługa pakietu ruchu (wersja packet-based)
     * 
     * @param player Gracz
     * @param from Lokacja początkowa
     * @param to Lokacja docelowa
     * @return true, jeśli wykryto cheata, false w przeciwnym przypadku
     */
    public boolean handleMovePacket(Player player, Location from, Location to) {
        // Nie sprawdzaj, jeśli kontrola jest wyłączona
        if (!enabled) return false;
        
        // Ignoruj graczy w trybie kreatywnym, spectator lub z uprawnieniami do latania
        if (player.getGameMode() == GameMode.CREATIVE || 
            player.getGameMode() == GameMode.SPECTATOR ||
            player.getAllowFlight() || 
            player.isFlying()) {
            
            // Aktualizuj ostatnią bezpieczną lokację
            lastSafeLocation = to.clone();
            return false;
        }
        
        // Sprawdź, czy gracz teleportuje się (duża odległość)
        if (from.distanceSquared(to) > 100) {
            // Aktualizuj ostatnią bezpieczną lokację i zakończ
            lastSafeLocation = to.clone();
            return false;
        }
        
        // Jeśli to pierwsze wywołanie, zapisz lokację i zakończ
        if (lastSafeLocation == null) {
            lastSafeLocation = to.clone();
            return false;
        }
        
        // Sprawdź, czy gracz przechodzi przez stały blok
        if (isPassingThroughSolidBlock(from, to)) {
            // Zwiększ licznik naruszeń
            phaseViolationsCount++;
            
            // Ustaw czas naruszenia
            lastViolationTime = System.currentTimeMillis();
            
            // Wykryto potencjalnego cheata Phase
            handleViolation(player, "Phase", "Przechodzenie przez blok");
            
            // Jeśli włączone jest anulowanie, cofnij gracza
            if (cancelViolation) {
                // Teleportuj gracza do ostatniej bezpiecznej lokacji
                teleportToSafe(player);
                return true;
            }
            
            return true;
        } else {
            // Sprawdź, czy minął czas resetowania naruszeń
            if (System.currentTimeMillis() - lastViolationTime > VIOLATION_RESET_TIME) {
                phaseViolationsCount = 0;
            }
            
            // Aktualizuj ostatnią bezpieczną lokację, jeśli jest bezpieczna
            if (isSafeLocation(to)) {
                lastSafeLocation = to.clone();
            }
        }
        
        return false;
    }
    
    /**
     * Sprawdza, czy gracz przechodzi przez stały blok
     * 
     * @param from Lokacja początkowa
     * @param to Lokacja docelowa
     * @return true, jeśli gracz przechodzi przez stały blok, false w przeciwnym przypadku
     */
    private boolean isPassingThroughSolidBlock(Location from, Location to) {
        // Wektor kierunku ruchu
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        
        // Jeśli odległość jest zbyt mała, nie ma potrzeby sprawdzania
        if (length < 0.1) {
            return false;
        }
        
        // Normalizacja wektora
        direction.normalize();
        
        // Rozmiar kroku (0.1 bloku)
        double stepSize = 0.1;
        
        // Liczba kroków
        int steps = (int) Math.ceil(length / stepSize);
        
        // Obecna pozycja
        Vector currentPos = from.toVector();
        
        // BoundingBox gracza
        BoundingBox playerBox = BoundingBox.of(
            new Vector(0, 0, 0),
            new Vector(0.6, 1.8, 0.6)
        );
        
        // Sprawdź każdy punkt na drodze
        for (int i = 0; i < steps; i++) {
            // Przesuń pozycję o krok
            currentPos.add(direction.clone().multiply(stepSize));
            
            // Lokalizacja do sprawdzenia
            Location checkLoc = currentPos.toLocation(from.getWorld());
            
            // Przesunięty BoundingBox gracza na tę pozycję
            BoundingBox shiftedBox = playerBox.clone().shift(
                checkLoc.getX() - 0.3,
                checkLoc.getY(),
                checkLoc.getZ() - 0.3
            );
            
            // Sprawdź, czy gracz koliduje z jakimś stałym blokiem
            if (isCollidingWithSolidBlock(shiftedBox, checkLoc.getWorld())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Sprawdza, czy BoundingBox koliduje z jakimś stałym blokiem
     * 
     * @param box BoundingBox do sprawdzenia
     * @param world Świat, w którym znajduje się BoundingBox
     * @return true, jeśli koliduje z jakimś stałym blokiem, false w przeciwnym przypadku
     */
    private boolean isCollidingWithSolidBlock(BoundingBox box, org.bukkit.World world) {
        // Oblicz zakres bloków do sprawdzenia
        int minX = (int) Math.floor(box.getMinX());
        int minY = (int) Math.floor(box.getMinY());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxX = (int) Math.ceil(box.getMaxX());
        int maxY = (int) Math.ceil(box.getMaxY());
        int maxZ = (int) Math.ceil(box.getMaxZ());
        
        // Sprawdź wszystkie bloki w zakresie
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    
                    // Jeśli blok jest stały (nieprzechodni)
                    if (!isPassable(block.getType())) {
                        // BoundingBox bloku
                        BoundingBox blockBox = BoundingBox.of(
                            new Vector(x, y, z),
                            new Vector(x + 1, y + 1, z + 1)
                        );
                        
                        // Jeśli BoundingBox gracza przecina się z BoundingBox bloku
                        if (box.overlaps(blockBox)) {
                            return true;
                        }
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Sprawdza, czy lokacja jest bezpieczna (nie w stałym bloku)
     * 
     * @param location Lokacja do sprawdzenia
     * @return true, jeśli lokacja jest bezpieczna, false w przeciwnym przypadku
     */
    private boolean isSafeLocation(Location location) {
        // Sprawdź, czy blok, w którym znajduje się głowa gracza, jest przechodni
        Block headBlock = location.getBlock();
        if (!isPassable(headBlock.getType())) {
            return false;
        }
        
        // Sprawdź, czy blok, w którym znajdują się nogi gracza, jest przechodni
        Block feetBlock = location.clone().subtract(0, 1, 0).getBlock();
        if (!isPassable(feetBlock.getType())) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Sprawdza, czy materiał jest przechodni
     * 
     * @param material Materiał do sprawdzenia
     * @return true, jeśli materiał jest przechodni, false w przeciwnym przypadku
     */
    private boolean isPassable(Material material) {
        return PASSABLE_MATERIALS.contains(material);
    }
    
    /**
     * Teleportuje gracza do ostatniej bezpiecznej lokacji
     * 
     * @param player Gracz
     */
    private void teleportToSafe(Player player) {
        // Zatrzymaj ruch gracza
        player.setVelocity(new Vector(0, 0, 0));
        
        // Teleportuj gracza do ostatniej bezpiecznej lokacji
        player.teleport(lastSafeLocation);
    }
}