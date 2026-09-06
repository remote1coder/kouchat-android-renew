
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

package net.usikkert.kouchat.android.notification;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import net.usikkert.kouchat.android.R;
import net.usikkert.kouchat.android.controller.TrustKeyController;
import net.usikkert.kouchat.android.settings.AndroidSettings;
import net.usikkert.kouchat.misc.User;

/**
 * Service for handling trust-key notifications.
 *
 * <p>When a peer initiates an encrypted connection, the trust dialog is opened directly
 * if the app is in the foreground. If the app is backgrounded, Android blocks the
 * activity start - this notification is the fallback, so the user can tap it to open
 * the trust dialog. Mirrors the file transfer notification pattern.</p>
 *
 * @author Christian Ihle
 */
public class TrustKeyNotificationService {

    private static final int TRUST_NOTIFICATION_ID = 200000000;

    private final Context context;
    private final NotificationManager notificationManager;
    private final NotificationHelper notificationHelper;

    public TrustKeyNotificationService(final Context context,
                                       final NotificationManager notificationManager,
                                       final AndroidSettings settings) {
        this.context = context;
        this.notificationManager = notificationManager;

        notificationHelper = new NotificationHelper(context, settings);
    }

    /**
     * Notifies the user that a peer wants to establish an encrypted connection, and that
     * its key needs to be verified. Tapping the notification opens the trust dialog.
     *
     * @param peer The peer requesting the encrypted connection.
     * @param fingerprint The peer's key fingerprint (for the dialog, passed via the intent).
     */
    public void notifyTrustRequested(final User peer, final String fingerprint) {
        final int notificationId = getNotificationId(peer.getCode());
        final String channelId = context.getString(R.string.notifications_channel_id_main_chat_messages);
        final NotificationCompat.Builder notification = new NotificationCompat.Builder(context, channelId);

        notification.setSmallIcon(R.drawable.ic_stat_notify_activity);
        notification.setTicker(context.getString(R.string.notification_trust_ticker, peer.getNick()));
        notification.setContentTitle(context.getString(R.string.notification_trust_title));
        notification.setContentText(context.getString(R.string.notification_trust_text, peer.getNick()));
        notification.setContentIntent(createIntentForTrustDialog(notificationId, peer, fingerprint));
        notification.setPriority(NotificationCompat.PRIORITY_MAX);
        notification.setCategory(NotificationCompat.CATEGORY_MESSAGE);
        notification.setAutoCancel(true);

        notificationHelper.setFeedbackEffects(notification);

        notificationManager.notify(notificationId, notification.build());
    }

    /**
     * Removes the trust notification for a peer, e.g. after the user has decided.
     *
     * @param userCode The user code of the peer.
     */
    public void cancelTrustNotification(final int userCode) {
        notificationManager.cancel(getNotificationId(userCode));
    }

    private int getNotificationId(final int userCode) {
        return TRUST_NOTIFICATION_ID + userCode;
    }

    private PendingIntent createIntentForTrustDialog(final int notificationId, final User peer, final String fingerprint) {
        final Intent intent = new Intent(context, TrustKeyController.class);
        intent.putExtra("userCode", peer.getCode());
        intent.putExtra("userNick", peer.getNick());
        intent.putExtra("fingerprint", fingerprint);
        intent.setAction("trustKey" + peer.getCode() + " " + System.currentTimeMillis()); // Unique, to avoid caching
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return PendingIntent.getActivity(context, notificationId, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
