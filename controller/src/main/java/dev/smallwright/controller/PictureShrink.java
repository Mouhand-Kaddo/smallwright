package dev.smallwright.controller;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.util.Size;
import dev.smallwright.project.ChatStore;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * One picture from the photo picker, decoded at its shrunk size and saved again as a JPEG that fits
 * {@link AttachedImages}. The picture never leaves the phone here.
 */
public final class PictureShrink {
    private static final int MAX_SOURCE_SIDE = 16384;
    private static final long MAX_SOURCE_PIXELS = 100_000_000L;
    private static final int[] QUALITIES = {85, 70, 55, 40};
    private static final int THUMBNAIL_SIDE = 256;

    private PictureShrink() {}

    static byte[] jpeg(ContentResolver resolver, Uri uri) throws IOException {
        if (resolver == null || uri == null || !"content".equals(uri.getScheme()))
            throw new IOException("Invalid picture");
        try {
            String type = resolver.getType(uri);
            if (type == null || !type.startsWith("image/")) throw new IOException("Not a picture");
        } catch (RuntimeException unreadable) {
            throw new IOException("Could not read the picture");
        }
        return jpeg(ImageDecoder.createSource(resolver, uri));
    }

    /** The same for a picture's bytes, such as a generated app's PNG (ADR 0008). */
    static byte[] jpeg(byte[] picture) throws IOException {
        if (picture == null || picture.length == 0) throw new IOException("Invalid picture");
        return jpeg(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(picture.clone())));
    }

    private static byte[] jpeg(ImageDecoder.Source source) throws IOException {
        Bitmap decoded;
        try {
            decoded =
                    ImageDecoder.decodeBitmap(
                            source,
                            (decoder, info, ignored) -> {
                                Size size = info.getSize();
                                int width = size.getWidth();
                                int height = size.getHeight();
                                if (width < 1
                                        || height < 1
                                        || width > MAX_SOURCE_SIDE
                                        || height > MAX_SOURCE_SIDE
                                        || (long) width * height > MAX_SOURCE_PIXELS)
                                    throw new IllegalArgumentException("Picture too large");
                                int[] target = AttachedImages.fitWithin(width, height);
                                decoder.setTargetSize(target[0], target[1]);
                                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                            });
        } catch (IOException | RuntimeException | OutOfMemoryError unreadable) {
            throw new IOException("Could not read the picture");
        }
        Bitmap opaque = decoded;
        try {
            if (decoded.hasAlpha()) {
                opaque =
                        Bitmap.createBitmap(
                                decoded.getWidth(), decoded.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(opaque);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(decoded, 0, 0, null);
            }
            for (int quality : QUALITIES) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                if (!opaque.compress(Bitmap.CompressFormat.JPEG, quality, bytes))
                    throw new IOException("Could not shrink the picture");
                if (ChatStore.isStorableImage(bytes.toByteArray())) return bytes.toByteArray();
            }
            throw new IOException("The picture is too detailed to send");
        } finally {
            if (opaque != decoded) opaque.recycle();
            decoded.recycle();
        }
    }

    /** A small copy for the screen, or null when the bytes do not decode. */
    public static Bitmap thumbnail(byte[] jpeg) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, bounds);
        if (bounds.outWidth < 1 || bounds.outHeight < 1) return null;
        BitmapFactory.Options small = new BitmapFactory.Options();
        small.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (small.inSampleSize * 2)
                >= THUMBNAIL_SIDE) small.inSampleSize *= 2;
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, small);
    }
}
