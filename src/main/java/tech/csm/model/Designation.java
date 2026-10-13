package tech.csm.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "designations")
public class Designation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "designation_id")
    private Long designationId;

    @Column(name = "designation_name")
    private String designationName;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Designation() {}

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

    public Boolean getIsActive() { 
    	return isActive;  
    }
    public void setIsActive(Boolean isActive) { 
    	this.isActive = isActive; 
    }

    public LocalDateTime getCreatedAt() { 
    	return createdAt; 
    }
    public void setCreatedAt(LocalDateTime createdAt) { 
    	this.createdAt = createdAt; 
    }
    
    
}