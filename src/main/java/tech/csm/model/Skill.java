package tech.csm.model;


import jakarta.persistence.*;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "skills")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "skill_id")
    @JsonIgnore
    private Integer skillId;

    
    @Column(name = "skill_name")
    private String skillName;

    // Same category = related skill ("transferable, needs validation") matching me.
    // JSON me nahi bhejte taaki /api/skills ka shape na badle.
    @Column(name = "skill_category")
    @JsonIgnore
    private String skillCategory;

	public String getSkillCategory() {
		return skillCategory;
	}

	public void setSkillCategory(String skillCategory) {
		this.skillCategory = skillCategory;
	}


	public Integer getSkillId() {
		return skillId;
	}


	public void setSkillId(Integer skillId) {
		this.skillId = skillId;
	}


	public String getSkillName() {
		return skillName;
	}

	public void setSkillName(String skillName) {
		this.skillName = skillName;
	}

    
	
   
}