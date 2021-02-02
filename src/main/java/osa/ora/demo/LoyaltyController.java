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
	 * Rest Service to return the loyalty account balance
	 * @param account id
	 * @return the account balance
	 */
	@GetMapping("/balance/{account}")
	public String getBalance(@PathVariable(value = "account") Integer account) {
		System.out.println("Get Balance for account: "+account);
		String results="{\"account\":"+account+ ",\"balance\": 3000}";
		return results;
	}
	/**
	 * Rest Service to return the last transaction for an account
	 * @param account id
	 * @return the last transaction
	 */
	@GetMapping("/transaction/{account}")
	public String getLastTransaction(@PathVariable(value = "account") Integer account) {
		System.out.println("Get Last Transactions for account: "+account);
		String results="{\"transaction\":"+account+ ",\"value\": 200,\"description\": \"Pizza Purchase\"}";
		return results;
	}

}
