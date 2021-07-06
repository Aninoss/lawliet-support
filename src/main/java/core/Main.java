package core;

import java.io.IOException;
import javax.security.auth.login.LoginException;
import net.dv8tion.jda.api.JDABuilder;

public class Main {

    public static void main(String[] args) throws LoginException, InterruptedException, IOException {
        JDABuilder.createLight(System.getenv("BOT_TOKEN"))
                .addEventListeners(new DiscordMessageHandler())
                .build()
                .awaitReady();
    }

}
