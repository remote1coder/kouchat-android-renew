
/***************************************************************************
 *   Copyright 2006-2019 by Christian Ihle                                 *
 *   contact@kouchat.net                                                   *
 *                                                                         *
 *   This file is part of KouChat.                                         *
 *                                                                         *
 *   KouChat is free software; you can redistribute it and/or modify       *
 *   it under the terms of the GNU Lesser General Public License as        *
 *   published by the Free Software Foundation, either version 3 of        *
 *   the License, or (at your option) any later version.                   *
 *                                                                         *
 *   KouChat is distributed in the hope that it will be useful,            *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU      *
 *   Lesser General Public License for more details.                       *
 *                                                                         *
 *   You should have received a copy of the GNU Lesser General Public      *
 *   License along with KouChat.                                           *
 *   If not, see <http://www.gnu.org/licenses/>.                           *
 ***************************************************************************/

package net.usikkert.kouchat.misc;

import net.usikkert.kouchat.settings.Settings;
import net.usikkert.kouchat.ui.ChatWindow;
import net.usikkert.kouchat.ui.UserInterface;
import net.usikkert.kouchat.util.Tools;
import net.usikkert.kouchat.util.Validate;

/**
 * Formats different kind of messages for display in a chat window,
 * and logs them to file.
 *
 * @author Christian Ihle
 */
public class MessageController {

    private final Settings settings;
    private final User me;
    private final ChatWindow chat;
    private final ChatLogger cLog;
    private final UserInterface ui;

    /**
     * Initializes the logger and loads settings.
     *
     * @param chat The user interface object to write the formatted messages to.
     * @param ui The user interface.
     * @param settings The settings to use.
     * @param errorHandler The error handler to use.
     */
    public MessageController(final ChatWindow chat, final UserInterface ui, final Settings settings,
                             final ErrorHandler errorHandler) {
        Validate.notNull(chat, "ChatWindow can not be null");
        Validate.notNull(ui, "UserInterface can not be null");
        Validate.notNull(settings, "Settings can not be null");
        Validate.notNull(errorHandler, "Error handler can not be null");

        this.chat = chat;
        this.ui = ui;
        this.settings = settings;

        me = settings.getMe();
        cLog = new ChatLogger(settings, errorHandler);
    }

    /**
     * This is a message from another user.
     * The result will look like this:<br />
     * [hour:min:sec] &lt;user&gt; message<br />
     * The message will be shown in the color spesified.
     *
     * @param user The user who wrote the message.
     * @param message The message the user wrote.
     * @param color The color the user chose for the message.
     */
    public void showUserMessage(final String user, final String message, final int color) {
        final String msg = Tools.getTime() + " <" + user + ">: " + message;
        chat.appendToChat(msg, color);
        cLog.append(msg);
    }

    /**
     * This is an information message from the system. The result
     * will look like this:<br />
     * [hour:min:sec] *** message<br />
     * The message will be shown in the color spesified in the settings.
     *
     * @param message The system message to show.
     */
    public void showSystemMessage(final String message) {
        final String msg = Tools.getTime() + " *** " + message;
        chat.appendToChat(msg, settings.getSysColor());
        cLog.append(msg);
    }

    /**
     * This is a normal message written by the application user,
     * meant to be seen by all other users. It will look like this:<br />
     * [hour:min:sec] &lt;nick&gt; message<br />
     * The message will be shown in the color spesified in the settings.
     *
     * @param message The message written by the application user.
     */
    public void showOwnMessage(final String message) {
        final String msg = Tools.getTime() + " <" + me.getNick() + ">: " + message;
        chat.appendToChat(msg, settings.getOwnColor());
        cLog.append(msg);
    }

    /**
     * Shows an image in the chat, preceded by a label with the time and the
     * nick of the sender.
     *
     * <p>If a private chat window is open with the given chat peer, the image
     * is shown there. Otherwise it is shown in the main chat.</p>
     *
     * @param chatPeer The user the image exchange is with. Used to decide
     *                 whether to route the image to a private chat window.
     * @param senderNick The nick of the sender of the image.
     * @param imageBytes The raw bytes of the image to show.
     */
    public void showImageMessage(final User chatPeer, final String senderNick, final byte[] imageBytes) {
        final String label = Tools.getTime() + " <" + senderNick + ">: ";

        if (chatPeer.getPrivchat() != null) {
            chatPeer.getPrivchat().appendImage(imageBytes, label, settings.getSysColor());
            final ChatLogger privateChatLogger = chatPeer.getPrivateChatLogger();

            if (privateChatLogger != null) {
                privateChatLogger.append(label + "[image]");
            }
        } else {
            chat.appendImage(imageBytes, label, settings.getSysColor());
            cLog.append(label + "[image]");
        }
    }

    /**
     * This is a private message from another user.
     * The result will look like this:<br />
     * [hour:min:sec] &lt;user&gt; privmsg<br />
     * The message will be shown in the color spesified.
     *
     * @param user The user who wrote the message.
     * @param privmsg The message the user wrote.
     * @param color The color the user chose for the message.
     */
    public void showPrivateUserMessage(final User user, final String privmsg, final int color) {
        if (user.getPrivchat() == null) {
            ui.createPrivChat(user);
        }

        final String msg = Tools.getTime() + " <" + user + ">: " + privmsg;
        user.getPrivchat().appendToPrivateChat(msg, color);
        final ChatLogger privateChatLogger = user.getPrivateChatLogger();

        if (privateChatLogger != null) {
            privateChatLogger.append(msg);
        }
    }

    /**
     * This is a normal private message written by the application user,
     * meant to be seen by a single user. It will look like this:<br />
     * [hour:min:sec] &lt;nick&gt; privmsg<br />
     * The message will be shown in the color spesified in the settings.
     *
     * @param user The user which the message was meant for.
     * @param privmsg The message written by the application user.
     */
    public void showPrivateOwnMessage(final User user, final String privmsg) {
        if (user.getPrivchat() == null) {
            ui.createPrivChat(user);
        }

        final String msg = Tools.getTime() + " <" + me.getNick() + ">: " + privmsg;
        user.getPrivchat().appendToPrivateChat(msg, settings.getOwnColor());
        final ChatLogger privateChatLogger = user.getPrivateChatLogger();

        if (privateChatLogger != null) {
            privateChatLogger.append(msg);
        }
    }

    /**
     * This is an information message from the system. The result
     * will look like this:<br />
     * [hour:min:sec] *** privmsg<br />
     * The message will be shown in the color spesified in the settings.
     *
     * @param user The user this system message applies to.
     * @param privmsg The system message to show.
     */
    public void showPrivateSystemMessage(final User user, final String privmsg) {
        final String msg = Tools.getTime() + " *** " + privmsg;
        final net.usikkert.kouchat.ui.PrivateChatWindow privchat = user.getPrivchat();

        if (privchat != null) {
            privchat.appendToPrivateChat(msg, settings.getSysColor());

            final ChatLogger privateChatLogger = user.getPrivateChatLogger();

            if (privateChatLogger != null) {
                privateChatLogger.append(msg);
            }
        }

        else {
            // No private chat window is open (e.g. the handshake completed via auto-mesh in
            // P2P mode). Fall back to the main chat so the status message is not lost.
            chat.appendToChat(msg, settings.getSysColor());
            cLog.append(msg);
        }
    }

    /**
     * Cleanup that must be done when shutting down. Closes the chat logger.
     */
    public void shutdown() {
        cLog.close();
    }
}
