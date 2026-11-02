package dev.smallwright.controller;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/** Places a disposable sealed bundle in Android Downloads for the actual picker/UI test. */
final class RecoveryImportDocumentProbe {
    static final String PHRASE = "disposable recovery ui phrase";

    private RecoveryImportDocumentProbe() {}

    static String create(Context context) throws Exception {
        String title = "smallwright-restore-probe-" + UUID.randomUUID() + ".plbr";
        byte[] main =
                ("package dev.smallwright.generated.counter;\n"
                                + "import android.app.Activity;\n"
                                + "import android.os.Bundle;\n"
                                + "import android.widget.TextView;\n"
                                + "public final class MainActivity extends Activity {\n"
                                + "  @Override public void onCreate(Bundle state) {\n"
                                + "    super.onCreate(state);\n"
                                + "    TextView label = new TextView(this);\n"
                                + "    label.setText(\"Restored on phone\");\n"
                                + "    setContentView(label);\n"
                                + "  }\n"
                                + "}\n")
                        .getBytes(StandardCharsets.UTF_8);
        byte[] layout =
                ("<View xmlns:android=\"http://schemas.android.com/apk/res/android\" "
                                + "android:layout_width=\"1dp\" android:layout_height=\"1dp\"/>\n")
                        .getBytes(StandardCharsets.UTF_8);
        RecoveryBundle bundle =
                RecoveryBundle.create(
                        4,
                        7,
                        Map.of(
                                "java/dev/smallwright/generated/counter/MainActivity.java", main,
                                "res/layout/empty.xml", layout),
                        PortableSigningIdentity.create(),
                        null);
        char[] phrase = PHRASE.toCharArray();
        byte[] sealed = null;
        try {
            sealed = bundle.seal(phrase);
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, title);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri document =
                    context.getContentResolver()
                            .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (document == null) throw new IllegalStateException("Downloads document unavailable");
            try (OutputStream output =
                    context.getContentResolver().openOutputStream(document, "w")) {
                if (output == null) throw new IllegalStateException("Downloads stream unavailable");
                output.write(sealed);
            }
            ContentValues visible = new ContentValues();
            visible.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (context.getContentResolver().update(document, visible, null, null) != 1)
                throw new IllegalStateException("Recovery document was not published");
            return title;
        } finally {
            Arrays.fill(phrase, '\0');
            if (sealed != null) Arrays.fill(sealed, (byte) 0);
        }
    }
}
