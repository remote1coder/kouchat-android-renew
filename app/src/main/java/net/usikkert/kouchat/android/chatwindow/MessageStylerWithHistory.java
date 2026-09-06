
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

package net.usikkert.kouchat.android.chatwindow;

import java.util.List;

import net.usikkert.kouchat.android.component.DefaultLineHeightSpan;
import net.usikkert.kouchat.android.smiley.Smiley;
import net.usikkert.kouchat.android.smiley.SmileyLocator;
import net.usikkert.kouchat.android.smiley.SmileyMap;
import net.usikkert.kouchat.util.Validate;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.util.Linkify;

/**
 * Builds the messages that are shown in the chat, with support for smileys, links and colors.
 * All the messages are stored for later use.
 *
 * @author Christian Ihle
 */
public class MessageStylerWithHistory {

    /** Upper cap for inline image width, so tablets do not get huge images. */
    private static final int MAX_IMAGE_WIDTH_CAP = 1024;

    private final SmileyLocator smileyLocator;
    private final SmileyMap smileyMap;
    private final SpannableStringBuilder history;

    /** Application context used to read the current screen width (rotation aware). */
    private final Context appContext;

    public MessageStylerWithHistory(final Context context) {
        Validate.notNull(context, "Context can not be null");

        smileyMap = new SmileyMap(context);
        smileyLocator = new SmileyLocator(smileyMap.getSmileyCodes());
        history = new SpannableStringBuilder();
        appContext = context.getApplicationContext();
    }

    /**
     * Adds styling to the message, and appends it to the history.
     *
     * <p>Trims the message first, to avoid blank lines.</p>
     *
     * @param message The message to style and add to the history.
     * @param color The color to style the message with.
     * @return The styled message.
     */
    public CharSequence styleAndAppend(final String message, final int color) {
        final String trimmedMessage = message.trim();
        final SpannableStringBuilder messageBuilder = new SpannableStringBuilder(trimmedMessage + "\n");

        addColor(trimmedMessage, color, messageBuilder);
        addSmileys(trimmedMessage, messageBuilder);
        addLinks(messageBuilder);
        fixLineHeight(trimmedMessage, messageBuilder);

        history.append(messageBuilder);

        return messageBuilder;
    }

    /**
     * Returns all the styled messages added to the history.
     *
     * @return All messages.
     */
    public CharSequence getHistory() {
        return history;
    }

    /**
     * Styles a label and an image, and appends them to the history.
     *
     * <p>The image is scaled to ~90% of the current screen width (recomputed each
     * time, so it adapts to portrait/landscape rotation) and rendered with explicit
     * bounds so it always displays large. The original bytes are kept on the span
     * so a tap can zoom in and a long press can save the full-resolution image.</p>
     *
     * @param imageBytes The raw bytes of the image to display.
     * @param label The text label to show before the image.
     * @param color The color to show the label in.
     * @return The styled message.
     */
    public CharSequence styleAndAppendImage(final byte[] imageBytes, final String label, final int color) {
        Validate.notNull(imageBytes, "Image bytes can not be null");

        final SpannableStringBuilder builder = new SpannableStringBuilder();

        final int labelStart = builder.length();
        builder.append(label);
        final int labelEnd = builder.length();

        if (labelEnd > labelStart) {
            builder.setSpan(new ForegroundColorSpan(color), labelStart, labelEnd, 0);
        }

        final int imageStart = builder.length();
        builder.append(" "); // Placeholder replaced by the image below
        final int imageEnd = builder.length();

        final int targetWidth = computeMaxImageWidth();
        final Bitmap bitmap = decodeAndScale(imageBytes, targetWidth);

        if (bitmap != null) {
            final BitmapDrawable drawable = new BitmapDrawable(appContext.getResources(), bitmap);
            drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            builder.setSpan(new InlineImageSpan(drawable, imageBytes), imageStart, imageEnd, 0);
        }

        builder.append("\n");

        history.append(builder);

        return builder;
    }

    private int computeMaxImageWidth() {
        return Math.min(appContext.getResources().getDisplayMetrics().widthPixels * 9 / 10,
                         MAX_IMAGE_WIDTH_CAP);
    }

    private static Bitmap decodeAndScale(final byte[] imageBytes, final int targetWidth) {
        if (targetWidth <= 0) {
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        }

        final Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        if (bitmap == null) {
            return null;
        }

        if (bitmap.getWidth() == targetWidth) {
            return bitmap;
        }

        final int height = bitmap.getHeight() * targetWidth / Math.max(bitmap.getWidth(), 1);

        return Bitmap.createScaledBitmap(bitmap, targetWidth, height, true);
    }

    private void addColor(final String message, final int color, final SpannableStringBuilder messageBuilder) {
        messageBuilder.setSpan(new ForegroundColorSpan(color), 0, message.length(), 0);
    }

    private void addLinks(final SpannableStringBuilder messageBuilder) {
        Linkify.addLinks(messageBuilder, Linkify.WEB_URLS);
    }

    private void addSmileys(final String message, final SpannableStringBuilder messageBuilder) {
        final List<Smiley> smileys = smileyLocator.findSmileys(message);

        for (final Smiley smiley : smileys) {
            final Drawable drawableSmiley = smileyMap.getSmiley(smiley.getCode());
            final ImageSpan smileySpan = new ImageSpan(drawableSmiley, smiley.getCode(), ImageSpan.ALIGN_BOTTOM);

            messageBuilder.setSpan(smileySpan, smiley.getStartPosition(), smiley.getEndPosition(), 0);
        }
    }

    private void fixLineHeight(final String message, final SpannableStringBuilder messageBuilder) {
        // Line spacing is broken on Android 5, 6 and 7. See #77941
        // Don't add this span, or the smileys will partially overlap the text.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            messageBuilder.setSpan(new DefaultLineHeightSpan(), 0, message.length(), 0);
        }
    }

    /**
     * An image span that also carries the original image bytes, so the chat view
     * can open a full-resolution zoom view or save the image when the span is tapped.
     */
    public static class InlineImageSpan extends ImageSpan {

        private final byte[] imageBytes;

        public InlineImageSpan(final Drawable drawable, final byte[] imageBytes) {
            super(drawable, ImageSpan.ALIGN_BOTTOM);
            this.imageBytes = imageBytes;
        }

        public byte[] getImageBytes() {
            return imageBytes;
        }
    }
}
