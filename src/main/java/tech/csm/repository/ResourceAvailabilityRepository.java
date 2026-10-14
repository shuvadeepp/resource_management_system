package tech.csm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tech.csm.model.ResourceAvailability;

@Repository
public interface ResourceAvailabilityRepository extends JpaRepository<ResourceAvailability, Integer> {

}
