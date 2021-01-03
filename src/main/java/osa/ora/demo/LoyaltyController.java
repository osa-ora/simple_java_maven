package osa.ora.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/loyalty/v1")
public class LoyaltyController {
	
	/**
	 * Rest Service to return the loyalty account
	 * @param account id
	 * @return the LoyaltyAccount
	 */
	@GetMapping("/balance/{account}")
	public String getBalance(@PathVariable(value = "account") Integer account) {
		System.out.println("Get Balance: "+account);
		String results="{\"account\":"+account+ ",\"balance\": 3000}";
		return results;
	}

}
