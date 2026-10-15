package tech.csm.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



import tech.csm.model.Employees;
import tech.csm.repository.EmployeesRepository;

@RestController
@RequestMapping("/api")
public class MainController {
	
	@Autowired
	private EmployeesRepository employeesRepository;
	
	@GetMapping
	public String getApi()
	{
		return "API Calling";
	}
	
	@GetMapping("/emp")
	public ResponseEntity<?>  getAllEMployees()
	{
		List<Employees> employees = employeesRepository.findAll();
		if(employees == null) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Data Not found");
		return ResponseEntity.status(HttpStatus.OK).body(employees);
	}

}
