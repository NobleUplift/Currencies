package com.nobleuplift.currencies;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Holding;
import com.nobleuplift.currencies.entities.HoldingPK;
import com.nobleuplift.currencies.entities.Transaction;
import com.nobleuplift.currencies.entities.TransactionPK;
import com.nobleuplift.currencies.entities.Unit;

public class Currencies extends JavaPlugin {
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
        list.add(TransactionPK.class);
        list.add(Transaction.class);
        list.add(Unit.class);
        return list;
    }
	
	@EventHandler
	public void onEnable() {
		instance = this;
		
		getConfig().options().copyDefaults(false);
		saveConfig();
		
		System.out.print("[Currencies] Enabled.");
	}
	

	@EventHandler
	public void onDisable() {
		System.out.print("[Currencies] Disabled.");
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		String command = cmd.getName().toLowerCase();
		
		if (command.equals("currencies")) {
			if (args.length == 0) {
				CurrenciesCommand.help(sender);
			} else {
				CurrenciesCommand.subcommands(sender, args);
			}
			return true;
		} else {
			return false;
		}
	}
}
