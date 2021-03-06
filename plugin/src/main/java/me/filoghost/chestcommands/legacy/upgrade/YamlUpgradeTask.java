/*
 * Copyright (C) filoghost and contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package me.filoghost.chestcommands.legacy.upgrade;

import java.nio.file.Path;
import me.filoghost.fcommons.config.Config;
import me.filoghost.fcommons.config.ConfigLoader;
import me.filoghost.fcommons.config.exception.ConfigLoadException;
import me.filoghost.fcommons.config.exception.ConfigSaveException;

public abstract class YamlUpgradeTask extends UpgradeTask {

    private final ConfigLoader configLoader;
    private Config updatedConfig;

    public YamlUpgradeTask(ConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    @Override
    public final Path getOriginalFile() {
        return configLoader.getFile();
    }

    @Override
    public final Path getUpgradedFile() {
        return configLoader.getFile();
    }

    @Override
    public final void computeChanges() throws ConfigLoadException {
        if (!configLoader.fileExists()) {
            return;
        }
        Config config = configLoader.load();
        computeYamlChanges(config);
        this.updatedConfig = config;
    }

    @Override
    public final void saveChanges() throws ConfigSaveException {
        configLoader.save(updatedConfig);
    }

    protected abstract void computeYamlChanges(Config config);

    protected void removeNode(Config config, String node) {
        if (config.contains(node)) {
            config.remove(node);
            setSaveRequired();
        }
    }

    protected void replaceStringValue(Config settingsConfig, String node, String target, String replacement) {
        String value = settingsConfig.getString(node);
        if (value.contains(target)) {
            settingsConfig.setString(node, value.replace(target, replacement));
            setSaveRequired();
        }
    }

}
