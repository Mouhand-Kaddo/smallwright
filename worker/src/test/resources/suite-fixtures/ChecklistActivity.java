package dev.smallwright.generated.counter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String PREFS = "checklist";
    private final List<Item> items = new ArrayList<>();
    private ArrayAdapter<Item> adapter;
    private TextView remaining;

    static final class Item {
        String text;
        boolean done;

        Item(String text, boolean done) {
            this.text = text;
            this.done = done;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        load();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        remaining = new TextView(this);
        remaining.setTextSize(20);
        remaining.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(remaining);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final EditText input = new EditText(this);
        input.setHint("New item");
        input.setSingleLine(true);
        row.addView(
                input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button add = new Button(this);
        add.setText("Add");
        add.setOnClickListener(
                v -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) {
                        Toast.makeText(this, "Enter an item", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    items.add(new Item(text, false));
                    input.setText("");
                    save();
                    refresh();
                });
        row.addView(add);
        root.addView(row);
        Button clear = new Button(this);
        clear.setText("Clear completed");
        clear.setOnClickListener(
                v ->
                        new AlertDialog.Builder(this)
                                .setTitle("Clear completed?")
                                .setMessage("Delete all completed items?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton(
                                        "Delete",
                                        (dialog, which) -> {
                                            for (int i = items.size() - 1; i >= 0; i--) {
                                                if (items.get(i).done) items.remove(i);
                                            }
                                            save();
                                            refresh();
                                        })
                                .show());
        root.addView(clear);
        ListView list = new ListView(this);
        adapter =
                new ArrayAdapter<Item>(this, android.R.layout.simple_list_item_1, items) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        CheckBox box =
                                convertView instanceof CheckBox
                                        ? (CheckBox) convertView
                                        : new CheckBox(getContext());
                        Item item = getItem(position);
                        box.setOnCheckedChangeListener(null);
                        box.setText(item.text);
                        box.setChecked(item.done);
                        box.setPaintFlags(
                                item.done
                                        ? box.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                                        : box.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                        box.setTextColor(item.done ? Color.GRAY : Color.BLACK);
                        box.setOnCheckedChangeListener(
                                (button, checked) -> {
                                    item.done = checked;
                                    save();
                                    refresh();
                                });
                        return box;
                    }
                };
        list.setAdapter(adapter);
        list.setOnItemLongClickListener(
                (parent, view, position, id) -> {
                    Item item = items.get(position);
                    EditText rename = new EditText(this);
                    rename.setText(item.text);
                    new AlertDialog.Builder(this)
                            .setTitle("Rename")
                            .setView(rename)
                            .setPositiveButton(
                                    "Save",
                                    (d, w) -> {
                                        item.text = rename.getText().toString();
                                        save();
                                        refresh();
                                    })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                });
        root.addView(
                list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        refresh();
    }

    private void refresh() {
        int open = 0;
        for (Item item : items) if (!item.done) open++;
        remaining.setText("Incomplete: " + open);
        adapter.notifyDataSetChanged();
    }

    private void load() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        try {
            JSONArray array = new JSONArray(prefs.getString("items", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                items.add(new Item(object.getString("text"), object.optBoolean("done", false)));
            }
        } catch (JSONException e) {
            items.clear();
        }
    }

    private void save() {
        JSONArray array = new JSONArray();
        try {
            for (Item item : items) {
                JSONObject object = new JSONObject();
                object.put("text", item.text);
                object.put("done", item.done);
                array.put(object);
            }
        } catch (JSONException e) {
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString("items", array.toString())
                .apply();
    }
}
