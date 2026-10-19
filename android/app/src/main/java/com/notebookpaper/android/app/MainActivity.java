package com.notebookpaper.android.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.notebookpaper.android.NotebookPaperGenerator;

public class MainActivity extends AppCompatActivity {

    private ZoomableImageView mPaperImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPaperImage = findViewById(R.id.paperImage);

        // Render at screen width, proportional height for a preview.
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = Math.round(width * (250.0f / 176.0f));

        mPaperImage.setPaperImage(NotebookPaperGenerator.generate(width, height));

        // Double-tap toggles between fit-to-screen and full zoom.
        mPaperImage.setOnDoubleTapListener(this::toggleZoom);

        Button regenerate = findViewById(R.id.regenerateButton);
        regenerate.setOnClickListener(v -> {
            mPaperImage.setPaperImage(NotebookPaperGenerator.generate(width, height));
            Toast.makeText(this, "Paper regenerated", Toast.LENGTH_SHORT).show();
        });

        Button resetButton = findViewById(R.id.resetButton);
        resetButton.setOnClickListener(v -> mPaperImage.zoomToCenter());

        Button termsButton = findViewById(R.id.termsButton);
        termsButton.setOnClickListener(v -> {
            startActivity(new Intent(this, TermsOfUseActivity.class));
        });
    }

    private void toggleZoom() {
        if (mPaperImage.isFullSize()) {
            mPaperImage.zoomToCenter();
        } else {
            mPaperImage.zoomToFullSize();
        }
    }
}