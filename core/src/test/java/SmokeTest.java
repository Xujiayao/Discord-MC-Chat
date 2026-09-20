import com.xujiayao.discord_mc_chat.Constants;
import org.junit.jupiter.api.Test;

/**
 * Proves that the Gradle test wiring of the core module is functional.
 *
 * @author Xujiayao
 */
class SmokeTest {

	@Test
	void version() {
		System.out.println("Compiling DMCC Version: " + Constants.VERSION);
	}
}
