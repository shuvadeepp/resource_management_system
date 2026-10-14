package tech.csm.repository;

import tech.csm.model.Skill;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Integer> {
	Optional<Skill> findBySkillNameIgnoreCase(String skillName);
}