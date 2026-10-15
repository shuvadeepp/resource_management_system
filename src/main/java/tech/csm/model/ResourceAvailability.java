package tech.csm.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name="resource_availability")
public class ResourceAvailability {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name="availability_id")
	private String availabilityId;
	

	@Column(name="availability_status")
	private String availabilityStatus;
	
	@Column(name="available_until")
	private Date availableUntil;
	
	@ManyToOne
	@JoinColumn(name = "employee_id")
	private Employees employee; 

	@JsonIgnore
	public Employees getEmployee() {
		return employee;
	}

	public void setEmployee(Employees employee) {
		this.employee = employee;
	}

	public String getAvailabilityId() {
		return availabilityId;
	}

	public void setAvailabilityId(String availabilityId) {
		this.availabilityId = availabilityId;
	}

	

	public String getAvailabilityStatus() {
		return availabilityStatus;
	}

	public void setAvailabilityStatus(String availabilityStatus) {
		this.availabilityStatus = availabilityStatus;
	}

	public Date getAvailableUntil() {
		return availableUntil;
	}

	public void setAvailableUntil(Date availableUntil) {
		this.availableUntil = availableUntil;
	}


}
