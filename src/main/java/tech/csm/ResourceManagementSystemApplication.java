package tech.csm;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ResourceManagementSystemApplication {

	public static void main(String[] args) {
		// Saare Java timestamps India time me (DB session bhi Asia/Kolkata - application.properties dekho)
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		SpringApplication.run(ResourceManagementSystemApplication.class, args);
	}

}
