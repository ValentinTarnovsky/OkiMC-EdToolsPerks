package com.okimc.edtoolsperks;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;

public class LicenseManager {
    private final JavaPlugin plugin;
    private final String VALIDATION_URL = "https://okimc-license-server.okimc-dev.workers.dev";

    public LicenseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean checkLicense() {
        try {
            File pluginsFolder = plugin.getDataFolder().getParentFile();
            File sharedFolder = new File(pluginsFolder, ".OkiMC-License");
            File licenseFile = new File(sharedFolder, "license.yml");

            if (!licenseFile.exists()) {
                plugin.getLogger().severe("[OkiMC] License not found at: " + licenseFile.getPath());
                try {
                    sharedFolder.mkdirs();
                    if (!licenseFile.exists()) {
                        licenseFile.createNewFile();
                    }
                } catch (Exception ignored) {
                }
                return false;
            }

            List<String> lines = Files.readAllLines(licenseFile.toPath());
            if (lines.isEmpty()) {
                return false;
            }

            String licenseKey = lines.get(0).trim();
            return validateOnline(licenseKey);
        } catch (Exception e) {
            plugin.getLogger().severe("[OkiMC] License check error: " + e.getMessage());
            return false;
        }
    }

    private boolean validateOnline(String key) {
        try {
            URL url = new URL(VALIDATION_URL + "?key=" + key);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "OkiMC-Plugin");
            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
