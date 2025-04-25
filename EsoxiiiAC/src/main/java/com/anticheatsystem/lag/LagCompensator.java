package com.anticheatsystem.lag;

import com.anticheatsystem.AntiCheatMain;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * System kompensacji lagów dla dokładniejszej detekcji oszustw.
 * Monitoruje opóźnienia serwera (TPS) oraz ping graczy, dostosowując
 * czułość detekcji do aktualnych warunków sieciowych.
 */
public class LagCompensator {
    
    private final AntiCheatMain plugin;
    
    // Średnie TPS mierzone w ciągu ostatnich 5 sekund
    private double averageTPS = 20.0;
    
    // Znaczniki czasowe dla obliczania TPS
    private final ConcurrentLinkedQueue<Long> tickRates = new ConcurrentLinkedQueue<>();
    
    // Maksymalny czas przechowywania pomiarów (5 sekund = 100 ticków przy 20 TPS)
    private static final int MAX_SAMPLES = 100;
    
    // Mapa przechowująca historię pingów graczy
    private final Map<UUID, PlayerLatencyData> playerLatency = new HashMap<>();
    
    // Mapa przechowująca ostatnie transakcyjne ID dla graczy
    private final Map<UUID, Short> playerTransactionIds = new HashMap<>();
    
    /**
     * Konstruktor systemu kompensacji lagów
     * 
     * @param plugin Instancja głównego pluginu
     */
    public LagCompensator(AntiCheatMain plugin) {
        this.plugin = plugin;
        
        // Uruchom zadanie mierzące TPS
        startTPSMonitor();
    }
    
