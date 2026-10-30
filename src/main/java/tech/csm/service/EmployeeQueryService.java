package tech.csm.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.csm.model.Employees;
import tech.csm.repository.EmployeesRepository;

/**
 * /api/getEmployees aur /api/employees/search ke liye Employees entities,
 * skills + availability pehle se bhari hui. JSON wahi entity serialization
 * deti hai (shape bilkul same), bas ab 3 queries me - pehle har employee ki
 * skills aur availability ke liye alag-alag lazy queries chalti thi.
 */
@Service
public class EmployeeQueryService {

    private final EmployeesRepository employeesRepository;

    public EmployeeQueryService(EmployeesRepository employeesRepository) {
        this.employeesRepository = employeesRepository;
    }

    @Transactional(readOnly = true)
    public List<Employees> findAllWithDetails() {
        List<Employees> employees = employeesRepository.findAllWithRefs();
        if (!employees.isEmpty()) {
            List<Integer> ids = employees.stream().map(Employees::getEmployeeId).toList();
            employeesRepository.fetchSkills(ids);       // same persistence context -> isi list ke objects bharte hain
            employeesRepository.fetchAvailability(ids);
        }
        return employees;
    }
}
