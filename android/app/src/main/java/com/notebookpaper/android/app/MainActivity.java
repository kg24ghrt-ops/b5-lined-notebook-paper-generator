package com.notebookpaper.android.app;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.notebookpaper.android.NotebookPaperGenerator;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
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
    private static final String NOTES_FILE = "notes.txt";

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

        // Restore any previously saved notes.
        loadNotes();

        // Generate the paper on a background thread at a fixed working
        // resolution (not tied to the view size), so editing stays fluid.
        mPaperImage.post(this::generatePaper);

        // Tap a ruled line to edit text directly on that line, anywhere on
        // the page (inside or outside the red margin line).
        mPaperImage.setOnLineTapListener((bitmapX, bitmapY) -> onPaperTapped(bitmapX, bitmapY));

        // Hide the active-line highlight while the user pans or zooms; it is
        // repositioned correctly on the next tap.
        mPaperImage.setOnTransformListener(() -> mLineHighlight.setVisibility(View.GONE));

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

                Button saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> saveToGallery());

        Button shareButton = findViewById(R.id.shareButton);
        shareButton.setOnClickListener(v -> sharePaper());

        Button overflowButton = findViewById(R.id.overflowButton);
        overflowButton.setOnClickListener(v -> showOverflowMenu(v));

        mPaperImage.setOnTransformListener(() -> mLineHighlight.setVisibility(View.GONE));
        mPaperImage.setOnTransformEndListener(() -> {
            if (mEditingLineIndex >= 0) {
                positionLineHighlight(mEditingLineIndex);
            }
        });
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
        // The highlight sits on the paper bitmap (working resolution), so
        // compute ruled-line geometry from the bitmap, not the view size.
        Bitmap paper = mPaperImage.getPaperBitmap();
        int height = paper != null ? paper.getHeight() : Math.max(1, mPaperImage.getHeight());
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
        saveNotes();
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
        saveNotes();
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
     * Persists typed lines to internal storage so they survive process death.
     * Format: one line per entry, "index\\ttext" with newlines escaped as
     * U+0001 (unit separator, very unlikely in user text).
     */
    private void loadNotes() {
        File file = new File(getFilesDir(), NOTES_FILE);
        if (!file.exists()) return;
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                int index = Integer.parseInt(line.substring(0, tab));
                String text = line.substring(tab + 1).replace("\u0001", "\n");
                mLineTexts.put(index, text);
            }
        } catch (IOException | NumberFormatException ignored) {
            // Corrupt or missing notes file: start fresh.
        }
    }

    private void saveNotes() {
        File file = new File(getFilesDir(), NOTES_FILE);
        try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
            for (int i = 0; i < mLineTexts.size(); i++) {
                int index = mLineTexts.keyAt(i);
                String text = mLineTexts.valueAt(i);
                if (text == null) continue;
                w.println(index + "\t" + text.replace("\n", "\u0001"));
            }
        } catch (IOException ignored) {
            // Best-effort persistence; notes are non-critical.
        }
    }

    /**
     * Saves the generated paper to the MediaStore so it appears in the gallery.
     * Writes through scoped storage using ContentValues (the non-deprecated
     * replacement for insertImage), needs no storage permission on API 26+.
     */
    private void saveToGallery() {
        showLoading(true);
        mExecutor.execute(() -> {
            int w = widthPxForDpi(SAVE_DPI);
            int h = heightPxForDpi(SAVE_DPI);
            Bitmap highRes = NotebookPaperGenerator.generate(this, w, h,
                    NotebookPaperGenerator.DEFAULT_BUCKLE_COUNT, typedLines());
            Uri uri = null;
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "Notebook Paper.png");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            Uri imageUri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (imageUri != null) {
                try (OutputStream os = getContentResolver().openOutputStream(imageUri)) {
                    if (os != null) highRes.compress(Bitmap.CompressFormat.PNG, 100, os);
                    uri = imageUri;
                } catch (IOException ignored) {
                    uri = null;
                }
            }
            highRes.recycle();
            final Uri finalUri = uri;
            mMain.post(() -> {
                showLoading(false);
                Toast.makeText(this,
                        finalUri != null ? R.string.msg_saved : R.string.msg_save_failed,
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    /**
     * Shares the current paper (high resolution) via a share sheet. The PNG is
     * written to the app cache and exposed through FileProvider so other apps
     * can receive it.
     */
    private void sharePaper() {
        showLoading(true);
        mExecutor.execute(() -> {
            int w = widthPxForDpi(SAVE_DPI);
            int h = heightPxForDpi(SAVE_DPI);
            Bitmap highRes = NotebookPaperGenerator.generate(this, w, h,
                    NotebookPaperGenerator.DEFAULT_BUCKLE_COUNT, typedLines());
            File cacheFile = new File(getCacheDir(), "notebook-paper.png");
            Uri uri = null;
            try (FileOutputStream os = new FileOutputStream(cacheFile)) {
                highRes.compress(Bitmap.CompressFormat.PNG, 100, os);
                uri = FileProvider.getUriForFile(this,
                        "com.notebookpaper.android.app.fileprovider", cacheFile);
            } catch (IOException ignored) {
                uri = null;
            }
            highRes.recycle();
            final Uri finalUri = uri;
            mMain.post(() -> {
                showLoading(false);
                if (finalUri == null) {
                    Toast.makeText(this, R.string.msg_save_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("image/png");
                share.putExtra(Intent.EXTRA_STREAM, finalUri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, "Share notebook paper"));
            });
        });
    }

    /**
     * Shows an overflow popup with secondary actions.
     */
    private void showOverflowMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.inflate(R.menu.overflow_menu);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_clear_line) {
                clearLine();
                return true;
            }
            if (id == R.id.action_clear_all) {
                clearAll();
                return true;
            }
            if (id == R.id.action_regenerate) {
                generatePaper();
                Toast.makeText(this, "Paper regenerated", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (id == R.id.action_reset_zoom) {
                mPaperImage.zoomToCenter();
                return true;
            }
            if (id == R.id.action_terms) {
                startActivity(new Intent(this, TermsOfUseActivity.class));
                return true;
            }
            return false;
        });
        menu.show();
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