    /**
     * Uruchamia zadanie regularnego mierzenia TPS serwera
     */
    private void startTPSMonitor() {
        new BukkitRunnable() {
            private long lastTickTime = System.currentTimeMillis();
            
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long elapsed = now - lastTickTime;
                
                // Dodaj nowy pomiar
                if (elapsed > 0) {
                    double instantTPS = 1000.0 / elapsed;
                    instantTPS = Math.min(20.0, instantTPS); // Maksymalnie 20 TPS
                    
                    tickRates.add((long) (instantTPS * 100)); // Zapisujemy z precyzją do 2 miejsc po przecinku
                    
                    // Usuń stare pomiary
                    while (tickRates.size() > MAX_SAMPLES) {
                        tickRates.poll();
                    }
                    
                    // Oblicz średnie TPS
                    if (!tickRates.isEmpty()) {
                        double sum = 0;
                        for (long tps : tickRates) {
                            sum += tps;
                        }
                        averageTPS = (sum / tickRates.size()) / 100.0; // Przywróć skalę
                    }
                    
                    lastTickTime = now;
                }
            }
        }.runTaskTimer(plugin, 1L, 1L); // Uruchom co tick (50ms)
    }
    
    /**
     * Aktualizuje dane o pingu gracza
     * 
     * @param player Gracz
     * @param ping Ping gracza w ms
     */
    public void updatePlayerPing(Player player, int ping) {
        UUID playerId = player.getUniqueId();
        PlayerLatencyData data = playerLatency.computeIfAbsent(playerId, id -> new PlayerLatencyData());
        data.addPingSample(ping);
    }
    
    /**
     * Rejestruje nowe transakcyjne ID dla gracza
     * Używane przy pomiarze opóźnienia na poziomie pakietów
     * 
     * @param player Gracz
     * @param transactionId ID transakcji
     * @param timestamp Znacznik czasowy wysłania
     */
    public void registerTransaction(Player player, short transactionId, long timestamp) {
        UUID playerId = player.getUniqueId();
        playerTransactionIds.put(playerId, transactionId);
        
        // Zapisz również dane transakcji w obiekcie latencji dla późniejszego pomiaru
        PlayerLatencyData data = playerLatency.computeIfAbsent(playerId, id -> new PlayerLatencyData());
        data.setTransactionSendTime(timestamp);
    }
    
    /**
     * Obsługuje odpowiedź na transakcję od gracza
     * 
     * @param player Gracz
     * @param transactionId ID transakcji
     * @param timestamp Znacznik czasowy odpowiedzi
     * @return true jeśli transakcja została rozpoznana, false w przeciwnym przypadku
     */
    public boolean handleTransactionResponse(Player player, short transactionId, long timestamp) {
        UUID playerId = player.getUniqueId();
        
        // Sprawdź czy to odpowiedź na naszą transakcję
        Short expectedId = playerTransactionIds.get(playerId);
        if (expectedId != null && expectedId.equals(transactionId)) {
            // Oblicz latencję na podstawie czasu wysłania i otrzymania
            PlayerLatencyData data = playerLatency.get(playerId);
            if (data != null) {
                long sendTime = data.getTransactionSendTime();
                if (sendTime > 0) {
                    long latency = timestamp - sendTime;
                    data.addTransactionLatencySample(latency);
                    playerTransactionIds.remove(playerId); // Usuń po obsłużeniu
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Oblicza współczynnik kompensacji lagów dla gracza
     * Wartość ta jest używana do dostosowania czułości detekcji
     * 
     * @param player Gracz
     * @return Współczynnik kompensacji (1.0 = normalne warunki, wyższe wartości = większe lagi)
     */
    public double getCompensationFactor(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerLatencyData data = playerLatency.get(playerId);
        
        // Domyślny współczynnik (brak kompensacji)
        double factor = 1.0;
        
        // Uwzględnij ping gracza
        if (data != null) {
            int ping = data.getAveragePing();
            
            // Dodaj kompensację na podstawie pingu (logarytmiczna skala)
            if (ping > 50) {
                factor += Math.log10(ping / 50.0) * 0.2;
            }
            
            // Uwzględnij również pomiary z transakcji, jeśli są dostępne
            if (data.hasTransactionData()) {
                long transactionLatency = data.getAverageTransactionLatency();
                if (transactionLatency > ping) {
                    // Jeśli latencja transakcji jest większa niż ping, to może to wskazywać
                    // na problemy z pakietami lub asynchroniczność klienta
                    factor += 0.1 * (transactionLatency - ping) / 50.0;
                }
            }
        }
        
        // Uwzględnij TPS serwera
        if (averageTPS < 19.0) {
            // Dodaj kompensację na podstawie spadku TPS
            factor += (20.0 - averageTPS) * 0.15;
        }
        
        return factor;
    }
    
    /**
     * Pobiera średnie TPS serwera
     * 
     * @return Średnie TPS
     */
    public double getAverageTPS() {
        return averageTPS;
    }
    
    /**
     * Sprawdza, czy serwer doświadcza lagów (TPS < 18)
     * 
     * @return true jeśli serwer laguje, false w przeciwnym przypadku
     */
    public boolean isServerLagging() {
        return averageTPS < 18.0;
    }
    
    /**
     * Sprawdza, czy gracz ma wysokie opóźnienie
     * 
     * @param player Gracz
     * @param threshold Próg pingu w ms (domyślnie 150ms)
     * @return true jeśli ping gracza przekracza próg
     */
    public boolean hasHighPing(Player player, int threshold) {
        UUID playerId = player.getUniqueId();
        PlayerLatencyData data = playerLatency.get(playerId);
        
        if (data != null) {
            return data.getAveragePing() > threshold;
        }
        
        // Jeśli nie mamy danych, używamy bezpośredniego pomiaru z Bukkita
        return player.getPing() > threshold;
    }
    
    /**
     * Czyści dane dla gracza (np. przy wyjściu z serwera)
     * 
     * @param player Gracz
     */
    public void clearPlayerData(Player player) {
        UUID playerId = player.getUniqueId();
        playerLatency.remove(playerId);
        playerTransactionIds.remove(playerId);
    }
    
    /**
     * Pobiera dane o opóźnieniu gracza
     * 
     * @param player Gracz
     * @return Dane o opóźnieniu lub null, jeśli nie ma
     */
    public PlayerLatencyData getPlayerLatencyData(Player player) {
        return playerLatency.get(player.getUniqueId());
    }
}