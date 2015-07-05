package com.nobleuplift.currencies;

import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;

import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Transaction;
import com.nobleuplift.currencies.entities.Unit;

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
	public static final String CURRENCIES_REJECTBILL = "${currencies.rejectbill}";
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
		Currencies.tell(sender, CURRENCIES_LIST);
		Currencies.tell(sender, CURRENCIES_OPENACCOUNT);
		Currencies.tell(sender, CURRENCIES_SETDEFAULT);
		Currencies.tell(sender, CURRENCIES_BALANCE);
		Currencies.tell(sender, CURRENCIES_PAY);
		Currencies.tell(sender, CURRENCIES_BILL);
		Currencies.tell(sender, CURRENCIES_PAYBILL);
		Currencies.tell(sender, CURRENCIES_REJECTBILL);
		Currencies.tell(sender, CURRENCIES_TRANSACTIONS);
		Currencies.tell(sender, CURRENCIES_CREDIT);
		Currencies.tell(sender, CURRENCIES_DEBIT);
		Currencies.tell(sender, CURRENCIES_BANKRUPT);
	}
	
	protected static void subcommands(CommandSender sender, String[] args) {
		if (!sender.hasPermission("currencies." + args[0].toLowerCase())) {
			Currencies.tell(sender, "You require the permission currencies." + args[0].toLowerCase() + " to run this command.");
			return;
		}
		
		switch (args[0].toLowerCase()) {
			case "create":
				if (args.length == 3) {
					try {
						CurrenciesCore.createCurrency(args[1], args[2]);
						Currencies.tell(sender, "Currency " + args[2] + " (" + args[1] + ") created.");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else if (args.length == 4) {
					boolean prefix = ("false".equals(args[3]) ? false : true);
					try {
						CurrenciesCore.createCurrency(args[1], args[2], prefix);
						Currencies.tell(sender, "Currency " + args[2] + " (" + args[1] + ") created " + (prefix ? "with prefix" : "without prefix") + ".");
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
						Currencies.tell(sender, "Unit " + args[2] + "/" + args[3] + " (" + args[4] + ") in " + args[1] + " created.");
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
						CurrenciesCore.addParent(args[1], args[2], args[3], args[4], Integer.parseInt(args[5]), args[6]);
						Currencies.tell(sender, "Parent unit " + args[2] + "/" + args[3] + " (" + args[4] + ") in " + args[1] + " created. " + 
							"1 " + args[4] + " equals " + Integer.parseInt(args[5]) + " " + args[6] + ".");
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
						CurrenciesCore.addChild(args[1], args[2], args[3], args[4], Integer.parseInt(args[5]), args[6]);
						Currencies.tell(sender, "Child unit " + args[2] + "/" + args[3] + " (" + args[4] + ") in " + args[1] + " created. " + 
							Integer.parseInt(args[5]) + " " + args[4] + " equals 1 " + args[6] + ".");
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
						int page = 1;
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
						Currencies.tell(sender, "Currencies " + (((page - 1) * 10) + 1) + " through " + (((page - 1) * 10) + 10) + ":");
						for (Currency currency : currencies) {
							Currencies.tell(sender, "(" + currency.getAcronym() + ") " + /*currency.getId() + ". " + */currency.getName());
							Map<Short, Unit> units = CurrenciesCore.getUnits(currency);
							for (Map.Entry<Short, Unit> entry : units.entrySet()) {
								Unit u = entry.getValue();
								Currencies.tell(sender, "  " + "(" + u.getSymbol() + ") " + u.getName() + "/" + u.getAlternate() + 
									(u.getChildUnit() != null ? " - Equal to " + u.getChildMultiples() + " " + u.getChildUnit().getAlternate() : ""));
							}
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
				if (args.length == 3) {
					try {
						CurrenciesCore.openAccount(args[1], args[2]);
						Currencies.tell(sender, "Created new account " + args[1] + " owned by " + args[2] + ".");
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
				if (args.length == 3) {
					try {
						Account account = CurrenciesCore.getAccountFromPlayer(sender.getName(), false);
						Currency currency = CurrenciesCore.getCurrencyFromAmount(account, args[2]);
						CurrenciesCore.pay(sender.getName(), args[1], currency.getAcronym(), args[2]);
						Currencies.tell(sender, "Paid " + args[1] + " " + args[2] + ".");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_PAY);
				}
				break;
			
			case "bill":
				if (args.length == 3) {
					try {
						Account account = CurrenciesCore.getAccountFromPlayer(sender.getName(), false);
						Currency currency = CurrenciesCore.getCurrencyFromAmount(account, args[2]);
						CurrenciesCore.bill(sender.getName(), args[1], currency.getAcronym(), args[2]);
						Currencies.tell(sender, "Sent " + args[1] + " a bill for " + args[2] + ".");
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
						Transaction t = CurrenciesCore.processBill(sender.getName(), true);
						Currencies.tell(sender, "Paid transaction " + t.getId() + ".");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else if (args.length == 2) {
					try {
						Transaction t = CurrenciesCore.processBill(sender.getName(), true, args[1]);
						Currencies.tell(sender, "Paid transaction " + t.getId() + ".");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_PAYBILL);
				}
				break;
				
			case "rejectbill":
				if (args.length == 1) {
					try {
						Transaction t = CurrenciesCore.processBill(sender.getName(), false);
						Currencies.tell(sender, "Rejected transaction " + t.getId() + ".");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else if (args.length == 2) {
					try {
						Transaction t = CurrenciesCore.processBill(sender.getName(), false, args[1]);
						Currencies.tell(sender, "Rejected transaction " + t.getId() + ".");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_REJECTBILL);
				}
				break;
			
			case "transactions":
				if (args.length > 0) {
					List<Transaction> transactions = null;
					try {
						int page = 1;
						if (args.length == 3) {
							try {
								page = Integer.parseInt(args[2]);
							} catch (NumberFormatException e) {
								throw new CurrenciesException(args[2] + " is not a valid integer.");
							}
							
							transactions = CurrenciesCore.transactions(args[1], page);
						} else if (args.length == 2) {
							try {
								page = Integer.parseInt(args[1]);
							} catch (NumberFormatException e) {
								throw new CurrenciesException(args[1] + " is not a valid integer.");
							}
							
							transactions = CurrenciesCore.transactions(sender.getName(), page);
						} else {
							transactions = CurrenciesCore.transactions(sender.getName());
						}
						
						Currencies.tell(sender, "--------------------");
						Currencies.tell(sender, "Transactions " + (((page - 1) * 10) + 1) + " through " + (((page - 1) * 10) + 10) + ":");
						for (Transaction t : transactions) {
							if (t.getTypeId() == CurrenciesCore.TRANSACTION_TYPE_PAY_ID) {
								sender.sendMessage(t.getId() + ". " + t.getSender().getName() + " paid " +
									t.getRecipient().getName() + " " + 
									CurrenciesCore.formatCurrency(t.getUnit().getCurrency(), t.getTransactionAmount())
								);
							} else if (t.getTypeId() == CurrenciesCore.TRANSACTION_TYPE_BILL_ID) {
								sender.sendMessage(t.getId() + ". " + t.getRecipient().getName() + " billed " + t.getSender().getName() + " for " +
									CurrenciesCore.formatCurrency(t.getUnit().getCurrency(), t.getTransactionAmount()) + " and s/he " 
									 + (t.getPaid() == null ? " has not paid." : (t.getPaid() ? " paid." : " did not pay."))
								);
							} else if (t.getTypeId() == CurrenciesCore.TRANSACTION_TYPE_CREDIT_ID) {
								sender.sendMessage(t.getId() + ". Credited " + CurrenciesCore.formatCurrency(t.getUnit().getCurrency(), t.getTransactionAmount()) + " to " + t.getRecipient().getName());
							} else if (t.getTypeId() == CurrenciesCore.TRANSACTION_TYPE_DEBIT_ID) {
								sender.sendMessage(t.getId() + ". Debited " + CurrenciesCore.formatCurrency(t.getUnit().getCurrency(), t.getTransactionAmount()) + " from " + t.getRecipient().getName());
							} else if (t.getTypeId() == CurrenciesCore.TRANSACTION_TYPE_BANKRUPT_ID) {
								sender.sendMessage(t.getId() + ". Bankrupted " + t.getSender().getName() + " on " + CurrenciesCore.formatCurrency(t.getUnit().getCurrency(), t.getTransactionAmount()));
							} else {
								sender.sendMessage(t.getId() + ". " + t.getSender().getName() + (t.getPaid() == null ? " has not paid " : (t.getPaid() ? " paid " : " did not pay ")) +
									t.getRecipient().getName() + " " + 
									CurrenciesCore.formatCurrency(t.getUnit().getCurrency(), t.getTransactionAmount())
								);
							}
						}
						Currencies.tell(sender, "--------------------");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_TRANSACTIONS);
				}
				break;
				
			case "credit":
				if (args.length == 3) {
					try {
						Account account = CurrenciesCore.getAccountFromPlayer(sender.getName(), false);
						Currency currency = CurrenciesCore.getCurrencyFromAmount(account, args[2]);
						CurrenciesCore.credit(args[1], currency.getAcronym(), args[2]);
						Currencies.tell(sender, "You have credited " + args[2] + " to " + args[1] + ".");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_CREDIT);
				}
				break;
			
			case "debit":
				if (args.length == 3) {
					try {
						Account account = CurrenciesCore.getAccountFromPlayer(sender.getName(), false);
						Currency currency = CurrenciesCore.getCurrencyFromAmount(account, args[2]);
						CurrenciesCore.debit(args[1], currency.getAcronym(), args[2]);
						Currencies.tell(sender, "You have debited " + args[2] + " from " + args[1] + ".");
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
						if (!sender.hasPermission("currencies.bankrupt.all")) {
							throw new CurrenciesException("You must have the permission currencies.bankrupt.all to bankrupt an account on all currencies.");
						}
						
						CurrenciesCore.bankrupt(args[1]);
						Currencies.tell(sender, "Account " + args[1] + " has bankrupted on all currencies.");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else if (args.length == 3) {
					try {
						CurrenciesCore.bankrupt(args[1], args[2]);
						Currencies.tell(sender, "Account " + args[1] + " has bankrupted on currency " + args[2] + ".");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else if (args.length == 4) {
					try {
						if (!sender.hasPermission("currencies.credit")) {
							throw new CurrenciesException("You must have the permission currencies.credit to give a bankrupted account a starting balance.");
						}
						
						CurrenciesCore.bankrupt(args[1], args[2], args[3]);
						Currencies.tell(sender, "Account " + args[1] + " has bankrupted on currency " + args[2] + " but has been given a starting balance of " + args[3] + ".");
					} catch (CurrenciesException e) {
						Currencies.tell(sender, e.getMessage());
					}
				} else {
					Currencies.tell(sender, CURRENCIES_BANKRUPT);
				}
				break;
			
			default:
				Currencies.tell(sender, "Invalid subcommand: " + args[0]);
		}
	}
}
