package com.notebookpaper.android.app;

import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class TermsOfUseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(32, 32, 32, 32);
        scroll.setOnClickListener(v -> finish());

        TextView text = new TextView(this);
        text.setText(
                "Terms of Use\n\n" +
                "This app generates notebook paper images for personal use.\n\n" +
                "Generated images are created on-device and are not uploaded to any server.\n\n" +
                "By using this app you agree that the generated images are provided 'as is' " +
                "without warranty of any kind.\n\n" +
                "The source code is open source. See the repository for full license details.\n\n" +
                "You may use, modify, and distribute the generated images freely.");
        text.setLineSpacing(0, 1.4f);
        scroll.addView(text);
        setContentView(scroll);
    }
}