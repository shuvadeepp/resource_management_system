package tech.csm.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "employees")
@JsonPropertyOrder({

    "employeeCode", "name", "designation", "department",
    "skills", "totalExperienceYears",
    "availabilityStatus", "availableFrom"
})
public class Employees implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonIgnore
	@Column(name = "employee_id")
	private Integer employeeId;

	
	@Column(name="employee_code")
	private String employeeCode;
	
	@Column(name = "employee_name")
	private String name;
	@JsonIgnore
	private String email;
	@JsonIgnore

	
	private String phone;

	@Column(name = "total_experience_years")
	private Double totalExperienceYears;

	@ManyToOne
	@JoinColumn(name = "designation_id")
	private Designation designation;

	@ManyToOne
	@JoinColumn(name = "department_id")
	private Departments department;

	
	@OneToMany(mappedBy = "employee")
	private List<ResourceAvailability> resourceAvailability = new ArrayList<>();
	
	@OneToMany(mappedBy = "employee")
	private List<EmployeeSkill> employeeSkill =new ArrayList<>();
	
	
	
	@JsonIgnore
	public List<EmployeeSkill> getEmployeeSkill() {
		return employeeSkill;
	}

	public void setEmployeeSkill(List<EmployeeSkill> employeeSkill) {
		this.employeeSkill = employeeSkill;
	}

	// ---------- JSON ke liye string getters ----------
    @JsonProperty("designation")
    public String getDesignationName() {
        return designation == null ? null : designation.getDesignationName();
    }

    @JsonProperty("department")
    public String getDepartmentName() {
        return department == null ? null : department.getDepartmentName();
    }
    
    @JsonProperty("availabilityStatus")
    public String getAvailabilityStatus() {
        if (resourceAvailability == null || resourceAvailability.isEmpty()) {
            return null;
        }

        return resourceAvailability.get(0).getAvailabilityStatus();
    }

    @JsonProperty("availableFrom")
    public String getAvailableFrom() {
        if (resourceAvailability == null || resourceAvailability.isEmpty()) {
            return null;
        }

        Date date = resourceAvailability.get(0).getAvailableUntil();

        if (date == null) {
            return null;
        }

        return new java.text.SimpleDateFormat("yyyy-MM-dd").format(date);
    }
    
    @JsonProperty("skills")
    public List<EmployeeSkill> getSkills() {
        return employeeSkill;
    }
	

	@ManyToOne
	@JoinColumn(name = "location_id")
	private Location location;

	@Column(name = "date_of_joining")
	@JsonIgnore
	private Date doj;

	@Column(name = "employment_status")
	@JsonIgnore
	private String employmentStatus;

	@Column(name = "source_system")
	@JsonIgnore
	private String sourceSystem;

	@Column(name = "erp_employee_id")
	@JsonIgnore
	private String erpEmpID;

	@Column(name = "erp_last_synced_at")
	@JsonIgnore
	private LocalDateTime erpLastSyncedAt;

	@Column(name = "created_at", updatable = false)
	@CreationTimestamp
	@JsonIgnore
	private LocalDateTime createdAt;

	@Column(name = "updated_at")
	@UpdateTimestamp
	@JsonIgnore
	private LocalDateTime updatedAt;

	


	public String getEmployeeCode() {
		return employeeCode;
	}

	public void setEmployeeCode(String employeeCode) {
		this.employeeCode = employeeCode;
	}

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }


	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }

	public Double getTotalExperienceYears() { return totalExperienceYears; }
	public void setTotalExperienceYears(Double totalExperienceYears) { this.totalExperienceYears = totalExperienceYears; }

	public Designation getDesignation() { return designation; }
	public void setDesignation(Designation designation) { this.designation = designation; }

	public Departments getDepartment() { return department; }
	
	@JsonIgnore
	public Location getLocation() { return location; }
	public void setLocation(Location location) { this.location = location; }

	public Date getDoj() { return doj; }
	public void setDoj(Date doj) { this.doj = doj; }

	public String getEmploymentStatus() { return employmentStatus; }
	public void setEmploymentStatus(String employmentStatus) { this.employmentStatus = employmentStatus; }

	public String getSourceSystem() { return sourceSystem; }
	public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }

	public String getErpEmpID() { return erpEmpID; }
	public void setErpEmpID(String erpEmpID) { this.erpEmpID = erpEmpID; }

	public LocalDateTime getErpLastSyncedAt() { return erpLastSyncedAt; }
	public void setErpLastSyncedAt(LocalDateTime erpLastSyncedAt) { this.erpLastSyncedAt = erpLastSyncedAt; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }


	public void setDepartment(Departments department) {
		this.department = department;
	}
	
	@JsonIgnore
	public List<ResourceAvailability> getResourceAvailability() {
	    return resourceAvailability;
	}

	public void setResourceAvailability(
	        List<ResourceAvailability> resourceAvailability) {
	    this.resourceAvailability = resourceAvailability;
	}
	
	
//	 @OneToMany(mappedBy = "employee")
//	private Set<ResourceAvailability> availabilities = new HashSet<>();
//	
//	@OneToMany(mappedBy = "employee")
//	private Set<EmployeeSkill> skills = new HashSet<>();

	
	


	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

	public Integer getEmployeeId() {
		return employeeId;
	}

	public void setEmployeeId(Integer employeeId) {
		this.employeeId = employeeId;
	}

	@Override
	public String toString() {
		return "Employees [employeeId=" + employeeId + ", employeeCode=" + employeeCode + ", name=" + name + ", email="
				+ email + ", phone=" + phone + ", totalExperienceYears=" + totalExperienceYears + ", designation="
				+ designation + ", department=" + department + ", resourceAvailability=" + resourceAvailability
				+ ", employeeSkill=" + employeeSkill + ", location=" + location + ", doj=" + doj + ", employmentStatus="
				+ employmentStatus + ", sourceSystem=" + sourceSystem + ", erpEmpID=" + erpEmpID + ", erpLastSyncedAt="
				+ erpLastSyncedAt + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + "]";
	}

	

	
}
