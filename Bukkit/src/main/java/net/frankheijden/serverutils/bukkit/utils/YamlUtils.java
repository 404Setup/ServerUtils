package net.frankheijden.serverutils.bukkit.utils;

import java.io.File;
import java.io.IOException;

import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;

public class YamlUtils {

    public static void addDefaults(MemorySection defaults, YamlConfiguration yml) {
        addDefaults(defaults, yml, "");
    }

    private static void addDefaults(MemorySection defaults, YamlConfiguration yml, String root) {
        if (defaults == null) return;
        for (String key : defaults.getKeys(false)) {
            String newKey = (root.isEmpty() ? "" : root + ".") + key;
            Object value = defaults.get(key);
            if (value instanceof MemorySection) {
                addDefaults((MemorySection) value, yml, newKey);
            } else if (yml.get(newKey) == null) {
                yml.set(newKey, value);
            }
        }
    }

    /**
     * Initiates a YamlConfiguration from a file with associated defaults.
     * @param file The yml file.
     * @param def The default YamlConfiguration to be applied.
     * @return The loaded YamlConfiguration of the file with defaults.
     */
    public static YamlConfiguration init(File file, YamlConfiguration def) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        YamlUtils.addDefaults(def, yml);

        try {
            // Idk somehow the order messes up
            // of the messages if we don't do this
            file.delete();
            file.createNewFile();

            yml.save(file);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return yml;
    }
}