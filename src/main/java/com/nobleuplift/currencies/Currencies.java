package com.nobleuplift.currencies;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

public class Currencies extends JavaPlugin {
	@EventHandler
	public void onEnable() {
		getConfig().options().copyDefaults(false);
		saveConfig();
		
		getDatabase();
		
		System.out.print("[Currencies] Enabled.");
	}
	

	@EventHandler
	public void onDisable() {
		System.out.print("[Currencies] Disabled.");
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		String command = cmd.getName().toLowerCase();
		
		if (command.equals("currency") || command.equals("cur")) {
			if (args.length == 0) {
				sender.sendMessage("Please refer to the guide.");
				//CurrenciesCommand.help(sender);
			} else {
				CurrenciesCommand.subcommands(sender, args);
			}
		}
		
		return false;
	}
}
