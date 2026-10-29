package tech.csm.repository; 

import tech.csm.model.ProjectRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRequirementRepository extends JpaRepository<ProjectRequirement, Integer> {
}