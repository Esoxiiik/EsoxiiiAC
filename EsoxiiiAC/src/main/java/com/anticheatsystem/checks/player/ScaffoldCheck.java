package com.anticheatsystem.checks.player;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sprawdzenie wykrywające graczy używających scaffold (automatyczne stawianie bloków)
 */
public class ScaffoldCheck extends Check {

    // Mapa przechowująca dane o stawianiu bloków przez graczy
    private final Map<UUID, BlockPlaceData> blockPlaceData = new ConcurrentHashMap<>();
    
    // Minimalny czas między postawieniami bloków (w ms)
    private static final long MIN_PLACE_TIME = 100;
    
    // Maksymalna liczba postawionych bloków w krótkim czasie
    private static final int MAX_BLOCKS_IN_TIME_FRAME = 12;
    
    // Okres czasu do analizy (w ms)
    private static final long TIME_FRAME = 3000;
    
    // Maksymalny kąt do wykrycia podejrzanych rotacji
    private static final double MAX_ANGLE_DIFFERENCE = 40.0;
    
    public ScaffoldCheck(AntiCheatMain plugin) {
        super(plugin, "scaffold", "player");
    }
    
    /**
     * Sprawdza, czy gracz używa scaffold hacka
     * 
     * @param player Gracz do sprawdzenia
     * @param block Postawiony blok
     * @param against Blok, na którym został postawiony
     * @param event Wydarzenie postawienia bloku
     */
    public void checkScaffold(Player player, Block block, Block against, BlockPlaceEvent event) {
        // Ignoruj graczy w trybie kreatywnym
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Pobierz lub utwórz dane dla gracza
        BlockPlaceData data = blockPlaceData.computeIfAbsent(playerId, k -> new BlockPlaceData());
        
        // Aktualizuj dane
        data.addBlockPlace(currentTime, player.getLocation(), block.getLocation());
        
        // Różne sprawdzenia na scaffold
        checkPlacementSpeed(player, data, currentTime);
        checkPlacementPattern(player, data);
        checkAbnormalRotations(player, data);
        checkImpossiblePlacements(player, block, against);
    }
    
    /**
     * Sprawdza czy gracz stawia bloki zbyt szybko
     */
    private void checkPlacementSpeed(Player player, BlockPlaceData data, long currentTime) {
        List<Long> placeTimes = data.getPlaceTimes();
        
        // Jeśli mamy co najmniej 2 postawienia bloków
        if (placeTimes.size() >= 2) {
            // Sprawdź czas od ostatniego postawienia
            long lastTime = placeTimes.get(placeTimes.size() - 2);
            long timeDiff = currentTime - lastTime;
            
            // Jeśli bloki stawiane są zbyt szybko
            if (timeDiff < MIN_PLACE_TIME) {
                String details = String.format("block_placement_speed: %dms (min: %dms)", 
                        timeDiff, MIN_PLACE_TIME);
                flag(player, 3, details);
            }
            
            // Sprawdź liczbę bloków postawionych w określonym przedziale czasu
            int blocksInTimeFrame = 0;
            for (int i = placeTimes.size() - 1; i >= 0; i--) {
                if (currentTime - placeTimes.get(i) <= TIME_FRAME) {
                    blocksInTimeFrame++;
                } else {
                    break;
                }
            }
            
            // Jeśli postawiono zbyt wiele bloków w krótkim czasie
            if (blocksInTimeFrame > MAX_BLOCKS_IN_TIME_FRAME) {
                String details = String.format("block_placement_rate: %d bloków w %.1f s", 
                        blocksInTimeFrame, TIME_FRAME / 1000.0);
                flag(player, 5, details);
                
                // Powiadom administratorów
                plugin.notifyAdmins("Gracz " + player.getName() + 
                        " podejrzany o scaffold: " + details);
            }
        }
    }
    
    /**
     * Sprawdza wzorce stawiania bloków
     */
    private void checkPlacementPattern(Player player, BlockPlaceData data) {
        List<BlockPlaceInfo> placeHistory = data.getPlaceHistory();
        
        // Sprawdź tylko jeśli mamy wystarczająco dużo danych
        if (placeHistory.size() < 5) {
            return;
        }
        
        // Sprawdź odległości między stawianymi blokami
        List<Double> distances = new ArrayList<>();
        for (int i = 1; i < placeHistory.size(); i++) {
            Location prevLoc = placeHistory.get(i-1).blockLocation;
            Location currLoc = placeHistory.get(i).blockLocation;
            
            // Pomiń, jeśli bloki są w różnych światach
            if (!prevLoc.getWorld().equals(currLoc.getWorld())) {
                continue;
            }
            
            // Oblicz odległość między blokami
            double distance = prevLoc.distance(currLoc);
            distances.add(distance);
        }
        
        // Jeśli mamy wystarczająco odległości do analizy
        if (distances.size() >= 4) {
            // Sprawdź czy odległości są zbyt regularne (typowe dla scaffold)
            double mean = distances.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            
            double variance = distances.stream()
                    .mapToDouble(d -> Math.pow(d - mean, 2))
                    .average()
                    .orElse(0);
            
            double stdDev = Math.sqrt(variance);
            
            // Jeśli odchylenie standardowe jest bardzo małe (zbyt regularne stawianie)
            if (stdDev < 0.1 && mean >= 0.9 && mean <= 1.1) {
                String details = String.format("regular_placement: śr=%.2f, stddev=%.2f", 
                        mean, stdDev);
                flag(player, 4, details);
            }
        }
        
        // Sprawdź, czy gracz stawia bloki w idealnej linii (charakterystyczne dla scaffold)
        if (placeHistory.size() >= 6) {
            int straightLinePlacements = 0;
            
            // Pobierz 6 ostatnich bloków
            List<Location> last6Blocks = new ArrayList<>();
            for (int i = placeHistory.size() - 6; i < placeHistory.size(); i++) {
                last6Blocks.add(placeHistory.get(i).blockLocation);
            }
            
            // Sprawdź, czy bloki są w linii prostej
            if (areBlocksInStraightLine(last6Blocks)) {
                String details = "straight_line_placement: bloki stawiane w idealnej linii";
                flag(player, 5, details);
            }
        }
    }
    
