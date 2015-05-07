package com.nobleuplift.currencies.entities;

import java.sql.Timestamp;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Created on 2015 May 1st at 09:11 PM.
 * 
 * @author Patrick
 */
@Entity
@Table(name="currencies_currency")
public class Currency {
	@Id
	private short id;
	private String name;
	private String acronym;
	private boolean prefix;
	private Timestamp dateCreated;
	private Timestamp dateModified;
	private Timestamp dateDeleted;
	private boolean deleted;
	
	public Currency() {
		super();
	}

	public short getId() {
		return id;
	}

	public void setId(short id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getAcronym() {
		return acronym;
	}

	public void setAcronym(String acronym) {
		this.acronym = acronym;
	}

	public boolean isPrefix() {
		return prefix;
	}

	public void setPrefix(boolean prefix) {
		this.prefix = prefix;
	}

	public Timestamp getDateCreated() {
		return dateCreated;
	}

	public void setDateCreated(Timestamp dateCreated) {
		this.dateCreated = dateCreated;
	}

	public Timestamp getDateModified() {
		return dateModified;
	}

	public void setDateModified(Timestamp dateModified) {
		this.dateModified = dateModified;
	}

	public Timestamp getDateDeleted() {
		return dateDeleted;
	}

	public void setDateDeleted(Timestamp dateDeleted) {
		this.dateDeleted = dateDeleted;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}
}
