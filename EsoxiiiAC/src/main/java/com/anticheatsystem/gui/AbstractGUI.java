package com.anticheatsystem.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Abstrakcyjna klasa bazowa dla wszystkich interfejsów GUI w systemie
 */
public abstract class AbstractGUI implements InventoryHolder {
    
    // Inventory Bukkit do wyświetlenia
    protected Inventory inventory;
    
    // Tytuł wyświetlany w inwentarzu
    protected final String title;
    
    // Rozmiar inwentarza (musi być wielokrotnością 9)
    protected final int size;
    
    /**
     * Konstruktor dla interfejsu GUI
     * 
     * @param title Tytuł GUI
     * @param size Rozmiar GUI (musi być wielokrotnością 9)
     */
    public AbstractGUI(String title, int size) {
        this.title = title;
        this.size = size;
        this.inventory = Bukkit.createInventory(this, size, title);
    }
    
    /**
     * Pobierz obiekt Inventory
     */
    @Override
    public Inventory getInventory() {
        return inventory;
    }
    
    /**
     * Otwórz GUI dla gracza
     * 
     * @param player Gracz, dla którego ma być otwarte GUI
     */
    public void open(Player player) {
        // Wypełnij GUI (odświeżenie zawartości)
        populate();
        
        // Otwórz GUI dla gracza
        player.openInventory(inventory);
    }
    
    /**
     * Obsługa kliknięcia w GUI
     * 
     * @param event Zdarzenie kliknięcia
     */
    public abstract void handleClick(InventoryClickEvent event);
    
    /**
     * Wypełnia GUI elementami. Powinna być wywołana ponownie za każdym razem,
     * gdy GUI jest otwierane (dla odświeżenia zawartości).
     */
    public abstract void populate();
    
    /**
     * Sprawdź, czy dany slot jest w odpowiednim zakresie dla tego GUI
     * 
     * @param slot Numer slotu do sprawdzenia
     * @return true jeśli slot jest w zakresie, false w przeciwnym przypadku
     */
    protected boolean isValidSlot(int slot) {
        return slot >= 0 && slot < size;
    }
    
    /**
     * Umieść przedmiot w danym slocie
     * 
     * @param slot Numer slotu
     * @param item Przedmiot do umieszczenia
     */
    protected void setItem(int slot, ItemStack item) {
        if (isValidSlot(slot)) {
            inventory.setItem(slot, item);
        }
    }
    
    /**
     * Wypełnij inwentarz danym przedmiotem
     * 
     * @param item Przedmiot do wypełnienia
     */
    protected void fillInventory(ItemStack item) {
        for (int i = 0; i < size; i++) {
            inventory.setItem(i, item);
        }
    }
    
    /**
     * Wypełnij obramowanie inwentarza danym przedmiotem
     * 
     * @param item Przedmiot do wypełnienia obramowania
     */
    protected void fillBorder(ItemStack item) {
        int rows = size / 9;
        
        // Górny i dolny rząd
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, item); // Górny rząd
            inventory.setItem(size - 9 + i, item); // Dolny rząd
        }
        
        // Lewa i prawa kolumna (bez rogów, które już są wypełnione)
        for (int row = 1; row < rows - 1; row++) {
            inventory.setItem(row * 9, item); // Lewa kolumna
            inventory.setItem(row * 9 + 8, item); // Prawa kolumna
        }
    }
    
    /**
     * Wyczyść zawartość GUI
     */
    protected void clear() {
        inventory.clear();
    }
}