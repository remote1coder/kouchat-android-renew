
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
 *   License along with KouChat.                                            *
 *   If not, see <http://www.gnu.org/licenses/>.                            *
 ***************************************************************************/

package net.usikkert.kouchat.net;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.usikkert.kouchat.event.ReceiverListener;
import net.usikkert.kouchat.misc.User;
import net.usikkert.kouchat.net.tcp.TCPReceiverListener;
import net.usikkert.kouchat.util.Logger;

import org.jetbrains.annotations.Nullable;

/**
 * Drops duplicate copies of the same message.
 *
 * <p>Every outgoing message can arrive several times at the receiver: over udp
 * multicast, over udp unicast (mesh peers), and over tcp. Instead of trying to
 * pick one transport per user (which breaks when the tcp connection state is
 * stale), each message is hashed, and a hash seen within
 * {@link #DEDUPLICATION_WINDOW_MILLIS} is considered a duplicate and dropped.
 * The first copy to arrive is forwarded, no matter which transport it used.</p>
 *
 * <p>The window must stay well below the idle message interval (15 seconds),
 * or repeated periodic messages would be dropped.</p>
 *
 * @author Christian Ihle
 */
public class MessageDeduplicator implements ReceiverListener, TCPReceiverListener {

    private static final Logger LOG = Logger.getLogger(MessageDeduplicator.class);

    /** How long a message hash is remembered, and used to drop duplicates. */
    private static final long DEDUPLICATION_WINDOW_MILLIS = 3000;

    /** Upper bound on remembered hashes, to keep memory usage predictable. */
    private static final int MAX_CACHE_SIZE = 1000;

    private final Pattern privateMessagePattern;

    /** Hashes of recently seen messages, mapped to the time they arrived. */
    private final Map<Integer, Long> recentMessageHashes = new ConcurrentHashMap<>();

    @Nullable
    private ReceiverListener mainChatListener;

    @Nullable
    private ReceiverListener privateChatListener;

    /**
     * Constructor.
     */
    public MessageDeduplicator() {
        this.privateMessagePattern = Pattern.compile("^(\\d+)!(PRIVMSG)#.+");
    }

    public void registerMainChatReceiverListener(final ReceiverListener theListener) {
        this.mainChatListener = theListener;
    }

    public void registerPrivateChatReceiverListener(final ReceiverListener theListener) {
        this.privateChatListener = theListener;
    }

    /**
     * A message arrived over udp (multicast or unicast). Dropped if an
     * identical message was already seen recently.
     *
     * {@inheritDoc}
     */
    @Override
    public void messageArrived(final String message, final String ipAddress) {
        if (isDuplicate(message)) {
            LOG.fine("Dropped duplicate udp message: " + message);
            return;
        }

        forwardMessageToListener(message, ipAddress);
    }

    /**
     * A message arrived over tcp. Dropped if an identical message was already
     * seen recently - this is the common case, since every message that fits
     * in a udp packet is sent over both udp and tcp.
     *
     * {@inheritDoc}
     */
    @Override
    public void messageArrived(final String message, final String ipAddress, final User user) {
        if (isDuplicate(message)) {
            LOG.fine("Dropped duplicate tcp message: " + message);
            return;
        }

        forwardMessageToListener(message, ipAddress);
    }

    /**
     * Checks if an identical message was seen within the deduplication window,
     * and remembers the message either way.
     *
     * @param message The message that arrived.
     * @return True if the message is a duplicate that should be dropped.
     */
    private boolean isDuplicate(final String message) {
        final long now = System.currentTimeMillis();
        final Integer messageHash = message.hashCode();

        if (recentMessageHashes.size() > MAX_CACHE_SIZE) {
            cleanUpCache(now);
        }

        final Long previousTime = recentMessageHashes.put(messageHash, now);

        return previousTime != null && now - previousTime <= DEDUPLICATION_WINDOW_MILLIS;
    }

    /**
     * Removes hashes older than the deduplication window. If the cache is
     * still too large afterwards (a burst of unique messages), it is cleared -
     * the window is short, so the memory is better spent elsewhere.
     *
     * @param now The current time in milliseconds.
     */
    private void cleanUpCache(final long now) {
        for (final Map.Entry<Integer, Long> entry : recentMessageHashes.entrySet()) {
            if (now - entry.getValue() > DEDUPLICATION_WINDOW_MILLIS) {
                recentMessageHashes.remove(entry.getKey(), entry.getValue());
            }
        }

        if (recentMessageHashes.size() > MAX_CACHE_SIZE) {
            LOG.warning("Cleared " + recentMessageHashes.size() + " message hashes from the deduplication cache");
            recentMessageHashes.clear();
        }
    }

    /**
     * Forwards the message to the private chat listener if it is a private
     * message, or to the main chat listener otherwise.
     *
     * @param message The message to forward.
     * @param ipAddress The ip address the message came from.
     */
    private void forwardMessageToListener(final String message, final String ipAddress) {
        final Matcher privateMessageMatcher = privateMessagePattern.matcher(message);

        if (privateMessageMatcher.matches()) {
            if (privateChatListener != null) {
                privateChatListener.messageArrived(message, ipAddress);
            }
        }

        else {
            if (mainChatListener != null) {
                mainChatListener.messageArrived(message, ipAddress);
            }
        }
    }
}
