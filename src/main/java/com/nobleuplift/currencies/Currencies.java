package com.nobleuplift.currencies;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Created on 2015 April 22 at ‏‎08:58:50 PM.
 *
 * @author Patrick
 */
public class Currencies extends JavaPlugin implements Listener {
	public static final String VERSION = "${project.version}";
	public static final String PREFIX = "§a[Currencies]§r ";
	public static boolean DEBUG = false;

	protected static Currencies instance;
	private DatabaseManager db;

	protected static Currencies getInstance() {
		return instance;
	}

	public DatabaseManager getDb() {
		return db;
	}

	@Override
	public void onEnable() {
		instance = this;

		saveDefaultConfig();
		Currencies.DEBUG = getConfig().getBoolean("debug");

		db = new DatabaseManager(getConfig());

		String configVersion = getConfig().getString("version");

		if ("new".equals(configVersion)) {
			initSchema();
			configVersion = "1.0.0";
			getConfig().set("version", "1.0.0");
			saveConfig();
		}

		if ("1.0.0".equals(configVersion)) {
			migrateFromV100();
			getConfig().set("version", "1.1.0");
			saveConfig();
		}

		CurrenciesCore.init(db);

		Bukkit.getPluginManager().registerEvents(this, this);
		getLogger().info(PREFIX + " Enabled.");
	}

