package com.notebookpaper.android.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.notebookpaper.android.NotebookPaperGenerator;
import android.util.SparseArray;

public class MainActivity extends AppCompatActivity {

    private ZoomableImageView mPaperImage;
    private EditText mLineEdit;
    private final SparseArray<String> mLineTexts = new SparseArray<>();
    private int mEditingLineIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPaperImage = findViewById(R.id.paperImage);
        mLineEdit = findViewById(R.id.lineEdit);

        // Generate the paper once the view has been laid out, so its
        // dimensions are known and the bitmap can be sized to the view.
        mPaperImage.post(this::generatePaper);

        // Tap a ruled line to edit text directly on that line, anywhere on
        // the page (inside or outside the red margin line).
        mPaperImage.setOnLineTapListener((bitmapX, bitmapY) -> onPaperTapped(bitmapX, bitmapY));

        // Double-tap toggles between fit-to-screen and full zoom.
        mPaperImage.setOnDoubleTapListener(this::toggleZoom);

        mLineEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    commitEdit();
                    return true;
                }
                return false;
            }
        });

        mLineEdit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) commitEdit();
            }
        });

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

        Button saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> saveToGallery());
    }

    private void onPaperTapped(float bitmapX, float bitmapY) {
        if (mLineEdit.getVisibility() == View.VISIBLE) {
            commitEdit();
        }
        int width = Math.max(1, mPaperImage.getWidth());
        int height = Math.max(1, mPaperImage.getHeight());
        int topMargin = NotebookPaperGenerator.topMarginPx(height);
        int spacing = NotebookPaperGenerator.lineSpacingPx(height);
        if (bitmapY < topMargin) return;
        int lineIndex = Math.round((bitmapY - topMargin) / (float) spacing);
        if (lineIndex < 0) return;
        mEditingLineIndex = lineIndex;

        // Position the inline editor on the tapped ruled line in screen space.
        float[] screenPt = new float[2];
        float lineY = topMargin + lineIndex * spacing;
        mPaperImage.mapBitmapToScreen(0f, lineY, screenPt);
        float textSize = mLineEdit.getTextSize();
        mLineEdit.setY(screenPt[1] - textSize);
        mLineEdit.setText(TextUtils.isEmpty(mLineTexts.get(lineIndex)) ? "" : mLineTexts.get(lineIndex));
        mLineEdit.setSelection(mLineEdit.getText().length());
        mLineEdit.setVisibility(View.VISIBLE);
        mLineEdit.requestFocus();

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(mLineEdit, InputMethodManager.SHOW_IMPLICIT);
    }

    private void commitEdit() {
        if (mEditingLineIndex < 0) return;
        String text = mLineEdit.getText().toString();
        if (TextUtils.isEmpty(text)) {
            mLineTexts.remove(mEditingLineIndex);
        } else {
            mLineTexts.put(mEditingLineIndex, text);
        }
        mEditingLineIndex = -1;
        mLineEdit.setVisibility(View.GONE);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mLineEdit.getWindowToken(), 0);
        generatePaper();
    }

    private void generatePaper() {
        int width = Math.max(1, mPaperImage.getWidth());
        int height = Math.max(1, mPaperImage.getHeight());
        mPaperImage.setPaperImage(NotebookPaperGenerator.generate(
                this, width, height, NotebookPaperGenerator.DEFAULT_BUCKLE_COUNT, mLineTexts));
    }

    /**
     * Saves the generated paper to the MediaStore so it appears in the gallery.
     * Uses MediaStore.insertImage, which writes through the scoped-storage
     * provider and needs no storage permission on Android 11+ (minSdk 30).
     * <p>
     * shortcut: insertImage is deprecated since API 29, upgrade to
     * MediaStore.createWriteRequest if Google removes it or a chooser UI is wanted.
     */
    private void saveToGallery() {
        Bitmap bitmap = mPaperImage.getPaperBitmap();
        if (bitmap == null) return;
        String uri = MediaStore.Images.Media.insertImage(
                getContentResolver(), bitmap, "Notebook Paper", "Generated notebook paper");
        Toast.makeText(this,
                uri != null ? R.string.msg_saved : R.string.msg_save_failed,
                Toast.LENGTH_SHORT).show();
    }

    private void toggleZoom() {
        if (mPaperImage.isFullSize()) {
            mPaperImage.zoomToCenter();
        } else {
            mPaperImage.zoomToFullSize();
        }
    }
}
