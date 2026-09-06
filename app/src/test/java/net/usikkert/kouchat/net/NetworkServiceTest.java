
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

package net.usikkert.kouchat.net;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import net.usikkert.kouchat.junit.ExpectedException;
import net.usikkert.kouchat.misc.Controller;
import net.usikkert.kouchat.misc.ErrorHandler;
import net.usikkert.kouchat.misc.User;
import net.usikkert.kouchat.misc.UserList;
import net.usikkert.kouchat.net.tcp.TCPNetworkService;
import net.usikkert.kouchat.settings.NetworkMode;
import net.usikkert.kouchat.settings.Settings;
import net.usikkert.kouchat.util.TestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test of {@link NetworkService}.
 *
 * @author Christian Ihle
 */
@SuppressWarnings("HardCodedStringLiteral")
public class NetworkServiceTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Settings settings;
    private ErrorHandler errorHandler;
    private Controller controller;

    @Before
    public void setUp() {
        settings = mock(Settings.class);
        errorHandler = mock(ErrorHandler.class);
        controller = mock(Controller.class);

        when(settings.getMe()).thenReturn(mock(User.class));
        when(controller.getUserList()).thenReturn(mock(UserList.class));
    }

    @Test
    public void constructorShouldThrowExceptionIfControllerIsNull() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Controller can not be null");

        new NetworkService(null, settings, errorHandler);
    }

    @Test
    public void constructorShouldThrowExceptionIfSettingsIsNull() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Settings can not be null");

        new NetworkService(controller, null, errorHandler);
    }

    @Test
    public void constructorShouldThrowExceptionIfErrorHandlerIsNull() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Error handler can not be null");

        new NetworkService(controller, settings, null);
    }

    @Test
    public void networkServiceShouldLoadPrivateChatObjectsWhenEnabled() {
        when(settings.isNoPrivateChat()).thenReturn(false);

        final NetworkService networkService = new NetworkService(controller, settings, errorHandler);

        assertNotNull(TestUtils.getFieldValue(networkService, UDPReceiver.class, "udpReceiver"));
        assertNotNull(TestUtils.getFieldValue(networkService, UDPSender.class, "udpSender"));
    }

    @Test
    public void networkServiceShouldNotLoadPrivateChatObjectsWhenDisabled() {
        when(settings.isNoPrivateChat()).thenReturn(true);

        final NetworkService networkService = new NetworkService(controller, settings, errorHandler);

        assertNull(TestUtils.getFieldValue(networkService, UDPReceiver.class, "udpReceiver"));
        assertNull(TestUtils.getFieldValue(networkService, UDPSender.class, "udpSender"));
    }

    @Test
    public void registerPrivateChatReceiverListenerShouldNotFailWhenPrivateChatDisabled() {
        when(settings.isNoPrivateChat()).thenReturn(true);

        final NetworkService networkService = new NetworkService(controller, settings, errorHandler);

        networkService.registerPrivateChatReceiverListener(null);
    }

    @Test
    public void beforeNetworkCameUpShouldDoNothing() {
        final NetworkService networkService = new NetworkService(controller, settings, errorHandler);

        networkService.beforeNetworkCameUp();
    }

    @Test
    public void networkCameUpShouldNotFailWhenPrivateChatDisabled() {
        when(settings.isNoPrivateChat()).thenReturn(true);

        final NetworkService networkService = new NetworkService(controller, settings, errorHandler);

        networkService.networkCameUp(false);
    }

    @Test
    public void networkWentDownShouldNotFailWhenPrivateChatDisabled() {
        when(settings.isNoPrivateChat()).thenReturn(true);

        final NetworkService networkService = new NetworkService(controller, settings, errorHandler);

        networkService.networkWentDown(false);
    }

    @Test
    public void sendMessageToUserShouldNotSendMessageWhenPrivateChatDisabled() {
        when(settings.isNoPrivateChat()).thenReturn(true);
        final User user = new User("User", 111);

        final NetworkService networkService = new NetworkService(controller, settings, errorHandler);

        final boolean messageSent = networkService.sendMessageToUser("Nothing", user);
        assertFalse(messageSent);
    }

    @Test
    public void sendMessageToAllUsersShouldUseBroadcastInsteadOfMulticastInBroadcastMode() {
        when(settings.isNoPrivateChat()).thenReturn(true);
        when(settings.getNetworkMode()).thenReturn(NetworkMode.BROADCAST);

        final NetworkService networkService = new NetworkService(controller, settings, errorHandler);
        final MessageSender messageSender = TestUtils.setFieldValueWithMock(networkService, "messageSender", MessageSender.class);
        final TCPNetworkService tcpNetworkService =
                TestUtils.setFieldValueWithMock(networkService, "tcpNetworkService", TCPNetworkService.class);

        when(messageSender.sendBroadcast("message")).thenReturn(true);

        assertTrue(networkService.sendMessageToAllUsers("message"));

        verify(messageSender).sendBroadcast("message");
        verify(messageSender, never()).send("message");
        verify(tcpNetworkService).sendMessageToAll("message");
    }

    @Test
    public void sendMessageToAllUsersShouldUseMulticastOutsideBroadcastMode() {
        when(settings.isNoPrivateChat()).thenReturn(true);
        when(settings.getNetworkMode()).thenReturn(NetworkMode.MULTICAST);

        final NetworkService networkService = new NetworkService(controller, settings, errorHandler);
        final MessageSender messageSender = TestUtils.setFieldValueWithMock(networkService, "messageSender", MessageSender.class);
        final TCPNetworkService tcpNetworkService =
                TestUtils.setFieldValueWithMock(networkService, "tcpNetworkService", TCPNetworkService.class);

        when(messageSender.send("message")).thenReturn(true);

        assertTrue(networkService.sendMessageToAllUsers("message"));

        verify(messageSender).send("message");
        verify(messageSender, never()).sendBroadcast(anyString());
        verify(tcpNetworkService).sendMessageToAll("message");
    }

    @Test
    public void sendMessageToAllUsersShouldUseTcpOnlyForMessagesTooLargeForUdp() {
        when(settings.isNoPrivateChat()).thenReturn(true);
        when(settings.getNetworkMode()).thenReturn(NetworkMode.MULTICAST);

        final NetworkService networkService = new NetworkService(controller, settings, errorHandler);
        final MessageSender messageSender = TestUtils.setFieldValueWithMock(networkService, "messageSender", MessageSender.class);
        final TCPNetworkService tcpNetworkService =
                TestUtils.setFieldValueWithMock(networkService, "tcpNetworkService", TCPNetworkService.class);
        final ConnectionWorker connectionWorker =
                TestUtils.setFieldValueWithMock(networkService, "connectionWorker", ConnectionWorker.class);

        when(connectionWorker.isNetworkUp()).thenReturn(true);

        // A message larger than the udp limit of 450 bytes
        final StringBuilder sb = new StringBuilder(451);

        for (int i = 0; i < 451; i++) {
            sb.append('a');
        }

        final String longMessage = sb.toString();

        assertTrue(networkService.sendMessageToAllUsers(longMessage));

        verify(tcpNetworkService).sendMessageToAll(longMessage);
        verify(messageSender, never()).send(anyString());
        verify(messageSender, never()).sendBroadcast(anyString());
    }

    @Test
    public void sendMessageToUserShouldUseTcpOnlyForMessagesTooLargeForUdp() {
        when(settings.isNoPrivateChat()).thenReturn(false);
        final User user = new User("User", 111);

        final NetworkService networkService = new NetworkService(controller, settings, errorHandler);
        final UDPSender udpSender = TestUtils.setFieldValueWithMock(networkService, "udpSender", UDPSender.class);
        final TCPNetworkService tcpNetworkService =
                TestUtils.setFieldValueWithMock(networkService, "tcpNetworkService", TCPNetworkService.class);
        final ConnectionWorker connectionWorker =
                TestUtils.setFieldValueWithMock(networkService, "connectionWorker", ConnectionWorker.class);

        when(connectionWorker.isNetworkUp()).thenReturn(true);

        // A message larger than the udp limit of 450 bytes
        final StringBuilder sb = new StringBuilder(451);

        for (int i = 0; i < 451; i++) {
            sb.append('a');
        }

        final String longMessage = sb.toString();

        assertTrue(networkService.sendMessageToUser(longMessage, user));

        verify(tcpNetworkService).sendMessageToUser(longMessage, user);
        verify(udpSender, never()).send(anyString(), anyString(), anyInt());
    }
}
