package org.bot;

import org.bot.commandhandlers.StartHandler;
import org.bot.commandhandlers.MenuHandler;
import org.bot.commandhandlers.AdminHandler;
import org.bot.commandhandlers.RentHandler;
import org.bot.commandhandlers.SessionService;
import org.bot.commandhandlers.ActiveRentsHandler;
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
        // Handle callback queries (inline buttons)
        if (update.hasCallbackQuery()) {
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            String data = update.getCallbackQuery().getData();
            String callbackId = update.getCallbackQuery().getId();

            // admin-specific callbacks
            if (data != null && (data.startsWith("admin_") || "admin_menu".equals(data))) {
                AdminHandler.handleCallbackQuery(msgInteraction, chatId, data, callbackId);
                return;
            }

            // rent-specific callbacks
            if (data != null && data.startsWith("rent_")) {
                RentHandler.handleCallbackQuery(msgInteraction, chatId, data, callbackId);
                return;
            }

            // active-rents callbacks
            if (data != null && data.startsWith("ar_")) {
                ActiveRentsHandler.handleCallbackQuery(msgInteraction, chatId, data, callbackId);
                return;
            }

            // default to StartHandler for other callbacks
            StartHandler.handleCallbackQuery(msgInteraction, chatId, data, callbackId);
            return;
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String message = update.getMessage().getText();
            String splitRegex = "\\s+";
            String[] splitMessage = message.split(splitRegex);

            // Early: route menu button texts (reply-keyboard labels) directly to MenuHandler.
            // This ensures pressing "Active rents", "Admin", "Rent car" etc. works even if another flow was active.
            if (!splitMessage[0].startsWith("/")) {
                String label = splitMessage[0].trim();
                // include common menu labels; MenuHandler will validate auth and clear flows as needed
                if (label.equalsIgnoreCase("Admin")
                        || label.equalsIgnoreCase("Rent")
                        || label.equalsIgnoreCase("Rent car")
                        || label.equalsIgnoreCase("Menu")
                        || label.equalsIgnoreCase("Active")
                        || label.equalsIgnoreCase("Active rents")
                        || label.equalsIgnoreCase("Profile")
                        || label.equalsIgnoreCase("Help")) {
                    MenuHandler.handleMenuSelection(msgInteraction, chatId, message);
                    return;
                }
            }

            // ActiveRentsHandler precedence
            if (!splitMessage[0].startsWith("/") && ActiveRentsHandler.hasActive(chatId)) {
                ActiveRentsHandler.handleIncomingMessage(msgInteraction, chatId, message);
                return;
            }

            // If AdminHandler is currently expecting input, it has precedence
            if (!splitMessage[0].startsWith("/") && AdminHandler.hasActive(chatId)) {
                AdminHandler.handleIncomingMessage(msgInteraction, chatId, message);
                return;
            }

            // If RentHandler is currently expecting input, it has precedence over StartHandler
            if (!splitMessage[0].startsWith("/") && RentHandler.hasActive(chatId)) {
                RentHandler.handleIncomingMessage(msgInteraction, chatId, message);
                return;
            }

            // If there is an active start-conversation for this chat, forward raw text to it
            if (!splitMessage[0].startsWith("/") && StartHandler.hasActive(chatId)) {
                StartHandler.handleIncomingMessage(msgInteraction, chatId, message);
                return;
            }

            switch (splitMessage[0]) {
                case "/start":
                    StartHandler.startCommand(msgInteraction, chatId);
                    break;

                case "/help":
                    // подробная справка по всем командам проекта (обновлённый текст)
                    msgInteraction.sendMessage(chatId,
                        "Справка — команды и краткое описание:\n\n" +
                        "/start — начать: вход или регистрация через подсказки бота.\n" +
                        "/help — показать эту справку.\n" +
                        "/menu — открыть главное меню (только для зарегистрированных пользователей).\n" +
                        "/admin — открыть панель администратора (только для зарегистрированных). Если вы не админ, бот попросит пароль; при успешной проверке флаг isAdmin сохраняется в базе.\n" +
                        "/rent — начать бронирование авто (или кнопка \"Rent car\" в меню). Последовательность: выбор филиала → выбор авто → ввод даты приёма (DD.MM.YYYY) → выбор филиала возврата → ввод даты возврата (DD.MM.YYYY).\n" +
                        "/rents или кнопка \"Active rents\" — показать ваши активные брони (только для зарегистрированных). Можно выбрать бронь кнопкой или по id, затем отменить или изменить даты/филиалы.\n\n" +
                        "Примечания:\n" +
                        "- В большинстве мест можно выбрать через Inline-кнопки или ввести id/дату с клавиатуры (ввод валидируется).\n" +
                        "- Формат дат: DD.MM.YYYY. Дата приёма ≥ сегодня; дата возврата ≥ дата приёма.\n"
                    );
                    break;

                case "/menu":
                    if (!MenuHandler.isAuthenticated(chatId)) {
                        msgInteraction.sendMessage(chatId, "Only registered/logged-in users can use /menu. Please /start to login or register.");
                    } else {
                        // stop other active flows for this chat
                        if (StartHandler.hasActive(chatId)) {
                            StartHandler.cancelFlow(chatId);
                        }
                        // ensure rent and admin flows are ended so reopening them starts fresh
                        SessionService.endRentSession(chatId);
                        SessionService.endAdminSession(chatId);

                        msgInteraction.sendMessage(chatId, "Menu activated. Other commands are suspended while you use the menu.");
                        MenuHandler.showMenu(msgInteraction, chatId, true);
                    }
                    break;

                case "/admin":
                case "Admin":
                    // route Admin button or /admin command to MenuHandler which will verify auth and forward to AdminHandler
                    MenuHandler.handleMenuSelection(msgInteraction, chatId, splitMessage[0]);
                    break;

                case "/rent":
                case "Rent":
                case "Rent car":
                    // route rent command
                    MenuHandler.handleMenuSelection(msgInteraction, chatId, splitMessage[0]);
                    break;

                case "/rents":
                case "Active":
                case "Active rents":
                    MenuHandler.handleMenuSelection(msgInteraction, chatId, splitMessage[0]);
                    break;

                default:
                    msgInteraction.sendMessage(chatId, "Unknown command. Type /help to see available commands.");
                    break;
            }
        }
    }
}
