package top.xujiayao.mcdiscordchat.utils;

/**
 * @author Xujiayao
 */
public record Texts(String unformattedReferencedMessage,
                    String unformattedChatMessage,
                    String unformattedOtherMessage,
                    String formattedReferencedMessage,
                    String formattedChatMessage,
                    String formattedOtherMessage,
                    String serverStarted,
                    String serverStopped,
                    String joinServer,
                    String leftServer,
                    String deathMessage,
                    String advancementTask,
                    String advancementChallenge,
                    String advancementGoal,
                    String highMspt,
                    String consoleLogMessage) {
}
