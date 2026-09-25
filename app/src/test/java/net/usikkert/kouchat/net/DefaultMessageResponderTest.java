
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

import java.io.File;

import net.usikkert.kouchat.crypto.CryptoManager;
import net.usikkert.kouchat.junit.ExpectedException;
import net.usikkert.kouchat.message.CoreMessages;
import net.usikkert.kouchat.misc.ChatState;
import net.usikkert.kouchat.misc.Controller;
import net.usikkert.kouchat.misc.MessageController;
import net.usikkert.kouchat.misc.SortedUserList;
import net.usikkert.kouchat.misc.Topic;
import net.usikkert.kouchat.misc.User;
import net.usikkert.kouchat.misc.UserList;
import net.usikkert.kouchat.misc.WaitingList;
import net.usikkert.kouchat.settings.AutoAcceptFiles;
import net.usikkert.kouchat.settings.Settings;
import net.usikkert.kouchat.ui.UserInterface;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Test of {@link DefaultMessageResponder}.
 *
 * @author Christian Ihle
 */
@SuppressWarnings("HardCodedStringLiteral")
public class DefaultMessageResponderTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private DefaultMessageResponder responder;

    private Controller controller;
    private UserInterface userInterface;
    private Settings settings;
    private MessageController messageController;
    private UserList userList;
    private ChatState chatState;
    private CoreMessages coreMessages;
    private WaitingList waitingList;

    private User user;
    private User me;

    @Before
    public void setUp() {
        controller = mock(Controller.class);
        userInterface = mock(UserInterface.class);
        settings = new Settings();
        messageController = mock(MessageController.class);
        userList = new SortedUserList();
        chatState = mock(ChatState.class);
        coreMessages = new CoreMessages();
        waitingList = mock(WaitingList.class);

        when(userInterface.getMessageController()).thenReturn(messageController);
        when(controller.getUserList()).thenReturn(userList);
        when(controller.getChatState()).thenReturn(chatState);
        when(controller.getWaitingList()).thenReturn(waitingList);

        responder = new DefaultMessageResponder(controller, userInterface, settings, coreMessages);

        user = new User("Tester", 100);
        user.setIpAddress("192.168.10.123");

        me = settings.getMe();
        me.setNick("Me");

        //  Get rid of constructor operations from list of verifications
        verify(controller).getTransferList();
        verify(controller).getWaitingList();
        verify(controller).getChatState();
        verify(userInterface).getMessageController();
    }

    @Test
    public void constructorShouldThrowExceptionIfControllerIsNull() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Controller can not be null");

        new DefaultMessageResponder(null, userInterface, settings, coreMessages);
    }

    @Test
    public void constructorShouldThrowExceptionIfUserInterfaceIsNull() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("UserInterface can not be null");

        new DefaultMessageResponder(controller, null, settings, coreMessages);
    }

    @Test
    public void constructorShouldThrowExceptionIfSettingsIsNull() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Settings can not be null");

        new DefaultMessageResponder(controller, userInterface, null, coreMessages);
    }

    @Test
    public void constructorShouldThrowExceptionIfCoreMessagesIsNull() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Core messages can not be null");

        new DefaultMessageResponder(controller, userInterface, settings, null);
    }

    @Test
    public void messageArrivedShouldShowMessageAndNotifyUserInterfaceAndSetNewMessageFlagWhenVisibleButNotFocused() {
        setUpExistingUser();

        when(userInterface.isVisible()).thenReturn(true);
        when(userInterface.isFocused()).thenReturn(false);

        responder.messageArrived(100, "msg", 200);

        verify(messageController).showUserMessage("Tester", "msg", 200);
        verify(userInterface).notifyMessageArrived(user, "msg");
        assertTrue(me.isNewMsg());
    }

    @Test
    public void messageArrivedShouldShowMessageAndNotifyUserInterfaceButNotSetNewMessageFlagWhenVisibleAndFocused() {
        setUpExistingUser();

        when(userInterface.isVisible()).thenReturn(true);
        when(userInterface.isFocused()).thenReturn(true);

        responder.messageArrived(100, "msg2", 200);

        verify(messageController).showUserMessage("Tester", "msg2", 200);
        verify(userInterface).notifyMessageArrived(user, "msg2");
        assertFalse(me.isNewMsg());
    }

    @Test
    public void messageArrivedShouldShowMessageAndNotifyUserInterfaceButNotSetNewMessageFlagWhenNotVisible() {
        setUpExistingUser();

        when(userInterface.isVisible()).thenReturn(false);
        when(userInterface.isFocused()).thenReturn(false); // Can't be focused if not visible

        responder.messageArrived(100, "msg3", 200);

        verify(messageController).showUserMessage("Tester", "msg3", 200);
        verify(userInterface).notifyMessageArrived(user, "msg3");
        assertFalse(me.isNewMsg());
    }

    @Test
    public void messageArrivedShouldDoNothingIfUserIsAway() {
        setUpExistingUser();
        user.setAway(true);

        responder.messageArrived(100, "msg", 200);

        verifyNoMoreInteractions(messageController, userInterface);
    }

    @Test
    public void messageArrivedShouldDoNothingIfUserIsUnknown() {
        setUpUnknownUser();

        responder.messageArrived(100, "msg", 200);

        verifyNoMoreInteractions(messageController, userInterface);
    }

    @Test
    public void userLogOffShouldDoNothingIfUserIsUnknown() {
        setUpUnknownUser();

        responder.userLogOff(100);

        verifyNoInteractions(messageController);
        verify(controller, never()).removeUser(any(User.class), anyString());
    }

    @Test
    public void userLogOffShouldRemoveUserAndShowSystemMessage() {
        setUpExistingUser();

        responder.userLogOff(100);

        verify(messageController).showSystemMessage("Tester logged off");
        verify(controller).removeUser(user, "Tester logged off");
    }

    @Test
    public void userLogOnShouldAddUserToListAndShowSystemMessage() {
        responder.userLogOn(user);

        assertEquals(0, userList.indexOf(user));
        verify(messageController).showSystemMessage("Tester logged on from 192.168.10.123");
        verify(waitingList).removeWaitingUser(user.getCode());
    }

    @Test
    public void userLogOnShouldResetNickAndSendNickCrashMessageFirstIfUserHasMyNick() {
        user.setNick("me");

        responder.userLogOn(user);

        assertEquals(0, userList.indexOf(user));
        verify(messageController).showSystemMessage("100 logged on from 192.168.10.123");
        verify(controller).sendNickCrashMessage("me");
    }

    @Test
    public void userLogOnShouldResetNickFirstIfUserHasNickNameInUseBySomeoneElse() {
        when(controller.isNickInUse("Tester")).thenReturn(true);

        responder.userLogOn(user);

        assertEquals(0, userList.indexOf(user));
        verify(messageController).showSystemMessage("100 logged on from 192.168.10.123");
        verify(controller, never()).sendNickCrashMessage(anyString());
    }

    @Test
    public void userLogOnShouldResetNickFirstIfUserHasInvalidNickName() {
        user.setNick("No!");

        responder.userLogOn(user);

        assertEquals(0, userList.indexOf(user));
        verify(messageController).showSystemMessage("100 logged on from 192.168.10.123");
        verify(controller, never()).sendNickCrashMessage(anyString());
    }

    @Test
    public void topicChangedShouldDoNothingWhenTimeIsZero() {
        responder.topicChanged(300, "Nothing", "Harry", 0);

        verifyNoMoreInteractions(messageController, userInterface);
    }

    @Test
    public void topicChangedShouldDoNothingWhenNickNameIsEmpty() {
        responder.topicChanged(300, "Nothing", "", 1000);

        verifyNoMoreInteractions(messageController, userInterface);
    }

    @Test
    public void topicChangedShouldUpdateTopicAndShowTopicIsMessageWhenNotDoneWithLogonYet() {
        final Topic topic = new Topic();
        when(controller.getTopic()).thenReturn(topic);
        when(chatState.isLogonCompleted()).thenReturn(false);
        final long time = new DateTime().withDate(2013, 8, 22).withTime(13, 45, 5, 0).toDate().getTime();

        responder.topicChanged(300, "See ya!", "Harry", time);

        verify(messageController).showSystemMessage("Topic is: See ya! (set by Harry at 13:45:05, 22. Aug. 13)");
        verify(userInterface).showTopic();
        verifyTopic(topic, "See ya!", "Harry", time);
    }

    @Test
    public void topicChangedShouldUpdateTopicAndShowTopicChangedMessageWhenDoneWithLogon() {
        final Topic topic = new Topic();
        when(controller.getTopic()).thenReturn(topic);
        when(chatState.isLogonCompleted()).thenReturn(true);
        final long time = System.currentTimeMillis();

        responder.topicChanged(300, "See ya!", "Harry", time);

        verify(messageController).showSystemMessage("Harry changed the topic to: See ya!");
        verify(userInterface).showTopic();
        verifyTopic(topic, "See ya!", "Harry", time);
    }

    @Test
    public void topicChangedShouldDoNothingIfNewTopicIsTheSameAsTheOldTopic() {
        final Topic topic = new Topic("Old topic", "Niles", 1000);
        when(controller.getTopic()).thenReturn(topic);

        responder.topicChanged(300, "Old topic", "Niles", 2000); // Newer timestamp, same topic

        verifyNoMoreInteractions(messageController, userInterface);
        verifyTopic(topic, "Old topic", "Niles", 1000);
    }

    @Test
    public void topicChangedShouldDoNothingIfNewTopicHasOlderTimestamp() {
        final Topic topic = new Topic("Old topic", "Niles", 2000);
        when(controller.getTopic()).thenReturn(topic);

        responder.topicChanged(300, "Older topic", "Niles", 1000); // Older timestamp, must be old topic

        verifyNoMoreInteractions(messageController, userInterface);
        verifyTopic(topic, "Old topic", "Niles", 2000);
    }

    @Test
    public void topicChangedShouldUpdateTopicAndShowTopicRemovedMessageWhenTopicIsNull() {
        final Topic topic = new Topic();
        when(controller.getTopic()).thenReturn(topic);
        when(chatState.isLogonCompleted()).thenReturn(true);
        final long time = System.currentTimeMillis();

        responder.topicChanged(300, null, "Harry", time);

        verify(messageController).showSystemMessage("Harry removed the topic");
        verify(userInterface).showTopic();
        verifyTopic(topic, "", "", time);
    }

    @Test
    public void topicChangedShouldDoNothingWhenTopicIsNullButNotDoneWithLogonYet() {
        final Topic topic = new Topic();
        when(controller.getTopic()).thenReturn(topic);
        when(chatState.isLogonCompleted()).thenReturn(false);

        responder.topicChanged(300, null, "Harry", System.currentTimeMillis());

        verifyNoMoreInteractions(messageController, userInterface);
        verifyTopic(topic, "", "", 0);
    }

    @Test
    public void topicChangedShouldDoNothingWhenTopicIsNullButTimeIsOlderThanCurrentTopic() {
        final Topic topic = new Topic("Current topic", "Harry", 2000);
        when(controller.getTopic()).thenReturn(topic);
        when(chatState.isLogonCompleted()).thenReturn(true);

        responder.topicChanged(300, null, "Harry", 1000);

        verifyNoMoreInteractions(messageController, userInterface);
        verifyTopic(topic, "Current topic", "Harry", 2000);
    }

    @Test
    public void clientInfoShouldFillDetailsOnKnownUser() {
        setUpExistingUser();

        responder.clientInfo(100, "swing", 150, "linux",
                             4000, 5000);

        final long logonTime = System.currentTimeMillis() - 150;
        // Allow some slack to avoid flaky test
        assertTrue(user.getLogonTime() <= logonTime && user.getLogonTime() > logonTime - 10);

        assertEquals("swing", user.getClient());
        assertEquals("linux", user.getOperatingSystem());
        assertEquals(4000, user.getPrivateChatPort());
        assertEquals(5000, user.getTcpChatPort());
    }

    @Test
    public void clientInfoShouldDoNothingOnUnknownUser() {
        setUpUnknownUser();

        responder.clientInfo(100, "swing", 150, "linux",
                             4000, 5000);

        assertEquals(0, user.getLogonTime());
        assertEquals("<unknown>", user.getClient());
        assertEquals("<unknown>", user.getOperatingSystem());
        assertEquals(0, user.getPrivateChatPort());
        assertEquals(0, user.getTcpChatPort());
    }

    private void verifyTopic(final Topic topic, final String expectedTopic, final String expectedNick,
                             final long expectedTime) {
        assertEquals(expectedTopic, topic.getTopic());
        assertEquals(expectedNick, topic.getNick());
        assertEquals(expectedTime, topic.getTime());
    }

    private void setUpExistingUser() {
        when(controller.isNewUser(100)).thenReturn(false);
        when(controller.getUser(100)).thenReturn(user);
    }

    private void setUpUnknownUser() {
        when(controller.isNewUser(100)).thenReturn(true);
        when(controller.getUser(100)).thenReturn(null);
    }

    @Test
    public void fileSendShouldAutoAcceptImageFromUserWithActiveEncryptedChannel() throws Exception {
        final TransferList transferList = setUpFileSend(AutoAcceptFiles.IMAGES, true);
        final FileReceiver fileReceiver = mockFileReceiver(transferList, "photo.png");

        responder.fileSend(100, 1024, "photo.png", "Tester", 42);

        verify(userInterface, never()).askFileSave(fileReceiver);
        verify(fileReceiver).accept();
        verify(userInterface).showTransfer(fileReceiver);
        verify(controller).sendFileAccept(user, 40756, 42, "photo.png");
        verify(fileReceiver).transfer();
        verify(transferList).removeFileReceiver(fileReceiver);
    }

    @Test
    public void fileSendShouldNotAutoAcceptWhenEncryptedChannelIsInactive() throws Exception {
        final TransferList transferList = setUpFileSend(AutoAcceptFiles.IMAGES, false);
        final FileReceiver fileReceiver = mockFileReceiver(transferList, "photo.png");

        when(userInterface.askFileSave(fileReceiver)).thenReturn(false);

        responder.fileSend(100, 1024, "photo.png", "Tester", 42);

        verify(userInterface).askFileSave(fileReceiver);
        verify(fileReceiver, never()).accept();
        verify(controller).sendFileAbort(user, 42, "photo.png");
    }

    @Test
    public void fileSendShouldNotAutoAcceptWhenScopeIsOff() throws Exception {
        final TransferList transferList = setUpFileSend(AutoAcceptFiles.OFF, true);
        final FileReceiver fileReceiver = mockFileReceiver(transferList, "photo.png");

        when(userInterface.askFileSave(fileReceiver)).thenReturn(false);

        responder.fileSend(100, 1024, "photo.png", "Tester", 42);

        verify(userInterface).askFileSave(fileReceiver);
        verify(fileReceiver, never()).accept();
    }

    @Test
    public void fileSendShouldNotAutoAcceptNonImageWhenScopeIsImages() throws Exception {
        final TransferList transferList = setUpFileSend(AutoAcceptFiles.IMAGES, true);
        final FileReceiver fileReceiver = mockFileReceiver(transferList, "notes.txt");

        when(userInterface.askFileSave(fileReceiver)).thenReturn(false);

        responder.fileSend(100, 1024, "notes.txt", "Tester", 42);

        verify(userInterface).askFileSave(fileReceiver);
        verify(fileReceiver, never()).accept();
    }

    @Test
    public void fileSendShouldAutoAcceptAnyFileWhenScopeIsAll() throws Exception {
        final TransferList transferList = setUpFileSend(AutoAcceptFiles.ALL, true);
        final FileReceiver fileReceiver = mockFileReceiver(transferList, "notes.txt");

        responder.fileSend(100, 1024, "notes.txt", "Tester", 42);

        verify(userInterface, never()).askFileSave(fileReceiver);
        verify(fileReceiver).accept();
        verify(controller).sendFileAccept(user, 40756, 42, "notes.txt");
    }

    @Test
    public void fileSendShouldRejectAutomaticallyWhenUserDoesNotAnswerInTime() {
        final TransferList transferList = setUpFileSend(AutoAcceptFiles.IMAGES, false);
        final FileReceiver fileReceiver = realFileReceiver(transferList, "photo.png");

        responder.setFileAnswerTimeout(100);

        // Simulate a user interface that blocks for longer than the timeout
        when(userInterface.askFileSave(fileReceiver)).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(final InvocationOnMock invocation) throws Throwable {
                Thread.sleep(400);
                return false;
            }
        });

        responder.fileSend(100, 1024, "photo.png", "Tester", 42);

        assertTrue(fileReceiver.isTimedOut());
        assertTrue(fileReceiver.isCanceled());
        verify(userInterface).cancelFileSavePrompt(fileReceiver);
        verify(controller).sendFileAbort(user, 42, "photo.png");
        verify(transferList).removeFileReceiver(fileReceiver);
    }

    /**
     * Sets up the mocks needed for the fileSend tests, and returns the mocked transfer list.
     *
     * @param autoAcceptFiles The auto-accept scope to use.
     * @param channelActive If the encrypted channel with the user should be active.
     * @return The mocked transfer list.
     */
    private TransferList setUpFileSend(final AutoAcceptFiles autoAcceptFiles, final boolean channelActive) {
        setUpExistingUser();

        settings.setAutoAcceptFiles(autoAcceptFiles);

        final CryptoManager cryptoManager = mock(CryptoManager.class);
        when(cryptoManager.isChannelActive(user)).thenReturn(channelActive);
        when(controller.getCryptoManager()).thenReturn(cryptoManager);

        final TransferList transferList = mock(TransferList.class);
        when(controller.getTransferList()).thenReturn(transferList);

        // The responder captures the transfer list in the constructor,
        // so a new one is needed now that the stub is in place
        responder = new DefaultMessageResponder(controller, userInterface, settings, coreMessages);

        return transferList;
    }

    /**
     * Creates a mocked file receiver, registered with the transfer list.
     *
     * @param transferList The mocked transfer list.
     * @param fileName The name of the file.
     * @return The mocked file receiver.
     * @throws ServerException If stubbing of startServer fails.
     */
    private FileReceiver mockFileReceiver(final TransferList transferList, final String fileName) throws ServerException {
        final FileReceiver fileReceiver = mock(FileReceiver.class);
        when(fileReceiver.getId()).thenReturn(7);
        when(fileReceiver.getFile()).thenReturn(new File(System.getProperty("java.io.tmpdir"), fileName));
        when(fileReceiver.startServer()).thenReturn(40756);
        when(fileReceiver.transfer()).thenReturn(true);
        when(transferList.addFileReceiver(eq(user), any(File.class), eq(1024L))).thenReturn(fileReceiver);

        return fileReceiver;
    }

    /**
     * Creates a real file receiver, registered with the transfer list. Used
     * where the answer state machine of the receiver matters.
     *
     * @param transferList The mocked transfer list.
     * @param fileName The name of the file.
     * @return The real file receiver.
     */
    private FileReceiver realFileReceiver(final TransferList transferList, final String fileName) {
        final FileReceiver fileReceiver =
                new FileReceiver(user, new File(System.getProperty("java.io.tmpdir"), fileName), 1024, 7);
        when(transferList.addFileReceiver(eq(user), any(File.class), eq(1024L))).thenReturn(fileReceiver);

        return fileReceiver;
    }
}
