package com.anticheatsystem.checks.pingspoof;

import com.anticheatsystem.AntiCheatMain;
import com.anticheatsystem.checks.Check;
import com.anticheatsystem.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PingSpoofCheck wykrywa graczy używających modyfikacji do manipulowania wartością pingu
 * lub zatrzymywania pakietów, aby uzyskać przewagę w grze.
 */
public class PingSpoofCheck extends Check {
    
    // Mapa przechowująca historię pingów graczy
    private final Map<UUID, LinkedList<Integer>> pingHistory = new ConcurrentHashMap<>();
    
    // Mapa przechowująca czasy odpowiedzi na keep-alive
    private final Map<UUID, Map<Integer, Long>> keepAliveResponses = new ConcurrentHashMap<>();
    
    // Mapa przechowująca czasy wysłania keep-alive
    private final Map<UUID, Map<Integer, Long>> keepAliveSent = new ConcurrentHashMap<>();
    
    // Maksymalna liczba próbek pingu
    private static final int MAX_PING_SAMPLES = 20;
    
    // Maksymalna liczba próbek keep-alive
    private static final int MAX_KEEP_ALIVE_SAMPLES = 10;
    
    // Próg standardowego odchylenia pingu wskazujący na spoofing
    private static final double PING_STD_DEV_THRESHOLD = 80.0;
    
    // Próg różnicy między pingiem a czasem keep-alive
    private static final double PING_VS_KEEP_ALIVE_THRESHOLD = 100.0;
    
    // Maksymalna akceptowalna różnica między kolejnymi pingami
    private static final int MAX_PING_DELTA = 150;
    
    // Liczba pingów z dużą różnicą wskazująca na spoofing
    private static final int UNUSUAL_PING_DELTA_THRESHOLD = 5;
    
    // Maksymalna liczba naruszenia przed podejściem sankcji
    private int unusualPingDeltaCount = 0;
    
    // Czas ostatniego naruszenia
    private long lastViolationTime = 0;
    
    // Główna instancja pluginu
    private final AntiCheatMain plugin;
    
    /**
     * Konstruktor klasy PingSpoofCheck
     * 
     * @param playerData Dane gracza
     * @param plugin Główna instancja pluginu
     */
    public PingSpoofCheck(PlayerData playerData, AntiCheatMain plugin) {
        super(playerData, "PingSpoof");
        this.plugin = plugin;
        
        // Domyślnie włączony
        this.enabled = true;
        
        // Standardowe ustawienia
        this.cancelViolation = false; // Nie można anulować pingu
        this.notifyViolation = true;
        this.maxViolations = 5;
        
        // Rozpocznij monitorowanie pingów
        startPingMonitoring();
    }
    
