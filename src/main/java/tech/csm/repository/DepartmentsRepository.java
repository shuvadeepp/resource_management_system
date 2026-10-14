package tech.csm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tech.csm.model.Departments;

@Repository
public interface DepartmentsRepository extends JpaRepository<Departments, Integer> {

}
