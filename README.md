## Multiple currencies, complex subdivisions, exact integers 

**Currencies** allows a server to have not only multiple currencies, but to subdivide those currencies into multiple units, all with an integer base opposed to a floating-point base. This means that the money for all of your players is always stored as integers, not a float or a double, ![giving the money on your server complete accuracy](http://effbot.org/pyfaq/why-are-floating-point-calculations-so-inaccurate.htm).

## Commands
Currencies currently has 17 commands.

#### /currencies create <acronym> <name> [prefix] - Creates a currency
* acronym - An acronym is a three-letter unique identifier for every currency created by Currencies. This is true for every occurrence of <acronym>
* name - You can also use quotes to specify the name of the currency.
* prefix (default: true) - Prefix controls whether the symbols of the currency will be before their amounts or after. For example, with prefix: $10.50, without prefix: 10$50.

####  /currencies delete <acronym> - Deletes a currency (only partially implemented as of now)

####  /currencies addprime <acronym> <name> <plural> <symbol> - Creates the central unit of the currency. It should be the first unit that comes to mind when you think of this currency.
* name - the singular name of the unit of currency, i.e. dollar. Same in the next two commands.
* plural - the plural name of the unit of currency, i.e. dollars. Same in the next two commands.
* symbol - the symbol of the unit, i.e. $. Same in the next two commands.

#### /currencies addparent <acronym> <name> <plural> <symbol> <multiplier> <child> - Add a parent unit to a child unit
* multiplier - how many multiples of the child equal the parent
* child - the symbol of the child unit

#### /currencies addchild <acronym> <name> <plural> <symbol> <divisor> <parent> - Add a child unit to a parent unit. Each unit can only have one child.
* divisor - how many divisions of the parent equal the child
* parent - the symbol of the parent unit

#### /currencies list [page] - List currencies.
* page (default: 1) - optional parameter to specify the next page of currencies, if you have more than 10.

#### /openaccount | /currencies openaccount <name> <owner> - Open a non-player account 
* name - Name of the account. Must be greater than 16 characters. Can be defined with quotes, i.e. "Noble Coding Inc.".
* parent - Owner of the account.

#### /setdefault | /currencies setdefault <acronym> - Sets a player's default currency. Required when a server has multiple currencies with the same prime symbol, the United States Dollar ($) and the Canadian Dollar ($).

#### `/balance | /currencies balance [player] [acronym]` - Shows a player's balance.
* player (default: you) - When specified, you can see another player's balance.
* acronym (default: all) - When you only want to see one currency.

#### /pay | /currencies pay <player> <amount> - Pay a player.

#### /bill | /currencies bill <player> <amount> - Bill a player.

#### /paybill | /currencies paybill [transaction] - Pay a bill.

#### /rejectbill | /currencies rejectbill [transaction] - Reject a bill.

#### `/transactions | /currencies transactions [page|player] [page]` - View your transactions.

#### /credit | /currencies credit <player> <amount>` - Give a player money (put money into circulation).

#### /debit | /currencies debit <player> <amount>` - Take away money from a player (takes money out of circulation).

#### `/bankrupt | /currencies bankrupt <player> [acronym] [amount]` - Bankrupt a player. Does not take money out of circulation
* player - Without any other parameters, will bankrupt a player on all currencies.
* acronym - the single currency to bankrupt a player on
* amount - the amount to credit back to the user after bankruptcy (creates money).

## Permissions
Permissions for the most part match the command names with a prefix:

* currencies.create
* currencies.delete
* currencies.add (not yet implemented)
* currencies.addprime
* currencies.addparent
* currencies.addchild
* currencies.list
* currencies.openaccount
* currencies.set (not yet implemented)
* currencies.setdefault
* currencies.balance
* currencies.balance.others (not yet implemented)
* currencies.pay
* currencies.bill
* currencies.paybill
* currencies.rejectbill
* currencies.transactions
* currencies.transactions.others (not yet implemented)
* currencies.credit
* currencies.debit
* currencies.bankrupt
* currencies.baknrupt.all (required to run /bankrupt <player> with no other parameters)

## Creating Currencies
_For a complete list of non-decimal currencies, ![please see the page on the wiki](http://dev.bukkit.org/bukkit-plugins/currencies/pages/list-of-non-decimal-currencies/)._

Want to create a simple decimalized currency? USA! USA!

```
/currencies create USD 'United States Dollar'
/currencies addprime USD dollar dollars $
/currencies addchild USD cent cents . 100 $
```

So, if you wanted to create the old Great British Pound before decimalization (yeah, American spelling!), you would do the following (during development I actually was using £, but I can't type it on my keyboard in Minecraft, even with ALT codes, so I started using L. Don't hate):

```
/currencies create GBP 'Great British Pound' false
/currencies addprime GBP pound pounds L
/currencies addchild GBP shilling shillings s 20 L
/currencies addchild GBP penny pence d 12 s
/currencies addchild GBP farthing farthings f 4 d

// Swap last two parameters
/currencies addparent GBP 'guinea' 'guinea' gu 21 s
/currencies addparent GBP crown crowns c 5 s
/currencies addparent GBP 'double florin' 'double florin' df 4 s
/currencies addparent GBP florin florins fl 2 s

/currencies addparent GBP 'half guinea' 'half guinea' gh 126 d
/currencies addparent GBP halfcrown halfcrowns hc 30 d
/currencies addparent GBP sixpence sixpence sp 6 d
/currencies addparent GBP threepence threepence tp 3 d
/currencies addparent GBP twopence twopence wp 3 d
/currencies addparent GBP groat groats g 4 d
/currencies addparent GBP halfgroat halfgroats hg 2 d

/currencies addparent GBP 'three halfpence' 'three halfpence' th 6 f
/currencies addparent GBP halfpenny halfpence hp 2 f
```

![Great British Pound](http://i.imgur.com/7128fra.png)

## Using Currencies
To start using a currency, you must first put it into circulation. You can do this manually or with a plugin that implements Currencies.  To do this manually, you use the credit command:

```
/credit NobleUplift 200L20hc17g
```

This will give me 200 pounds, 20 halfcrowns, and 17 groats.

If I decided that was too much to give myself, I can always take the money out of circulation with the debit command:

```
/debit NobleUplift 0L20hc17g
```

Note how I provided 0L in the currency amount. This is a requirement if you are only crediting/debiting minor units of a currency, in order to identify it.

You can seamlessly go from one currency to another, so long as it does not share a symbol with another currency:

```
/credit NobleUplift 100L
/credit NobleUplift $29.99
```

The commands pay and bill work the exact same way as credit and debit, except you are giving someone else money or requesting it for yourself:

```
/pay Shopkeeper 10L
/bill Customer $20
```

If you bill a user, however, that user must either pay or reject the bill:

```
/paybill
```

But if you have multiple bills pending, you must get the transaction number of the bill, and then process it:

```
/transactions 2
/rejectbill 9
```

The final command for managing currencies is bankrupt:

```
/bankrupt NobleUplift
```

This will remove **all** of my currencies, and is incredibly dangerous. That's why I require a special permission to run this.

If I only wanted to bankrupt myself in England, probably to avoid taxes, I would run the following:

```
/bankrupt NobleUplift GBP
```

But what's the point in avoiding taxes if I don't get anything for it? This sets my currency to an exact value after bankrupting, so it also requires the credit permission:

```
/credit NobleUplift GBP 100L
```

And that is very simply the usage of Currencies!

## API
The API of Currencies is CurrenciesCore, located here:

![https://github.com/NobleUplift/Currencies/blob/master/src/main/java/com/nobleuplift/currencies/CurrenciesCore.java](https://github.com/NobleUplift/Currencies/blob/master/src/main/java/com/nobleuplift/currencies/CurrenciesCore.java)

If my "All Rights Reserved" license is an issue, I am planning on picking a license for Currencies, but I haven't decided on one. There are so many!

## Reserved Accounts
There are four reserved accounts in Currencies that are used for the purposes of tracking Currencies in circulation and for future, yet to be implemented, functionality:

1. Minecraft Central Bank - The Bank receives all currencies that a player bankrupts on, so as not to take the money out of circulation.
2. Minecraft Central Banker - For each currency, the total amount of each currency is recorded as the Banker's holdings. At any time the Banker's holdings can be viewed to see the total amount of currency in circulation on your server!
3. The Enderman Marker - Counterpart to the Minecraft Central Bank. Will be implemented at a later date.
4. The Enderman Marketeer - Counterpart to the Minecraft Central Banker. Will be implemented at a later date.

**For plugin developers**: If you create, for instance, a shop plugin, and you want that shop plugin to pay and receive infinite amounts of money, use the credit/debit methods. If you only want to buy/sell items at an equal ratio (i.e. players can only sell items if an equal number of players are buying items, taking into account the price), then use the pay method with the Minecraft Central Bank, swapping the parameters depending on the direction of payment.