    /**
     * Rozpoczyna monitorowanie pingów graczy w regularnych odstępach czasu
     */
    private void startPingMonitoring() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Dla każdego gracza online
            for (Player player : Bukkit.getOnlinePlayers()) {
                // Pobierz ping gracza
                int ping = player.getPing();
                
                // Jeśli ping jest podejrzanie mały, ustaw minimalną wartość
                if (ping < 0) {
                    ping = 0;
                }
                
                // Dodaj ping do historii
                UUID playerId = player.getUniqueId();
                LinkedList<Integer> history = pingHistory.computeIfAbsent(playerId, k -> new LinkedList<>());
                history.add(ping);
                
                // Ogranicz rozmiar historii
                while (history.size() > MAX_PING_SAMPLES) {
                    history.removeFirst();
                }
                
                // Jeśli mamy wystarczająco dużo próbek, sprawdź ping
                if (history.size() >= 10) {
                    analyzePing(player, history);
                }
            }
        }, 20L, 20L); // Uruchom co sekundę (20 ticków)
    }
    
    /**
     * Analizuje historię pingów gracza w celu wykrycia manipulacji
     * 
     * @param player Gracz
     * @param pingHistory Historia pingów
     */
    private void analyzePing(Player player, List<Integer> pingHistory) {
        // Nie sprawdzaj, jeśli kontrola jest wyłączona
        if (!enabled) return;
        
        // Średni ping
        double averagePing = calculateAverage(pingHistory);
        
        // Standardowe odchylenie pingu
        double stdDev = calculateStandardDeviation(pingHistory, averagePing);
        
        // Sprawdź czy standardowe odchylenie jest podejrzanie wysokie
        if (stdDev > PING_STD_DEV_THRESHOLD && averagePing > 50) {
            // Niestabilność pingu może wskazywać na PingSpoof
            handleViolation(player, "PingSpoof", String.format(
                "Niestabilny ping (avg=%.1f, stddev=%.1f)", averagePing, stdDev));
        }
        
        // Sprawdź różnice między kolejnymi pingami
        int unusualDeltaCount = 0;
        int previousPing = pingHistory.get(0);
        
        for (int i = 1; i < pingHistory.size(); i++) {
            int currentPing = pingHistory.get(i);
            int delta = Math.abs(currentPing - previousPing);
            
            // Jeśli różnica jest zbyt duża
            if (delta > MAX_PING_DELTA) {
                unusualDeltaCount++;
            }
            
            previousPing = currentPing;
        }
        
        // Jeśli jest zbyt wiele podejrzanych różnic
        if (unusualDeltaCount >= UNUSUAL_PING_DELTA_THRESHOLD) {
            // Zwiększ licznik naruszeń
            unusualPingDeltaCount++;
            
            // Czas od ostatniego naruszenia
            long currentTime = System.currentTimeMillis();
            long timeSinceLastViolation = currentTime - lastViolationTime;
            
            // Jeśli jest to kolejne naruszenie w krótkim czasie
            if (timeSinceLastViolation < 60000 && unusualPingDeltaCount >= 3) {
                handleViolation(player, "PingSpoof", String.format(
                    "Podejrzane zmiany pingu (%d znaczących skoków, avg=%.1f)", 
                    unusualDeltaCount, averagePing));
                
                unusualPingDeltaCount = 0;
            }
            
            lastViolationTime = currentTime;
        }
        
        // Porównaj ping z czasami keep-alive
        compareWithKeepAlive(player);
    }
    
    /**
     * Porównuje ping gracza z czasami odpowiedzi na keep-alive
     * 
     * @param player Gracz
     */
    private void compareWithKeepAlive(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Pobierz czasy odpowiedzi na keep-alive
        Map<Integer, Long> responses = keepAliveResponses.get(playerId);
        Map<Integer, Long> sent = keepAliveSent.get(playerId);
        
        // Jeśli nie mamy danych
        if (responses == null || sent == null || responses.isEmpty()) {
            return;
        }
        
        // Oblicz średni czas odpowiedzi na keep-alive
        double avgKeepAliveTime = 0;
        int count = 0;
        
        for (Map.Entry<Integer, Long> entry : responses.entrySet()) {
            int id = entry.getKey();
            Long sentTime = sent.get(id);
            
            if (sentTime != null) {
                long responseTime = entry.getValue() - sentTime;
                avgKeepAliveTime += responseTime;
                count++;
            }
        }
        
        if (count > 0) {
            avgKeepAliveTime /= count;
            
            // Porównaj ze średnim pingiem
            List<Integer> history = pingHistory.get(playerId);
            if (history != null && !history.isEmpty()) {
                double avgPing = calculateAverage(history);
                
                // Jeśli różnica jest zbyt duża
                if (Math.abs(avgPing - avgKeepAliveTime / 2) > PING_VS_KEEP_ALIVE_THRESHOLD) {
                    handleViolation(player, "PingSpoof", String.format(
                        "Niezgodność pingu z keep-alive (ping=%.1f, ka=%.1f)", 
                        avgPing, avgKeepAliveTime / 2));
                }
            }
        }
    }
    
    /**
     * Rejestruje wysłanie pakietu keep-alive
     * 
     * @param player Gracz
     * @param id Identyfikator keep-alive
     */
    public void registerKeepAliveSent(Player player, int id) {
        UUID playerId = player.getUniqueId();
        Map<Integer, Long> sent = keepAliveSent.computeIfAbsent(playerId, k -> new HashMap<>());
        
        // Zapisz czas wysłania
        sent.put(id, System.currentTimeMillis());
        
        // Ogranicz rozmiar mapy
        if (sent.size() > MAX_KEEP_ALIVE_SAMPLES) {
            int oldestId = sent.keySet().stream().min(Integer::compare).orElse(-1);
            if (oldestId != -1) {
                sent.remove(oldestId);
            }
        }
    }
    
    /**
     * Rejestruje odpowiedź na pakiet keep-alive
     * 
     * @param player Gracz
     * @param id Identyfikator keep-alive
     */
    public void registerKeepAliveResponse(Player player, int id) {
        UUID playerId = player.getUniqueId();
        Map<Integer, Long> responses = keepAliveResponses.computeIfAbsent(playerId, k -> new HashMap<>());
        
        // Zapisz czas odpowiedzi
        responses.put(id, System.currentTimeMillis());
        
        // Ogranicz rozmiar mapy
        if (responses.size() > MAX_KEEP_ALIVE_SAMPLES) {
            int oldestId = responses.keySet().stream().min(Integer::compare).orElse(-1);
            if (oldestId != -1) {
                responses.remove(oldestId);
            }
        }
    }
    
    /**
     * Oblicza średnią wartość z listy
     * 
     * @param values Lista wartości
     * @return Średnia wartość
     */
    private double calculateAverage(List<Integer> values) {
        double sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum / values.size();
    }
    
    /**
     * Oblicza standardowe odchylenie z listy
     * 
     * @param values Lista wartości
     * @param mean Średnia wartość
     * @return Standardowe odchylenie
     */
    private double calculateStandardDeviation(List<Integer> values, double mean) {
        double variance = 0;
        for (int value : values) {
            variance += Math.pow(value - mean, 2);
        }
        variance /= values.size();
        return Math.sqrt(variance);
    }
    
    /**
     * Czyści dane dla gracza (np. przy wyjściu z serwera)
     * 
     * @param player Gracz
     */
    public void clearPlayerData(Player player) {
        UUID playerId = player.getUniqueId();
        pingHistory.remove(playerId);
        keepAliveResponses.remove(playerId);
        keepAliveSent.remove(playerId);
    }
}