package com.nobleuplift.currencies.entities;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * Created on 2015 May 1st at 09:11 PM.
 * 
 * @author Patrick
 */
@Entity
@Table(name="currencies_holder")
public class Holder {
	@EmbeddedId
	private HolderId holderId;

	public Holder() {
		super();
	}

	public HolderId getHolderId() {
		return holderId;
	}

	public void setHolderId(HolderId holderId) {
		this.holderId = holderId;
	}
}
