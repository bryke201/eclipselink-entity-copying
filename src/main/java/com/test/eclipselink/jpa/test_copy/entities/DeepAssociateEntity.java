package com.test.eclipselink.jpa.test_copy.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "DEEP_ASSOCIATE_ENTITY")
public class DeepAssociateEntity {

	@Id
	private Long id;

	@Column(name = "DATA1")
	private String data1;

	@Column(name = "DATA2")
	private String data2;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getData1() {
		return data1;
	}

	public void setData1(String data1) {
		this.data1 = data1;
	}

	public String getData2() {
		return data2;
	}

	public void setData2(String data2) {
		this.data2 = data2;
	}

}
