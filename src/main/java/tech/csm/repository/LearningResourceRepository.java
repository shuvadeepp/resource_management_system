package tech.csm.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tech.csm.model.LearningResource;

public interface LearningResourceRepository extends JpaRepository<LearningResource, Integer> {

    /** Recommendations ke liye: sirf approved + active. */
    @Query("select r from LearningResource r where r.skillId in :skillIds and r.isApproved = true and r.isActive = true")
    List<LearningResource> findUsable(@Param("skillIds") Collection<Integer> skillIds);

    Optional<LearningResource> findBySkillIdAndResourceTitle(Integer skillId, String resourceTitle);
}

