package com.nobleuplift.currencies;

import org.bukkit.command.CommandSender;

/**
 * Created on 2015 May 2nd at 04:10:01 PM.
 * 
 * @author NobleUplift
 */
public final class CurrenciesCommand {
	public static final String CURRENCIES_CREATE = "${currencies.create}";
	public static final String CURRENCIES_DELETE = "${currencies.delete}";
	public static final String CURRENCIES_ADDPRIME = "${currencies.addprime}";
	public static final String CURRENCIES_ADDPARENT = "${currencies.addparent}";
	public static final String CURRENCIES_ADDCHILD = "${currencies.addchild}";
	public static final String CURRENCIES_BALANCE = "${currencies.balance}";
	public static final String CURRENCIES_PAY = "${currencies.pay}";
	public static final String CURRENCIES_BILL = "${currencies.bill}";
	public static final String CURRENCIES_PAYBILL = "${currencies.paybill}";
	public static final String CURRENCIES_CREDIT= "${currencies.credit}";
	public static final String CURRENCIES_DEBIT = "${currencies.debit}";
	public static final String CURRENCIES_BANKRUPT = "${currencies.bankrupt}";
	
	protected static void help(CommandSender player) {
		player.sendMessage(CURRENCIES_CREATE);
		player.sendMessage(CURRENCIES_DELETE);
		player.sendMessage(CURRENCIES_ADDPRIME);
		player.sendMessage(CURRENCIES_ADDPARENT);
		player.sendMessage(CURRENCIES_ADDCHILD);
		player.sendMessage(CURRENCIES_BALANCE);
		player.sendMessage(CURRENCIES_PAY);
		player.sendMessage(CURRENCIES_BILL);
		player.sendMessage(CURRENCIES_PAYBILL);
		player.sendMessage(CURRENCIES_CREDIT);
		player.sendMessage(CURRENCIES_DEBIT);
		player.sendMessage(CURRENCIES_BANKRUPT);
	}
	
	protected static void subcommands(CommandSender sender, String[] args) {
		switch (args[0].toLowerCase()) {
			case "create":
				if (args.length == 3) {
					try {
						CurrenciesCore.createCurrency(args[1], args[2]);
					} catch (CurrenciesException e) {
						sender.sendMessage(e.getMessage());
					}
				} else if (args.length == 4) {
					boolean prefix = ("false".equals(args[3]) ? false : true);
					try {
						CurrenciesCore.createCurrency(args[1], args[2], prefix);
					} catch (CurrenciesException e) {
						sender.sendMessage(e.getMessage());
					}
				} else {
					sender.sendMessage(CURRENCIES_CREATE);
				}
				break;
			
			case "delete":
				if (args.length == 2) {
					try {
						CurrenciesCore.deleteCurrency(args[1]);
					} catch (CurrenciesException e) {
						sender.sendMessage(e.getMessage());
					}
				} else {
					sender.sendMessage(CURRENCIES_DELETE);
				}
				break;
			
			case "addprime":
				if (args.length == 4) {
					
				} else if (args.length == 5) {
					
				} else {
					sender.sendMessage(CURRENCIES_ADDPRIME);
				}
				break;
			
			case "addparent":
				if (args.length == 6) {
					
				} else if (args.length == 7) {
					
				} else {
					sender.sendMessage(CURRENCIES_ADDPARENT);
				}
				break;
			
			case "addchild":
				if (args.length == 6) {
					
				} else if (args.length == 7) {
					
				} else {
					sender.sendMessage(CURRENCIES_ADDCHILD);
				}
				break;
			
			case "balance":
				if (args.length == 1) {
					
				} else if (args.length == 2) {
					
				} else if (args.length == 3) {
					
				} else {
					sender.sendMessage(CURRENCIES_BALANCE);
				}
				break;
			
			case "pay":
				if (args.length == 4) {
					try {
						CurrenciesCore.pay(args[1], args[2], args[3]);
					} catch (CurrenciesException e) {
						sender.sendMessage(e.getMessage());
					}
				} else {
					sender.sendMessage(CURRENCIES_PAY);
				}
				break;
			
			case "bill":
				if (args.length == 4) {
					
				} else {
					sender.sendMessage(CURRENCIES_BILL);
				}
				break;
			
			case "paybill":
				if (args.length == 2) {
					
				} else {
					sender.sendMessage(CURRENCIES_PAYBILL);
				}
				break;
			
			case "credit":
				if (args.length == 4) {
					
				} else {
					sender.sendMessage(CURRENCIES_CREDIT);
				}
				break;
			
			case "debit":
				if (args.length == 4) {
					
				} else {
					sender.sendMessage(CURRENCIES_DEBIT);
				}
				break;
			
			case "bankrupt":
				if (args.length > 1 && args.length < 5) {
					
				} else {
					sender.sendMessage(CURRENCIES_BANKRUPT);
				}
				break;
			
			default:
				help(sender);
		}
	}
}
