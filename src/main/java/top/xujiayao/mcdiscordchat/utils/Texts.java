package top.xujiayao.mcdiscordchat.utils;

/**
 * @author Xujiayao
 */
public record Texts(String unformattedResponseMessage,
                    String unformattedChatMessage,
                    String unformattedOtherMessage,
                    String unformattedCommandNotice,
                    String formattedResponseMessage,
                    String formattedChatMessage,
                    String formattedOtherMessage,
                    String formattedCommandNotice,
                    String serverStarted,
                    String serverStopped,
                    String joinServer,
                    String leftServer,
                    String deathMessage,
                    String advancementTask,
                    String advancementChallenge,
                    String advancementGoal,
                    String highMspt,
                    String offlineChannelTopic,
                    String onlineChannelTopic,
                    String onlineChannelTopicForMultiServer) {
}
