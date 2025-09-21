package org.bot;

import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                .chatId(chatId) // reverted to original usage
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
                .chatId(chatId) // reverted to original usage
                .text(text)
                .replyMarkup(replyKeyboardMarkup)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            LOGGER.severe("Failed to send message: " + e.getMessage());
        }
    }

    // Add: send a reply keyboard with given button labels (single row)
    public void sendMessageWithReplyKeyboard(long chatId, String text, List<String> buttons) {
        // build rows first
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        for (String b : buttons) {
            row.add(new KeyboardButton(b));
        }
        keyboard.add(row);

        // use builder instead of constructor (some API versions don't expose a public constructor)
        ReplyKeyboardMarkup keyboardMarkup = ReplyKeyboardMarkup.builder()
                .keyboard(keyboard)
                .resizeKeyboard(true)
                .oneTimeKeyboard(false)
                .selective(false)
                .build();

        sendMessage(chatId, text, keyboardMarkup);
    }

    // Changed: use InlineKeyboardRow and builders to match API signatures
    public void sendMessageWithInlineKeyboard(long chatId, String text, Map<String, String> buttonLabelToCallbackData) {
        List<InlineKeyboardRow> rowsInline = new ArrayList<>();
        for (Map.Entry<String, String> entry : buttonLabelToCallbackData.entrySet()) {
            String label = entry.getKey();
            String callbackData = entry.getValue();

            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(label)
                    .callbackData(callbackData)
                    .build();

            InlineKeyboardRow row = new InlineKeyboardRow();
            row.add(button);
            rowsInline.add(row);
        }

        InlineKeyboardMarkup markupInline = InlineKeyboardMarkup.builder()
                .keyboard(rowsInline)
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .replyMarkup(markupInline)
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException ex) {
            LOGGER.severe("Failed to send inline keyboard message: " + ex.getMessage());
        }
    }

    // Add: answer callback query to remove spinner / show optional text
    public void answerCallbackQuery(String callbackId, String text) {
        if (callbackId == null) return;
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackId)
                .text(text)
                .showAlert(false)
                .build();
        try {
            telegramClient.execute(answer);
        } catch (TelegramApiException ex) {
            LOGGER.severe("Failed to answer callback query: " + ex.getMessage());
        }
    }
}
