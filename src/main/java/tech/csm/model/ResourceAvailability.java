package tech.csm.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * History table: ek employee ki kai rows, sabse nayi (max availability_id) current.
 * "[AUTO] " notes wali rows AvailabilitySyncService banata hai; baaki (HR/seed) rows base hain.
 */
@Entity
@Table(name="resource_availability")
public class ResourceAvailability {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name="availability_id")
	private Integer availabilityId;


	@Column(name="availability_status")
	private String availabilityStatus;

	// DATE column - bina @Temporal ke java.util.Date TIMESTAMP ki tarah bind hota hai
	@Temporal(TemporalType.DATE)
	@Column(name="available_from")
	private Date availableFrom;

	@Temporal(TemporalType.DATE)
	@Column(name="available_until")
	private Date availableUntil;

	@Column(name="available_capacity_percentage")
	private BigDecimal availableCapacityPercentage;

	@Column(name="expected_duration_months")
	private Integer expectedDurationMonths;

	@Column(name="availability_notes")
	private String availabilityNotes;

	@Column(name="effective_from")
	private LocalDate effectiveFrom;

	@Column(name="effective_until")
	private LocalDate effectiveUntil;

	@Column(name="recorded_by")
	private Integer recordedBy;

	@Column(name="created_at", insertable = false, updatable = false)
	private LocalDateTime createdAt;

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

	public Integer getExpectedDurationMonths() {
		return expectedDurationMonths;
	}

	public void setExpectedDurationMonths(Integer expectedDurationMonths) {
		this.expectedDurationMonths = expectedDurationMonths;
	}

	public String getAvailabilityNotes() {
		return availabilityNotes;
	}

	public void setAvailabilityNotes(String availabilityNotes) {
		this.availabilityNotes = availabilityNotes;
	}

	public LocalDate getEffectiveFrom() {
		return effectiveFrom;
	}

	public void setEffectiveFrom(LocalDate effectiveFrom) {
		this.effectiveFrom = effectiveFrom;
	}

	public LocalDate getEffectiveUntil() {
		return effectiveUntil;
	}

	public void setEffectiveUntil(LocalDate effectiveUntil) {
		this.effectiveUntil = effectiveUntil;
	}

	public Integer getRecordedBy() {
		return recordedBy;
	}

	public void setRecordedBy(Integer recordedBy) {
		this.recordedBy = recordedBy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}


}
