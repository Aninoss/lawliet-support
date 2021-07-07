package core;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.vdurmont.emoji.EmojiParser;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.StringUtil;

public class DiscordMessageHandler extends ListenerAdapter {

    private final static Logger LOGGER = LoggerFactory.getLogger(DiscordMessageHandler.class);

    private final SettingsManager settingsManager;
    private final BotpressAPI botpressAPI;

    public DiscordMessageHandler() throws IOException {
        settingsManager = new SettingsManager();
        botpressAPI = new BotpressAPI(System.getenv("BOTPRESS_DOMAIN"));
    }

    @Override
    public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        if (messageIsValid(event.getMessage())) {
            botpressAPI.request(
                    System.getenv("BOTPRESS_BOTID"),
                    event.getAuthor().getId(),
                    processMessageContent(event.getMessage())
            ).exceptionally(e -> {
                LOGGER.error("Exception on message retrieval", e);
                return null;
            }).thenAccept(responses -> {
                handleResponses(event, responses);
            });
        }
    }

    private String processMessageContent(Message message) {
        String content = message.getContentRaw();
        content = EmojiParser.removeAllEmojis(content);
        content = content.replaceAll("<[^\\n>]*>", "");
        return content;
    }

    private boolean messageIsValid(Message message) {
        boolean channelAccess = StringUtil.stringIsInt(message.getChannel().getName()); /* enable access to all ticket channels */
        if (!channelAccess) {
            JSONArray channels = settingsManager.get().getJSONArray("channels");
            for (int i = 0; i < channels.length(); i++) {
                if (channels.getLong(i) == message.getChannel().getIdLong()) {
                    channelAccess = true;
                    break;
                }
            }
        }
        return channelAccess &&
                message.getGuild().getSelfMember().hasPermission(message.getTextChannel(), Permission.MESSAGE_WRITE) &&
                !message.getAuthor().isBot();
    }

    private void handleResponses(GuildMessageReceivedEvent event, List<String> responses) {
        long offset = 0;
        for (int i = 0; i < responses.size(); i++) {
            String response = responses.get(i);
            long reactOffset = 500;
            long typingOffset = Math.min(3500, response.length() * 30L);
            long finalOffset = offset;
            int finalI = i;
            event.getChannel().sendTyping().queueAfter(offset + reactOffset, TimeUnit.MILLISECONDS, v -> {
                if (finalI == 0 && event.getGuild().getSelfMember().hasPermission(event.getChannel(), Permission.MESSAGE_HISTORY)) {
                    event.getMessage().reply(response)
                            .mentionRepliedUser(false)
                            .queueAfter(finalOffset + typingOffset, TimeUnit.MILLISECONDS);
                } else {
                    event.getChannel().sendMessage(response)
                            .queueAfter(finalOffset + typingOffset, TimeUnit.MILLISECONDS);
                }
            });
            offset += reactOffset + typingOffset;
        }
    }

}