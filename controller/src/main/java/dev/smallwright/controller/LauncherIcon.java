package dev.smallwright.controller;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import dev.smallwright.agent.ImageClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Android's own decoder turns a checked PNG, JPEG or WebP into the square PNG an app icon is made
 * of, or into the WebP picture an app shows.
 */
final class LauncherIcon {
    private static final int PICTURE_QUALITY = 85;
    private static final java.util.Set<String> READABLE =
            java.util.Set.of("image/png", "image/jpeg", "image/webp");

    private LauncherIcon() {}

    /**
     * Reads the size first and refuses anything but a PNG, JPEG or WebP within {@link
     * ImageClient}'s limits, then decodes no more pixels than needed, crops the centred square and
     * scales it to {@code side}.
     */
    static byte[] square(byte[] image, int side) throws IOException {
        Bitmap decoded = decode(image, side);
        Bitmap cropped = null;
        Bitmap scaled = null;
        try {
            int edge = Math.min(decoded.getWidth(), decoded.getHeight());
            cropped =
                    Bitmap.createBitmap(
                            decoded,
                            (decoded.getWidth() - edge) / 2,
                            (decoded.getHeight() - edge) / 2,
                            edge,
                            edge);
            scaled = Bitmap.createScaledBitmap(cropped, side, side, true);
            return encoded(scaled, Bitmap.CompressFormat.PNG, 100, "the icon");
        } finally {
            if (scaled != null && scaled != cropped) scaled.recycle();
            if (cropped != null && cropped != decoded) cropped.recycle();
            decoded.recycle();
        }
    }

    /**
     * Refuses anything but a PNG, JPEG or WebP within {@link ImageClient}'s limits, then shrinks
     * it, keeping its shape, until neither side is longer than {@code maxSide}, and writes it as a
     * lossy WebP.
     */
    static AppIcons.Picture picture(byte[] image, int maxSide) throws IOException {
        Bitmap decoded = decode(image, maxSide / 2);
        Bitmap scaled = null;
        try {
            double scale =
                    Math.min(
                            1.0,
                            (double) maxSide / Math.max(decoded.getWidth(), decoded.getHeight()));
            int width = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(decoded.getHeight() * scale));
            scaled = Bitmap.createScaledBitmap(decoded, width, height, true);
            byte[] webp =
                    encoded(
                            scaled,
                            Bitmap.CompressFormat.WEBP_LOSSY,
                            PICTURE_QUALITY,
                            "the picture");
            return new AppIcons.Picture(webp, width, height);
        } finally {
            if (scaled != null && scaled != decoded) scaled.recycle();
            decoded.recycle();
        }
    }

    /**
     * Decodes a checked image at the smallest power-of-two sample that keeps both sides over {@code
     * wanted * 2}.
     */
    private static Bitmap decode(byte[] image, int wanted) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(image, 0, image.length, bounds);
        if (bounds.outMimeType == null || !READABLE.contains(bounds.outMimeType))
            throw new IOException("Android could not read the image as a PNG, JPEG or WebP.");
        int width = bounds.outWidth;
        int height = bounds.outHeight;
        if (Math.min(width, height) < ImageClient.MIN_SIDE
                || Math.max(width, height) > ImageClient.MAX_SIDE)
            throw new IOException("The image is " + width + "×" + height + " pixels.");
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
        decode.inSampleSize = 1;
        while (Math.min(width, height) / (decode.inSampleSize * 2) >= wanted * 2)
            decode.inSampleSize *= 2;
        Bitmap decoded = BitmapFactory.decodeByteArray(image, 0, image.length, decode);
        if (decoded == null) throw new IOException("Android could not decode the image.");
        return decoded;
    }

    private static byte[] encoded(
            Bitmap bitmap, Bitmap.CompressFormat format, int quality, String what)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!bitmap.compress(format, quality, out))
            throw new IOException("Android could not write " + what + ".");
        return out.toByteArray();
    }
}