    /**
     * Sprawdza, czy bloki są stawiane w idealnej linii prostej
     */
    private boolean areBlocksInStraightLine(List<Location> locations) {
        if (locations.size() < 3) {
            return false;
        }
        
        // Sprawdź czy wszystkie bloki są w tym samym świecie
        for (int i = 1; i < locations.size(); i++) {
            if (!locations.get(0).getWorld().equals(locations.get(i).getWorld())) {
                return false;
            }
        }
        
        // Sprawdź czy bloki są w linii X
        boolean straightX = true;
        int x = locations.get(0).getBlockX();
        for (int i = 1; i < locations.size(); i++) {
            if (locations.get(i).getBlockX() != x) {
                straightX = false;
                break;
            }
        }
        
        // Sprawdź czy bloki są w linii Z
        boolean straightZ = true;
        int z = locations.get(0).getBlockZ();
        for (int i = 1; i < locations.size(); i++) {
            if (locations.get(i).getBlockZ() != z) {
                straightZ = false;
                break;
            }
        }
        
        // Sprawdź czy bloki są w linii Y
        boolean straightY = true;
        int y = locations.get(0).getBlockY();
        for (int i = 1; i < locations.size(); i++) {
            if (locations.get(i).getBlockY() != y) {
                straightY = false;
                break;
            }
        }
        
        // Jeśli wszystkie bloki są w linii prostej na jednej osi, ale nie na wszystkich
        return (straightX && !straightZ) || (straightZ && !straightX) || 
               (straightY && (!straightX || !straightZ));
    }
    
    /**
     * Sprawdza nienaturalne rotacje podczas stawiania bloków
     */
    private void checkAbnormalRotations(Player player, BlockPlaceData data) {
        List<BlockPlaceInfo> placeHistory = data.getPlaceHistory();
        
        // Sprawdź tylko jeśli mamy wystarczająco dużo danych
        if (placeHistory.size() < 3) {
            return;
        }
        
        // Sprawdź zmiany rotacji podczas stawiania bloków
        for (int i = 2; i < placeHistory.size(); i++) {
            float yaw1 = placeHistory.get(i-2).playerYaw;
            float yaw2 = placeHistory.get(i-1).playerYaw;
            float yaw3 = placeHistory.get(i).playerYaw;
            
            float pitch1 = placeHistory.get(i-2).playerPitch;
            float pitch2 = placeHistory.get(i-1).playerPitch;
            float pitch3 = placeHistory.get(i).playerPitch;
            
            // Oblicz zmiany yaw
            float yawChange1 = Math.abs(yaw2 - yaw1);
            float yawChange2 = Math.abs(yaw3 - yaw2);
            
            // Normalizuj zmiany yaw (ponieważ 359 -> 0 to zmiana o 1, a nie 359)
            yawChange1 = Math.min(yawChange1, 360 - yawChange1);
            yawChange2 = Math.min(yawChange2, 360 - yawChange2);
            
            // Oblicz zmiany pitch
            float pitchChange1 = Math.abs(pitch2 - pitch1);
            float pitchChange2 = Math.abs(pitch3 - pitch2);
            
            // Sprawdź nagłe zmiany kierunku (charakterystyczne dla scaffold)
            if (yawChange1 > MAX_ANGLE_DIFFERENCE && yawChange2 > MAX_ANGLE_DIFFERENCE) {
                String details = String.format("abnormal_rotation: zbyt duże zmiany yaw (%.1f°, %.1f°)", 
                        yawChange1, yawChange2);
                flag(player, 3, details);
            }
            
            // Sprawdź, czy gracz nagle patrzy w dół i z powrotem (typowe dla scaffold)
            if (pitchChange1 > 45 && pitchChange2 > 45 && 
                    pitch2 > 70 && // patrzenie w dół
                    (pitch1 < 30 || pitch3 < 30)) { // patrzenie przed siebie
                
                String details = String.format("scaffold_rotation: nagłe patrzenie w dół (%.1f°->%.1f°->%.1f°)", 
                        pitch1, pitch2, pitch3);
                flag(player, 5, details);
                
                // Powiadom administratorów
                plugin.notifyAdmins("Gracz " + player.getName() + 
                        " podejrzany o scaffold: " + details);
            }
        }
    }
    
