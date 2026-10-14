package tech.csm.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "employees")
public class Employees implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "employee_id")
	private Integer employeeId;

	@Column(name = "employee_code")
	private String employeeCode;

	@Column(name = "employee_name")
	private String employeeName;

	private String email;

	private String phone;

	@Column(name = "designation_id")
	private Integer designationId;

	@Column(name = "department_id")
	private Integer departmentId;

	@Column(name = "location_id")
	private Integer location_Id;

	@Column(name = "total_experience_years")
	private Double totalExperienceYears;

	@Column(name = "date_of_joining")
	private Date doj;

	@Column(name = "employment_status")
	private String employmentStatus;

	@Column(name = "source_system")
	private String sourceSystem;

	@Column(name = "erp_employee_id")
	private String erpEmpID;

	@Column(name = "erp_last_synced_at")
	private LocalDateTime erpLastSyncedAt;

	@Column(name = "created_at", updatable = false)
	@CreationTimestamp
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	@UpdateTimestamp
	private LocalDateTime updatedAt;

	@Override
	public String toString() {
		return "Employees [employeeId=" + employeeId + ", employeeCode=" + employeeCode + ", employeeName="
				+ employeeName + ", email=" + email + ", phone=" + phone + ", designationId=" + designationId
				+ ", departmentId=" + departmentId + ", location_Id=" + location_Id + ", totalExperienceYears="
				+ totalExperienceYears + ", doj=" + doj + ", employmentStatus=" + employmentStatus
				+ ", sourceSystem=" + sourceSystem + ", erpEmpID=" + erpEmpID + ", erpLastSyncedAt="
				+ erpLastSyncedAt + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + "]";
	}

	public Integer getEmployeeId() {
		return employeeId;
	}

	public void setEmployeeId(Integer employeeId) {
		this.employeeId = employeeId;
	}

	public String getEmployeeCode() {
		return employeeCode;
	}

	public void setEmployeeCode(String employeeCode) {
		this.employeeCode = employeeCode;
	}

	public String getEmployeeName() {
		return employeeName;
	}

	public void setEmployeeName(String employeeName) {
		this.employeeName = employeeName;
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

	public Integer getDesignationId() {
		return designationId;
	}

	public void setDesignationId(Integer designationId) {
		this.designationId = designationId;
	}

	public Integer getDepartmentId() {
		return departmentId;
	}

	public void setDepartmentId(Integer departmentId) {
		this.departmentId = departmentId;
	}

	public Integer getLocation_Id() {
		return location_Id;
	}

	public void setLocation_Id(Integer location_Id) {
		this.location_Id = location_Id;
	}

	public Double getTotalExperienceYears() {
		return totalExperienceYears;
	}

	public void setTotalExperienceYears(Double totalExperienceYears) {
		this.totalExperienceYears = totalExperienceYears;
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
}