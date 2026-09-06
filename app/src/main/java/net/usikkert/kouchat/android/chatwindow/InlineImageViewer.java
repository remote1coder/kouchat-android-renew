
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import net.usikkert.kouchat.android.filetransfer.AndroidFileUtils;
import net.usikkert.kouchat.util.Validate;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.Spannable;
import android.util.Base64;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

/**
 * Attaches touch handling to a chat {@link TextView} so inline images can be
 * tapped to open a zoomable full-resolution view, and long-pressed to save
 * the image to the downloads directory.
 *
 * <p>The touch listener returns <code>false</code> so normal scrolling and
 * clickable links (via {@link android.text.method.LinkMovementMethod}) keep
 * working - this only observes taps and long presses on top of images.</p>
 *
 * @author Christian Ihle
 */
public final class InlineImageViewer {

    private final Context context;
    private final AndroidFileUtils androidFileUtils = new AndroidFileUtils();

    public InlineImageViewer(final Context context) {
        Validate.notNull(context, "Context can not be null");

        this.context = context;
    }

    /**
     * Attaches the tap/long-press handling to the given chat text view.
     *
     * @param textView The chat text view showing inline images.
     */
    public void attach(final TextView textView) {
        Validate.notNull(textView, "TextView can not be null");

        final GestureDetector detector = new GestureDetector(textView.getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(final MotionEvent e) {
                        final byte[] bytes = findImageBytes(textView, e);

                        if (bytes != null) {
                            showZoom(bytes);
                            return true;
                        }

                        return false;
                    }

                    @Override
                    public void onLongPress(final MotionEvent e) {
                        final byte[] bytes = findImageBytes(textView, e);

                        if (bytes != null) {
                            showImageMenu(bytes);
                        }
                    }
                });

        textView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(final View v, final MotionEvent event) {
                detector.onTouchEvent(event);

                return false; // Let LinkMovementMethod handle links and the view handle scrolling
            }
        });
    }

    private byte[] findImageBytes(final TextView textView, final MotionEvent e) {
        if (textView.getLayout() == null) {
            return null;
        }

        final int offset = textView.getOffsetForPosition(e.getX(), e.getY());

        if (offset < 0) {
            return null;
        }

        final Spannable text = (Spannable) textView.getText();
        final MessageStylerWithHistory.InlineImageSpan[] spans =
                text.getSpans(offset, offset, MessageStylerWithHistory.InlineImageSpan.class);

        return spans.length > 0 ? spans[0].getImageBytes() : null;
    }

    private void showZoom(final byte[] imageBytes) {
        final android.app.Dialog dialog = new android.app.Dialog(context);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        final WebView webView = new WebView(context);
        final WebSettings settings = webView.getSettings();
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        final String mime = detectMime(imageBytes);
        final String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        final String html = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "</head><body style='margin:0;padding:0;background:#222'>"
                + "<img src='data:" + mime + ";base64," + base64 + "' style='width:100%;height:auto'>"
                + "</body></html>";

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);

        dialog.setContentView(webView);
        dialog.show();
    }

    private void showImageMenu(final byte[] imageBytes) {
        new AlertDialog.Builder(context)
                .setTitle("Image")
                .setItems(new String[]{"Save to Downloads", "Share image"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        if (which == 0) {
                            saveImage(imageBytes);
                        } else if (which == 1) {
                            shareImage(imageBytes);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareImage(final byte[] imageBytes) {
        final String mime = detectMime(imageBytes);
        final String extension = mime.substring("image/".length());
        final File dir = new File(context.getCacheDir(), "shared");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        final File file = new File(dir, "kouchat_image." + extension);

        try (final FileOutputStream out = new FileOutputStream(file)) {
            out.write(imageBytes);
        }

        catch (final IOException e) {
            Toast.makeText(context, "Could not share image", Toast.LENGTH_SHORT).show();
            return;
        }

        final Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        final Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(mime);
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            context.startActivity(Intent.createChooser(share, "Share image"));
        }

        catch (final Exception e) {
            Toast.makeText(context, "No app to share with", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveImage(final byte[] imageBytes) {
        final String extension = "." + detectMime(imageBytes).substring("image/".length());
        final File file = androidFileUtils.createFileInDownloadsWithAvailableName(context, "kouchat_image" + extension);

        try (final FileOutputStream out = new FileOutputStream(file)) {
            out.write(imageBytes);
            androidFileUtils.addFileToMediaDatabase(context, file);
            Toast.makeText(context, "Saved: " + file.getName(), Toast.LENGTH_LONG).show();
        }

        catch (final IOException e) {
            Toast.makeText(context, "Could not save image", Toast.LENGTH_SHORT).show();
        }
    }

    private static String detectMime(final byte[] bytes) {
        if (bytes.length >= 4 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "image/png";
        }

        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
            return "image/jpeg";
        }

        if (bytes.length >= 3 && bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46) {
            return "image/gif";
        }

        return "image/png";
    }
}