    /**
     * Sprawdza niemożliwe do wykonania postawienia bloków
     */
    private void checkImpossiblePlacements(Player player, Block block, Block against) {
        Location playerLoc = player.getLocation();
        Location blockLoc = block.getLocation();
        
        // Sprawdź czy gracz stawi blok na sobie (często niemożliwe bez scaffold)
        if (blockLoc.getBlockX() == playerLoc.getBlockX() && 
                blockLoc.getBlockZ() == playerLoc.getBlockZ() &&
                Math.abs(blockLoc.getBlockY() - playerLoc.getBlockY()) <= 1) {
            
            Vector playerDirection = playerLoc.getDirection().normalize();
            Vector toBlock = blockLoc.toVector().subtract(playerLoc.toVector()).normalize();
            
            // Oblicz kąt między kierunkiem patrzenia a kierunkiem do bloku
            double angle = Math.toDegrees(Math.acos(playerDirection.dot(toBlock)));
            
            // Jeśli kąt jest zbyt duży (gracz nie patrzy na blok)
            if (angle > 90) {
                String details = String.format("impossible_placement: blok postawiony poza polem widzenia (kąt=%.1f°)", 
                        angle);
                flag(player, 7, details);
                
                // Powiadom administratorów
                plugin.notifyAdmins("Gracz " + player.getName() + 
                        " używa scaffold: " + details);
            }
        }
        
        // Sprawdź czy blok został postawiony na powietrzu (niemożliwe bez scaffold)
        if (against.getType() == Material.AIR) {
            String details = "impossible_placement: blok postawiony na powietrzu";
            flag(player, 10, details);
            
            // Powiadom administratorów
            plugin.notifyAdmins("Gracz " + player.getName() + 
                    " używa scaffold: " + details);
        }
        
        // Sprawdź czy blok został postawiony na niewidocznej ścianie
        if (!isBlockFaceVisible(player, against, getBlockFace(block, against))) {
            String details = "impossible_placement: blok postawiony na niewidocznej ścianie";
            flag(player, 5, details);
        }
    }
    
    /**
     * Pobiera ścianę bloku, na której został postawiony drugi blok
     */
    private BlockFace getBlockFace(Block placed, Block against) {
        for (BlockFace face : BlockFace.values()) {
            if (placed.getX() - against.getX() == face.getModX() &&
                placed.getY() - against.getY() == face.getModY() &&
                placed.getZ() - against.getZ() == face.getModZ()) {
                return face;
            }
        }
        return null;
    }
    
    /**
     * Sprawdza, czy ściana bloku jest widoczna dla gracza
     */
    private boolean isBlockFaceVisible(Player player, Block block, BlockFace face) {
        if (face == null) {
            return true; // w razie wątpliwości, zakładamy że jest widoczna
        }
        
        // Pobierz normalne dla danej ściany
        Vector normal = new Vector(face.getModX(), face.getModY(), face.getModZ()).normalize();
        
        // Pobierz kierunek patrzenia gracza
        Vector playerDirection = player.getLocation().getDirection().normalize();
        
        // Oblicz kąt między normalną ściany a kierunkiem patrzenia
        double angle = Math.toDegrees(Math.acos(normal.dot(playerDirection)));
        
        // Jeśli kąt jest mniejszy niż 90 stopni, ściana jest widoczna
        return angle < 90;
    }
    
    /**
     * Klasa przechowująca informacje o postawionym bloku
     */
    private static class BlockPlaceInfo {
        final long time;
        final Location playerLocation;
        final Location blockLocation;
        final float playerYaw;
        final float playerPitch;
        
        BlockPlaceInfo(long time, Location playerLocation, Location blockLocation) {
            this.time = time;
            this.playerLocation = playerLocation.clone();
            this.blockLocation = blockLocation.clone();
            this.playerYaw = playerLocation.getYaw();
            this.playerPitch = playerLocation.getPitch();
        }
    }
    
    /**
     * Klasa przechowująca dane o stawianiu bloków przez gracza
     */
    private static class BlockPlaceData {
        private final List<Long> placeTimes = new ArrayList<>();
        private final List<BlockPlaceInfo> placeHistory = new ArrayList<>();
        
        void addBlockPlace(long time, Location playerLocation, Location blockLocation) {
            // Dodaj czas postawienia
            placeTimes.add(time);
            
            // Ogranicz rozmiar listy
            while (placeTimes.size() > 50) {
                placeTimes.remove(0);
            }
            
            // Dodaj informacje o postawieniu
            placeHistory.add(new BlockPlaceInfo(time, playerLocation, blockLocation));
            
            // Ogranicz rozmiar historii
            while (placeHistory.size() > 20) {
                placeHistory.remove(0);
            }
        }
        
        List<Long> getPlaceTimes() {
            return placeTimes;
        }
        
        List<BlockPlaceInfo> getPlaceHistory() {
            return placeHistory;
        }
    }
}