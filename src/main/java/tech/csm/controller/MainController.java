package tech.csm.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tech.csm.dto.RequirementRequest;
import tech.csm.dto.RequirementResponse;
import tech.csm.model.Employees;
import tech.csm.repository.EmployeesRepository;
import tech.csm.service.RequirementMatchService;

@RestController
@RequestMapping("/api")
public class MainController {

	@Autowired
	private EmployeesRepository employeesRepository;

	@Autowired
	private RequirementMatchService requirementMatchService;

	@PostMapping("/search_request")
	public ResponseEntity<RequirementResponse> searchRequest(@Valid @RequestBody RequirementRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(requirementMatchService.createAndMatch(request));
	}

	@GetMapping
	public String getApi() {
		return "API Calling";
	}

	@GetMapping("/emp")
	public ResponseEntity<?> getAllEMployees() {
		List<Employees> employees = employeesRepository.findAll();
		if (employees == null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Data Not found");
		}
		return ResponseEntity.status(HttpStatus.OK).body(employees);
	}
}