package com.nobleuplift.currencies;

import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;

import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Transaction;

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
	public static final String CURRENCIES_LIST = "${currencies.list}";
	public static final String CURRENCIES_OPENACCOUNT = "${currencies.openaccount}";
	public static final String CURRENCIES_SETDEFAULT = "${currencies.setdefault}";
	public static final String CURRENCIES_BALANCE = "${currencies.balance}";
	public static final String CURRENCIES_PAY = "${currencies.pay}";
	public static final String CURRENCIES_BILL = "${currencies.bill}";
	public static final String CURRENCIES_PAYBILL = "${currencies.paybill}";
	public static final String CURRENCIES_TRANSACTIONS = "${currencies.transactions}";
	public static final String CURRENCIES_CREDIT= "${currencies.credit}";
	public static final String CURRENCIES_DEBIT = "${currencies.debit}";
	public static final String CURRENCIES_BANKRUPT = "${currencies.bankrupt}";
	
	protected static void help(CommandSender sender) {
		Currencies.tell(sender, CURRENCIES_CREATE);
		Currencies.tell(sender, CURRENCIES_DELETE);
		Currencies.tell(sender, CURRENCIES_ADDPRIME);
		Currencies.tell(sender, CURRENCIES_ADDPARENT);
		Currencies.tell(sender, CURRENCIES_ADDCHILD);
		Currencies.tell(sender, CURRENCIES_BALANCE);
		Currencies.tell(sender, CURRENCIES_PAY);
		Currencies.tell(sender, CURRENCIES_BILL);
		Currencies.tell(sender, CURRENCIES_PAYBILL);
		Currencies.tell(sender, CURRENCIES_CREDIT);
		Currencies.tell(sender, CURRENCIES_DEBIT);
		Currencies.tell(sender, CURRENCIES_BANKRUPT);
	}
	
	protected static void subcommands(CommandSender sender, String[] args) {
		switch (args[0].toLowerCase()) {
			case "create":
				if (args.length == 3) {
					try {
						CurrenciesCore.createCurrency(args[1], args[2]);
						Currencies.tell(sender, "Currency " + args[2] + " created.");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else if (args.length == 4) {
					boolean prefix = ("false".equals(args[3]) ? false : true);
					try {
						CurrenciesCore.createCurrency(args[1], args[2], prefix);
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_CREATE);
				}
				break;
			
			case "delete":
				if (args.length == 2) {
					try {
						CurrenciesCore.deleteCurrency(args[1]);
						Currencies.tell(sender, "Currency " + args[1] + " deleted.");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_DELETE);
				}
				break;
			
			case "addprime":
				if (args.length == 5) {
					try {
						CurrenciesCore.addPrime(args[1], args[2], args[3], args[4]);
						Currencies.tell(sender, "Unit " + args[2] + " created.");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_ADDPRIME);
				}
				break;
			
			case "addparent":
				if (args.length == 7) {
					try {
						CurrenciesCore.addParent(args[1], args[2], args[3], args[4], args[5], Integer.parseInt(args[6]));
						Currencies.tell(sender, "Unit " + args[2] + " created.");
					} catch (NumberFormatException e) {
						Currencies.tell(sender, "Multiplier must be an integer.");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_ADDPARENT);
				}
				break;
			
			case "addchild":
				if (args.length == 7) {
					try {
						CurrenciesCore.addChild(args[1], args[2], args[3], args[4], args[5], Integer.parseInt(args[6]));
						Currencies.tell(sender, "Unit " + args[2] + " created.");
					} catch (NumberFormatException e) {
						Currencies.tell(sender, "Divisor must be an integer.");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_ADDCHILD);
				}
				break;
			
			case "list":
				if (args.length > 0) {
					List<Currency> currencies = null;
					try {
						int page = 0;
						if (args.length == 2) {
							try {
								page = Integer.parseInt(args[1]);
							} catch (NumberFormatException e) {
								throw new CurrenciesException(args[1] + " is not a valid integer.");
							}
							
							currencies = CurrenciesCore.list(page);
						} else {
							currencies = CurrenciesCore.list();
						}
						
						Currencies.tell(sender, "--------------------");
						Currencies.tell(sender, "Currencies " + ((page * 10) + 1) + " through " + ((page * 10) + 10) + ":");
						for (Currency currency : currencies) {
							sender.sendMessage(currency.getId() + ". " + currency.getName() + " (" + currency.getAcronym() + ")");
						}
						Currencies.tell(sender, "--------------------");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_LIST);
				}
				break;
				
			case "openaccount":
				if (args.length == 2) {
					try {
						CurrenciesCore.openAccount(args[1]);
						Currencies.tell(sender, "Created new account " + args[1] + ".");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_OPENACCOUNT);
				}
				break;
				
			case "setdefault":
				if (args.length == 2) {
					try {
						CurrenciesCore.setDefault(sender.getName(), args[1]);
						Currencies.tell(sender, "Your default currency is now " + args[1] + ".");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_SETDEFAULT);
				}
				break;
			
			case "balance":
				if (args.length == 1) {
					try {
						Map<Currency, Long> currencies = CurrenciesCore.balance(sender.getName());
						Currencies.tell(sender, "--------------------");
						Currencies.tell(sender, "Your balance: ");
						for (Map.Entry<Currency, Long> entry : currencies.entrySet()) {
							Currencies.tell(sender, entry.getKey().getName() + ": " + CurrenciesCore.formatCurrency(entry.getKey(), entry.getValue()));
						}
						Currencies.tell(sender, "--------------------");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else if (args.length == 2) {
					try {
						Map<Currency, Long> currencies = CurrenciesCore.balance(args[1]);
						Currencies.tell(sender, "--------------------");
						Currencies.tell(sender, args[1] + "'s balance: ");
						for (Map.Entry<Currency, Long> entry : currencies.entrySet()) {
							Currencies.tell(sender, entry.getKey().getName() + ": " + CurrenciesCore.formatCurrency(entry.getKey(), entry.getValue()));
						}
						Currencies.tell(sender, "--------------------");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else if (args.length == 3) {
					try {
						Map<Currency, Long> currencies = CurrenciesCore.balance(args[1], args[2]);
						Currencies.tell(sender, "--------------------");
						Currencies.tell(sender, args[1] + "'s balance: ");
						for (Map.Entry<Currency, Long> entry : currencies.entrySet()) {
							Currencies.tell(sender, entry.getKey().getName() + ": " + CurrenciesCore.formatCurrency(entry.getKey(), entry.getValue()));
						}
						Currencies.tell(sender, "--------------------");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_BALANCE);
				}
				break;
			
			case "pay":
				if (args.length == 4) {
					try {
						CurrenciesCore.pay(sender.getName(), args[1], args[2], args[3]);
						Currencies.tell(sender, "Paid " + args[1] + " " + args[3] + " " + args[2] + ".");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_PAY);
				}
				break;
			
			case "bill":
				if (args.length == 4) {
					try {
						CurrenciesCore.bill(sender.getName(), args[1], args[2], args[3]);
						Currencies.tell(sender, "Sent " + args[1] + "a bill for " + args[3] + " " + args[2] + ".");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_BILL);
				}
				break;
			
			case "paybill":
				if (args.length == 1) {
					try {
						Transaction t = CurrenciesCore.paybill(sender.getName());
						
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else if (args.length == 2) {
					try {
						CurrenciesCore.paybill(sender.getName(), args[1]);
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_PAYBILL);
				}
				break;
			
			case "credit":
				if (args.length == 4) {
					try {
						CurrenciesCore.credit(args[1], args[2], args[3]);
						Currencies.tell(sender, "You have credited " + args[3] + " " + args[2] + " to " + args[1] + ".");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_CREDIT);
				}
				break;
			
			case "debit":
				if (args.length == 4) {
					try {
						CurrenciesCore.debit(args[1], args[2], args[3]);
						Currencies.tell(sender, "You have debited " + args[3] + " " + args[2] + " from " + args[1] + ".");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_DEBIT);
				}
				break;
			
			case "bankrupt":
				if (args.length == 2) {
					try {
						CurrenciesCore.bankrupt(args[1]);
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else if (args.length == 3) {
					try {
						CurrenciesCore.bankrupt(args[1], args[2]);
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else if (args.length == 4) {
					try {
						CurrenciesCore.bankrupt(args[1], args[2], args[3]);
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_BANKRUPT);
				}
				break;
			
			default:
				Currencies.tell(sender, "Invalid subcommand: " + args[0]);
				//help(sender);
		}
	}
}
