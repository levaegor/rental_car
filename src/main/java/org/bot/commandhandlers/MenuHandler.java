package org.bot.commandhandlers;

import org.bot.MessageInteraction;
import java.util.*;

/**
 * Manages authenticated sessions and shows the main reply-keyboard menu.
 * Now delegates authentication state to SessionService.
 */
public class MenuHandler {

    // mark chat as authenticated (forward to SessionService)
    public static void markAuthenticated(long chatId, String login) {
        SessionService.markAuthenticated(chatId, login);
    }

    // check auth (forward)
    public static boolean isAuthenticated(long chatId) {
        return SessionService.isAuthenticated(chatId);
    }

    // optional: get login for chatId
    public static String getLogin(long chatId) {
        return SessionService.getAuthenticatedLogin(chatId);
    }

    // show the reply-keyboard menu for the chat
    public static void showMenu(MessageInteraction msgInteraction, long chatId, boolean showSuspensionMessage) {
        List<String> buttons = Arrays.asList("Rent car", "Admin", "Active rents", "Help"); // removed "Logout"
        String title = "Main menu. Choose an action:";
        msgInteraction.sendMessageWithReplyKeyboard(chatId, title, buttons);
    }

    // New: handle selection from the menu (button text or /admin)
    public static void handleMenuSelection(MessageInteraction msgInteraction, long chatId, String selection) {
        if (selection == null) return;
        String cmd = selection.trim();

        // Menu button pressed -> show menu (clear active flows)
        if (cmd.equalsIgnoreCase("Menu") || cmd.equalsIgnoreCase("/menu")) {
            if (!SessionService.isAuthenticated(chatId)) {
                msgInteraction.sendMessage(chatId, "Only registered/logged-in users can use the menu. Please /start to login or register.");
                return;
            }
            // stop other active flows for this chat
            SessionService.cancelFlow(chatId); // start flow
            SessionService.endRentSession(chatId);
            SessionService.endAdminSession(chatId);
            msgInteraction.sendMessage(chatId, "Menu activated. Other commands are suspended while you use the menu.");
            showMenu(msgInteraction, chatId, true);
            return;
        }

        // Admin entry: require authenticated user, then forward to AdminHandler
        if (cmd.equalsIgnoreCase("Admin") || cmd.equalsIgnoreCase("/admin")) {
            if (!SessionService.isAuthenticated(chatId)) {
                msgInteraction.sendMessage(chatId, "Only registered/logged-in users can use Admin. Please /start to login or register.");
                return;
            }
            // clear any previous admin/rent sessions to start fresh
            SessionService.endAdminSession(chatId);
            SessionService.endRentSession(chatId);

            // forward to AdminHandler (expected to exist in project)
            try {
                org.bot.commandhandlers.AdminHandler.handleAdminCommand(msgInteraction, chatId);
            } catch (NoClassDefFoundError | Exception e) {
                // AdminHandler missing or error — inform user (developer should ensure AdminHandler exists)
                msgInteraction.sendMessage(chatId, "Admin functionality is not available right now.");
            }
            return;
        }

        // Rent car entry: forward to RentHandler
        if (cmd.equalsIgnoreCase("Rent") || cmd.equalsIgnoreCase("Rent car") || cmd.equalsIgnoreCase("/rent")) {
            if (!SessionService.isAuthenticated(chatId)) {
                msgInteraction.sendMessage(chatId, "Only registered/logged-in users can rent cars. Please /start to login or register.");
                return;
            }
            // clear any previous rent/admin sessions to ensure fresh flow
            SessionService.endRentSession(chatId);
            SessionService.endAdminSession(chatId);

            try {
                org.bot.commandhandlers.RentHandler.startRentFlow(msgInteraction, chatId);
            } catch (NoClassDefFoundError | Exception e) {
                msgInteraction.sendMessage(chatId, "Rent functionality is not available right now.");
            }
            return;
        }

        // Active rents: forward to ActiveRentsHandler
        if (cmd.equalsIgnoreCase("Active rents") || cmd.equalsIgnoreCase("/rents")) {
            if (!SessionService.isAuthenticated(chatId)) {
                msgInteraction.sendMessage(chatId, "Only registered/logged-in users can view active rents. Please /start to login or register.");
                return;
            }
            // ensure fresh handler state
            SessionService.endRentSession(chatId);
            SessionService.endAdminSession(chatId);
            try {
                org.bot.commandhandlers.ActiveRentsHandler.startActiveRentsFlow(msgInteraction, chatId);
            } catch (NoClassDefFoundError | Exception e) {
                msgInteraction.sendMessage(chatId, "Active rents functionality is not available right now.");
            }
            return;
        }

        // other menu items: minimal placeholders
        if (cmd.equalsIgnoreCase("Show active rentals") || cmd.equalsIgnoreCase("/rentals")) {
            msgInteraction.sendMessage(chatId, "Active rentals are not implemented yet.");
            return;
        }

        if (cmd.equalsIgnoreCase("Help")) {
            msgInteraction.sendMessage(chatId,
                "Справка — доступные команды и краткое описание:\n\n" +
                "/start — начать (вход или регистрация). Следуйте подсказкам бота.\n" +
                "/help — показать эту справку.\n" +
                "/menu — открыть главное меню (доступно только зарегистрированным пользователям).\n" +
                "/admin — открыть панель администратора (только для зарегистрированных).\n" +
                "/rent — начать оформление брони (кнопка \"Rent car\" в меню). Бронирование: выбор филиала → выбор авто → ввод даты приёма (DD.MM.YYYY) → выбор филиала возврата → ввод даты возврата (DD.MM.YYYY).\n" +
                "/rents или кнопка \"Active rents\" — показать ваши активные брони (только для зарегистрированных). Можно выбрать бронь кнопкой или по id и изменить/отменить её.\n\n" +
                "Общие правила:\n" +
                "- Выбор доступен через Inline-кнопки или ввод id/дат с клавиатуры (ввод валидируется).\n" +
                "- Формат даты: DD.MM.YYYY. Дата приёма ≥ сегодня; дата возврата ≥ дата приёма.\n"
            );
            return;
        }

        // default fallback
        msgInteraction.sendMessage(chatId, "Unknown menu selection.");
    }
}
