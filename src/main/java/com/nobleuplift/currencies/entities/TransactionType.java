package com.nobleuplift.currencies.entities;

/**
 * The kind of a Transaction. Transaction.typeId stores the raw short ID (the DB column type is unchanged);
 * this enum is the mapping boundary between that ID and code that needs to branch on transaction type.
 */
public enum TransactionType {
	PAY((short) 1),
	BILL((short) 2),
	CREDIT((short) 3),
	DEBIT((short) 4),
	BANKRUPT((short) 5);

	private final short id;

	TransactionType(short id) {
		this.id = id;
	}

	public short getId() {
		return id;
	}

	public static TransactionType fromId(short id) {
		for (TransactionType type : values()) {
			if (type.id == id) {
				return type;
			}
		}
		return null;
	}
}
