package com.nobleuplift.currencies;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Holder;
import com.nobleuplift.currencies.entities.HolderPK;
import com.nobleuplift.currencies.entities.Holding;
import com.nobleuplift.currencies.entities.HoldingPK;
import com.nobleuplift.currencies.entities.Transaction;
import com.nobleuplift.currencies.entities.Unit;

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
	
	protected static Currencies getInstance() {
		return instance;
	}
	
    @Override
    public List<Class<?>> getDatabaseClasses() {
        List<Class<?>> list = new ArrayList<Class<?>>();
        list.add(Account.class);
        list.add(Currency.class);
        list.add(HolderPK.class);
        list.add(Holder.class);
        list.add(HoldingPK.class);
        list.add(Holding.class);
        list.add(Transaction.class);
        list.add(Unit.class);
        return list;
    }
	
    @Override
	public void onEnable() {
		instance = this;
		
		//getConfig().options().copyDefaults(false);
		//saveConfig();
		
		String configVersion = getConfig().getString("version");
		if ("new".equals(configVersion)) {
			Currencies.getInstance().getDatabase().createSqlUpdate("CREATE TABLE IF NOT EXISTS `currencies_currency` (   `id` SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,   `name` VARCHAR(64) NOT NULL,   `acronym` VARCHAR(3) NOT NULL,   `prefix` TINYINT UNSIGNED NOT NULL DEFAULT '1',   `date_created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,   `date_modified` TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:01',   `date_deleted` TIMESTAMP NULL,   `deleted` TINYINT(1) UNSIGNED NOT NULL DEFAULT '0',   PRIMARY KEY (`id`),   UNIQUE INDEX `name_UNIQUE` (`name` ASC),   UNIQUE INDEX `acronym_UNIQUE` (`acronym` ASC)) ENGINE = InnoDB;").execute();
			Currencies.getInstance().getDatabase().createSqlUpdate("CREATE TABLE IF NOT EXISTS `currencies_account` (   `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,   `name` VARCHAR(64) NOT NULL,   `uuid` VARCHAR(37) NULL,   `default_currency_id` SMALLINT UNSIGNED NULL DEFAULT NULL,   `date_created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,   `date_modified` TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:01',   PRIMARY KEY (`id`),   UNIQUE INDEX `name_UNIQUE` (`name` ASC),   UNIQUE INDEX `uuid_UNIQUE` (`uuid` ASC),   INDEX `fk_currencies_account_currencies_currency1_idx` (`default_currency_id` ASC),   CONSTRAINT `fk_currencies_account_currencies_currency1`     FOREIGN KEY (`default_currency_id`)     REFERENCES  `currencies_currency` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION) ENGINE = InnoDB;").execute();
			Currencies.getInstance().getDatabase().createSqlUpdate("CREATE TABLE IF NOT EXISTS `currencies_unit` (   `id` SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,   `currency_id` SMALLINT UNSIGNED NOT NULL,   `child_unit_id` SMALLINT UNSIGNED NULL,   `name` VARCHAR(32) NOT NULL,   `alternate` VARCHAR(32) NOT NULL,   `symbol` VARCHAR(2) NOT NULL,   `prime` TINYINT(1) UNSIGNED NOT NULL,   `main` TINYINT(1) UNSIGNED NOT NULL,   `child_multiples` INT UNSIGNED NOT NULL,   `base_multiples` INT UNSIGNED NOT NULL,   `date_created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,   `date_modified` TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:01',   PRIMARY KEY (`id`),   INDEX `fk_currencies_currency_has_currencies_unit_idx` (`currency_id` ASC),   UNIQUE INDEX `name_UNIQUE` (`name` ASC),   UNIQUE INDEX `singular_UNIQUE` (`alternate` ASC),   INDEX `fk_currencies_unit_has_currencies_child_idx` (`child_unit_id` ASC),   CONSTRAINT `fk_currencies_currency_has_currencies_unit`     FOREIGN KEY (`currency_id`)     REFERENCES `currencies_currency` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION,   CONSTRAINT `fk_currencies_unit_has_currencies_child`     FOREIGN KEY (`child_unit_id`)     REFERENCES `currencies_unit` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION) ENGINE = InnoDB;").execute();
			Currencies.getInstance().getDatabase().createSqlUpdate("CREATE TABLE IF NOT EXISTS `currencies_holding` (   `account_id` INT UNSIGNED NOT NULL,   `unit_id` SMALLINT UNSIGNED NOT NULL,   `amount` BIGINT NOT NULL,   PRIMARY KEY (`account_id`, `unit_id`),   INDEX `fk_currencies_unit_has_currencies_holding_idx` (`unit_id` ASC),   INDEX `fk_currencies_account_has_currencies_holding_idx` (`account_id` ASC),   CONSTRAINT `fk_currencies_account_has_currencies_holding`     FOREIGN KEY (`account_id`)     REFERENCES `currencies_account` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION,   CONSTRAINT `fk_currencies_unit_has_currencies_holding`     FOREIGN KEY (`unit_id`)     REFERENCES `currencies_unit` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION) ENGINE = InnoDB;").execute();
			Currencies.getInstance().getDatabase().createSqlUpdate("CREATE TABLE IF NOT EXISTS `currencies_holder` (   `parent_account_id` INT UNSIGNED NOT NULL,   `child_account_id` INT UNSIGNED NOT NULL,   `length` SMALLINT NOT NULL DEFAULT 1,   PRIMARY KEY (`parent_account_id`, `child_account_id`),   INDEX `fk_currencies_account_has_currencies_parent_account_idx` (`parent_account_id` ASC),   INDEX `fk_currencies_account_has_currencies_child_account_idx` (`child_account_id` ASC),   CONSTRAINT `fk_currencies_account_has_currencies_parent_account`     FOREIGN KEY (`parent_account_id`)     REFERENCES `currencies_account` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION,   CONSTRAINT `fk_currencies_account_has_currencies_child_account`     FOREIGN KEY (`child_account_id`)     REFERENCES `currencies_account` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION) ENGINE = InnoDB;").execute();
			Currencies.getInstance().getDatabase().createSqlUpdate("CREATE TABLE IF NOT EXISTS `currencies_transaction` (   `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,   `sender_id` INT UNSIGNED NOT NULL,   `recipient_id` INT UNSIGNED NOT NULL,   `unit_id` SMALLINT UNSIGNED NOT NULL,   `type_id` SMALLINT UNSIGNED NOT NULL,   `transaction_amount` BIGINT NOT NULL,   `final_sender_amount` BIGINT NULL DEFAULT NULL,   `final_recipient_amount` BIGINT NULL,   `paid` TINYINT(1) UNSIGNED NULL DEFAULT '1',   `date_paid` TIMESTAMP NULL DEFAULT NULL,   `date_created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,   PRIMARY KEY (`id`),   INDEX `fk_currencies_recipient_has_currencies_transaction_idx` (`recipient_id` ASC),   INDEX `fk_currencies_sender_has_currencies_transaction_idx` (`sender_id` ASC),   INDEX `fk_currencies_unit_has_currencies_transaction_idx` (`unit_id` ASC),   CONSTRAINT `fk_currencies_sender_has_currencies_transaction`     FOREIGN KEY (`sender_id`)     REFERENCES `minecraft`.`currencies_account` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION,   CONSTRAINT `fk_currencies_recipient_has_currencies_transaction`     FOREIGN KEY (`recipient_id`)     REFERENCES `minecraft`.`currencies_account` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION,   CONSTRAINT `fk_currencies_unit_has_currencies_transaction`     FOREIGN KEY (`unit_id`)     REFERENCES `minecraft`.`currencies_unit` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION) ENGINE = InnoDB;").execute();
			
			Currencies.getInstance().getDatabase().createSqlUpdate("INSERT IGNORE INTO `currencies_account` VALUES (1, 'Minecraft Central Bank', NULL, NULL, NOW(), NOW()), (2, 'Minecraft Central Banker', NULL, NULL, NOW(), NOW()), (3, 'The Enderman Market', NULL, NULL, NOW(), NOW()), (4, 'The Enderman Marketeer', NULL, NULL, NOW(), NOW());").execute();
			Currencies.getInstance().getDatabase().createSqlUpdate("INSERT IGNORE INTO `currencies_holder` VALUES (1, 1, 0), (2, 2, 0), (3, 3, 0), (4, 4, 0), (2, 1, 1), (4, 3, 1);");

			configVersion = "1.0.0";
			getConfig().set("version", "1.0.0");
			saveConfig();
		}
		
		if ("1.0.0".equals(configVersion)) {
			Currencies.getInstance().getDatabase().createSqlUpdate("ALTER TABLE `currencies_currency` ADD COLUMN `default_currency` TINYINT(1) NOT NULL DEFAULT '0' COMMENT '' AFTER `prefix`;").execute();
			Currencies.getInstance().getDatabase().createSqlUpdate("INSERT IGNORE INTO `currencies_currency` (`id`, `name`, `acronym`, `prefix`, `default_currency`) VALUES (1, 'Craftcoin', 'MCC', true, true);").execute();
			Currencies.getInstance().getDatabase().createSqlUpdate("INSERT IGNORE INTO `currencies_unit`(`id`,`currency_id`,`child_unit_id`,`name`,`alternate`,`symbol`,`prime`,`main`,`child_multiples`,`base_multiples`) VALUES (1, 1, 2, 'craftcoin', 'craftcoins', '$', true, true, 100, 100);").execute();
			Currencies.getInstance().getDatabase().createSqlUpdate("INSERT IGNORE INTO `currencies_unit`(`id`,`currency_id`,`child_unit_id`,`name`,`alternate`,`symbol`,`prime`,`main`,`child_multiples`,`base_multiples`) VALUES (2, 1, null, 'craftcent', 'craftcents', '.', false, true, 0, 0);").execute();
			/* 
			 * Since a server owner on 1.0.0 already has a currency, 
			 * set the existing currency to be the default.
			 * Assumes that the currency created already has a subunit, 
			 * so that the INSERT IGNORE will skip an existing currency.
			 */ 
			Currencies.getInstance().getDatabase().createSqlUpdate("UPDATE `currencies_currency` SET `default_currency`='1' WHERE `id`='1';").execute();
			
			configVersion = "1.1.0";
			getConfig().set("version", "1.1.0");
			saveConfig();
		}
		
		// TODO: Add error message?
		//if (!VERSION.equals(configVersion)) {
		//	getConfig().set("version", VERSION);
		//	saveConfig();
		//}
		Currencies.DEBUG = getConfig().getBoolean("debug");
		
		Bukkit.getPluginManager().registerEvents(this, this);
		
		getLogger().info(PREFIX + " Enabled.");
	}
	
    @Override
	public void onDisable() {
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
		List<String> retval = new ArrayList<>();
		
		boolean doubleQuoteOpen = false;
		boolean singleQuoteOpen = false;
		String doubleQuoteBuffer = "";
		String singleQuoteBuffer = "";
		
		for (int i = 0; i < args.length; i++ ) {
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
		
		return (String[]) retval.toArray(new String[retval.size()]);
	}
	
	public static String[] arrayPrepend(String[] args, String prepend) {
		String[] retval = new String[args.length + 1];
		
		retval[0] = prepend;
		
		for (int i = 0; i < args.length; i++) {
			retval[i + 1] = args[i];
		}
		
		return retval;
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player p = event.getPlayer();
		
		getLogger().info("Creating player account for " + p.getName() + " (" + p.getUniqueId().toString() + ").");
		Account pa = Currencies.getInstance().getDatabase().find(Account.class).where().eq("uuid", p.getUniqueId().toString()).findUnique();
		if (pa == null) {
			Account nameAccount = Currencies.getInstance().getDatabase().find(Account.class)
				.where()
				.eq("name", p.getName())
				.findUnique();
			if (nameAccount != null) {
				nameAccount.setName(nameAccount.getName() + "CurrenciesAccount" + nameAccount.getId());
				Currencies.getInstance().getDatabase().save(nameAccount);
			}
			
			pa = new Account();
			pa.setName(p.getName());
			pa.setUuid(p.getUniqueId().toString());
			pa.setDefaultCurrency(null);
			pa.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
			pa.setDateModified(new Timestamp(Calendar.getInstance().getTimeInMillis()));
			Currencies.getInstance().getDatabase().save(pa);
			
			Holder h = new Holder();
			HolderPK hpk = new HolderPK();
			hpk.setParentAccountId(pa.getId());
			hpk.setChildAccountId(pa.getId());
			h.setId(hpk);
			h.setLength((short) 0);
			Currencies.getInstance().getDatabase().save(h);
		} else if (!p.getName().equals(pa.getName())) {
			pa.setName(p.getName());
			pa.setDateModified(new Timestamp(Calendar.getInstance().getTimeInMillis()));
			Currencies.getInstance().getDatabase().save(pa);
		}
	}
	
	public static void tell(CommandSender player, String message) {
		player.sendMessage(PREFIX + message);
	}
}
