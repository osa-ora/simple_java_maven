package osa.ora.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import osa.ora.demo.LoyaltyController;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DemoApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
   	public void testGetBalance() {
        	LoyaltyController loyaltyController = new LoyaltyController();
        	String result = loyaltyController.getBalance(100);
		System.out.println("Testing Balance 100, results="+result);
        	assertEquals(result, "{\"account\":"+100+ ",\"balance\": 3000}");
		result = loyaltyController.getBalance(200);
                System.out.println("Testing Balance 200, results="+result);
                assertEquals(result, "{\"account\":"+200+ ",\"balance\": 3000}");
    	}
        @Test
        public void testGetBalance2() {
                LoyaltyController loyaltyController = new LoyaltyController();
                String result = loyaltyController.getBalance(120);
                System.out.println("Testing Balance 120, results="+result);
                assertEquals(result, "{\"account\":"+120+ ",\"balance\": 3000}");
                result = loyaltyController.getBalance(220);
                System.out.println("Testing Balance 220, results="+result);
                assertEquals(result, "{\"account\":"+220+ ",\"balance\": 3000}");
        }
}
