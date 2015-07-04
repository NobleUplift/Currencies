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
import com.nobleuplift.currencies.entities.Holding;
import com.nobleuplift.currencies.entities.HoldingPK;
import com.nobleuplift.currencies.entities.Transaction;
import com.nobleuplift.currencies.entities.Unit;

public class Currencies extends JavaPlugin implements Listener {
	public static final String PREFIX = "§a[Currencies]§r ";
	public static final boolean DEBUG = true;

	public static final int CENTRAL_BANK = 1;
	public static final int BANKER = 2;
	public static final int BLACK_MARKET = 3;
	public static final int TRADER = 4;
	
	protected static Currencies instance;
	
	protected static Currencies getInstance() {
		return instance;
	}
	
    @Override
    public List<Class<?>> getDatabaseClasses() {
        List<Class<?>> list = new ArrayList<Class<?>>();
        list.add(Account.class);
        list.add(Currency.class);
        list.add(HoldingPK.class);
        list.add(Holding.class);
        list.add(Transaction.class);
        list.add(Unit.class);
        return list;
    }
	
    @Override
	public void onEnable() {
		instance = this;
		
		getConfig().options().copyDefaults(false);
		saveConfig();
		
		Bukkit.getPluginManager().registerEvents(this, this);
		
		Currencies.getInstance().getDatabase().createSqlUpdate("CREATE TABLE IF NOT EXISTS `currencies_currency` (   `id` SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,   `name` VARCHAR(64) NOT NULL,   `acronym` VARCHAR(3) NOT NULL,   `prefix` TINYINT UNSIGNED NOT NULL DEFAULT '1',   `date_created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,   `date_modified` TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:01',   `date_deleted` TIMESTAMP NULL,   `deleted` TINYINT(1) UNSIGNED NOT NULL DEFAULT '0',   PRIMARY KEY (`id`),   UNIQUE INDEX `name_UNIQUE` (`name` ASC),   UNIQUE INDEX `acronym_UNIQUE` (`acronym` ASC)) ENGINE = InnoDB;").execute();
		Currencies.getInstance().getDatabase().createSqlUpdate("CREATE TABLE IF NOT EXISTS `currencies_account` (   `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,   `name` VARCHAR(64) NOT NULL,   `uuid` VARCHAR(37) NULL,   `default_currency_id` SMALLINT UNSIGNED NULL DEFAULT NULL,   `date_created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,   `date_modified` TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:01',   PRIMARY KEY (`id`),   UNIQUE INDEX `name_UNIQUE` (`name` ASC),   UNIQUE INDEX `uuid_UNIQUE` (`uuid` ASC),   INDEX `fk_currencies_account_currencies_currency1_idx` (`default_currency_id` ASC),   CONSTRAINT `fk_currencies_account_currencies_currency1`     FOREIGN KEY (`default_currency_id`)     REFERENCES  `currencies_currency` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION) ENGINE = InnoDB;").execute();
		Currencies.getInstance().getDatabase().createSqlUpdate("CREATE TABLE IF NOT EXISTS `currencies_unit` (   `id` SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,   `currency_id` SMALLINT UNSIGNED NOT NULL,   `child_unit_id` SMALLINT UNSIGNED NULL,   `name` VARCHAR(32) NOT NULL,   `alternate` VARCHAR(32) NOT NULL,   `symbol` VARCHAR(2) NOT NULL,   `prime` TINYINT(1) UNSIGNED NOT NULL,   `main` TINYINT(1) UNSIGNED NOT NULL,   `child_multiples` INT UNSIGNED NOT NULL,   `base_multiples` INT UNSIGNED NOT NULL,   `date_created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,   `date_modified` TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:01',   PRIMARY KEY (`id`),   INDEX `fk_currencies_currency_has_currencies_unit_idx` (`currency_id` ASC),   UNIQUE INDEX `name_UNIQUE` (`name` ASC),   UNIQUE INDEX `singular_UNIQUE` (`alternate` ASC),   INDEX `fk_currencies_unit_has_currencies_child_idx` (`child_unit_id` ASC),   CONSTRAINT `fk_currencies_currency_has_currencies_unit`     FOREIGN KEY (`currency_id`)     REFERENCES `currencies_currency` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION,   CONSTRAINT `fk_currencies_unit_has_currencies_child`     FOREIGN KEY (`child_unit_id`)     REFERENCES `currencies_unit` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION) ENGINE = InnoDB;").execute();
		Currencies.getInstance().getDatabase().createSqlUpdate("CREATE TABLE IF NOT EXISTS `currencies_holding` (   `account_id` INT UNSIGNED NOT NULL,   `unit_id` SMALLINT UNSIGNED NOT NULL,   `amount` BIGINT NOT NULL,   PRIMARY KEY (`account_id`, `unit_id`),   INDEX `fk_currencies_unit_has_currencies_holding_idx` (`unit_id` ASC),   INDEX `fk_currencies_account_has_currencies_holding_idx` (`account_id` ASC),   CONSTRAINT `fk_currencies_account_has_currencies_holding`     FOREIGN KEY (`account_id`)     REFERENCES `currencies_account` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION,   CONSTRAINT `fk_currencies_unit_has_currencies_holding`     FOREIGN KEY (`unit_id`)     REFERENCES `currencies_unit` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION) ENGINE = InnoDB;").execute();
		Currencies.getInstance().getDatabase().createSqlUpdate("CREATE TABLE IF NOT EXISTS `currencies_holder` (   `parent_account_id` INT UNSIGNED NOT NULL,   `child_account_id` INT UNSIGNED NOT NULL,   `length` SMALLINT NOT NULL DEFAULT 1,   PRIMARY KEY (`parent_account_id`, `child_account_id`),   INDEX `fk_currencies_account_has_currencies_parent_account_idx` (`parent_account_id` ASC),   INDEX `fk_currencies_account_has_currencies_child_account_idx` (`child_account_id` ASC),   CONSTRAINT `fk_currencies_account_has_currencies_parent_account`     FOREIGN KEY (`parent_account_id`)     REFERENCES `currencies_account` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION,   CONSTRAINT `fk_currencies_account_has_currencies_child_account`     FOREIGN KEY (`child_account_id`)     REFERENCES `currencies_account` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION) ENGINE = InnoDB;").execute();
		Currencies.getInstance().getDatabase().createSqlUpdate("CREATE TABLE IF NOT EXISTS `currencies_transaction` (   `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,   `sender_id` INT UNSIGNED NOT NULL,   `recipient_id` INT UNSIGNED NOT NULL,   `unit_id` SMALLINT UNSIGNED NOT NULL,   `transaction_amount` BIGINT NOT NULL,   `final_sender_amount` BIGINT NULL DEFAULT NULL,   `final_recipient_amount` BIGINT NULL DEFAULT NULL,   `paid` TINYINT(1) UNSIGNED NULL DEFAULT '1',   `date_paid` TIMESTAMP NULL DEFAULT NULL,   `date_created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,   PRIMARY KEY (`id`),   INDEX `fk_currencies_recipient_has_currencies_transaction_idx` (`recipient_id` ASC),   INDEX `fk_currencies_sender_has_currencies_transaction_idx` (`sender_id` ASC),   INDEX `fk_currencies_unit_has_currencies_transaction_idx` (`unit_id` ASC),   CONSTRAINT `fk_currencies_sender_has_currencies_transaction`     FOREIGN KEY (`sender_id`)     REFERENCES `currencies_account` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION,   CONSTRAINT `fk_currencies_recipient_has_currencies_transaction`     FOREIGN KEY (`recipient_id`)     REFERENCES `currencies_account` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION,   CONSTRAINT `fk_currencies_unit_has_currencies_transaction`     FOREIGN KEY (`unit_id`)     REFERENCES `currencies_unit` (`id`)     ON DELETE NO ACTION     ON UPDATE NO ACTION) ENGINE = InnoDB;").execute();
		
		Currencies.getInstance().getDatabase().createSqlUpdate("INSERT IGNORE INTO `currencies_account` (`id`, `name`, `uuid`, `default_currency_id`, `date_created`, `date_modified`) VALUES (1, 'Central_Bank', NULL, NULL, NOW(), NOW());").execute();
		Currencies.getInstance().getDatabase().createSqlUpdate("INSERT IGNORE INTO `currencies_account` (`id`, `name`, `uuid`, `default_currency_id`, `date_created`, `date_modified`) VALUES (2, 'Banker', NULL, NULL, NOW(), NOW());").execute();
		Currencies.getInstance().getDatabase().createSqlUpdate("INSERT IGNORE INTO `currencies_account` (`id`, `name`, `uuid`, `default_currency_id`, `date_created`, `date_modified`) VALUES (3, 'Black_Market', NULL, NULL, NOW(), NOW());").execute();
		Currencies.getInstance().getDatabase().createSqlUpdate("INSERT IGNORE INTO `currencies_account` (`id`, `name`, `uuid`, `default_currency_id`, `date_created`, `date_modified`) VALUES (4, 'Trader', NULL, NULL, NOW(), NOW());").execute();
		
		System.out.print("[Currencies] Enabled.");
	}
	
    @Override
	public void onDisable() {
		System.out.print("[Currencies] Disabled.");
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		String command = cmd.getName().toLowerCase();
		System.out.println("ARGS BEFORE PARSING: " + Arrays.toString(args));
		args = parseQuotes(args);
		System.out.println("ARGS AFTER PARSING: " + Arrays.toString(args));
		
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
			pa = new Account();
			pa.setName(p.getName());
			pa.setUuid(p.getUniqueId().toString());
			pa.setDefaultCurrency(null);
			pa.setDateCreated(new Timestamp(Calendar.getInstance().getTimeInMillis()));
			pa.setDateModified(new Timestamp(Calendar.getInstance().getTimeInMillis()));
			Currencies.getInstance().getDatabase().save(pa);
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
