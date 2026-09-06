
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
 *   If not, see <http://www.gnu.org/licenses/lgpl-3.0.txt>.               *
 ***************************************************************************/

package net.usikkert.kouchat.android.controller;

import net.usikkert.kouchat.android.R;
import net.usikkert.kouchat.android.chatwindow.AndroidUserInterface;
import net.usikkert.kouchat.android.service.ChatService;
import net.usikkert.kouchat.android.service.ChatServiceBinder;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Asks the user whether to trust a peer's encryption key, showing the key fingerprint.
 *
 * <p>Launched by {@link AndroidUserInterface#requestTrust} when a peer's key arrives.
 * Binds to the chat service, then shows a dialog with Trust / Don't Trust buttons.
 * The decision is reported back via {@link AndroidUserInterface#reportTrustDecision}.</p>
 *
 * @author Christian Ihle
 */
public class TrustKeyController extends AppCompatActivity {

    private ServiceConnection serviceConnection;
    private AndroidUserInterface androidUserInterface;
    private boolean decided;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        serviceConnection = createServiceConnection();
        bindService(new Intent(this, ChatService.class), serviceConnection, Context.BIND_NOT_FOREGROUND);
    }

    @Override
    protected void onDestroy() {
        if (serviceConnection != null) {
            unbindService(serviceConnection);
            serviceConnection = null;
        }

        // If the user dismissed the dialog without choosing, treat as declined.
        if (!decided && androidUserInterface != null) {
            androidUserInterface.reportTrustDecision(getIntent().getIntExtra("userCode", -1), false);
        }

        super.onDestroy();
    }

    private ServiceConnection createServiceConnection() {
        return new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName componentName, final IBinder iBinder) {
                final ChatServiceBinder binder = (ChatServiceBinder) iBinder;
                androidUserInterface = binder.getAndroidUserInterface();
                showDialog(androidUserInterface);
            }

            @Override
            public void onServiceDisconnected(final ComponentName componentName) { }
        };
    }

    private void showDialog(final AndroidUserInterface androidUserInterface) {
        final Intent intent = getIntent();
        final int userCode = intent.getIntExtra("userCode", -1);
        final String userNick = intent.getStringExtra("userNick");
        final String fingerprint = intent.getStringExtra("fingerprint");

        // The dialog is up; the notification that may have opened it is no longer needed.
        androidUserInterface.dismissTrustNotification(userCode);

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.trust_dialog_title);

        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        final int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        final TextView intro = new TextView(this);
        intro.setText(getString(R.string.trust_dialog_intro, userNick));
        layout.addView(intro);

        final TextView fpLabel = new TextView(this);
        fpLabel.setText(R.string.trust_dialog_fingerprint_label);
        fpLabel.setPadding(0, pad, 0, 0);
        layout.addView(fpLabel);

        final TextView fp = new TextView(this);
        fp.setText(fingerprint);
        fp.setTypeface(Typeface.MONOSPACE);
        fp.setTextSize(16);
        layout.addView(fp);

        final TextView compare = new TextView(this);
        compare.setText(getString(R.string.trust_dialog_compare, userNick));
        compare.setPadding(0, pad, 0, 0);
        layout.addView(compare);

        builder.setView(layout);

        builder.setPositiveButton(R.string.trust_dialog_trust, (dialog, which) -> {
            decided = true;
            androidUserInterface.reportTrustDecision(userCode, true);
            finish();
        });

        builder.setNegativeButton(R.string.trust_dialog_dont_trust, (dialog, which) -> {
            decided = true;
            androidUserInterface.reportTrustDecision(userCode, false);
            finish();
        });

        builder.setOnCancelListener(dialog -> {
            decided = true;
            androidUserInterface.reportTrustDecision(userCode, false);
            finish();
        });

        builder.show();
    }
}
