package org.bot;


import modules.Config;
import modules.Database;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

import org.telegram.telegrambots.meta.generics.TelegramClient;


import java.util.logging.Logger;


public class Bot implements LongPollingSingleThreadUpdateConsumer {
    private static final Logger LOGGER = Logger.getLogger(Bot.class.getName());
    private final TelegramClient telegramClient;
    private static MessageInteraction msgInteraction;

    public Bot(String botToken) {
        telegramClient = new OkHttpTelegramClient(botToken);
        msgInteraction = new MessageInteraction(telegramClient);
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String message = update.getMessage().getText();

            switch (message) {
                case "/start":
                    msgInteraction.sendMessage(chatId, "Welcome to the Bot!");
                    break;

                case "/help":
                    msgInteraction.sendMessage(chatId, "Available commands:\n/start - Start the bot\n/help - Show this help message");
                    break;

                default:
                    msgInteraction.sendMessage(chatId, "You said: " + message);
                    break;
            }

        }


    }

}
