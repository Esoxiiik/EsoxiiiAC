package com.anticheatsystem.config;

/**
 * Przechowuje konfigurację dla pojedynczego sprawdzenia
 */
public class CheckConfig {

    private final String name;
    private final boolean enabled;
    private final int sensitivity;
    private final int maxViolationsPerCheck;
    
    /**
     * Tworzy nową konfigurację sprawdzenia
     * 
     * @param name Nazwa sprawdzenia (np. "movement.fly")
     * @param enabled Czy sprawdzenie jest włączone
     * @param sensitivity Czułość sprawdzenia (1-10)
     * @param maxViolationsPerCheck Maksymalna liczba naruszeń dla tego sprawdzenia
     */
    public CheckConfig(String name, boolean enabled, int sensitivity, int maxViolationsPerCheck) {
        this.name = name;
        this.enabled = enabled;
        this.sensitivity = sensitivity;
        this.maxViolationsPerCheck = maxViolationsPerCheck;
    }
    
    /**
     * Pobiera nazwę sprawdzenia
     */
    public String getName() {
        return name;
    }
    
    /**
     * Sprawdza czy sprawdzenie jest włączone
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Pobiera czułość sprawdzenia
     */
    public int getSensitivity() {
        return sensitivity;
    }
    
    /**
     * Pobiera maksymalną liczbę naruszeń dla tego sprawdzenia
     */
    public int getMaxViolationsPerCheck() {
        return maxViolationsPerCheck;
    }
}