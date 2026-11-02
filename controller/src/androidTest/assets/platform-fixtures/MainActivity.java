package $PACKAGE;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private TextView status;
    private EditText note;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this);
        status.setText("Built with the Android platform files");
        note = new EditText(this);
        note.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setMinLines(3);
        note.setHint("Write a note");
        Button count = new Button(this);
        count.setText("Count letters");
        count.setOnClickListener(view -> status.setText("Letters: " + note.length()));
        Button clear = new Button(this);
        clear.setText("Clear");
        clear.setOnClickListener(this::clear);
        column.addView(status);
        column.addView(note);
        column.addView(count);
        column.addView(clear);
        setContentView(column);
        Log.w("PlatformFixture", "Started", new IllegalStateException("fixture warning"));
    }

    private void clear(View view) {
        note.setText("");
        status.setText("Cleared");
    }
}
