package tech.csm.model;

  
import jakarta.persistence.*;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@Entity
@Table(name = "employee_skills")
@JsonPropertyOrder({ "name", "proficiency", "experienceYears" })
public class EmployeeSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_skill_id")
    @JsonIgnore
    private Long employeeSkillId;
    
    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employees employee;

    private Integer isActive;
   
    
    public Integer getIsActive() {
		return isActive;
	}

	public void setIsActive(Integer isActive) {
		this.isActive = isActive;
	}


	@Column(name = "skill_priority")
    private String skillPriority;

    @Column(name = "proficiency_level")
    private String proficiencyLevel;

    @Column(name = "experience_months")
    private Integer experienceMonths;

   
       
    @ManyToOne
    @JoinColumn(name="skill_id")
    private Skill skill;
    
    @JsonProperty("name")
    public String getSkillName() { return skill == null? null:skill.getSkillName(); }
    
    @JsonProperty("experienceYears")
    public Integer getExperienceYears() {
        return experienceMonths == null ? null : Math.round(experienceMonths / 12f);
    }
    
    @JsonProperty("proficiency")
    public Integer getProficiency() {
        try {
            return proficiencyLevel == null ? null : Integer.valueOf(proficiencyLevel.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    @JsonIgnore
	public Long getEmployeeSkillId() {
		return employeeSkillId;
	}


	public void setEmployeeSkillId(Long employeeSkillId) {
		this.employeeSkillId = employeeSkillId;
	}

	@JsonIgnore
	public Employees getEmployee() {
		return employee;
	}


	public void setEmployee(Employees employee) {
		this.employee = employee;
	}

	@JsonIgnore
	public String getSkillPriority() {
		return skillPriority;
	}



	public void setSkillPriority(String skillPriority) {
		this.skillPriority = skillPriority;
	}

	@JsonIgnore
	public String getProficiencyLevel() {
		return proficiencyLevel;
	}


   


	public void setProficiencyLevel(String proficiencyLevel) {
		this.proficiencyLevel = proficiencyLevel;
	}

	@JsonIgnore
	public Integer getExperienceMonths() {
		return experienceMonths;
	}


	public void setExperienceMonths(Integer experienceMonths) {
		this.experienceMonths = experienceMonths;
	}




	@JsonIgnore
	public Skill getSkill() {
		return skill;
	}


	public void setSkill(Skill skill) {
		this.skill = skill;
	}
}