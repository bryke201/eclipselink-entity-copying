package com.test.eclipselink.jpa.test_copy.entities;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

@Entity
@Table(name = "ASSOCIATE_ENTITY")
public class AssociateEntity {

	@Id
	private Long id;

	@Column(name = "DATA1")
	private String data1;

	@Column(name = "DATA2")
	private String data2;

	@Column(name = "LAZY_DATA3")
	@Basic(fetch = FetchType.LAZY)
	private String lazyData3;

	@JoinColumn(name = "DEEP_ASSOC_FK")
	@OneToOne(fetch = FetchType.LAZY)
	private DeepAssociateEntity deepAssocEntity;

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

	public String getLazyData3() {
		return lazyData3;
	}

	public void setLazyData3(String lazyData3) {
		this.lazyData3 = lazyData3;
	}

	public DeepAssociateEntity getDeepAssocEntity() {
		return deepAssocEntity;
	}

	public void setDeepAssocEntity(DeepAssociateEntity deepAssocEntity) {
		this.deepAssocEntity = deepAssocEntity;
	}

}
