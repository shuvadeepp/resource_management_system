package tech.csm.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import org.hibernate.annotations.SQLRestriction;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "designations")

public class Designation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "designation_id")
    @JsonIgnore
    private Long designationId;

    @Column(name = "designation_name")
    private String designationName;

	public Long getDesignationId() {
		return designationId;
	}

	public void setDesignationId(Long designationId) {
		this.designationId = designationId;
	}

	public String getDesignationName() {
		return designationName;
	}

	public void setDesignationName(String designationName) {
		this.designationName = designationName;
	}

    
    
}