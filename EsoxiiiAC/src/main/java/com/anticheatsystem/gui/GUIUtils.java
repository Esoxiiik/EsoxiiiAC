package com.anticheatsystem.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Klasa narzędziowa do tworzenia elementów GUI
 */
public class GUIUtils {

    /**
     * Tworzy element GUI z określonego materiału, nazwy i opisu
     * 
     * @param material Materiał przedmiotu
     * @param name Nazwa przedmiotu
     * @param lore Opis przedmiotu (wiele linii)
     * @return Utworzony przedmiot ItemStack
     */
    public static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            
            if (lore.length > 0) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(coloredLore);
            }
            
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Tworzy element GUI z efektem świecenia
     * 
     * @param material Materiał przedmiotu
     * @param name Nazwa przedmiotu
     * @param lore Opis przedmiotu (wiele linii)
     * @return Utworzony przedmiot ItemStack z efektem świecenia
     */
    public static ItemStack createGlowingItem(Material material, String name, String... lore) {
        ItemStack item = createItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Tworzy przycisk Włącz/Wyłącz
     * 
     * @param enabled Stan przycisku (włączony/wyłączony)
     * @param name Nazwa przycisku
     * @param lore Opis przycisku (opcjonalny)
     * @return ItemStack reprezentujący przycisk
     */
    public static ItemStack createToggleButton(boolean enabled, String name, String... lore) {
        // Wybierz materiał i tekst w zależności od stanu
        Material material = enabled ? Material.LIME_WOOL : Material.RED_WOOL;
        String status = enabled ? "&aWŁĄCZONY" : "&cWYŁĄCZONY";
        
        // Połącz podany opis z informacją o statusie
        List<String> fullLore = new ArrayList<>();
        fullLore.add(status);
        fullLore.add("&7Status: " + (enabled ? "&aWłączony" : "&cWyłączony"));
        fullLore.add("");
        fullLore.addAll(Arrays.asList(lore));
        
        return createItem(material, name, fullLore.toArray(new String[0]));
    }
    
    /**
     * Tworzy przycisk nawigacyjny (np. Powrót, Dalej, itp.)
     * 
     * @param material Materiał przycisku
     * @param name Nazwa przycisku
     * @param action Akcja przycisku (np. "Kliknij, aby powrócić")
     * @return ItemStack reprezentujący przycisk nawigacyjny
     */
    public static ItemStack createNavigationButton(Material material, String name, String action) {
        return createItem(material, name, "&7" + action);
    }
    
    /**
     * Tworzy separator (np. szybę) do wizualnego oddzielenia sekcji GUI
     * 
     * @param color Kolor separatora (np. BLACK, WHITE, GRAY)
     * @return ItemStack reprezentujący separator
     */
    public static ItemStack createSeparator(ChatColor color) {
        Material material;
        
        switch (color) {
            case BLACK:
                material = Material.BLACK_STAINED_GLASS_PANE;
                break;
            case DARK_GRAY:
                material = Material.GRAY_STAINED_GLASS_PANE;
                break;
            case GRAY:
                material = Material.LIGHT_GRAY_STAINED_GLASS_PANE;
                break;
            case WHITE:
                material = Material.WHITE_STAINED_GLASS_PANE;
                break;
            case RED:
                material = Material.RED_STAINED_GLASS_PANE;
                break;
            case GREEN:
                material = Material.GREEN_STAINED_GLASS_PANE;
                break;
            default:
                material = Material.GRAY_STAINED_GLASS_PANE;
        }
        
        return createItem(material, " ", "");
    }
}