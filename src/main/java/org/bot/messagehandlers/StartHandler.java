package org.bot.messagehandlers;

import org.bot.MessageInteraction;

public class StartHandler {
    public static void startCommand(MessageInteraction msgInteraction, long chatId) {
        String welcomeMessage = "Welcome to the bot! Use /help to see available commands.";
        msgInteraction.sendMessage(chatId, welcomeMessage);
    }
}
