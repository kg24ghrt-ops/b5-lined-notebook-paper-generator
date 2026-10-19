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

        // Generate the paper once the view has been laid out, so its
        // dimensions are known and the bitmap can be sized to the view.
        mPaperImage.post(this::generatePaper);

        // Double-tap toggles between fit-to-screen and full zoom.
        mPaperImage.setOnDoubleTapListener(this::toggleZoom);

        Button regenerate = findViewById(R.id.regenerateButton);
        regenerate.setOnClickListener(v -> {
            generatePaper();
            Toast.makeText(this, "Paper regenerated", Toast.LENGTH_SHORT).show();
        });

        Button resetButton = findViewById(R.id.resetButton);
        resetButton.setOnClickListener(v -> mPaperImage.zoomToCenter());

        Button termsButton = findViewById(R.id.termsButton);
        termsButton.setOnClickListener(v -> {
            startActivity(new Intent(this, TermsOfUseActivity.class));
        });
    }

    private void generatePaper() {
        // Size the bitmap to the actual view dimensions (accounts for padding
        // and the button row sharing vertical space).
        int width = Math.max(1, mPaperImage.getWidth());
        int height = Math.max(1, mPaperImage.getHeight());
        mPaperImage.setPaperImage(NotebookPaperGenerator.generate(width, height));
    }

    private void toggleZoom() {
        if (mPaperImage.isFullSize()) {
            mPaperImage.zoomToCenter();
        } else {
            mPaperImage.zoomToFullSize();
        }
    }
}