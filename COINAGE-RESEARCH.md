# Sub-Farthing Coinage Research: Third, Quarter, and Half Farthing

Background research supporting the pre-decimal GBP test fixtures in
`NonDecimalCurrencyScenariosTest` and the denomination audit against
[Wikipedia's British coinage template](https://en.wikipedia.org/wiki/Template:British_coinage).
None of these three coins is representable in the current farthing-based unit
hierarchy without introducing a fictional base unit below the farthing (see
"Why sub-farthing denominations need a fictional base unit" below).

## Third farthing — Malta only, 1827–1913, never UK legal tender

In 1825 Britain made British coinage Malta's monetary standard, but Malta had
its own pre-existing unit, the *grano*, worth exactly 1/12 of a penny. Since a
British penny = 4 farthings, 1/12 penny = 1/3 farthing — so the third-farthing
wasn't an arbitrary fraction, it was struck specifically to let the existing
Maltese grano keep circulating as a British-denominated coin.

## Quarter farthing — Ceylon only, 1839–1853, never UK legal tender

Physically the smallest British copper coin ever minted (13.5mm, 1.2g) —
struck because Ceylon's price levels made even a farthing too coarse a unit
for everyday transactions.

## Half farthing — Ceylon-originated, but also UK legal tender

Unlike the other two, the half farthing was minted for Ceylon (1828–1856,
plus an 1868 bronze proof), but it was *also* declared legal tender in the UK
itself in 1842. So "colonial issue" is only half-true for the half-farthing —
it had genuine mainland circulation too.

All three denominations were demonetised together on 31 December 1869.

## Why sub-farthing denominations need a fictional base unit

These three fractions (½, ⅓, ¼) exist for three unrelated historical reasons
— a legacy Maltese unit, Ceylonese price levels, and general small-change
utility — which is exactly why nothing smaller than lcm(2,3,4)=12 can
represent all three as integers relative to the farthing. A currency modeling
all three coins alongside the standard farthing needs a base unit equal to
1/12 of a farthing, with:

- half farthing = 6 base units
- third farthing = 4 base units
- quarter farthing = 3 base units

This plugin's integer-only arithmetic already supports this without any code
changes: add a base unit tier below the farthing via `addChild`, then
register each coin with `addParent` at the appropriate multiplier (6, 4, and
3 respectively).

## Sources

- [Third farthing](https://en.wikipedia.org/wiki/Third_farthing)
- [Half farthing (British coin)](https://en.wikipedia.org/wiki/Half_farthing_(British_coin))
- [Quarter farthing (British coin)](https://en.wikipedia.org/wiki/Quarter_farthing_(British_coin))
