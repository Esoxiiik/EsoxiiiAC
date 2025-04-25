package com.anticheatsystem.checks.movement;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sprawdzenie wykrywające graczy używających Timer hacka 
 * (przyspieszony klient, który pozwala poruszać się szybciej)
 */
public class TimerCheck extends Check {

    // Mapa przechowująca dane o pakietach graczy
    private final Map<UUID, TimerData> playerPacketData = new ConcurrentHashMap<>();
    
    // Normalny czas między pakietami ruchu (w ms)
    private static final long NORMAL_PACKET_TIME = 50; // 20 pakietów na sekundę (50ms)
    
    // Minimalna liczba pakietów do analizy
    private static final int MIN_PACKETS = 20;
    
    // Maksymalne dopuszczalne odchylenie od normalnego czasu
    private static final double MAX_SPEED_MULTIPLIER = 1.03; // 3% tolerancji
    private static final double MIN_SPEED_MULTIPLIER = 0.97; // 3% tolerancji
    
    // Minimalny czas między flagami
    private static final long FLAG_COOLDOWN = 3000;
    
    public TimerCheck(AntiCheatMain plugin) {
        super(plugin, "timer", "movement");
    }
    
    /**
     * Rejestruje pakiet ruchu od gracza
     * 
     * @param player Gracz wysyłający pakiet
     */
    public void registerMovement(Player player) {
        // Ignoruj graczy w kreatywnym/spectator
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Pobierz lub utwórz dane gracza
        TimerData data = playerPacketData.computeIfAbsent(playerId, k -> new TimerData());
        
        // Dodaj czas pakietu
        data.addPacketTime(currentTime);
        
        // Sprawdź odstępy między pakietami
        checkPacketTiming(player, data);
    }
    
    /**
     * Analizuje odstępy między pakietami, aby wykryć timer hack
     */
    private void checkPacketTiming(Player player, TimerData data) {
        List<Long> packetTimes = data.getPacketTimes();
        
        // Sprawdź tylko jeśli mamy wystarczająco dużo pakietów
        if (packetTimes.size() < MIN_PACKETS) {
            return;
        }
        
        // Oblicz odstępy między pakietami
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < packetTimes.size(); i++) {
            intervals.add(packetTimes.get(i) - packetTimes.get(i - 1));
        }
        
        // Oblicz średni odstęp
        double averageInterval = intervals.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(NORMAL_PACKET_TIME);
        
        // Oblicz stosunek normalnego czasu do średniego odstępu
        // (Stosunek > 1: gracz wysyła pakiety zbyt szybko - timer hack)
        // (Stosunek < 1: gracz wysyła pakiety zbyt wolno - możliwe lag)
        double speedMultiplier = NORMAL_PACKET_TIME / averageInterval;
        
        // Zaktualizuj dane gracza o mnożniku prędkości
        data.addSpeedMultiplier(speedMultiplier);
        
        // Jeśli mamy wystarczająco dużo próbek mnożnika prędkości
        if (data.getSpeedMultipliers().size() >= 5) {
            // Oblicz średni mnożnik prędkości z ostatnich próbek
            double avgMultiplier = data.getSpeedMultipliers().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(1.0);
            
            // Oblicz odchylenie standardowe
            double variance = data.getSpeedMultipliers().stream()
                    .mapToDouble(m -> Math.pow(m - avgMultiplier, 2))
                    .average()
                    .orElse(0);
            double stdDev = Math.sqrt(variance);
            
            // Jeśli średni mnożnik jest zbyt wysoki (gracz wysyła pakiety zbyt szybko)
            // i odchylenie standardowe jest małe (konsystentne przyspieszenie)
            if (avgMultiplier > MAX_SPEED_MULTIPLIER && stdDev < 0.1) {
                long lastFlagTime = data.getLastFlagTime();
                long currentTime = System.currentTimeMillis();
                
                // Sprawdź cooldown flagi
                if (currentTime - lastFlagTime > FLAG_COOLDOWN) {
                    // Oblicz procent przyspieszenia
                    int speedPercentage = (int) ((avgMultiplier - 1.0) * 100);
                    
                    String details = String.format("timer: +%d%% przyspieszenie (mnożnik=%.2f, odchylenie=%.3f)", 
                            speedPercentage, avgMultiplier, stdDev);
                    flag(player, Math.min(speedPercentage / 5, maxViolationsPerCheck), details);
                    
                    // Aktualizuj czas ostatniej flagi
                    data.setLastFlagTime(currentTime);
                    
                    // Powiadom administratorów jeśli przyspieszenie jest znaczne
                    if (speedPercentage > 20) {
                        plugin.notifyAdmins("Gracz " + player.getName() + 
                                " używa timer hack: " + details);
                    }
                }
            }
            // Sprawdź również zbyt wolne wysyłanie pakietów (może to być brecket)
            else if (avgMultiplier < MIN_SPEED_MULTIPLIER && stdDev < 0.1) {
                long lastFlagTime = data.getLastFlagTime();
                long currentTime = System.currentTimeMillis();
                
                // Sprawdź cooldown flagi
                if (currentTime - lastFlagTime > FLAG_COOLDOWN) {
                    // Oblicz procent spowolnienia
                    int slowPercentage = (int) ((1.0 - avgMultiplier) * 100);
                    
                    String details = String.format("timer: -%d%% spowolnienie (mnożnik=%.2f, odchylenie=%.3f)", 
                            slowPercentage, avgMultiplier, stdDev);
                    flag(player, Math.min(slowPercentage / 10, maxViolationsPerCheck), details);
                    
                    // Aktualizuj czas ostatniej flagi
                    data.setLastFlagTime(currentTime);
                }
            }
        }
        
        // Wyczyść stare pakiety
        long oldestAllowed = System.currentTimeMillis() - 10000; // ostatnie 10 sekund
        data.removePacketsOlderThan(oldestAllowed);
    }
    
    /**
     * Klasa przechowująca dane o pakietach gracza
     */
    private static class TimerData {
        private final List<Long> packetTimes = new ArrayList<>();
        private final List<Double> speedMultipliers = new ArrayList<>();
        private long lastFlagTime = 0;
        
        void addPacketTime(long time) {
            packetTimes.add(time);
            
            // Ogranicz rozmiar listy
            while (packetTimes.size() > 100) {
                packetTimes.remove(0);
            }
        }
        
        void addSpeedMultiplier(double multiplier) {
            speedMultipliers.add(multiplier);
            
            // Ogranicz rozmiar listy
            while (speedMultipliers.size() > 20) {
                speedMultipliers.remove(0);
            }
        }
        
        void removePacketsOlderThan(long time) {
            packetTimes.removeIf(t -> t < time);
        }
        
        List<Long> getPacketTimes() {
            return packetTimes;
        }
        
        List<Double> getSpeedMultipliers() {
            return speedMultipliers;
        }
        
        long getLastFlagTime() {
            return lastFlagTime;
        }
        
        void setLastFlagTime(long lastFlagTime) {
            this.lastFlagTime = lastFlagTime;
        }
    }
}