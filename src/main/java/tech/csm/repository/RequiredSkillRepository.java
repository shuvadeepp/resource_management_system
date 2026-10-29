package tech.csm.repository;
//repository/RequiredSkillRepository.java


import java.util.List;

import tech.csm.model.RequiredSkill;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequiredSkillRepository extends JpaRepository<RequiredSkill, Integer> {

	List<RequiredSkill> findByRequirementId(Integer requirementId);
}