package osa.ora.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Loyalty {
	
	/**
	 * Rest Service for welcome page
	 * 
	 */
	@GetMapping("/welcome")
	public String welcome() {
		System.out.println("Welcome to loyalty service page ");
		String results="{\"To Check your balance\":"+"\"/loyalty/v1/balance/1000\"}";
		return results;
	}
	/**
	 * Rest Service for home page
	 * 
	 */
	@GetMapping("/")
	public String homePage() {
		System.out.println("Home page ");
		String results="{\"To Check your balance\":"+"\"/loyalty/v1/balance/1000\"}";
		return results;
	}

}
