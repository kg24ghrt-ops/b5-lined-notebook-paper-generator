package com.notebookpaper.android.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.notebookpaper.android.NotebookPaperGenerator;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Render at screen width, proportional height for a preview.
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = Math.round(width * (250.0f / 176.0f));

        setPaperImage(NotebookPaperGenerator.generate(width, height));

        Button regenerate = findViewById(R.id.regenerateButton);
        regenerate.setOnClickListener(v -> {
            setPaperImage(NotebookPaperGenerator.generate(width, height));
            Toast.makeText(this, "Paper regenerated", Toast.LENGTH_SHORT).show();
        });

        Button termsButton = findViewById(R.id.termsButton);
        termsButton.setOnClickListener(v -> {
            startActivity(new Intent(this, TermsOfUseActivity.class));
        });
    }

    private void setPaperImage(android.graphics.Bitmap bitmap) {
        ImageView paperImage = findViewById(R.id.paperImage);
        paperImageBitmap(bitmap);
    }
}