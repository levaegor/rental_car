package org.bot.commandhandlers;

import modules.Database;
import org.bot.MessageInteraction;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.*;
import java.util.logging.Logger;

public class StartHandler {

    private static final Logger LOGGER = Logger.getLogger(StartHandler.class.getName());

    // regexes are now taken from SessionService
    private static final String EMAIL_REGEX = SessionService.EMAIL_REGEX;
    private static final String PHONE_REGEX = SessionService.PHONE_REGEX;
    private static final String DRIVER_LICENSE_REGEX = SessionService.DRIVER_LICENSE_REGEX;

    public static void startCommand(MessageInteraction msgInteraction, long chatId) {
        // show a reply keyboard with "Rent car" and "Admin" and ask for login
        String welcome = "Welcome to the Car Rental Bot!\nPlease enter your login:";
        msgInteraction.sendMessage(
                chatId,
                welcome
        );

        // use SessionService.createSession helper
        SessionService.createSession(chatId, SessionService.State.AWAIT_LOGIN);
        msgInteraction.sendMessage(chatId, "Enter login:"); // <-- removed inline Back here
    }

    public static boolean hasActive(long chatId) {
        return SessionService.hasActive(chatId);
    }

    public static void handleIncomingMessage(MessageInteraction msgInteraction, long chatId, String text) {
        SessionService.Session ctx = SessionService.getSession(chatId);
        if (ctx == null) {
            // no active flow; ignore
            return;
        }

        String trimmed = text == null ? "" : text.trim();

        try {
            switch (ctx.state) {
                case AWAIT_LOGIN:
                    if (trimmed.isEmpty()) {
                        // keep asking for login without Back
                        msgInteraction.sendMessage(chatId, "Login cannot be empty. Please enter your login:");
                        return;
                    }

                    // check DB for login
                    Boolean existsB = Database.withUserRetries(msgInteraction, chatId, () -> Database.isUser(trimmed));
                    if (existsB == null) return; // DB failure already reported
                    boolean exists = existsB;
                    ctx.login = trimmed;
                    if (exists) {
                        ctx.state = SessionService.State.AWAIT_PASSWORD;
                        // After login check the Back button is allowed (go back one step -> re-enter login)
                        msgInteraction.sendMessageWithInlineKeyboard(
                                chatId,
                                "User found. Please enter your password:",
                                Collections.singletonMap("Back", "back")
                        );
                    } else {
                        ctx.state = SessionService.State.AWAIT_SIGNUP_PASSWORD;
                        msgInteraction.sendMessageWithInlineKeyboard(
                                chatId,
                                "No such login. Please choose a password for a new account:",
                                Collections.singletonMap("Back", "back")
                        );
                    }
                    break;

                case AWAIT_PASSWORD:
                    if (trimmed.isEmpty()) {
                        msgInteraction.sendMessageWithInlineKeyboard(
                                chatId,
                                "Password cannot be empty. Please enter your password:",
                                Collections.singletonMap("Back", "back")
                        );
                        return;
                    }

                    Boolean okB = Database.withUserRetries(msgInteraction, chatId, () -> Database.verifyPassword(ctx.login, trimmed));
                    if (okB == null) return;
                    boolean ok = okB;
                    if (ok) {
                        // mark authenticated and show menu automatically
                        MenuHandler.markAuthenticated(chatId, ctx.login);
                        SessionService.removeSession(chatId);
                        msgInteraction.sendMessage(chatId, "Authorization successful. Welcome, " + ctx.login + "!");
                        MenuHandler.showMenu(msgInteraction, chatId, false);
                    } else {
                        msgInteraction.sendMessageWithInlineKeyboard(
                                chatId,
                                "Incorrect password. Please try again:",
                                Collections.singletonMap("Back", "back")
                        );
                    }
                    break;

                case AWAIT_SIGNUP_PASSWORD:
                    if (trimmed.isEmpty()) {
                        msgInteraction.sendMessageWithInlineKeyboard(
                                chatId,
                                "Password cannot be empty. Please choose a password for the new login:",
                                Collections.singletonMap("Back", "back")
                        );
                        return;
                    }
                    ctx.password = trimmed;
                    ctx.state = SessionService.State.AWAIT_SIGNUP_EMAIL;
                    msgInteraction.sendMessageWithInlineKeyboard(
                            chatId,
                            "Please enter your email:",
                            Collections.singletonMap("Back", "back")
                    );
                    break;

                case AWAIT_SIGNUP_EMAIL:
                    if (trimmed.isEmpty() || !trimmed.matches(EMAIL_REGEX)) {
                        msgInteraction.sendMessageWithInlineKeyboard(
                                chatId,
                                "Invalid email. Please enter a valid email (example: abc@sobaka.ru):",
                                Collections.singletonMap("Back", "back")
                        );
                        return;
                    }
                    // check uniqueness
                    Boolean usedEmailB = Database.withUserRetries(msgInteraction, chatId, () -> Database.isEmailUsed(trimmed));
                    if (usedEmailB == null) return;
                    if (usedEmailB) {
                        msgInteraction.sendMessageWithInlineKeyboard(
                                chatId,
                                "This email is already in use. Please enter a different email:",
                                Collections.singletonMap("Back", "back")
                        );
                        return; // do not advance state
                    }
                    ctx.email = trimmed;
                    ctx.state = SessionService.State.AWAIT_SIGNUP_PHONE;
                    msgInteraction.sendMessageWithInlineKeyboard(
                            chatId,
                            "Please enter your phone number (format +79999999999):",
                            Collections.singletonMap("Back", "back")
                    );
                    break;

                case AWAIT_SIGNUP_PHONE:
                    if (trimmed.isEmpty() || !trimmed.matches(PHONE_REGEX)) {
                        msgInteraction.sendMessageWithInlineKeyboard(
                                chatId,
                                "Invalid phone. Example: +79999999999. Please enter phone:",
                                Collections.singletonMap("Back", "back")
                        );
                        return;
                    }
                    // check uniqueness
                    Boolean usedPhoneB = Database.withUserRetries(msgInteraction, chatId, () -> Database.isPhoneUsed(trimmed));
                    if (usedPhoneB == null) return;
                    if (usedPhoneB) {
                        msgInteraction.sendMessageWithInlineKeyboard(
                                chatId,
                                "This phone number is already registered. Please enter a different phone number:",
                                Collections.singletonMap("Back", "back")
                        );
                        return; // do not advance state
                    }
                    ctx.phone = trimmed;
                    ctx.state = SessionService.State.AWAIT_SIGNUP_LICENSE;
                    msgInteraction.sendMessageWithInlineKeyboard(
                            chatId,
                            "Please enter your driver license number (10 chars, uppercase letters or digits):",
                            Collections.singletonMap("Back", "back")
                    );
                    break;

                case AWAIT_SIGNUP_LICENSE:
                    if (trimmed.isEmpty() || !trimmed.matches(DRIVER_LICENSE_REGEX)) {
                        msgInteraction.sendMessageWithInlineKeyboard(
                                chatId,
                                "Invalid driver license. It must be 10 characters (A-Z or 0-9). Please enter again:",
                                Collections.singletonMap("Back", "back")
                        );
                        return;
                    }
                    // check uniqueness
                    Boolean usedLicB = Database.withUserRetries(msgInteraction, chatId, () -> Database.isLicenseUsed(trimmed));
                    if (usedLicB == null) return;
                    if (usedLicB) {
                        msgInteraction.sendMessageWithInlineKeyboard(
                                chatId,
                                "This driver license number is already registered. Please enter a different license number:",
                                Collections.singletonMap("Back", "back")
                        );
                        return; // do not advance state
                    }
                    ctx.driverLicense = trimmed;

                    // Final sanity: ensure none of required signup fields are missing (shouldn't happen because validated earlier)
                    if (ctx.login == null || ctx.login.isEmpty()
                            || ctx.password == null || ctx.password.isEmpty()
                            || ctx.email == null || ctx.email.isEmpty()
                            || ctx.phone == null || ctx.phone.isEmpty()
                            || ctx.driverLicense == null || ctx.driverLicense.isEmpty()) {
                        msgInteraction.sendMessageWithInlineKeyboard(
                                chatId,
                                "Some required fields are missing. Please restart with /start and try again.",
                                Collections.singletonMap("Back", "back")
                        );
                        SessionService.removeSession(chatId);
                        return;
                    }

                    // All fields validated. Create the user entry with provided data.
                    Boolean createdB = Database.withUserRetries(msgInteraction, chatId, () -> {
                        Database.createUserWithPassword(ctx.login, ctx.password, ctx.email, ctx.phone, ctx.driverLicense);
                        return Boolean.TRUE;
                    });
                    if (createdB == null) return;

                    // mark authenticated and show menu automatically
                    MenuHandler.markAuthenticated(chatId, ctx.login);
                    SessionService.removeSession(chatId);
                    msgInteraction.sendMessage(chatId, "Account created and signed in as " + ctx.login + ". Welcome!");
                    MenuHandler.showMenu(msgInteraction, chatId, false);
                    break;

                default:
                    // unknown state: reset
                    SessionService.removeSession(chatId);
                    break;
            }
        } catch (Exception e) {
            // Do NOT remove the user's session here; preserve user state so they can retry.
            LOGGER.severe("Database error for chatId " + chatId + ": " + e.getClass().getName() + " - " + e.getMessage());
            // Differentiate transient/recoverable vs fatal where useful
            if (e instanceof SQLTransientException || e instanceof SQLRecoverableException) {
                msgInteraction.sendMessage(chatId, "Temporary database issue. Please try again in a moment (your progress was saved).");
            } else {
                msgInteraction.sendMessage(chatId, "Database error occurred. Please try again later (your progress was saved).");
            }
            // keep sessions.get(chatId) intact so user can continue without restarting
        }
    }

