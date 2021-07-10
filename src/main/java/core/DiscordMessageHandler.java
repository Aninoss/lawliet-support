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
        botpressAPI = new BotpressAPI(
                System.getenv("BOTPRESS_DOMAIN"),
                System.getenv("BOTPRESS_USER"),
                System.getenv("BOTPRESS_PASSWORD"),
                settingsManager.get().getInt("cooldown_minutes")
        );
    }

    @Override
    public void onGuildMessageReceived(@NotNull GuildMessageReceivedEvent event) {
        String botId = System.getenv("BOTPRESS_BOTID");
        String userId = event.getAuthor().getId();

        if (messageIsValid(event) && botpressAPI.isNew(botId, userId)) {
            botpressAPI.request(
                    botId,
                    userId,
                    processMessageContent(event.getMessage()),
                    settingsManager.get().getDouble("confidence_threshold")
            ).thenAccept(responses -> {
                handleResponses(event, responses);
            }).exceptionally(e -> {
                LOGGER.error("Exception on message retrieval", e);
                return null;
            });
        }
    }

    private String processMessageContent(Message message) {
        String content = message.getContentRaw();
        content = EmojiParser.removeAllEmojis(content);
        content = content.replaceAll("<[^\\n>]*>", "");
        return content;
    }

    private boolean messageIsValid(GuildMessageReceivedEvent event) {
        boolean channelAccess = StringUtil.stringIsInt(event.getChannel().getName()); /* enable access to all ticket channels */
        if (!channelAccess) {
            JSONArray channels = settingsManager.get().getJSONArray("channels");
            for (int i = 0; i < channels.length(); i++) {
                if (channels.getLong(i) == event.getChannel().getIdLong()) {
                    channelAccess = true;
                    break;
                }
            }
        }
        return channelAccess &&
                !event.isWebhookMessage() &&
                event.getMember().getRoles().stream().noneMatch(r -> r.getIdLong() == settingsManager.get().getLong("role_ignored")) &&
                event.getGuild().getSelfMember().hasPermission(event.getChannel(), Permission.MESSAGE_WRITE) &&
                !event.getAuthor().isBot();
    }

    private void handleResponses(GuildMessageReceivedEvent event, List<String> responses) {
        long offset = 0;
        for (int i = 0; i < responses.size(); i++) {
            String response = responses.get(i);
            long reactOffset = 500;
            long typingOffset = Math.min(3000, response.length() * 25L);
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