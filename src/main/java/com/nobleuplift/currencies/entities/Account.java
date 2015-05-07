package com.nobleuplift.currencies.entities;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Created on 2015 May 1st at 09:11 PM.
 * 
 * @author Patrick
 */
@Entity
@Table(name="currencies_account")
public class Account {
	@Id
	private long id;
	private String name;
	private String uuid;
	
	public Account() {
		super();
	}
	
	public long getId() {
		return id;
	}
	
	public void setId(long id) {
		this.id = id;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getUuid() {
		return uuid;
	}
	
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
}
