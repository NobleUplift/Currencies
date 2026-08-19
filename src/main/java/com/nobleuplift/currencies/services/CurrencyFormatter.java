package com.nobleuplift.currencies.services;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nobleuplift.currencies.ConnectionProvider;
import com.nobleuplift.currencies.Currencies;
import com.nobleuplift.currencies.CurrenciesException;
import com.nobleuplift.currencies.CurrenciesRuntimeException;
import com.nobleuplift.currencies.CurrencyDTO;
import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Unit;

/**
 * Currency string parsing/formatting: pure read-only operations over unit
 * lookups, no transaction/write involvement.
 */
public class CurrencyFormatter {

    private final ConnectionProvider connectionProvider;
    private final CurrencyRepository repository;

    public CurrencyFormatter(ConnectionProvider connectionProvider, CurrencyRepository repository) {
        this.connectionProvider = connectionProvider;
        this.repository = repository;
    }

    public Map<Currency, String> formatCurrencies(Map<Currency, Long> currencyAmounts) {
        Map<Currency, String> retval = new HashMap<>();
        for (Map.Entry<Currency, Long> currencyAmount : currencyAmounts.entrySet()) {
            Currency c = currencyAmount.getKey();
            retval.put(c, formatCurrency(c, currencyAmount.getValue()));
        }
        return retval;
    }

    public String formatCurrency(Currency currency, long amount) {
        try (Connection conn = connectionProvider.getConnection()) {
            List<Unit> units = repository.queryMainUnitsForCurrencyDescending(conn, currency);

            String retval = "";
            if (amount < 0) {
                retval += "-";
                amount = Math.abs(amount);
            }
            Unit prime = null;
            long remainder = amount;
            for (Unit u : units) {
                if (u.isPrime()) {
                    prime = u;
                }

                if (u.getBaseMultiples() > 0) {
                    long quotient = remainder / u.getBaseMultiples();
                    if (quotient == 0) {
                        continue;
                    }
                    if (currency.isPrefix()) {
                        retval += u.getSymbol() + quotient;
                    } else {
                        retval += quotient + u.getSymbol();
                    }
                    remainder = remainder % u.getBaseMultiples();
                } else if (remainder != 0) {
                    if (currency.isPrefix()) {
                        retval += u.getSymbol() + remainder;
                    } else {
                        retval += remainder + u.getSymbol();
                    }
                }
            }

            if (amount == 0 && prime != null) {
                retval += currency.isPrefix() ? prime.getSymbol() + "0" : "0" + prime.getSymbol();
            }

            return retval;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in formatCurrency: " + e.getMessage(), e);
        }
    }

    public long parseCurrency(Currency currency, String amount) throws CurrenciesException {
        boolean isNegative = false;
        if (amount.matches("(^-).*")) {
            isNegative = true;
            amount = amount.replaceAll("(^-)", "");
            if (Currencies.DEBUG) {
                Currencies.getPluginLogger().info("PARSED CURRENCY WILL BE NEGATIVE: " + amount);
            }
        }

        // http://stackoverflow.com/questions/2206378/how-to-split-a-string-but-also-keep-the-delimiters
        String[] parts = amount.replaceAll("([0-9-]+)", "|$1|").replaceAll("(^\\|*)|(\\|*$)", "").split("\\|");
        if (Currencies.DEBUG) {
            Currencies.getPluginLogger().info("PARSE CURRENCY - ALL: " + java.util.Arrays.toString(parts));
        }

        if (parts.length == 0 || parts.length == 1) {
            throw new CurrenciesException("Either no symbol or no currency amount was provided.");
        }

        long baseAmount = 0;
        Unit partUnit = null;
        Long partAmount = null;

        try (Connection conn = connectionProvider.getConnection()) {
            for (String part : parts) {
                if (Currencies.DEBUG) {
                    Currencies.getPluginLogger().info("PARSE CURRENCY - PART: " + part);
                }

                if (part.matches("\\D+")) {
                    partUnit = repository.queryUnitBySymbolAndCurrency(conn, currency, part);
                    if (partUnit == null) {
                        throw new CurrenciesException(part + " is not a valid symbol.");
                    }
                    if (Currencies.DEBUG) {
                        Currencies.getPluginLogger().info("PARSE CURRENCY - UNIT: " + partUnit.getName());
                    }
                } else {
                    try {
                        partAmount = Math.abs(Long.parseLong(part));
                    } catch (NumberFormatException e) {
                        throw new CurrenciesException(part + " could not be parsed into a number.");
                    }
                    if (Currencies.DEBUG) {
                        Currencies.getPluginLogger().info("PARSE CURRENCY - PART AMOUNT: " + partAmount);
                    }
                }

                if (partUnit != null && partAmount != null) {
                    baseAmount += partUnit.getBaseMultiples() != 0
                            ? partAmount * partUnit.getBaseMultiples()
                            : partAmount;

                    if (Currencies.DEBUG) {
                        Currencies.getPluginLogger().info("PARSE CURRENCY - BASE AMOUNT: " + baseAmount);
                    }

                    partUnit = null;
                    partAmount = null;
                }
            }
        } catch (CurrenciesException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in parseCurrency: " + e.getMessage(), e);
        }

        if (Currencies.DEBUG) {
            Currencies.getPluginLogger().info("PARSE CURRENCY - FINAL AMOUNT: " + baseAmount);
        }

        return isNegative ? baseAmount * -1 : baseAmount;
    }