	private void initSchema() {
		try (Connection conn = db.getConnection();
		     Statement stmt = conn.createStatement()) {
			stmt.execute("CREATE TABLE IF NOT EXISTS `currencies_currency` ("
				+ "`id` SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,"
				+ "`name` VARCHAR(64) NOT NULL,"
				+ "`acronym` VARCHAR(3) NOT NULL,"
				+ "`prefix` TINYINT UNSIGNED NOT NULL DEFAULT '1',"
				+ "`date_created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
				+ "`date_modified` TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:01',"
				+ "`date_deleted` TIMESTAMP NULL,"
				+ "`deleted` TINYINT(1) UNSIGNED NOT NULL DEFAULT '0',"
				+ "PRIMARY KEY (`id`),"
				+ "UNIQUE INDEX `name_UNIQUE` (`name` ASC),"
				+ "UNIQUE INDEX `acronym_UNIQUE` (`acronym` ASC)"
				+ ") ENGINE = InnoDB");
			stmt.execute("CREATE TABLE IF NOT EXISTS `currencies_account` ("
				+ "`id` INT UNSIGNED NOT NULL AUTO_INCREMENT,"
				+ "`name` VARCHAR(64) NOT NULL,"
				+ "`uuid` VARCHAR(37) NULL,"
				+ "`default_currency_id` SMALLINT UNSIGNED NULL DEFAULT NULL,"
				+ "`date_created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
				+ "`date_modified` TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:01',"
				+ "PRIMARY KEY (`id`),"
				+ "UNIQUE INDEX `name_UNIQUE` (`name` ASC),"
				+ "UNIQUE INDEX `uuid_UNIQUE` (`uuid` ASC),"
				+ "INDEX `fk_currencies_account_currencies_currency1_idx` (`default_currency_id` ASC),"
				+ "CONSTRAINT `fk_currencies_account_currencies_currency1`"
				+ "  FOREIGN KEY (`default_currency_id`)"
				+ "  REFERENCES `currencies_currency` (`id`)"
				+ "  ON DELETE NO ACTION ON UPDATE NO ACTION"
				+ ") ENGINE = InnoDB");
			stmt.execute("CREATE TABLE IF NOT EXISTS `currencies_unit` ("
				+ "`id` SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,"
				+ "`currency_id` SMALLINT UNSIGNED NOT NULL,"
				+ "`child_unit_id` SMALLINT UNSIGNED NULL,"
				+ "`name` VARCHAR(32) NOT NULL,"
				+ "`alternate` VARCHAR(32) NOT NULL,"
				+ "`symbol` VARCHAR(2) NOT NULL,"
				+ "`prime` TINYINT(1) UNSIGNED NOT NULL,"
				+ "`main` TINYINT(1) UNSIGNED NOT NULL,"
				+ "`child_multiples` INT UNSIGNED NOT NULL,"
				+ "`base_multiples` INT UNSIGNED NOT NULL,"
				+ "`date_created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
				+ "`date_modified` TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:01',"
				+ "PRIMARY KEY (`id`),"
				+ "INDEX `fk_currencies_currency_has_currencies_unit_idx` (`currency_id` ASC),"
				+ "UNIQUE INDEX `name_UNIQUE` (`name` ASC),"
				+ "UNIQUE INDEX `singular_UNIQUE` (`alternate` ASC),"
				+ "INDEX `fk_currencies_unit_has_currencies_child_idx` (`child_unit_id` ASC),"
				+ "CONSTRAINT `fk_currencies_currency_has_currencies_unit`"
				+ "  FOREIGN KEY (`currency_id`) REFERENCES `currencies_currency` (`id`)"
				+ "  ON DELETE NO ACTION ON UPDATE NO ACTION,"
				+ "CONSTRAINT `fk_currencies_unit_has_currencies_child`"
				+ "  FOREIGN KEY (`child_unit_id`) REFERENCES `currencies_unit` (`id`)"
				+ "  ON DELETE NO ACTION ON UPDATE NO ACTION"
				+ ") ENGINE = InnoDB");
			stmt.execute("CREATE TABLE IF NOT EXISTS `currencies_holding` ("
				+ "`account_id` INT UNSIGNED NOT NULL,"
				+ "`unit_id` SMALLINT UNSIGNED NOT NULL,"
				+ "`amount` BIGINT NOT NULL,"
				+ "PRIMARY KEY (`account_id`, `unit_id`),"
				+ "INDEX `fk_currencies_unit_has_currencies_holding_idx` (`unit_id` ASC),"
				+ "INDEX `fk_currencies_account_has_currencies_holding_idx` (`account_id` ASC),"
				+ "CONSTRAINT `fk_currencies_account_has_currencies_holding`"
				+ "  FOREIGN KEY (`account_id`) REFERENCES `currencies_account` (`id`)"
				+ "  ON DELETE NO ACTION ON UPDATE NO ACTION,"
				+ "CONSTRAINT `fk_currencies_unit_has_currencies_holding`"
				+ "  FOREIGN KEY (`unit_id`) REFERENCES `currencies_unit` (`id`)"
				+ "  ON DELETE NO ACTION ON UPDATE NO ACTION"
				+ ") ENGINE = InnoDB");
			stmt.execute("CREATE TABLE IF NOT EXISTS `currencies_holder` ("
				+ "`parent_account_id` INT UNSIGNED NOT NULL,"
				+ "`child_account_id` INT UNSIGNED NOT NULL,"
				+ "`length` SMALLINT NOT NULL DEFAULT 1,"
				+ "PRIMARY KEY (`parent_account_id`, `child_account_id`),"
				+ "INDEX `fk_currencies_account_has_currencies_parent_account_idx` (`parent_account_id` ASC),"
				+ "INDEX `fk_currencies_account_has_currencies_child_account_idx` (`child_account_id` ASC),"
				+ "CONSTRAINT `fk_currencies_account_has_currencies_parent_account`"
				+ "  FOREIGN KEY (`parent_account_id`) REFERENCES `currencies_account` (`id`)"
				+ "  ON DELETE NO ACTION ON UPDATE NO ACTION,"
				+ "CONSTRAINT `fk_currencies_account_has_currencies_child_account`"
				+ "  FOREIGN KEY (`child_account_id`) REFERENCES `currencies_account` (`id`)"
				+ "  ON DELETE NO ACTION ON UPDATE NO ACTION"
				+ ") ENGINE = InnoDB");
			stmt.execute("CREATE TABLE IF NOT EXISTS `currencies_transaction` ("
				+ "`id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,"
				+ "`sender_id` INT UNSIGNED NOT NULL,"
				+ "`recipient_id` INT UNSIGNED NOT NULL,"
				+ "`unit_id` SMALLINT UNSIGNED NOT NULL,"
				+ "`type_id` SMALLINT UNSIGNED NOT NULL,"
				+ "`transaction_amount` BIGINT NOT NULL,"
				+ "`final_sender_amount` BIGINT NULL DEFAULT NULL,"
				+ "`final_recipient_amount` BIGINT NULL,"
				+ "`paid` TINYINT(1) UNSIGNED NULL DEFAULT '1',"
				+ "`date_paid` TIMESTAMP NULL DEFAULT NULL,"
				+ "`date_created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
				+ "PRIMARY KEY (`id`),"
				+ "INDEX `fk_currencies_recipient_has_currencies_transaction_idx` (`recipient_id` ASC),"
				+ "INDEX `fk_currencies_sender_has_currencies_transaction_idx` (`sender_id` ASC),"
				+ "INDEX `fk_currencies_unit_has_currencies_transaction_idx` (`unit_id` ASC),"
				+ "CONSTRAINT `fk_currencies_sender_has_currencies_transaction`"
				+ "  FOREIGN KEY (`sender_id`) REFERENCES `currencies_account` (`id`)"
				+ "  ON DELETE NO ACTION ON UPDATE NO ACTION,"
				+ "CONSTRAINT `fk_currencies_recipient_has_currencies_transaction`"
				+ "  FOREIGN KEY (`recipient_id`) REFERENCES `currencies_account` (`id`)"
				+ "  ON DELETE NO ACTION ON UPDATE NO ACTION,"
				+ "CONSTRAINT `fk_currencies_unit_has_currencies_transaction`"
				+ "  FOREIGN KEY (`unit_id`) REFERENCES `currencies_unit` (`id`)"
				+ "  ON DELETE NO ACTION ON UPDATE NO ACTION"
				+ ") ENGINE = InnoDB");
			stmt.execute("INSERT IGNORE INTO `currencies_account` VALUES"
				+ " (1, 'Minecraft Central Bank', NULL, NULL, NOW(), NOW()),"
				+ " (2, 'Minecraft Central Banker', NULL, NULL, NOW(), NOW()),"
				+ " (3, 'The Enderman Market', NULL, NULL, NOW(), NOW()),"
				+ " (4, 'The Enderman Marketeer', NULL, NULL, NOW(), NOW())");
			stmt.execute("INSERT IGNORE INTO `currencies_holder` VALUES"
				+ " (1, 1, 0), (2, 2, 0), (3, 3, 0), (4, 4, 0), (2, 1, 1), (4, 3, 1)");
		} catch (SQLException e) {
			getLogger().severe("Schema initialization failed: " + e.getMessage());
			getServer().getPluginManager().disablePlugin(this);
		}
	}

