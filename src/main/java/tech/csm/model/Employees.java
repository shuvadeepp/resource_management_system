package tech.csm.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.ManyToAny;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
@Entity
@Table(name="employees")
@JsonPropertyOrder({
	"employeeCode",
	"name",
	"phone"
}
)
public class Employees implements Serializable {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonIgnore
	@Column(name="employee_id")
	private Integer empId;
	
	@Column(name="employee_code")
	private String employeeId;
	
	@Column(name = "employee_name")
	private String name;
	
	private String email;
	
	private String phone;
	
	@Column(name = "total_experience_years")
	private Integer totalExperienceYears;
	
	@ManyToOne
	@JoinColumn(name="designation_id")
	private Designation designation;
	
	@ManyToOne
	@JoinColumn(name="department_id")
	private Departments department;
	
	@ManyToOne
	@JoinColumn(name="location_id")
	private Location location;
	
	@Column(name="date_of_joining")
	private Date doj;
	
	@Column(name="employment_status")
	private String employmentStatus;
	
	@Column(name="source_system")
	private String sourceSystem;
	
	@Column(name="erp_employee_id")
	private String erpEmpID;
	
	@Column(name="erp_last_synced_at")
	private LocalDateTime erpLastSyncedAt;
	
	@Column(name="created_at",updatable = false)
	@CreationTimestamp
	 private LocalDateTime createdAt;
	
	@Column(name="updated_at")
	@UpdateTimestamp
	private LocalDateTime updatedAt;

	public Integer getEmpId() {
		return empId;
	}

	public void setEmpId(Integer empId) {
		this.empId = empId;
	}

	public String getEmployeeId() {
		return employeeId;
	}

	public void setEmployeeId(String employeeId) {
		this.employeeId = employeeId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public Integer getTotalExperienceYears() {
		return totalExperienceYears;
	}

	public void setTotalExperienceYears(Integer totalExperienceYears) {
		this.totalExperienceYears = totalExperienceYears;
	}

	public Designation getDesignation() {
		return designation;
	}

	public void setDesignation(Designation designation) {
		this.designation = designation;
	}

	public Departments getDepartment() {
		return department;
	}

	public void setDepartment(Departments department) {
		this.department = department;
	}

	public Location getLocation() {
		return location;
	}

	public void setLocation(Location location) {
		this.location = location;
	}

	public Date getDoj() {
		return doj;
	}

	public void setDoj(Date doj) {
		this.doj = doj;
	}

	public String getEmploymentStatus() {
		return employmentStatus;
	}

	public void setEmploymentStatus(String employmentStatus) {
		this.employmentStatus = employmentStatus;
	}

	public String getSourceSystem() {
		return sourceSystem;
	}

	public void setSourceSystem(String sourceSystem) {
		this.sourceSystem = sourceSystem;
	}

	public String getErpEmpID() {
		return erpEmpID;
	}

	public void setErpEmpID(String erpEmpID) {
		this.erpEmpID = erpEmpID;
	}

	public LocalDateTime getErpLastSyncedAt() {
		return erpLastSyncedAt;
	}

	public void setErpLastSyncedAt(LocalDateTime erpLastSyncedAt) {
		this.erpLastSyncedAt = erpLastSyncedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	@Override
	public String toString() {
		return "Employees [empId=" + empId + ", employeeId=" + employeeId + ", name=" + name + ", email=" + email
				+ ", phone=" + phone + ", totalExperienceYears=" + totalExperienceYears + ", designation=" + designation
				+ ", department=" + department + ", location=" + location + ", doj=" + doj + ", employmentStatus="
				+ employmentStatus + ", sourceSystem=" + sourceSystem + ", erpEmpID=" + erpEmpID + ", erpLastSyncedAt="
				+ erpLastSyncedAt + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + "]";
	}

	

	
	
}
