package tech.csm.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;



import org.springframework.web.bind.annotation.PathVariable;

import tech.csm.model.EmployeeSkill;
import tech.csm.model.Employees;
import tech.csm.repository.DepartmentsRepository;
import tech.csm.repository.DesignationRepository;
import tech.csm.repository.EmployeesRepository;
import tech.csm.repository.ProjectRepository;
import tech.csm.repository.SkillRepository;

@RestController
@RequestMapping("/api")
public class MainController {

	@Autowired
	private EmployeesRepository employeesRepository;

	@Autowired
	private DesignationRepository designationRepository;

	@Autowired
	private DepartmentsRepository departmentsRepository;

	@Autowired
	private SkillRepository skillRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private tech.csm.service.EmployeeQueryService employeeQueryService;

	@Autowired
	private tech.csm.llm.GeminiLlmClient geminiLlmClient;

	@GetMapping("/llm/test")
	public ResponseEntity<String> testLlm(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "Say hello in one line") String q) {
	    return ResponseEntity.ok(geminiLlmClient.ask(q));
	}

	@GetMapping
	public String getApi()
	{
		return "API Calling";
	}

	@GetMapping("/getEmployees")
	public ResponseEntity<?>  getAllEMployees()
	{
		List<Employees> employees = employeeQueryService.findAllWithDetails();
		if(employees == null) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Data Not found");
		return ResponseEntity.status(HttpStatus.OK).body(employees);
	}

	// Candidate details screen ke liye - ek employee ka pura profile.
	@GetMapping("/employees/{id}")
	public ResponseEntity<?> getEmployeeById(@PathVariable Integer id) {
		return employeesRepository.findById(id)
				.<ResponseEntity<?>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(java.util.Map.of("message", "Employee not found: " + id)));
	}

	// Home screen ke "projects with suggestions ready" list ke liye.
	@GetMapping("/projects")
	public ResponseEntity<?> getProjects() {
		return ResponseEntity.ok(projectRepository.findAll());
	}


	@GetMapping("/designations")
	public ResponseEntity<?> getDesignations() {
		return ResponseEntity.ok(designationRepository.findAll());
	}

	@GetMapping("/departments")
	public ResponseEntity<?> getDepartments() {
		return ResponseEntity.ok(departmentsRepository.findAll());
	}

	@GetMapping("/skills")
	public ResponseEntity<?> getSkills() {
		return ResponseEntity.ok(skillRepository.findAll());
	}

	 
	@GetMapping("/employees/search")
	public ResponseEntity<?> searchEmployees(
			@RequestParam(required = false) String designation,
			@RequestParam(required = false) String department,
			@RequestParam(required = false) Double minExperience,
			@RequestParam(required = false) List<String> skills) {

		List<Employees> matches = new ArrayList<>();
		for (Employees emp : employeeQueryService.findAllWithDetails()) {
			if (matchesFilters(emp, designation, department, minExperience, skills)) {
				matches.add(emp);
			}
		}
		return ResponseEntity.status(HttpStatus.OK).body(matches);
	}

	private boolean matchesFilters(Employees emp, String designation, String department,
	                                Double minExperience, List<String> skills) {

		if (designation != null && !designation.isBlank()) {
			String empDesignation = emp.getDesignationName();
			if (empDesignation == null || !empDesignation.equalsIgnoreCase(designation)) {
				return false;
			}
		}

		if (department != null && !department.isBlank()) {
			String empDepartment = emp.getDepartmentName();
			if (empDepartment == null || !empDepartment.equalsIgnoreCase(department)) {
				return false;
			}
		}

		if (minExperience != null) {
			Double exp = emp.getTotalExperienceYears();
			if (exp == null || exp < minExperience) {
				return false;
			}
		}

		if (skills != null && !skills.isEmpty()) {
			List<String> empSkills = new ArrayList<>();
			for (EmployeeSkill es : emp.getSkills()) {
				if (es.getSkillName() != null) {
					empSkills.add(es.getSkillName().toLowerCase());
				}
			}
			boolean anyMatch = false;
			for (String wanted : skills) {
				if (wanted != null && !wanted.isBlank() && empSkills.contains(wanted.toLowerCase())) {
					anyMatch = true;
					break;
				}
			}
			if (!anyMatch) {
				return false;
			}
		}

		return true;
	}

}
