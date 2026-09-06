
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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import net.usikkert.kouchat.event.ReceiverListener;
import net.usikkert.kouchat.misc.User;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Test of {@link MessageDeduplicator}.
 *
 * @author Christian Ihle
 */
public class MessageDeduplicatorTest {

    private static final String MAIN_CHAT_MESSAGE = "12345!MSG#Nick:[-15987646]hello";
    private static final String PRIVATE_CHAT_MESSAGE = "12345!PRIVMSG#Nick:54321!hello";
    private static final String OTHER_MESSAGE = "12345!MSG#Nick:[-15987646]hello again";
    private static final String IP_ADDRESS = "192.168.1.10";

    private MessageDeduplicator messageDeduplicator;

    private ReceiverListener mainChatListener;
    private ReceiverListener privateChatListener;

    @Before
    public void setUp() {
        messageDeduplicator = new MessageDeduplicator();

        mainChatListener = mock(ReceiverListener.class);
        privateChatListener = mock(ReceiverListener.class);

        messageDeduplicator.registerMainChatReceiverListener(mainChatListener);
        messageDeduplicator.registerPrivateChatReceiverListener(privateChatListener);
    }

    @Test
    public void shouldForwardAMessageThatWasNotSeenBefore() {
        messageDeduplicator.messageArrived(MAIN_CHAT_MESSAGE, IP_ADDRESS);

        verify(mainChatListener).messageArrived(MAIN_CHAT_MESSAGE, IP_ADDRESS);
        verifyNoMoreInteractions(mainChatListener);
    }

    @Test
    public void shouldDropTheSameMessageArrivingTwiceOverUdp() {
        // Happens in mesh mode: the message is unicast and multicast to the peer
        messageDeduplicator.messageArrived(MAIN_CHAT_MESSAGE, IP_ADDRESS);
        messageDeduplicator.messageArrived(MAIN_CHAT_MESSAGE, IP_ADDRESS);

        verify(mainChatListener, times(1)).messageArrived(MAIN_CHAT_MESSAGE, IP_ADDRESS);
        verifyNoMoreInteractions(mainChatListener);
    }

    @Test
    public void shouldDropTheUdpCopyWhenTheMessageAlsoArrivesOverTcp() {
        messageDeduplicator.messageArrived(MAIN_CHAT_MESSAGE, IP_ADDRESS);
        messageDeduplicator.messageArrived(MAIN_CHAT_MESSAGE, IP_ADDRESS, mock(User.class));

        verify(mainChatListener, times(1)).messageArrived(MAIN_CHAT_MESSAGE, IP_ADDRESS);
        verifyNoMoreInteractions(mainChatListener);
    }

    @Test
    public void shouldForwardTheTcpCopyWhenTheUdpCopyWasLost() {
        // Multicast can be filtered on some networks, leaving only the tcp copy
        messageDeduplicator.messageArrived(MAIN_CHAT_MESSAGE, IP_ADDRESS, mock(User.class));

        verify(mainChatListener, times(1)).messageArrived(MAIN_CHAT_MESSAGE, IP_ADDRESS);
        verifyNoMoreInteractions(mainChatListener);
    }

    @Test
    public void shouldDropTheSameTcpMessageArrivingTwice() {
        final User user = mock(User.class);

        messageDeduplicator.messageArrived(MAIN_CHAT_MESSAGE, IP_ADDRESS, user);
        messageDeduplicator.messageArrived(MAIN_CHAT_MESSAGE, IP_ADDRESS, user);

        verify(mainChatListener, times(1)).messageArrived(MAIN_CHAT_MESSAGE, IP_ADDRESS);
        verifyNoMoreInteractions(mainChatListener);
    }

    @Test
    public void shouldForwardTwoDifferentMessagesFromTheSameUser() {
        messageDeduplicator.messageArrived(MAIN_CHAT_MESSAGE, IP_ADDRESS);
        messageDeduplicator.messageArrived(OTHER_MESSAGE, IP_ADDRESS);

        verify(mainChatListener).messageArrived(MAIN_CHAT_MESSAGE, IP_ADDRESS);
        verify(mainChatListener).messageArrived(OTHER_MESSAGE, IP_ADDRESS);
        verifyNoMoreInteractions(mainChatListener);
    }

    @Test
    public void shouldRoutePrivateMessagesToThePrivateChatListener() {
        messageDeduplicator.messageArrived(PRIVATE_CHAT_MESSAGE, IP_ADDRESS);

        verify(privateChatListener).messageArrived(PRIVATE_CHAT_MESSAGE, IP_ADDRESS);
        verifyNoMoreInteractions(privateChatListener, mainChatListener);
    }

    @Test
    public void shouldDropTheSamePrivateMessageArrivingOverUdpAndTcp() {
        messageDeduplicator.messageArrived(PRIVATE_CHAT_MESSAGE, IP_ADDRESS);
        messageDeduplicator.messageArrived(PRIVATE_CHAT_MESSAGE, IP_ADDRESS, mock(User.class));

        verify(privateChatListener, times(1)).messageArrived(PRIVATE_CHAT_MESSAGE, IP_ADDRESS);
        verifyNoMoreInteractions(privateChatListener, mainChatListener);
    }

    @Test
    public void shouldNotConfuseMessagesFromDifferentUsers() {
        final String messageFromOtherUser = "99999!MSG#Nick:[-15987646]hello";

        messageDeduplicator.messageArrived(MAIN_CHAT_MESSAGE, IP_ADDRESS);
        messageDeduplicator.messageArrived(messageFromOtherUser, IP_ADDRESS);

        final ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mainChatListener, times(2)).messageArrived(messageCaptor.capture(), eq(IP_ADDRESS));

        assertEquals(2, messageCaptor.getAllValues().size());
    }
}