    /**
     * Handle callback queries from inline buttons.
     * Expects callbackData "back" for the Back button.
     * callbackId is forwarded to MessageInteraction to answer callback queries (if available).
     *
     * Behavior change:
     * - Back now moves the user one step backward in the flow (not to the very start).
     * - Back button is not shown while asking for login (AWAIT_LOGIN).
     */
    public static void handleCallbackQuery(MessageInteraction msgInteraction, long chatId, String callbackData, String callbackId) {
        // If MessageInteraction has answerCallbackQuery, call it to remove spinner
        msgInteraction.answerCallbackQuery(callbackId, null); // second param = optional text; MessageInteraction should implement this

        SessionService.Session ctx = SessionService.getSession(chatId);
        if (callbackData == null) return;

        if ("back".equals(callbackData)) {
            if (ctx == null || ctx.state == SessionService.State.AWAIT_LOGIN) {
                // cancel flow if no context or already at login step
                SessionService.removeSession(chatId);
                msgInteraction.sendMessage(chatId, "Operation cancelled. Use /start to begin again.");
                return;
            }

            // Move one step back according to current state using SessionService helpers
            switch (ctx.state) {
                case AWAIT_PASSWORD:
                    // go back to login entry (no Back button)
                    SessionService.resetToAwaitLogin(chatId);
                    msgInteraction.sendMessage(chatId, "Going back. Enter login:");
                    break;

                case AWAIT_SIGNUP_PASSWORD:
                    // from signup password, back -> re-enter login
                    SessionService.resetToAwaitLogin(chatId);
                    msgInteraction.sendMessage(chatId, "Going back. Enter login:");
                    break;

                case AWAIT_SIGNUP_EMAIL:
                    // back -> choose password (still after login check)
                    SessionService.resetToSignupPassword(chatId);
                    msgInteraction.sendMessageWithInlineKeyboard(
                            chatId,
                            "Going back. Please choose a password for the new account:",
                            Collections.singletonMap("Back", "back")
                    );
                    break;

                case AWAIT_SIGNUP_PHONE:
                    // back -> re-enter email
                    SessionService.resetToSignupEmail(chatId);
                    msgInteraction.sendMessageWithInlineKeyboard(
                            chatId,
                            "Going back. Please enter your email:",
                            Collections.singletonMap("Back", "back")
                    );
                    break;

                case AWAIT_SIGNUP_LICENSE:
                    // back -> re-enter phone
                    SessionService.resetToSignupPhone(chatId);
                    msgInteraction.sendMessageWithInlineKeyboard(
                            chatId,
                            "Going back. Please enter your phone number (format +79999999999):",
                            Collections.singletonMap("Back", "back")
                    );
                    break;

                default:
                    // fallback: cancel
                    SessionService.removeSession(chatId);
                    msgInteraction.sendMessage(chatId, "Operation cancelled. Use /start to begin again.");
                    break;
            }
        }
        // Add more callback handling if needed
    }

    // New: cancel an active flow for a chat (used when opening /menu)
    public static void cancelFlow(long chatId) {
        SessionService.cancelFlow(chatId);
    }
}