	private void migrateFromV100() {
		try (Connection conn = db.getConnection();
		     Statement stmt = conn.createStatement()) {
			stmt.execute("ALTER TABLE `currencies_currency`"
				+ " ADD COLUMN IF NOT EXISTS `default_currency` TINYINT(1) NOT NULL DEFAULT '0' AFTER `prefix`");
			stmt.execute("INSERT IGNORE INTO `currencies_currency`"
				+ " (`id`, `name`, `acronym`, `prefix`, `default_currency`) VALUES (1, 'Craftcoin', 'MCC', true, true)");
			stmt.execute("INSERT IGNORE INTO `currencies_unit`"
				+ " (`id`,`currency_id`,`child_unit_id`,`name`,`alternate`,`symbol`,`prime`,`main`,`child_multiples`,`base_multiples`)"
				+ " VALUES (1, 1, 2, 'craftcoin', 'craftcoins', '$', true, true, 100, 100)");
			stmt.execute("INSERT IGNORE INTO `currencies_unit`"
				+ " (`id`,`currency_id`,`child_unit_id`,`name`,`alternate`,`symbol`,`prime`,`main`,`child_multiples`,`base_multiples`)"
				+ " VALUES (2, 1, null, 'craftcent', 'craftcents', '.', false, true, 0, 0)");
			stmt.execute("UPDATE `currencies_currency` SET `default_currency` = 1 WHERE `id` = 1");
		} catch (SQLException e) {
			getLogger().severe("Migration from 1.0.0 failed: " + e.getMessage());
			getServer().getPluginManager().disablePlugin(this);
		}
	}

	@Override
	public void onDisable() {
		if (db != null) {
			db.close();
		}
		getLogger().info(PREFIX + " Disabled.");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		String command = cmd.getName().toLowerCase();
		args = parseQuotes(args);
		if (Currencies.DEBUG) {
			System.out.println("PARSED ARGS: " + Arrays.toString(args));
		}

		switch (command) {
			case "currencies":
				if (args.length == 0) {
					CurrenciesCommand.help(sender);
				} else {
					CurrenciesCommand.subcommands(sender, args);
				}
				return true;

			case "openaccount":
			case "setdefault":
			case "balance":
			case "pay":
			case "bill":
			case "paybill":
			case "rejectbill":
			case "transactions":
			case "credit":
			case "debit":
			case "bankrupt":
				CurrenciesCommand.subcommands(sender, arrayPrepend(args, command));
				return true;

			default:
				return false;
		}
	}

