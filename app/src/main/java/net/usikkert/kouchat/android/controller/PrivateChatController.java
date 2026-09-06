
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

package net.usikkert.kouchat.android.controller;

import net.usikkert.kouchat.android.R;
import net.usikkert.kouchat.android.chatwindow.AndroidPrivateChatWindow;
import net.usikkert.kouchat.android.chatwindow.AndroidUserInterface;
import net.usikkert.kouchat.android.chatwindow.InlineImageViewer;
import net.usikkert.kouchat.android.filetransfer.AndroidFileUtils;
import net.usikkert.kouchat.android.service.ChatService;
import net.usikkert.kouchat.android.service.ChatServiceBinder;
import net.usikkert.kouchat.misc.User;
import net.usikkert.kouchat.net.FileToSend;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Controller for private chat with another user.
 *
 * @author Christian Ihle
 */
public class PrivateChatController extends AppCompatActivity {

    private ControllerUtils controllerUtils = new ControllerUtils();

    private TextView privateChatView;
    private EditText privateChatInput;
    private ScrollView privateChatScroll;
    private ActionBar actionBar;
    private ServiceConnection serviceConnection;

    private AndroidUserInterface androidUserInterface;
    private AndroidPrivateChatWindow privateChatWindow;
    private User user;

    /** If this private chat is currently visible. */
    private boolean visible;

    /** If this private chat has been destroyed. */
    private boolean destroyed;

    private final AndroidFileUtils androidFileUtils = new AndroidFileUtils();

