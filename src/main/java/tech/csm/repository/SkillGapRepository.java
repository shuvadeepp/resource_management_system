package tech.csm.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.csm.model.SkillGap;

public interface SkillGapRepository extends JpaRepository<SkillGap, Integer> {

    List<SkillGap> findByMatchingResultId(Integer matchingResultId);

    List<SkillGap> findByMatchingResultIdIn(Collection<Integer> matchingResultIds);
}

