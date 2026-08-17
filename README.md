## Multiple currencies, complex subdivisions, exact integers 

**Currencies** allows a server to have not only multiple currencies, but to subdivide those currencies into multiple units, all with an integer base opposed to a floating-point base. This means that the money for all of your players is always stored as integers, not a float or a double, ![giving the money on your server complete accuracy](http://effbot.org/pyfaq/why-are-floating-point-calculations-so-inaccurate.htm).

## Commands
Currencies currently has 17 commands.

##### `/currencies create <acronym> <name> [prefix]`
Creates a currency.

* acronym - An acronym is a three-letter unique identifier for every currency created by Currencies. This is true for every occurrence of <acronym>
* name - You can also use quotes to specify the name of the currency.
* prefix (default: true) - Prefix controls whether the symbols of the currency will be before their amounts or after. For example, with prefix: $10.50, without prefix: 10$50.

#####  `/currencies delete <acronym>`
Deletes a currency (only partially implemented as of now).

#####  `/currencies addprime <acronym> <name> <plural> <symbol>`
Creates the central unit of the currency. It should be the first unit that comes to mind when you think of this currency.

* name - the singular name of the unit of currency, i.e. dollar. Same in the next two commands.
* plural - the plural name of the unit of currency, i.e. dollars. Same in the next two commands.
* symbol - the symbol of the unit, i.e. $. Same in the next two commands.

##### `/currencies addparent <acronym> <name> <plural> <symbol> <multiplier> <child>`
Add a parent unit to a child unit.

* multiplier - how many multiples of the child equal the parent
* child - the symbol of the child unit

##### `/currencies addchild <acronym> <name> <plural> <symbol> <divisor> <parent>`
Add a child unit to a parent unit. Each unit can only have one child.

* divisor - how many divisions of the parent equal the child
* parent - the symbol of the parent unit

##### `/currencies addalias <acronym> <name> <plural> <symbol> <existing-unit-symbol>` (not yet implemented)
Add an alternate name and symbol that resolve to an already-registered unit, instead of creating a new unit at a new value. Useful for letting players type either historical name for the same coin (e.g. "sovereign" for an existing "pound" unit) without tripping the addparent/addchild duplicate-value guard, since no new unit or value is actually created.

* symbol - the alias's own symbol; must not collide with any existing unit's symbol
* existing-unit-symbol - the symbol of the already-registered unit this alias should resolve to

##### `/currencies list [page]`
List currencies.

* page (default: 1) - optional parameter to specify the next page of currencies, if you have more than 10.

##### `/openaccount | /currencies openaccount <name> <owner>`
Open a non-player account.

* name - Name of the account. Must be greater than 16 characters. Can be defined with quotes, i.e. "Noble Coding Inc.".
* parent - Owner of the account.

##### `/setdefault | /currencies setdefault <acronym>`
Sets a player's default currency. Required when a server has multiple currencies with the same prime symbol, the United States Dollar ($) and the Canadian Dollar ($).

##### `/balance | /currencies balance [player] [acronym]`
Shows a player's balance.

* player (default: you) - When specified, you can see another player's balance.
* acronym (default: all) - When you only want to see one currency.

##### `/pay | /currencies pay <player> <amount>`
Pay a player.

##### `/bill | /currencies bill <player> <amount>`
Bill a player.

##### `/paybill | /currencies paybill [transaction]`
Pay a bill.

##### `/rejectbill | /currencies rejectbill [transaction]`
Reject a bill.

##### `/transactions | /currencies transactions [page|player] [page]`
View your transactions.

##### `/credit | /currencies credit <player> <amount>`
Give a player money (put money into circulation).

##### `/debit | /currencies debit <player> <amount>`
Take away money from a player (takes money out of circulation).

##### `/bankrupt | /currencies bankrupt <player> [acronym] [amount]`
Bankrupt a player. Does not take money out of circulation.

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
* currencies.addalias (not yet implemented)
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
_For a complete list of non-decimal currencies, ![please see the page on the wiki](http://dev.bukkit.org/bukkit-plugins/currencies/pages/list-of-non-decimal-currencies/), or the much more thorough ![Wikipedia article on non-decimal currency](https://en.wikipedia.org/wiki/Non-decimal_currency), which is the source for every historical currency below._

Every example below uses real, historically-documented denomination ratios (verified against multiple sources, not just the Wikipedia summary table). Where a currency had a long or regionally-varying history, one representative, internally-consistent period is picked and named accordingly.

### United States Dollar (USD)

Want to create a simple decimal currency?

```
/currencies create USD 'United States Dollar'
/currencies addprime USD dollar dollars $
/currencies addchild USD cent cents . 100 $
```

### Ancient Greek Drachma (DRC)

The Attic system: a drachma of 6 obols, an obol of 8 chalkoi, and two well-known larger denominations above the drachma -- the mina and the talent -- added with `addparent`. See ![Wikipedia: Drachma](https://en.wikipedia.org/wiki/Drachma).

```
/currencies create DRC 'Ancient Greek Drachma'
/currencies addprime DRC drachma drachmae dr
/currencies addchild DRC obol obols ob 6 dr
/currencies addchild DRC chalkous chalkoi ch 8 ob

/currencies addparent DRC mina minae mn 100 dr
/currencies addparent DRC talent talents tl 60 mn
```

### French Livre Tournois (LIV)

Pre-revolutionary France used the exact same duodecimal/vigesimal shape as pre-decimal Britain -- 20 sols to the livre, 12 deniers to the sol -- because both descended from the same Carolingian libra/solidus/denarius system. See ![Wikipedia: French livre](https://en.wikipedia.org/wiki/French_livre).

```
/currencies create LIV 'French Livre Tournois'
/currencies addprime LIV livre livres £
/currencies addchild LIV sol sols s 20 £
/currencies addchild LIV denier deniers d 12 s
```

### Spanish Colonial Peso (ESP)

The "piece of eight" -- 1 peso = 8 reales = 272 maravedís (34 maravedís per real, fixed by Spain's 1497 Medina del Campo monetary reform). Deliberately sharing USD's `$` symbol as its prime symbol: the modern dollar sign is itself widely believed to derive from the Spanish peso's mark, so this is a historically fitting example of the `/currencies setdefault` disambiguation the README already describes above. See ![Wikipedia: Spanish dollar](https://en.wikipedia.org/wiki/Spanish_dollar).

```
/currencies create ESP 'Spanish Colonial Peso'
/currencies addprime ESP peso pesos $
/currencies addchild ESP real reales r 8 $
/currencies addchild ESP maravedi maravedis mr 34 r
```

### Ottoman Kuruş (KRS)

Introduced in 1688: 1 kuruş = 40 para = 120 akçe (3 akçe per para). See ![Wikipedia: Kuruş](https://en.wikipedia.org/wiki/Kuru%C5%9F).

```
/currencies create KRS 'Ottoman Kurus'
/currencies addprime KRS kurus kurus ku
/currencies addchild KRS para para pa 40 ku
/currencies addchild KRS akce akce ak 3 pa
```

### Roman Imperial Denarius (DNR)

The early-Imperial (Augustan) standard: 1 aureus = 25 denarii = 100 sestertii = 400 asses = 1600 quadrantes. See ![Wikipedia: Denarius](https://en.wikipedia.org/wiki/Denarius).

```
/currencies create DNR 'Roman Imperial Denarius'
/currencies addprime DNR denarius denarii dn
/currencies addchild DNR sestertius sestertii ss 4 dn
/currencies addchild DNR as asses as 4 ss
/currencies addchild DNR quadrans quadrantes qd 4 as

/currencies addparent DNR aureus aurei au 25 dn
```

### British India Rupee (BIR)

The pre-1957 system: 1 rupee = 16 annas = 64 paisa = 192 pies. See ![Wikipedia: Indian rupee](https://en.wikipedia.org/wiki/Indian_rupee).

```
/currencies create BIR 'British India Rupee'
/currencies addprime BIR rupee rupees Rs
/currencies addchild BIR anna annas an 16 Rs
/currencies addchild BIR paisa paisa pi 4 an
/currencies addchild BIR pie pies pe 3 pi
```

### Edo-Period Japanese Ryō (RYO)

The gold-coinage side of Tokugawa Japan's three parallel currencies (gold, silver by weight, and copper mon) -- the only one of the three with fixed, non-floating internal ratios, which is why it's the one modeled here: 1 ryō = 4 bu = 16 shu. See ![Wikipedia: Ryō](https://en.wikipedia.org/wiki/Ry%C5%8D).

```
/currencies create RYO 'Edo-Period Japanese Ryo'
/currencies addprime RYO ryo ryo ry
/currencies addchild RYO bu bu bu 4 ry
/currencies addchild RYO shu shu sh 4 bu
```

### Dutch Guilder (NLG)

Pre-1817 guilder: 1 gulden = 20 stuivers = 160 duiten = 320 penningen. See ![Wikipedia: Dutch guilder](https://en.wikipedia.org/wiki/Dutch_guilder).

```
/currencies create NLG 'Dutch Guilder'
/currencies addprime NLG gulden guldens g
/currencies addchild NLG stuiver stuivers st 20 g
/currencies addchild NLG duit duiten du 8 st
/currencies addchild NLG penning penningen pn 2 du
```

### Polish Złoty, pre-decimal (PLZ)

The simplest currency here: 1 złoty = 30 groszy, no further subdivision. See ![Wikipedia: Polish złoty](https://en.wikipedia.org/wiki/Polish_z%C5%82oty).

```
/currencies create PLZ 'Polish Zloty (pre-decimal)'
/currencies addprime PLZ zloty zlotys zl
/currencies addchild PLZ grosz groszy gr 30 zl
```

### Siamese Baht / Tical (SIB)

1 baht = 4 salung = 8 fuang = 64 att. See ![Wikipedia: Thai baht](https://en.wikipedia.org/wiki/Thai_baht).

```
/currencies create SIB 'Siamese Baht (Tical)'
/currencies addprime SIB baht baht bt
/currencies addchild SIB salung salung sl 4 bt
/currencies addchild SIB fuang fuang fu 2 sl
/currencies addchild SIB att att at 8 fu
```

### Great British Pound (GBP)

So, if you wanted to create the old Great British Pound before decimalization, you would do the following. See ![Coins of the pound sterling](https://en.wikipedia.org/wiki/Coins_of_the_pound_sterling) for the full history of every denomination below.

Real pre-decimal ledgers prefixed the pound sign but suffixed everything below it -- `£3 12s. 6d.`, never `3£ 12s. 6d.` or `£3 s.12 d.6`. `Currency.prefix` is a single flag applied uniformly to every denomination when formatting a balance (`CurrencyFormatter.formatCurrency`), so it can't reproduce that mixed convention -- there's no per-unit override. `prefix` stays `false` here rather than `true`, because that's correct for the majority of this currency's units (shillings, pence, and every historical coin name below the pound were written number-first, e.g. `12s`, `6d`, `5c` for a crown); the tradeoff is that formatted balances show `3£` instead of `£3` for the pound itself. Multi-denomination command *input* (`200£20hc17g` below) always needs the number before its symbol regardless of this setting -- that's the parser splitting the string into digit/non-digit runs, not a display preference.

```
/currencies create GBP 'Great British Pound' false
/currencies addprime GBP pound pounds £
/currencies addchild GBP shilling shillings s 20 £
/currencies addchild GBP penny pence d 12 s
/currencies addchild GBP farthing farthings f 4 d

// Fictional denomination: no coin was ever struck at 1/12 farthing. It exists only so that the
// half farthing (6), third farthing (4), and quarter farthing (3) -- three real coins that are
// mutually irreducible fractions of the farthing -- can all be represented as integer multiples
// of a single base unit.
/currencies addchild GBP 'twelfth farthing' 'twelfth farthing' t 12 f

// addparent takes <multiplier> <child> -- the mirror image of addchild's <divisor> <parent>
// above: instead of dividing a unit to create a smaller one below it, it multiplies a unit to
// create a larger one above it. Each section below is grouped by which central unit from the
// addchild chain (pound/shilling/penny/farthing/twelfth farthing) the multiplier is counted
// against.

// Multiples of the pound (£)
/currencies addparent GBP 'double sovereign' 'double sovereign' dv 2 £
/currencies addparent GBP 'five pounds' 'five pounds' fp 5 £

// Multiples of the shilling (s)
/currencies addparent GBP 'guinea' 'guinea' gu 21 s
/currencies addparent GBP crown crowns c 5 s
/currencies addparent GBP 'double florin' 'double florin' df 4 s
/currencies addparent GBP florin florins fl 2 s
/currencies addparent GBP 'third guinea' 'third guinea' tg 7 s
/currencies addparent GBP 'half sovereign' 'half sovereign' hv 10 s
/currencies addparent GBP 'two guineas' 'two guineas' wg 42 s
/currencies addparent GBP 'five guineas' 'five guineas' fg 105 s

// Multiples of the penny (d)
/currencies addparent GBP 'half guinea' 'half guinea' gh 126 d
/currencies addparent GBP halfcrown halfcrowns hc 30 d
/currencies addparent GBP sixpence sixpence sp 6 d
/currencies addparent GBP threepence threepence tp 3 d
/currencies addparent GBP twopence twopence wp 3 d
/currencies addparent GBP groat groats g 4 d
/currencies addparent GBP halfgroat halfgroats hg 2 d
/currencies addparent GBP 'fifteen pence' 'fifteen pence' fn 15 d
/currencies addparent GBP 'quarter guinea' 'quarter guinea' qg 63 d

// Multiples of the farthing (f)
/currencies addparent GBP 'three halfpence' 'three halfpence' th 6 f
/currencies addparent GBP halfpenny halfpence hp 2 f

// Multiples of the twelfth farthing (t)
/currencies addparent GBP 'quarter farthing' 'quarter farthing' qf 3 t
/currencies addparent GBP 'half farthing' 'half farthing' hf 6 t
/currencies addparent GBP 'third farthing' 'third farthing' tf 4 t

// Grano was Malta's own pre-existing name for this same value (1/12 penny = 1/3 farthing) --
// the third farthing coin was struck specifically so the grano could keep circulating under
// British coinage. addalias is not yet implemented; this is the intended usage once it is.
/currencies addalias GBP grano grani gn tf
```

![Great British Pound](http://i.imgur.com/7128fra.png)

## Using Currencies
To start using a currency, you must first put it into circulation. You can do this manually or with a plugin that implements Currencies.  To do this manually, you use the credit command:

```
/credit NobleUplift 200£20hc17g
```

This will give me 200 pounds, 20 halfcrowns, and 17 groats.

If I decided that was too much to give myself, I can always take the money out of circulation with the debit command:

```
/debit NobleUplift 0£20hc17g
```

Note how I provided 0£ in the currency amount. This is a requirement if you are only crediting/debiting minor units of a currency, in order to identify it.

You can seamlessly go from one currency to another, so long as it does not share a symbol with another currency:

```
/credit NobleUplift 100£
/credit NobleUplift $29.99
```

The commands pay and bill work the exact same way as credit and debit, except you are giving someone else money or requesting it for yourself:

```
/pay Shopkeeper 10£
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
/credit NobleUplift GBP 100£
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