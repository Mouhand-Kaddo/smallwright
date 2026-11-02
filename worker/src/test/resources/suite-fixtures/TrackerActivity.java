package dev.smallwright.generated.counter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView total;
    private TextView progress;
    private TextView history;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getPreferences(MODE_PRIVATE);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        total = new TextView(this);
        progress = new TextView(this);
        history = new TextView(this);
        final EditText goal = new EditText(this);
        goal.setInputType(InputType.TYPE_CLASS_NUMBER);
        goal.setText(String.valueOf(prefs.getInt("goal", 8)));
        Button setGoal = new Button(this);
        setGoal.setText("Set goal");
        setGoal.setOnClickListener(
                v -> {
                    try {
                        int value = Integer.parseInt(goal.getText().toString());
                        prefs.edit().putInt("goal", Math.max(1, value)).apply();
                    } catch (NumberFormatException ignored) {
                    }
                    update();
                });
        Button add = new Button(this);
        add.setText("Add glass");
        add.setOnClickListener(
                v -> {
                    prefs.edit().putInt(key(0), prefs.getInt(key(0), 0) + 1).apply();
                    update();
                });
        Button undo = new Button(this);
        undo.setText("Undo last glass");
        undo.setOnClickListener(
                v -> {
                    int current = prefs.getInt(key(0), 0);
                    if (current > 0) prefs.edit().putInt(key(0), current - 1).apply();
                    update();
                });
        Button clear = new Button(this);
        clear.setText("Clear day");
        clear.setOnClickListener(
                v ->
                        new AlertDialog.Builder(this)
                                .setMessage("Reset today's total?")
                                .setPositiveButton(
                                        "Reset",
                                        (d, w) -> {
                                            prefs.edit().remove(key(0)).apply();
                                            update();
                                        })
                                .setNegativeButton("Cancel", null)
                                .show());
        layout.addView(total);
        layout.addView(progress);
        layout.addView(goal);
        layout.addView(setGoal);
        layout.addView(add);
        layout.addView(undo);
        layout.addView(clear);
        layout.addView(history);
        setContentView(layout);
        update();
    }

    private String key(int daysAgo) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo);
        return "day_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    private void update() {
        int today = prefs.getInt(key(0), 0);
        int goal = prefs.getInt("goal", 8);
        total.setText("Today's total: " + today);
        progress.setText(
                String.format(
                        Locale.US, "Progress: %d / %d (%d%%)", today, goal, today * 100 / goal));
        StringBuilder builder = new StringBuilder("Last 7 days\n");
        for (int i = 0; i < 7; i++) {
            builder.append(key(i).substring(4))
                    .append(": ")
                    .append(prefs.getInt(key(i), 0))
                    .append('\n');
        }
        history.setText(builder.toString());
        long now = new Date().getTime();
        if (now < 0) history.setText("");
    }
}
