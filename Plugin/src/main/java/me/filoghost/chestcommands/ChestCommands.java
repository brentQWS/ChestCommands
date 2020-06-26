/*
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package me.filoghost.chestcommands;

import me.filoghost.chestcommands.command.CommandHandler;
import me.filoghost.chestcommands.command.framework.CommandFramework;
import me.filoghost.chestcommands.config.CustomPlaceholders;
import me.filoghost.chestcommands.config.Lang;
import me.filoghost.chestcommands.config.Settings;
import me.filoghost.chestcommands.config.yaml.PluginConfig;
import me.filoghost.chestcommands.hook.BarAPIHook;
import me.filoghost.chestcommands.hook.BungeeCordHook;
import me.filoghost.chestcommands.hook.PlaceholderAPIHook;
import me.filoghost.chestcommands.hook.VaultEconomyHook;
import me.filoghost.chestcommands.legacy.UpgradeExecutorException;
import me.filoghost.chestcommands.legacy.UpgradesExecutor;
import me.filoghost.chestcommands.listener.CommandListener;
import me.filoghost.chestcommands.listener.InventoryListener;
import me.filoghost.chestcommands.listener.JoinListener;
import me.filoghost.chestcommands.listener.SignListener;
import me.filoghost.chestcommands.menu.AdvancedIconMenu;
import me.filoghost.chestcommands.menu.MenuManager;
import me.filoghost.chestcommands.menu.settings.MenuSettings;
import me.filoghost.chestcommands.parser.MenuParser;
import me.filoghost.chestcommands.task.RefreshMenusTask;
import me.filoghost.chestcommands.util.ErrorCollector;
import me.filoghost.chestcommands.util.FileUtils;
import me.filoghost.chestcommands.util.Utils;
import me.filoghost.updatechecker.UpdateChecker;
import org.bstats.bukkit.MetricsLite;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class ChestCommands extends JavaPlugin {


	public static final String CHAT_PREFIX = ChatColor.DARK_GREEN + "[" + ChatColor.GREEN + "ChestCommands" + ChatColor.DARK_GREEN + "] " + ChatColor.GREEN;

	
	private static ChestCommands instance;
	private MenuManager menuManager;
	private static Settings settings;
	private static Lang lang;
	private static CustomPlaceholders placeholders;

	private static ErrorCollector lastLoadErrors;
	private static String newVersion;

	@Override
	public void onEnable() {
		if (instance != null) {
			getLogger().warning("Please do not use /reload or plugin reloaders. Do \"/cc reload\" instead.");
			return;
		}

		instance = this;
		menuManager = new MenuManager();
		settings = new Settings();
		lang = new Lang();
		placeholders = new CustomPlaceholders();
		
		if (!Utils.isClassLoaded("org.bukkit.inventory.ItemFlag")) { // ItemFlag was added in 1.8
			if (Bukkit.getVersion().contains("(MC: 1.8)")) {
				criticalShutdown("ChestCommands requires a more recent version of Bukkit 1.8 to run.");
			} else {
				criticalShutdown("ChestCommands requires at least Bukkit 1.8 to run.");
			}
			return;
		}
		
		VaultEconomyHook.INSTANCE.setup();
		BarAPIHook.INSTANCE.setup();
		PlaceholderAPIHook.INSTANCE.setup();
		BungeeCordHook.INSTANCE.setup();
		
		if (!VaultEconomyHook.INSTANCE.isEnabled()) {
			getLogger().warning("Couldn't find Vault and a compatible economy plugin! Money-related features will not work.");
		}

		if (BarAPIHook.INSTANCE.isEnabled()) {
			getLogger().info("Hooked BarAPI");
		}

		if (PlaceholderAPIHook.INSTANCE.isEnabled()) {
			getLogger().info("Hooked PlaceholderAPI");
		}

		if (settings.update_notifications) {
			UpdateChecker.run(this, 56919, (String newVersion) -> {
				ChestCommands.newVersion = newVersion;
				
				getLogger().info("Found a new version: " + newVersion + " (yours: v" + getDescription().getVersion() + ")");
				getLogger().info("Download the update on Bukkit Dev:");
				getLogger().info("https://dev.bukkit.org/projects/chest-commands");
			});
		}

		// Start bStats metrics
		int pluginID = 3658;
		new MetricsLite(this, pluginID);

		Bukkit.getPluginManager().registerEvents(new CommandListener(menuManager), this);
		Bukkit.getPluginManager().registerEvents(new InventoryListener(menuManager), this);
		Bukkit.getPluginManager().registerEvents(new JoinListener(), this);
		Bukkit.getPluginManager().registerEvents(new SignListener(menuManager), this);

		CommandFramework.register(this, new CommandHandler(menuManager, "chestcommands"));

		ErrorCollector errorCollector = load();
		
		if (errorCollector.hasWarningsOrErrors()) {
			Bukkit.getScheduler().runTaskLater(this, errorCollector::logToConsole, 10L);
		}

		Bukkit.getScheduler().runTaskTimer(this, new RefreshMenusTask(), 2L, 2L);
	}


	@Override
	public void onDisable() {
		closeAllMenus();
	}


	public ErrorCollector load() {
		ErrorCollector errors = new ErrorCollector();
		menuManager.clear();
		boolean isFreshInstall = !getDataFolder().isDirectory();
		getDataFolder().mkdirs();

		try {
			new UpgradesExecutor(this).run(isFreshInstall);
		} catch (UpgradeExecutorException e) {
			getLogger().log(Level.SEVERE, "Encountered errors while running run automatic configuration upgrades. Some configuration files or menus may require manual updates.", e);
		}

		PluginConfig settingsYaml = getSettingsConfig();
		try {
			settingsYaml.load();
			settings.load(settingsYaml);
		} catch (Throwable t) {
			logConfigLoadException(settingsYaml, t);
		}

		PluginConfig langYaml = getLangConfig();
		try {
			langYaml.load();
			lang.load(langYaml);
		} catch (Throwable t) {
			logConfigLoadException(langYaml, t);
		}

		PluginConfig placeholdersYaml = getPlaceholdersConfig();
		try {
			placeholdersYaml.load();
			placeholders.load(placeholdersYaml, errors);
		} catch (Throwable t) {
			logConfigLoadException(placeholdersYaml, t);
		}

		// Load the menus
		File menusFolder = getMenusFolder();

		if (!menusFolder.isDirectory()) {
			// Create the directory with the default menu
			menusFolder.mkdirs();
			FileUtils.saveResourceSafe(this, "menu" + File.separator + "example.yml");
		}

		List<PluginConfig> menusList = getMenuConfigs(menusFolder);
		for (PluginConfig menuConfig : menusList) {
			try {
				menuConfig.load();
			} catch (Throwable t) {
				logConfigLoadException(menuConfig, t);
				continue;
			}

			MenuSettings menuSettings = MenuParser.loadMenuSettings(menuConfig, errors);
			AdvancedIconMenu iconMenu = MenuParser.loadMenu(menuConfig, menuSettings.getTitle(), menuSettings.getRows(), errors);

			menuManager.registerMenu(menuConfig.getFileName(), menuSettings.getCommands(), iconMenu, errors);

			iconMenu.setRefreshTicks(menuSettings.getRefreshTenths());

			if (menuSettings.getOpenActions() != null) {
				iconMenu.setOpenActions(menuSettings.getOpenActions());
			}

			if (menuSettings.getOpenTrigger() != null) {
				menuManager.registerTriggerItem(menuSettings.getOpenTrigger(), iconMenu);
			}
		}
		
		ChestCommands.lastLoadErrors = errors;
		return errors;
	}

	private void logConfigLoadException(PluginConfig config, Throwable t) {
		t.printStackTrace();

		if (t instanceof IOException) {
			getLogger().warning("Error while reading the file \"" + config.getFileName() +  "\". Default values will be used.");
		} else if (t instanceof InvalidConfigurationException) {
			getLogger().warning("Invalid YAML syntax in the file \"" + config.getFileName() + "\", please look at the error above. Default values will be used.");
		} else {
			getLogger().warning("Unhandled error while parsing the file \"" + config.getFileName() + "\". Please inform the developer.");
		}
	}

	public PluginConfig getLangConfig() {
		return new PluginConfig(this, "lang.yml");
	}

	public PluginConfig getSettingsConfig() {
		return new PluginConfig(this, "config.yml");
	}

	public PluginConfig getPlaceholdersConfig() {
		return new PluginConfig(this, "custom-placeholders.yml");
	}

	public File getMenusFolder() {
		return new File(getDataFolder(), "menu");
	}

	/**
	 * Loads all the configuration files recursively into a list.
	 */
	public List<PluginConfig> getMenuConfigs(File file) {
		List<PluginConfig> list = new ArrayList<>();
		if (file.isDirectory()) {
			for (File subFile : file.listFiles()) {
				list.addAll(getMenuConfigs(subFile));
			}
		} else if (file.isFile()) {
			if (file.getName().endsWith(".yml")) {
				list.add(new PluginConfig(this, file));
			}
		}
		return list;
	}


	public static void closeAllMenus() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (MenuManager.getOpenMenu(player) != null) {
				player.closeInventory();
			}
		}
	}


	public static ChestCommands getInstance() {
		return instance;
	}

	public MenuManager getMenuManager() {
		return menuManager;
	}

	public static Settings getSettings() {
		return settings;
	}

	public static Lang getLang() {
		return lang;
	}

	public static CustomPlaceholders getCustomPlaceholders() {
		return placeholders;
	}

	public static boolean hasNewVersion() {
		return newVersion != null;
	}

	public static String getNewVersion() {
		return newVersion;
	}
	
	public static ErrorCollector getLastLoadErrors() {
		return lastLoadErrors;
	}

	private static void criticalShutdown(String... errorMessage) {
		String separator = "****************************************************************************";

		List<String> output = new ArrayList<>();

		output.add(" ");
		output.add(separator);
		for (String line : errorMessage) {
			output.add("    " + line);
		}
		output.add(" ");
		output.add("    This plugin has been disabled.");
		output.add(separator);
		output.add(" ");
		
		System.out.println("\n" + output);
		
		try {
			Thread.sleep(5000);
		} catch (InterruptedException ignored) {}
		instance.setEnabled(false);
	}

}
