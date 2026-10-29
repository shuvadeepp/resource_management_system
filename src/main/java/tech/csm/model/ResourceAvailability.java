package tech.csm.model;

import java.math.BigDecimal;
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
	private Integer availabilityId;


	@Column(name="availability_status")
	private String availabilityStatus;

	@Column(name="available_from")
	private Date availableFrom;

	@Column(name="available_until")
	private Date availableUntil;

	@Column(name="available_capacity_percentage")
	private BigDecimal availableCapacityPercentage;

	public Date getAvailableFrom() {
		return availableFrom;
	}

	public void setAvailableFrom(Date availableFrom) {
		this.availableFrom = availableFrom;
	}

	public BigDecimal getAvailableCapacityPercentage() {
		return availableCapacityPercentage;
	}

	public void setAvailableCapacityPercentage(BigDecimal availableCapacityPercentage) {
		this.availableCapacityPercentage = availableCapacityPercentage;
	}

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

	public Integer getAvailabilityId() {
		return availabilityId;
	}

	public void setAvailabilityId(Integer availabilityId) {
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
