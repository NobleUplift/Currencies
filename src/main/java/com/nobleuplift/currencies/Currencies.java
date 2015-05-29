package com.nobleuplift.currencies;

import java.sql.Timestamp;
import java.util.ArrayList;
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
		
		System.out.print("[Currencies] Enabled.");
	}
	
    @Override
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
}
