package dev.smallwright.controller;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Bounded image intake and system-picker intent, with no provider upload. */
final class ScreenshotPickerProbe {
    private ScreenshotPickerProbe() {}

    static void run(Context context) throws Exception {
        Intent picker = ScreenshotPicker.createIntentForApi(33);
        if (!MediaStore.ACTION_PICK_IMAGES.equals(picker.getAction()))
            throw new AssertionError("System photo picker was not selected");
        Intent fallback = ScreenshotPicker.createIntentForApi(31);
        if (!Intent.ACTION_OPEN_DOCUMENT.equals(fallback.getAction())
                || !fallback.hasCategory(Intent.CATEGORY_OPENABLE)) {
            throw new AssertionError("Open document fallback was not selected");
        }
        if (!"image/*".equals(picker.getType()) || !"image/*".equals(fallback.getType()))
            throw new AssertionError("Picker accepts non-image data");

        File file = File.createTempFile("screenshot-probe-", ".png", context.getCacheDir());
        File oversized = null;
        File malformed = null;
        try {
            Bitmap image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
            try (FileOutputStream output = new FileOutputStream(file)) {
                if (!image.compress(Bitmap.CompressFormat.PNG, 100, output))
                    throw new AssertionError("Could not make test image");
            } finally {
                image.recycle();
            }
            TestImageSource source = new TestImageSource(file);
            Uri uri = Uri.parse("content://screenshot.test/image");
            Bitmap preview = ScreenshotPicker.readPreview(source, uri);
            if (preview.getWidth() != 2 || preview.getHeight() != 2)
                throw new AssertionError("Valid image did not preview");
            preview.recycle();
            source.mime = "text/plain";
            expectRefusal(source, uri);
            source.mime = "image/png";
            malformed = File.createTempFile("screenshot-malformed-", ".png", context.getCacheDir());
            try (FileOutputStream output = new FileOutputStream(malformed)) {
                output.write(new byte[] {1, 2, 3, 4});
            }
            source.file = malformed;
            expectRefusal(source, uri);
            oversized = File.createTempFile("screenshot-oversize-", ".png", context.getCacheDir());
            source.file = oversized;
            try (FileOutputStream output = new FileOutputStream(source.file)) {
                output.write(new byte[ScreenshotPicker.MAX_IMAGE_BYTES + 1]);
            }
            expectRefusal(source, uri);
            expectRefusal(source, Uri.parse("file:///tmp/no.png"));
        } finally {
            file.delete();
            if (malformed != null) malformed.delete();
            if (oversized != null) oversized.delete();
        }
    }

    private static void expectRefusal(TestImageSource source, Uri uri) throws Exception {
        try {
            ScreenshotPicker.readPreview(source, uri);
            throw new AssertionError("Invalid image accepted");
        } catch (IOException expected) {
            /* bounded failure */
        }
    }

    private static final class TestImageSource implements ScreenshotPicker.ImageSource {
        File file;
        String mime = "image/png";

        TestImageSource(File file) {
            this.file = file;
        }

        @Override
        public String type(Uri uri) {
            return mime;
        }

        @Override
        public InputStream open(Uri uri) throws IOException {
            return new FileInputStream(file);
        }
    }
}
