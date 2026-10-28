package com.notebookpaper.android.app;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.SparseArray;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.notebookpaper.android.NotebookPaperGenerator;

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
    private final SparseArray<String> mLineTexts = new SparseArray<>();
    private final SparseArray<NotebookPaperGenerator.HorizontalPosition> mLinePositions =
            new SparseArray<>();
    private final SparseArray<Float> mLineTapXmm = new SparseArray<>();
    private int mEditingLineIndex = -1;

    private static final int EDIT_DEBOUNCE_MS = 200;
    private final Handler mEditHandler = new Handler(Looper.getMainLooper());
    private Runnable mDebounceRender;

    private static final int CURSOR_BLINK_MS = 520;
    private final Handler mCursorHandler = new Handler(Looper.getMainLooper());
    private final Runnable mCursorBlink = new Runnable() {
        @Override
        public void run() {
            if (mEditingLineIndex < 0) return;
            mCursorVisible = !mCursorVisible;
            renderPaper();
            mCursorHandler.postDelayed(this, CURSOR_BLINK_MS);
        }
    };
    private boolean mCursorVisible;

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

        // Restore any previously saved notes.
        loadNotes();

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

        mLineEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mEditingLineIndex < 0) return;
                String text = s.toString();
                if (TextUtils.isEmpty(text)) {
                    mLineTexts.remove(mEditingLineIndex);
                } else {
                    mLineTexts.put(mEditingLineIndex, text);
                }
                scheduleDebouncedRender();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        mDebounceRender = () -> {
            saveNotes();
            renderPaper();
        };

                Button saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> saveToGallery());

        Button shareButton = findViewById(R.id.shareButton);
        shareButton.setOnClickListener(v -> sharePaper());

        Button overflowButton = findViewById(R.id.overflowButton);
        overflowButton.setOnClickListener(v -> showOverflowMenu(v));
    }

    private void onPaperTapped(float bitmapX, float bitmapY) {
        if (mEditingLineIndex >= 0) {
            commitEdit();
        }
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
        int w = paper != null ? paper.getWidth() : Math.max(1, mPaperImage.getWidth());
        float sx = w / (float) NotebookPaperGenerator.B5_WIDTH_MM;
        List<NotebookPaperGenerator.TypedLine> lines = typedLinesCopy();
        List<NotebookPaperGenerator.RenderedSpan> spans = NotebookPaperGenerator.renderedSpans(
                this, lines, w, bmHeight, topMargin, spacing, sx, 1f);
        int tappedLine = lineIndex;
        for (NotebookPaperGenerator.RenderedSpan span : spans) {
            if (tappedLine >= span.firstLine && tappedLine < span.firstLine + span.lineCount) {
                tappedLine = span.lineIndex;
                break;
            }
        }
        lineIndex = tappedLine;

        NotebookPaperGenerator.HorizontalPosition position = mLinePositions.get(lineIndex);
        if (position == null && paper != null) {
            float marginLineX = NotebookPaperGenerator.LEFT_MARGIN_MM * sx;
            position = bitmapX < marginLineX
                    ? NotebookPaperGenerator.HorizontalPosition.LEFT_OF_MARGIN
                    : NotebookPaperGenerator.HorizontalPosition.RIGHT_OF_MARGIN;
            mLinePositions.put(lineIndex, position);
            mLineTapXmm.put(lineIndex, bitmapX / sx);
        }
        mEditingLineIndex = lineIndex;

        mLineEdit.setText(TextUtils.isEmpty(mLineTexts.get(lineIndex)) ? "" : mLineTexts.get(lineIndex));
        mLineEdit.setSelection(mLineEdit.getText().length());
        mLineEdit.requestFocus();
        renderPaper();
        startCursorBlink();

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        mLineEdit.post(() -> imm.showSoftInput(mLineEdit, InputMethodManager.SHOW_IMPLICIT));
    }

    private void startCursorBlink() {
        mCursorVisible = true;
        mCursorHandler.removeCallbacks(mCursorBlink);
        mCursorHandler.post(mCursorBlink);
    }

    private void stopCursorBlink() {
        mCursorHandler.removeCallbacks(mCursorBlink);
        mCursorVisible = false;
    }

    private void commitEdit() {
        if (mEditingLineIndex < 0) return;
        stopCursorBlink();
        mEditingLineIndex = -1;
        mLineEdit.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mLineEdit.getWindowToken(), 0);
    }

    private void clearLine() {
        if (mEditingLineIndex >= 0) {
            mLineTexts.remove(mEditingLineIndex);
            mLinePositions.remove(mEditingLineIndex);
            mLineTapXmm.remove(mEditingLineIndex);
            mLineEdit.setText("");
            commitEdit();
            scheduleDebouncedRender();
        }
    }

    private void clearAll() {
        mLineTexts.clear();
        mLinePositions.clear();
        mLineTapXmm.clear();
        mEditingLineIndex = -1;
        mLineEdit.setText("");
        stopCursorBlink();
        scheduleDebouncedRender();
    }

    private void generatePaper() {
        showLoading(true);
        List<NotebookPaperGenerator.TypedLine> lines = typedLinesCopy();
        mExecutor.execute(() -> {
            Bitmap result = generateWorkingPaper(lines, -1, -1, false);
            mMain.post(() -> {
                mPaperImage.setPaperImage(result); // recycles the old bitmap
                showLoading(false);
            });
        });
    }

    private void renderPaper() {
        List<NotebookPaperGenerator.TypedLine> lines = typedLinesCopy();
        final int cursorLineIndex;
        final int cursorOffset;
        final boolean cursorVisible;
        if (mEditingLineIndex >= 0) {
            cursorLineIndex = mEditingLineIndex;
            cursorOffset = Math.max(0, Math.min(mLineEdit.getSelectionStart(),
                    mLineEdit.getText().length()));
            cursorVisible = mCursorVisible;
        } else {
            cursorLineIndex = -1;
            cursorOffset = -1;
            cursorVisible = false;
        }
        mExecutor.execute(() -> {
            Bitmap result = generateWorkingPaper(lines, cursorLineIndex, cursorOffset, cursorVisible);
            mMain.post(() -> mPaperImage.setPaperImage(result)); // recycles the old bitmap
        });
    }

    private void scheduleDebouncedRender() {
        mEditHandler.removeCallbacks(mDebounceRender);
        mEditHandler.postDelayed(mDebounceRender, EDIT_DEBOUNCE_MS);
    }

    /**
     * Draws the current text onto a copy of the cached blank paper at the
     * working resolution. The blank paper itself is regenerated only when the
     * working size changes.
     */
    private Bitmap generateWorkingPaper(List<NotebookPaperGenerator.TypedLine> lines,
                                        int cursorLineIndex, int cursorOffset, boolean cursorVisible) {
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

        if (mEditingLineIndex >= 0) {
            int marginLeftPx = Math.round(NotebookPaperGenerator.LEFT_MARGIN_MM * sx);
            paint.setColor(Color.argb(70, 30, 130, 210));
            float highlightY = topMarginPx + mEditingLineIndex * lineSpacingPx;
            canvas.drawRect(marginLeftPx, highlightY, w, highlightY + lineSpacingPx, paint);
        }

        NotebookPaperGenerator.drawTextOnPaper(canvas, paint, this, w, h,
                topMarginPx, lineSpacingPx, sx, sy, lines,
                cursorLineIndex, cursorOffset, cursorVisible);
        return working;
    }

    /**
     * Returns a snapshot of the current typed lines, copied on the main thread
     * so background rendering never reads the live SparseArray concurrently.
     */
    private List<NotebookPaperGenerator.TypedLine> typedLinesCopy() {
        List<NotebookPaperGenerator.TypedLine> lines = new ArrayList<>(mLineTexts.size());
        for (int i = 0; i < mLineTexts.size(); i++) {
            int index = mLineTexts.keyAt(i);
            lines.add(new NotebookPaperGenerator.TypedLine(index,
                    mLineTexts.valueAt(i),
                    mLinePositions.get(index,
                            NotebookPaperGenerator.HorizontalPosition.RIGHT_OF_MARGIN),
                    mLineTapXmm.get(index, Float.NaN)));
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
                int firstTab = line.indexOf('\t');
                if (firstTab < 0) continue;
                int secondTab = line.indexOf('\t', firstTab + 1);
                int index = Integer.parseInt(line.substring(0, firstTab));
                String text;
                if (secondTab < 0) {
                    text = line.substring(firstTab + 1).replace("\u0001", "\n");
                } else {
                    text = line.substring(firstTab + 1, secondTab).replace("\u0001", "\n");
                }
                NotebookPaperGenerator.HorizontalPosition position =
                        NotebookPaperGenerator.HorizontalPosition.RIGHT_OF_MARGIN;
                if (secondTab >= 0 && secondTab + 1 < line.length()) {
                    try {
                        int ordinal = Integer.parseInt(line.substring(secondTab + 1));
                        NotebookPaperGenerator.HorizontalPosition[] values =
                                NotebookPaperGenerator.HorizontalPosition.values();
                        if (ordinal >= 0 && ordinal < values.length) {
                            position = values[ordinal];
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                mLineTexts.put(index, text);
                mLinePositions.put(index, position);
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
                NotebookPaperGenerator.HorizontalPosition position =
                        mLinePositions.get(index,
                                NotebookPaperGenerator.HorizontalPosition.RIGHT_OF_MARGIN);
                w.println(index + "\t" + text.replace("\n", "\u0001") + "\t"
                        + position.ordinal());
            }
        } catch (IOException ignored) {
            // Best-effort persistence; notes are non-critical.
        }
    }

    /**
     * Saves the generated paper to the MediaStore so it appears in the gallery.
     * Writes through scoped storage using ContentValues. On API 26-28 this may
     * require WRITE_EXTERNAL_STORAGE; SecurityException is caught and reported
     * as a save failure rather than crashing the background thread.
     */
    private void saveToGallery() {
        showLoading(true);
        mExecutor.execute(() -> {
            int w = widthPxForDpi(SAVE_DPI);
            int h = heightPxForDpi(SAVE_DPI);
            Bitmap highRes = NotebookPaperGenerator.generate(this, w, h,
                    NotebookPaperGenerator.DEFAULT_BUCKLE_COUNT, typedLinesCopy());
            Uri uri = null;
            try {
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
            } catch (SecurityException ignored) {
                uri = null;
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
                    NotebookPaperGenerator.DEFAULT_BUCKLE_COUNT, typedLinesCopy());
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
    protected void onPause() {
        super.onPause();
        mEditHandler.removeCallbacks(mDebounceRender);
        saveNotes();
        stopCursorBlink();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mEditingLineIndex >= 0) {
            startCursorBlink();
        }
    }

    @Override
    protected void onDestroy() {
        mEditHandler.removeCallbacksAndMessages(null);
        mCursorHandler.removeCallbacksAndMessages(null);
        mExecutor.shutdownNow();
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
