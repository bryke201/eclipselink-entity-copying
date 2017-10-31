package com.test.eclipselink.jpa.test_copy.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.eclipse.persistence.annotations.PrivateOwned;

@Entity
@Table(name = "ROOT_ENTITY")
public class RootEntity {

	@Id
	private Long id;

	@Column(name = "DATA1")
	private String data1;

	@Column(name = "DATA2")
	private String data2;

	@PrivateOwned
	@JoinColumn(name = "ASSOC_FK")
	@OneToOne(fetch = FetchType.LAZY)
	private AssociateEntity association;

	@OneToOne(mappedBy = "circularRef", fetch = FetchType.LAZY)
	private AssociateEntity_1 association1;

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

	public AssociateEntity getAssociation() {
		return association;
	}

	public void setAssociation(AssociateEntity association) {
		this.association = association;
	}

	public AssociateEntity_1 getAssociation1() {
		return association1;
	}

	public void setAssociation1(AssociateEntity_1 association1) {
		this.association1 = association1;
	}

}