    public Currency getCurrencyFromAmount(Account account, String amount) throws CurrenciesException {
        return resolveCurrency(account, amount).getCurrency();
    }

    /**
     * Resolves an amount string whose currency is ambiguous: the currency is inferred from a prime unit
     * symbol embedded in the string (disambiguated via the account's default currency if the symbol is
     * shared by more than one currency), and the total base-unit amount is computed against that currency
     * in the same pass. Callers that previously called getCurrencyFromAmount() and then parseCurrency()
     * separately can use this instead to avoid resolving the currency and parsing the string twice.
     */
    public CurrencyDTO resolveCurrency(Account account, String amount) throws CurrenciesException {
        boolean isNegative = false;
        String working = amount;
        if (working.matches("(^-).*")) {
            isNegative = true;
            working = working.replaceAll("(^-)", "");
        }

        String[] parts = working.replaceAll("([0-9-]+)", "|$1|").replaceAll("(^\\|*)|(\\|*$)", "").split("\\|");
        if (parts.length == 0) {
            throw new CurrenciesException("Either no symbol or no currency amount was provided.");
        }

        if (parts.length == 1) {
            // No unit symbol at all: per CLAUDE.md, a bare integer is only ever valid in the prime
            // unit of the player's own default currency -- there is no server-wide fallback here.
            if (account == null || account.getDefaultCurrency() == null) {
                throw new CurrenciesException("Either no symbol or no currency amount was provided.");
            }

            long partAmount;
            try {
                partAmount = Math.abs(Long.parseLong(parts[0]));
            } catch (NumberFormatException e) {
                throw new CurrenciesException(parts[0] + " could not be parsed into a number.");
            }

            Currency currency = account.getDefaultCurrency();
            try (Connection conn = connectionProvider.getConnection()) {
                Unit baseUnit = repository.queryBaseUnit(conn, currency);
                if (baseUnit == null) {
                    throw new CurrenciesRuntimeException("Currency " + currency.getAcronym() + " has no base.");
                }
                Unit primeUnit = repository.queryPrimeUnit(conn, currency);
                if (primeUnit == null) {
                    throw new CurrenciesRuntimeException("Currency " + currency.getAcronym() + " has no prime unit.");
                }
                long baseAmount = primeUnit.getBaseMultiples() != 0
                        ? partAmount * primeUnit.getBaseMultiples()
                        : partAmount;
                return new CurrencyDTO(currency, baseUnit, isNegative ? baseAmount * -1 : baseAmount);
            } catch (SQLException e) {
                throw new CurrenciesRuntimeException("Database error in resolveCurrency: " + e.getMessage(), e);
            }
        }

        try (Connection conn = connectionProvider.getConnection()) {
            Currency currency = null;
            for (String part : parts) {
                if (!part.matches("\\D+")) {
                    continue;
                }
                List<Unit> primes = repository.queryPrimeUnitsBySymbol(conn, part);

                if (primes.size() == 1) {
                    if (currency != null) {
                        throw new CurrenciesException("Two prime units were provided in the currency string.");
                    }
                    currency = primes.get(0).getCurrency();
                } else if (primes.size() > 1) {
                    if (account == null || account.getDefaultCurrency() == null) {
                        throw new CurrenciesException(
                                "This currency shares a prime unit with other currencies. You must run /currencies setdefault <currency>.");
                    }
                    for (Unit p : primes) {
                        if (p.getCurrency().getId().equals(account.getDefaultCurrency().getId())) {
                            currency = p.getCurrency();
                            break;
                        }
                    }
                }
            }

            if (currency == null) {
                throw new CurrenciesException("No prime unit was located in your currency string.");
            }

            Unit baseUnit = repository.queryBaseUnit(conn, currency);
            if (baseUnit == null) {
                throw new CurrenciesRuntimeException("Currency " + currency.getAcronym() + " has no base.");
            }

            long baseAmount = 0;
            Unit partUnit = null;
            Long partAmount = null;
            for (String part : parts) {
                if (part.matches("\\D+")) {
                    partUnit = repository.queryUnitBySymbolAndCurrency(conn, currency, part);
                    if (partUnit == null) {
                        throw new CurrenciesException(part + " is not a valid symbol.");
                    }
                } else {
                    try {
                        partAmount = Math.abs(Long.parseLong(part));
                    } catch (NumberFormatException e) {
                        throw new CurrenciesException(part + " could not be parsed into a number.");
                    }
                }

                if (partUnit != null && partAmount != null) {
                    baseAmount += partUnit.getBaseMultiples() != 0
                            ? partAmount * partUnit.getBaseMultiples()
                            : partAmount;
                    partUnit = null;
                    partAmount = null;
                }
            }

            return new CurrencyDTO(currency, baseUnit, isNegative ? baseAmount * -1 : baseAmount);
        } catch (CurrenciesException e) {
            throw e;
        } catch (SQLException e) {
            throw new CurrenciesRuntimeException("Database error in resolveCurrency: " + e.getMessage(), e);
        }
    }
}
