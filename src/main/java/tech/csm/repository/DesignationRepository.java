package tech.csm.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tech.csm.model.Designation;

public interface DesignationRepository extends JpaRepository<Designation, Integer> {

}
