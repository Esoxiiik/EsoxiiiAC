package com.anticheatsystem.lag;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Klasa przechowująca dane o opóźnieniach sieciowych gracza.
 * Używana przez system kompensacji lagów.
 */
public class PlayerLatencyData {
    
    // Kolejka przechowująca próbki pingu
    private final Queue<Integer> pingSamples = new LinkedList<>();
    
    // Kolejka przechowująca próbki opóźnień transakcji
    private final Queue<Long> transactionLatencySamples = new LinkedList<>();
    
    // Maksymalna liczba próbek
    private static final int MAX_SAMPLES = 10;
    
    // Czas wysłania ostatniej transakcji
    private long transactionSendTime = 0;
    
    /**
     * Dodaje nową próbkę pingu
     * 
     * @param ping Wartość pingu w ms
     */
    public void addPingSample(int ping) {
        pingSamples.add(ping);
        
        // Usuń stare próbki, jeśli przekroczono limit
        while (pingSamples.size() > MAX_SAMPLES) {
            pingSamples.poll();
        }
    }
    
    /**
     * Dodaje nową próbkę opóźnienia transakcji
     * 
     * @param latency Opóźnienie w ms
     */
    public void addTransactionLatencySample(long latency) {
        // Ignoruj błędne wartości
        if (latency < 0 || latency > 10000) {
            return;
        }
        
        transactionLatencySamples.add(latency);
        
        // Usuń stare próbki, jeśli przekroczono limit
        while (transactionLatencySamples.size() > MAX_SAMPLES) {
            transactionLatencySamples.poll();
        }
    }
    
    /**
     * Ustawia czas wysłania transakcji
     * 
     * @param timestamp Znacznik czasowy wysłania
     */
    public void setTransactionSendTime(long timestamp) {
        this.transactionSendTime = timestamp;
    }
    
    /**
     * Pobiera czas wysłania transakcji
     * 
     * @return Znacznik czasowy wysłania
     */
    public long getTransactionSendTime() {
        return transactionSendTime;
    }
    
    /**
     * Sprawdza, czy są dostępne dane o opóźnieniach transakcji
     * 
     * @return true jeśli są dane, false w przeciwnym przypadku
     */
    public boolean hasTransactionData() {
        return !transactionLatencySamples.isEmpty();
    }
    
    /**
     * Oblicza średni ping
     * 
     * @return Średni ping w ms lub 0, jeśli brak danych
     */
    public int getAveragePing() {
        if (pingSamples.isEmpty()) {
            return 0;
        }
        
        int sum = 0;
        for (int ping : pingSamples) {
            sum += ping;
        }
        
        return sum / pingSamples.size();
    }
    
    /**
     * Oblicza średnie opóźnienie transakcji
     * 
     * @return Średnie opóźnienie w ms lub 0, jeśli brak danych
     */
    public long getAverageTransactionLatency() {
        if (transactionLatencySamples.isEmpty()) {
            return 0;
        }
        
        long sum = 0;
        for (long latency : transactionLatencySamples) {
            sum += latency;
        }
        
        return sum / transactionLatencySamples.size();
    }
    
    /**
     * Oblicza minimalny ping
     * 
     * @return Minimalny ping w ms lub 0, jeśli brak danych
     */
    public int getMinPing() {
        if (pingSamples.isEmpty()) {
            return 0;
        }
        
        int min = Integer.MAX_VALUE;
        for (int ping : pingSamples) {
            if (ping < min) {
                min = ping;
            }
        }
        
        return min;
    }
    
    /**
     * Oblicza maksymalny ping
     * 
     * @return Maksymalny ping w ms lub 0, jeśli brak danych
     */
    public int getMaxPing() {
        if (pingSamples.isEmpty()) {
            return 0;
        }
        
        int max = Integer.MIN_VALUE;
        for (int ping : pingSamples) {
            if (ping > max) {
                max = ping;
            }
        }
        
        return max;
    }
    
    /**
     * Czyści wszystkie dane
     */
    public void clear() {
        pingSamples.clear();
        transactionLatencySamples.clear();
        transactionSendTime = 0;
    }
}