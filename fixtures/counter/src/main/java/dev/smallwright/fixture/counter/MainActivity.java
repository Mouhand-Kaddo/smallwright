package dev.smallwright.fixture.counter;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private int count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView label = new TextView(this);
        Button button = new Button(this);
        button.setText(getString(R.string.increment));
        button.setOnClickListener(view -> {
            count += 1;
            label.setText(getString(R.string.count_format, count));
        });

        label.setText(getString(R.string.count_format, count));
        layout.addView(label);
        layout.addView(button);
        setContentView(layout);
    }
}
