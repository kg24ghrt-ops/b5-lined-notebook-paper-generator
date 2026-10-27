package com.notebookpaper.android.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.notebookpaper.android.NotebookPaperGenerator;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // Interactive editing uses a lower resolution than save/share so each
    // keystroke is cheap; the high-res render happens only on Save.
    private static final float WORKING_DPI = 150f;
    private static final float SAVE_DPI = 300f;
    private static final float MM_PER_INCH = 25.4f;

    private ZoomableImageView mPaperImage;
    private EditText mLineEdit;
    private ProgressBar mProgressBar;
    private View mLineHighlight;
    private final SparseArray<String> mLineTexts = new SparseArray<>();
    private int mEditingLineIndex = -1;

    // Cached blank paper (base + texture + lines + holes), regenerated only
    // when the working size changes. Text is drawn onto a copy of this.
    private Bitmap mBlankPaper;
    private int mBlankWidth;
    private int mBlankHeight;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMain = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPaperImage = findViewById(R.id.paperImage);
        mLineEdit = findViewById(R.id.lineEdit);
        mProgressBar = findViewById(R.id.progress);
        mLineHighlight = findViewById(R.id.lineHighlight);

        // Generate the paper on a background thread at a fixed working
        // resolution (not tied to the view size), so editing stays fluid.
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

        Button clearLineButton = findViewById(R.id.clearLineButton);
        clearLineButton.setOnClickListener(v -> clearLine());

        Button clearAllButton = findViewById(R.id.clearAllButton);
        clearAllButton.setOnClickListener(v -> clearAll());

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
        // The tap lands in bitmap space (working resolution), so compute the
        // ruled-line geometry from the paper bitmap, not the view size.
        Bitmap paper = mPaperImage.getPaperBitmap();
        int bmHeight = paper != null ? paper.getHeight() : height;
        int topMargin = NotebookPaperGenerator.topMarginPx(bmHeight);
        int spacing = NotebookPaperGenerator.lineSpacingPx(bmHeight);
        if (bitmapY < topMargin) return;
        int lineIndex = Math.round((bitmapY - topMargin) / (float) spacing);
        if (lineIndex < 0) return;
        mEditingLineIndex = lineIndex;

        // Position the inline editor centered on the tapped ruled line in screen
        // space so the text sits on the ruled line.
        float[] screenPt = new float[2];
        float lineY = topMargin + lineIndex * spacing;
        mPaperImage.mapBitmapToScreen(0f, lineY, screenPt);
        mLineEdit.setText(TextUtils.isEmpty(mLineTexts.get(lineIndex)) ? "" : mLineTexts.get(lineIndex));
        mLineEdit.setSelection(mLineEdit.getText().length());
        mLineEdit.setY(screenPt[1] - mLineEdit.getLineHeight() / 2f);
        mLineEdit.setVisibility(View.VISIBLE);
        mLineEdit.requestFocus();
        positionLineHighlight(lineIndex);

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(mLineEdit, InputMethodManager.SHOW_IMPLICIT);
    }

    private void positionLineHighlight(int lineIndex) {
        int width = Math.max(1, mPaperImage.getWidth());
        int height = Math.max(1, mPaperImage.getHeight());
        int topMargin = NotebookPaperGenerator.topMarginPx(height);
        int spacing = NotebookPaperGenerator.lineSpacingPx(height);
        float lineY = topMargin + lineIndex * spacing;
        float[] pt1 = new float[2];
        float[] pt2 = new float[2];
        mPaperImage.mapBitmapToScreen(0f, lineY, pt1);
        mPaperImage.mapBitmapToScreen(0f, lineY + spacing, pt2);
        float screenLineHeight = pt2[1] - pt1[1];
        float screenTop = pt1[1] - screenLineHeight * 0.25f; // center band on the ruled line
        mLineHighlight.setY(screenTop);
        mLineHighlight.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Math.round(screenLineHeight)));
        mLineHighlight.setVisibility(View.VISIBLE);
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
        mLineHighlight.setVisibility(View.GONE);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mLineEdit.getWindowToken(), 0);
        generatePaper();
    }

    private void clearLine() {
        if (mEditingLineIndex >= 0) {
            mLineTexts.remove(mEditingLineIndex);
            mLineEdit.setText("");
            commitEdit();
        }
    }

    private void clearAll() {
        mLineTexts.clear();
        mEditingLineIndex = -1;
        mLineEdit.setText("");
        mLineEdit.setVisibility(View.GONE);
        mLineHighlight.setVisibility(View.GONE);
        generatePaper();
    }

    private void generatePaper() {
        showLoading(true);
        mExecutor.execute(() -> {
            Bitmap result = generateWorkingPaper();
            mMain.post(() -> {
                mPaperImage.setPaperImage(result); // recycles the old bitmap
                showLoading(false);
            });
        });
    }

    /**
     * Draws the current text onto a copy of the cached blank paper at the
     * working resolution. The blank paper itself is regenerated only when the
     * working size changes.
     */
    private Bitmap generateWorkingPaper() {
        int w = widthPxForDpi(WORKING_DPI);
        int h = heightPxForDpi(WORKING_DPI);
        if (mBlankPaper == null || mBlankWidth != w || mBlankHeight != h) {
            if (mBlankPaper != null && !mBlankPaper.isRecycled()) {
                mBlankPaper.recycle();
            }
            mBlankPaper = NotebookPaperGenerator.generateBlankPaper(w, h,
                    NotebookPaperGenerator.DEFAULT_BUCKLE_COUNT);
            mBlankWidth = w;
            mBlankHeight = h;
        }

        // Text is drawn on a mutable copy so the cached blank paper stays pristine.
        Bitmap working = mBlankPaper.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(working);
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        float sx = w / NotebookPaperGenerator.B5_WIDTH_MM;
        float sy = h / NotebookPaperGenerator.B5_HEIGHT_MM;
        int topMarginPx = Math.round(NotebookPaperGenerator.TOP_MARGIN_MM * sy);
        int lineSpacingPx = Math.round(NotebookPaperGenerator.LINE_SPACING_MM * sy);

        NotebookPaperGenerator.drawTextOnPaper(canvas, paint, this, w, h,
                topMarginPx, lineSpacingPx, sx, sy, typedLines());
        return working;
    }

    private List<NotebookPaperGenerator.TypedLine> typedLines() {
        List<NotebookPaperGenerator.TypedLine> lines = new ArrayList<>(mLineTexts.size());
        for (int i = 0; i < mLineTexts.size(); i++) {
            lines.add(new NotebookPaperGenerator.TypedLine(mLineTexts.keyAt(i),
                    mLineTexts.valueAt(i)));
        }
        return lines;
    }

    private int widthPxForDpi(float dpi) {
        return Math.round((NotebookPaperGenerator.B5_WIDTH_MM / MM_PER_INCH) * dpi);
    }

    private int heightPxForDpi(float dpi) {
        return Math.round((NotebookPaperGenerator.B5_HEIGHT_MM / MM_PER_INCH) * dpi);
    }

    private void showLoading(boolean loading) {
        mProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        mPaperImage.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
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
        showLoading(true);
        mExecutor.execute(() -> {
            int w = widthPxForDpi(SAVE_DPI);
            int h = heightPxForDpi(SAVE_DPI);
            Bitmap highRes = NotebookPaperGenerator.generate(this, w, h,
                    NotebookPaperGenerator.DEFAULT_BUCKLE_COUNT, typedLines());
            String uri = MediaStore.Images.Media.insertImage(
                    getContentResolver(), highRes, "Notebook Paper",
                    "Generated notebook paper");
            highRes.recycle();
            mMain.post(() -> {
                showLoading(false);
                Toast.makeText(this,
                        uri != null ? R.string.msg_saved : R.string.msg_save_failed,
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    protected void onDestroy() {
        mExecutor.shutdownNow();
        if (mBlankPaper != null && !mBlankPaper.isRecycled()) {
            mBlankPaper.recycle();
            mBlankPaper = null;
        }
        Bitmap current = mPaperImage.getPaperBitmap();
        if (current != null && !current.isRecycled() && current != mBlankPaper) {
            current.recycle();
        }
        super.onDestroy();
    }

    private void toggleZoom() {
        if (mPaperImage.isFullSize()) {
            mPaperImage.zoomToCenter();
        } else {
            mPaperImage.zoomToFullSize();
        }
    }
}
