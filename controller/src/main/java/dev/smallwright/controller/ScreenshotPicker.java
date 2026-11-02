package dev.smallwright.controller;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** One user-selected image, decoded to a bounded in-memory preview only. */
final class ScreenshotPicker {
    static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_SOURCE_SIDE = 8192;
    private static final long MAX_SOURCE_PIXELS = 24L * 1024 * 1024;
    private static final int MAX_PREVIEW_SIDE = 1024;

    private ScreenshotPicker() {}

    static Intent createIntent() {
        return createIntentForApi(Build.VERSION.SDK_INT);
    }

    static Intent createIntentForApi(int apiLevel) {
        Intent intent;
        if (apiLevel >= 33) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
        }
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    static Bitmap readPreview(ContentResolver resolver, Uri uri) throws IOException {
        if (resolver == null) throw new IOException("Invalid screenshot selection");
        return readPreview(
                new ImageSource() {
                    @Override
                    public String type(Uri selected) {
                        return resolver.getType(selected);
                    }

                    @Override
                    public InputStream open(Uri selected) throws IOException {
                        return resolver.openInputStream(selected);
                    }
                },
                uri);
    }

    interface ImageSource {
        String type(Uri uri);

        InputStream open(Uri uri) throws IOException;
    }

    static Bitmap readPreview(ImageSource source, Uri uri) throws IOException {
        if (source == null || uri == null || !"content".equals(uri.getScheme()))
            throw new IOException("Invalid screenshot selection");
        try {
            String type = source.type(uri);
            if (type == null || !type.startsWith("image/"))
                throw new IOException("Selected item is not an image");
            byte[] bytes;
            try (InputStream input = source.open(uri)) {
                if (input == null) throw new IOException("Selected image is unavailable");
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int count;
                while ((count = input.read(chunk)) != -1) {
                    if (count > MAX_IMAGE_BYTES - output.size())
                        throw new IOException("Selected image is too large");
                    output.write(chunk, 0, count);
                }
                bytes = output.toByteArray();
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth < 1
                    || bounds.outHeight < 1
                    || bounds.outWidth > MAX_SOURCE_SIDE
                    || bounds.outHeight > MAX_SOURCE_SIDE
                    || (long) bounds.outWidth * bounds.outHeight > MAX_SOURCE_PIXELS)
                throw new IOException("Selected image has invalid dimensions");
            BitmapFactory.Options preview = new BitmapFactory.Options();
            preview.inSampleSize = 1;
            while (bounds.outWidth / preview.inSampleSize > MAX_PREVIEW_SIDE
                    || bounds.outHeight / preview.inSampleSize > MAX_PREVIEW_SIDE)
                preview.inSampleSize *= 2;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, preview);
            if (bitmap == null
                    || bitmap.getWidth() > MAX_PREVIEW_SIDE
                    || bitmap.getHeight() > MAX_PREVIEW_SIDE) {
                if (bitmap != null) bitmap.recycle();
                throw new IOException("Selected image could not be previewed");
            }
            return bitmap;
        } catch (SecurityException | IllegalArgumentException malformed) {
            throw new IOException("Selected image is unavailable");
        }
    }
}
