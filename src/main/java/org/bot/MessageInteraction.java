package org.bot;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.logging.Logger;

public class MessageInteraction {
    private static final Logger LOGGER = Logger.getLogger(MessageInteraction.class.getName());
    private final TelegramClient telegramClient;

    public MessageInteraction(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }
    public void sendMessage(long chatId, String text) {
        SendMessage message = SendMessage
                .builder()
                .chatId(chatId)
                .text(text)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            LOGGER.severe("Failed to send message: " + e.getMessage());
        }
    }

    public void sendMessage(long chatId, String text, ReplyKeyboardMarkup replyKeyboardMarkup) {
        SendMessage message = SendMessage
                .builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(replyKeyboardMarkup)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            LOGGER.severe("Failed to send message: " + e.getMessage());
        }
    }
}
