package dev.smallwright.generated.counter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int EXPORT_REQUEST = 42;
    private final List<String[]> notes = new ArrayList<>();
    private final List<String> visible = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private String filter = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        load();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        EditText search = new EditText(this);
        search.setHint("Search titles");
        search.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        filter = s.toString().toLowerCase();
                        refresh();
                    }
                });
        root.addView(search);
        Button add = new Button(this);
        add.setText("New note");
        add.setOnClickListener(v -> edit(-1));
        root.addView(add);
        Button export = new Button(this);
        export.setText("Export");
        export.setOnClickListener(
                v -> {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TITLE, "notes.txt");
                    startActivityForResult(intent, EXPORT_REQUEST);
                });
        root.addView(export);
        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, visible);
        list.setAdapter(adapter);
        list.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(
                            AdapterView<?> parent, android.view.View view, int position, long id) {
                        edit(indexOfVisible(position));
                    }
                });
        list.setOnItemLongClickListener(
                (parent, view, position, id) -> {
                    int index = indexOfVisible(position);
                    new AlertDialog.Builder(this)
                            .setTitle("Delete note?")
                            .setPositiveButton(
                                    "Delete",
                                    (d, w) -> {
                                        notes.remove(index);
                                        save();
                                        refresh();
                                    })
                            .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                            .show();
                    return true;
                });
        root.addView(list);
        setContentView(root);
        refresh();
    }

    private int indexOfVisible(int position) {
        String title = visible.get(position);
        for (int i = 0; i < notes.size(); i++) if (notes.get(i)[0].equals(title)) return i;
        return -1;
    }

    private void edit(int index) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText title = new EditText(this);
        EditText body = new EditText(this);
        if (index >= 0) {
            title.setText(notes.get(index)[0]);
            body.setText(notes.get(index)[1]);
        }
        form.addView(title);
        form.addView(body);
        new AlertDialog.Builder(this)
                .setTitle(index >= 0 ? "Edit note" : "New note")
                .setView(form)
                .setPositiveButton(
                        "Save",
                        (d, w) -> {
                            String[] note = {title.getText().toString(), body.getText().toString()};
                            if (index >= 0) notes.set(index, note);
                            else notes.add(note);
                            save();
                            refresh();
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refresh() {
        visible.clear();
        for (String[] note : notes)
            if (note[0].toLowerCase().contains(filter)) visible.add(note[0]);
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_REQUEST || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        StringBuilder text = new StringBuilder();
        for (String[] note : notes)
            text.append(note[0]).append("\n").append(note[1]).append("\n\n");
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            out.write(text.toString().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Exported", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void load() {
        try {
            JSONArray array = new JSONArray(getPreferences(MODE_PRIVATE).getString("notes", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                notes.add(new String[] {object.optString("title"), object.optString("body")});
            }
        } catch (JSONException ignored) {
        }
    }

    private void save() {
        JSONArray array = new JSONArray();
        for (String[] note : notes) {
            JSONObject object = new JSONObject();
            try {
                object.put("title", note[0]);
                object.put("body", note[1]);
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
            array.put(object);
        }
        getPreferences(MODE_PRIVATE).edit().putString("notes", array.toString()).apply();
    }
}