    private static final int REQUEST_CODE_PICK_IMAGE = 2002;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.private_chat);

        privateChatInput = findViewById(R.id.privateChatInput);
        privateChatView = findViewById(R.id.privateChatView);
        privateChatScroll = findViewById(R.id.privateChatScroll);

        final Intent chatServiceIntent = createChatServiceIntent();
        serviceConnection = createServiceConnection();
        bindService(chatServiceIntent, serviceConnection, Context.BIND_NOT_FOREGROUND);

        actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        controllerUtils.makeLinksClickable(privateChatView);
        new InlineImageViewer(this).attach(privateChatView);
        privateChatInput.requestFocus();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;

        if (privateChatWindow != null) {
            privateChatWindow.unregisterPrivateChatController();
            unbindService(serviceConnection);
        }

        privateChatInput.setOnKeyListener(null);
        controllerUtils.removeReferencesToTextViewFromText(privateChatView);
        controllerUtils.removeReferencesToTextViewFromText(privateChatInput);

        androidUserInterface = null;
        privateChatWindow = null;
        user = null;

        controllerUtils = null;
        privateChatView = null;
        privateChatInput = null;
        privateChatScroll = null;
        actionBar = null;
        serviceConnection = null;

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;

        if (androidUserInterface != null && user != null) {
            // Make sure that new private message notifications are hidden when the private chat is shown again
            // after being hidden. Happens when the screen is turned off and on again, or after pressing home,
            // and returning to the application, or clicking a link and returning, and so on.
            resetNewPrivateMessageIcon();
        }
    }

    @Override
    protected void onPause() {
        visible = false;
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.private_chat_menu, menu);

        return true;
    }

    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == android.R.id.home) { // Clicked on KouChat icon in the action bar
            return goBackToMainChat();
        } else if (itemId == R.id.privateChatMenuSendImage) {
            pickImage();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void pickImage() {
        final Intent pickImageIntent = new Intent(Intent.ACTION_GET_CONTENT);
        pickImageIntent.setType("image/*");
        startActivityForResult(pickImageIntent, REQUEST_CODE_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            sendImageToUser(data.getData());
        }
    }

    private void sendImageToUser(final Uri imageUri) {
        if (androidUserInterface == null || user == null) {
            return;
        }

        final FileToSend fileToSend = androidFileUtils.getFileFromUri(imageUri, getContentResolver());

        if (fileToSend != null) {
            androidUserInterface.sendFile(user, fileToSend);
        }
    }

    private boolean goBackToMainChat() {
        startActivity(new Intent(this, MainChatController.class));
        return true;
    }

    /**
     * Makes sure regular key events from anywhere in the activity are sent to the input field,
     * and giving it focus if it doesn't currently have focus.
     *
     * <p>Always asks the activity first, to make sure special keys are handled correctly, like the back button.</p>
     *
     * {@inheritDoc}
     */
    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        if (super.dispatchKeyEvent(event)) {
            return true;
        }

        if (!privateChatInput.hasFocus()) {
            privateChatInput.requestFocus();
        }

        return privateChatInput.dispatchKeyEvent(event);
    }

    private Intent createChatServiceIntent() {
        return new Intent(this, ChatService.class);
    }

    private ServiceConnection createServiceConnection() {
        return new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName componentName, final IBinder iBinder) {
                final ChatServiceBinder binder = (ChatServiceBinder) iBinder;
                androidUserInterface = binder.getAndroidUserInterface();

                setupPrivateChatWithUser();
            }

            @Override
            public void onServiceDisconnected(final ComponentName componentName) { }
        };
    }

    private void registerPrivateChatInputListener() {
        privateChatInput.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(final View v, final int keyCode, final KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_ENTER) {
                    sendPrivateMessage(privateChatInput.getText().toString());
                    privateChatInput.setText("");

                    return true;
                }

                return false;
            }
        });
    }

    protected void sendPrivateMessage(final String privateMessage) {
        if (privateMessage != null && privateMessage.trim().length() > 0) {
            androidUserInterface.sendPrivateMessage(privateMessage, user);
        }
    }

    private void setupPrivateChatWithUser() {
        setUser();

        if (user != null) {
            setPrivateChatWindow();
            setTitle();
            resetNewPrivateMessageIcon();
            registerPrivateChatInputListener();

            // Actively start the encrypted handshake with this peer (like the Swing UI does
            // when opening a private chat). No-op if already established or unsupported.
            androidUserInterface.initiateEncryptedChat(user);
        }
    }

    private void setTitle() {
        privateChatWindow.updateTitle();
    }

    private void setUser() {
        final Intent intent = getIntent();

        final int userCode = intent.getIntExtra("userCode", -1);
        user = androidUserInterface.getUser(userCode);
    }

    private void setPrivateChatWindow() {
        androidUserInterface.createPrivChat(user);

        privateChatWindow = (AndroidPrivateChatWindow) user.getPrivchat();
        privateChatWindow.registerPrivateChatController(this);
    }

    private void resetNewPrivateMessageIcon() {
        androidUserInterface.activatedPrivChat(user);
    }

    public void updatePrivateChat(final CharSequence savedChat) {
        privateChatView.setText(savedChat);

        // Run this after 1 second, because right after a rotate the layout is null and it's not possible to scroll yet
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // If rotating fast, this activity could already be destroyed before this runs
                if (!destroyed) {
                    controllerUtils.scrollTextViewToBottom(privateChatView, privateChatScroll);
                }
            }
        }, ControllerUtils.ONE_SECOND);
    }

    public void appendToPrivateChat(final CharSequence privateMessage) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (destroyed) {
                    return; // If rotating fast, this activity could already be destroyed before this runs
                }

                privateChatView.append(privateMessage);

                // Allow a way to avoid automatic scrolling to the bottom.
                // Just scroll somewhere and click on the text to remove focus from the input field.
                // Also fixes the annoying jumping scroll that happens sometimes.
                if (privateChatInput.hasFocus()) {
                    controllerUtils.scrollTextViewToBottom(privateChatView, privateChatScroll);
                }
            }
        });
    }

    /**
     * Returns if this private chat view is currently visible.
     *
     * @return If the view is visible.
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Updates the title and subtitle of the activity with the specified values.
     *
     * @param title The title to set.
     * @param subtitle The subtitle to set.
     */
    public void updateTitleAndSubtitle(final String title, final String subtitle) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                actionBar.setTitle(title);
                actionBar.setSubtitle(subtitle);
            }
        });
    }
}