	public static String[] parseQuotes(String[] args) {
		ArrayList<String> retval = new ArrayList<>();

		boolean doubleQuoteOpen = false;
		boolean singleQuoteOpen = false;
		String doubleQuoteBuffer = "";
		String singleQuoteBuffer = "";

		for (int i = 0; i < args.length; i++) {
			if (args[i].matches("^\".*\"$") && !doubleQuoteOpen && !singleQuoteOpen) {
				retval.add(args[i].replaceAll("(^\"*)|(\"*$)", ""));
			} else if (args[i].matches("^'.*'$") && !singleQuoteOpen && !doubleQuoteOpen) {
				retval.add(args[i].replaceAll("(^'*)|('*$)", ""));
			} else if (args[i].matches("^\".*") && !singleQuoteOpen) {
				doubleQuoteOpen = true;
				doubleQuoteBuffer = args[i].replaceAll("(^\"*)", "");
			} else if (args[i].matches("^\'.*") && !doubleQuoteOpen) {
				singleQuoteOpen = true;
				singleQuoteBuffer = args[i].replaceAll("(^'*)", "");
			} else if (args[i].matches(".*\"$") && doubleQuoteOpen) {
				doubleQuoteBuffer += " " + args[i].replaceAll("(\"*$)", "");
				retval.add(doubleQuoteBuffer);
				doubleQuoteOpen = false;
				doubleQuoteBuffer = "";
			} else if (args[i].matches(".*'$") && singleQuoteOpen) {
				singleQuoteBuffer += " " + args[i].replaceAll("('*$)", "");
				retval.add(singleQuoteBuffer);
				singleQuoteOpen = false;
				singleQuoteBuffer = "";
			} else if (doubleQuoteOpen) {
				doubleQuoteBuffer += " " + args[i];
			} else if (singleQuoteOpen) {
				singleQuoteBuffer += " " + args[i];
			} else {
				retval.add(args[i]);
			}
		}

		return retval.toArray(new String[0]);
	}

	public static String[] arrayPrepend(String[] args, String prepend) {
		String[] retval = new String[args.length + 1];
		retval[0] = prepend;
		for (int i = 0; i < args.length; i++) {
			retval[i + 1] = args[i];
		}
		return retval;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		String uuid = p.getUniqueId().toString();
		String name = p.getName();

		getLogger().info("Creating player account for " + name + " (" + uuid + ").");

		try (Connection conn = db.getConnection()) {
			Integer existingId = null;
			String existingName = null;

			try (PreparedStatement ps = conn.prepareStatement(
					"SELECT id, name FROM currencies_account WHERE uuid = ?")) {
				ps.setString(1, uuid);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						existingId = rs.getInt("id");
						existingName = rs.getString("name");
					}
				}
			}

			if (existingId == null) {
				// Rename any existing account that claims this player name
				try (PreparedStatement ps = conn.prepareStatement(
						"SELECT id, name FROM currencies_account WHERE name = ?")) {
					ps.setString(1, name);
					try (ResultSet rs = ps.executeQuery()) {
						if (rs.next()) {
							int oldId = rs.getInt("id");
							String oldName = rs.getString("name");
							try (PreparedStatement upd = conn.prepareStatement(
									"UPDATE currencies_account SET name = ?, date_modified = NOW() WHERE id = ?")) {
								upd.setString(1, oldName + "#" + oldId);
								upd.setInt(2, oldId);
								upd.executeUpdate();
							}
						}
					}
				}

				// Create new account
				try (PreparedStatement ps = conn.prepareStatement(
						"INSERT INTO currencies_account (name, uuid, date_created, date_modified) VALUES (?, ?, NOW(), NOW())",
						Statement.RETURN_GENERATED_KEYS)) {
					ps.setString(1, name);
					ps.setString(2, uuid);
					ps.executeUpdate();
					try (ResultSet keys = ps.getGeneratedKeys()) {
						if (keys.next()) {
							int newId = keys.getInt(1);
							try (PreparedStatement hps = conn.prepareStatement(
									"INSERT IGNORE INTO currencies_holder"
									+ " (parent_account_id, child_account_id, length) VALUES (?, ?, 0)")) {
								hps.setInt(1, newId);
								hps.setInt(2, newId);
								hps.executeUpdate();
							}
						}
					}
				}
			} else if (!name.equals(existingName)) {
				try (PreparedStatement ps = conn.prepareStatement(
						"UPDATE currencies_account SET name = ?, date_modified = NOW() WHERE id = ?")) {
					ps.setString(1, name);
					ps.setInt(2, existingId);
					ps.executeUpdate();
				}
			}
		} catch (SQLException e) {
			getLogger().severe("Failed to create/update account for " + name + ": " + e.getMessage());
		}
	}

	public static void tell(CommandSender player, String message) {
		player.sendMessage(PREFIX + message);
	}
}
