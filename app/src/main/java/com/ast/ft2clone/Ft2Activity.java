package com.ast.ft2clone;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.libsdl.app.SDLActivity;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Ft2Activity extends SDLActivity {
    private static final String TAG = "FT2ChromeOS";
    private static final long[] IMMERSIVE_RETRY_DELAYS_MS = {0L, 100L, 350L, 1000L, 2000L};
    private static final int COMMAND_SET_FULLSCREEN_PREFERENCE = 0x8001;
    private static final int COMMAND_PREPARE_EXIT = 0x8002;
    private static final int EXIT_BACKGROUND_COLOR = Color.rgb(16, 16, 16);

    private boolean launchFullscreen;
    private boolean finishScheduled;
    private boolean taskRemovalCommitted;
    private View exitCover;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        launchFullscreen = getIntent() != null
            && getIntent().getBooleanExtra(LauncherActivity.EXTRA_START_FULLSCREEN, false);
        setIntent(importIntentFile(getIntent()));
        super.onCreate(savedInstanceState);

        /* SurfaceView is rendered in a separate surface. Give the normal View
         * hierarchy an opaque fallback so Android never composites the theme's
         * default color while the native surface is being replaced or removed.
         */
        if (mLayout != null) {
            mLayout.setBackgroundColor(EXIT_BACKGROUND_COLOR);
        }

        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0);
        }

        if (launchFullscreen) {
            scheduleImmersiveFullscreen();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (shouldApplyImmersiveFullscreen()) {
            scheduleImmersiveFullscreen();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && shouldApplyImmersiveFullscreen()) {
            scheduleImmersiveFullscreen();
        }
    }

    @Override
    protected boolean onUnhandledMessage(int command, Object param) {
        if (command == COMMAND_SET_FULLSCREEN_PREFERENCE) {
            boolean enabled = param instanceof Integer && ((Integer) param) != 0;
            ChromeOsWindowState.setFullscreenPreferred(this, enabled);
            return true;
        }

        if (command == COMMAND_PREPARE_EXIT) {
            showExitCover();
            return true;
        }

        return super.onUnhandledMessage(command, param);
    }

    @Override
    public void finish() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(this::finish);
            return;
        }

        if (finishScheduled) {
            return;
        }
        finishScheduled = true;

        /* SDLMain calls finish() immediately after the native renderer and its
         * Surface have shut down. First cover the SurfaceView, then move the
         * complete task behind ChromeOS before destroying the Activity. This
         * keeps the system from displaying the task's empty transition window.
         */
        showExitCover();
        View decorView = getWindow().getDecorView();
        decorView.postOnAnimation(this::moveTaskBehindAndScheduleRemoval);
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        if (!shouldApplyImmersiveFullscreen()) {
            super.onSystemUiVisibilityChange(visibility);
            return;
        }

        int hiddenFlags = View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        if ((visibility & hiddenFlags) != hiddenFlags) {
            getWindow().getDecorView().post(this::applyImmersiveFullscreen);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        Intent importedIntent = importIntentFile(intent);
        setIntent(importedIntent);
        Uri uri = importedIntent != null ? importedIntent.getData() : null;
        if (uri != null && "file".equals(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null) {
                SDLActivity.onNativeDropFile(path);
            }
        }
    }

    @Override
    protected void onDestroy() {
        boolean terminateTrackerProcess = !isChangingConfigurations();

        /* Once SDL removes its Surface, ChromeOS can expose the Activity
         * background for a fraction of a frame. Force that fallback to the
         * same dark color as FT2 before SDL performs its final cleanup.
         */
        getWindow().setBackgroundDrawable(new ColorDrawable(EXIT_BACKGROUND_COLOR));
        getWindow().getDecorView().setBackgroundColor(EXIT_BACKGROUND_COLOR);

        /* SDLActivity waits for SDL_main(), sends its final quit event and
         * releases SDL's native Android objects here. Do not terminate the
         * dedicated tracker process before that cleanup has completed.
         */
        super.onDestroy();

        if (terminateTrackerProcess) {
            /* FT2 and SDL contain process-global state that is not designed for
             * a second SDL_main() run. The tracker Activity lives in its own
             * private process, so ending it after a clean shutdown leaves the
             * launcher and DocumentsProvider alive while guaranteeing a fresh
             * native process on the next start.
             */
            Log.i(TAG, "FT2 shutdown complete; terminating tracker process");
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    private void showExitCover() {
        if (exitCover != null || isDestroyed()) {
            return;
        }

        FrameLayout cover = new FrameLayout(this);
        cover.setBackgroundColor(EXIT_BACKGROUND_COLOR);
        cover.setClickable(true);
        cover.setFocusable(true);
        addContentView(
            cover,
            new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        );
        cover.bringToFront();
        cover.requestFocus();
        exitCover = cover;
    }

    private void moveTaskBehindAndScheduleRemoval() {
        if (taskRemovalCommitted) {
            return;
        }

        if (Build.VERSION.SDK_INT < 34) {
            overridePendingTransition(0, 0);
        }

        /* The launcher becomes visible while this Activity and its dark cover
         * are still alive. Finish and remove the background task shortly after
         * the compositor has switched away from it.
         */
        moveTaskToBack(true);
        new Handler(Looper.getMainLooper()).postDelayed(this::removeTaskAndFinish, 50L);
    }

    private void removeTaskAndFinish() {
        if (taskRemovalCommitted) {
            return;
        }
        taskRemovalCommitted = true;

        super.finishAndRemoveTask();
        if (Build.VERSION.SDK_INT < 34) {
            overridePendingTransition(0, 0);
        }
    }

    private void scheduleImmersiveFullscreen() {
        View decorView = getWindow().getDecorView();
        for (long delay : IMMERSIVE_RETRY_DELAYS_MS) {
            decorView.postDelayed(this::applyImmersiveFullscreen, delay);
        }
    }

    private void applyImmersiveFullscreen() {
        if (isFinishing() || isDestroyed() || !shouldApplyImmersiveFullscreen()) {
            return;
        }

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

        int legacyFlags = View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        window.getDecorView().setSystemUiVisibility(legacyFlags);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        }
    }

    private boolean shouldApplyImmersiveFullscreen() {
        return launchFullscreen || mFullscreenModeActive;
    }

    private Intent importIntentFile(Intent original) {
        if (original == null) {
            return new Intent();
        }

        Uri source = original.getData();
        if (source == null && Intent.ACTION_SEND.equals(original.getAction())) {
            source = original.getParcelableExtra(Intent.EXTRA_STREAM);
        }

        if (source == null || "file".equals(source.getScheme())) {
            return original;
        }

        try {
            File workspace = Workspace.getDirectory(this);
            String displayName = getDisplayName(source);
            File destination = Workspace.uniqueFile(workspace, displayName);
            copyUri(source, destination);

            Intent replacement = new Intent(original);
            replacement.setAction(Intent.ACTION_VIEW);
            replacement.setData(Uri.fromFile(destination));
            replacement.setFlags(original.getFlags() & ~Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            Log.i(TAG, "Imported file into FT2 workspace: " + destination.getName());
            return replacement;
        } catch (IOException | SecurityException exception) {
            Log.e(TAG, "Could not import shared file", exception);
            return original;
        }
    }

    private String getDisplayName(Uri uri) {
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(
                uri,
                new String[] {OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
            )) {
                if (cursor != null && cursor.moveToFirst()) {
                    int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (column >= 0) {
                        String displayName = cursor.getString(column);
                        if (displayName != null) {
                            return displayName;
                        }
                    }
                }
            }
        }

        String lastSegment = uri.getLastPathSegment();
        return lastSegment != null ? lastSegment : "imported-file";
    }

    private void copyUri(Uri source, File destination) throws IOException {
        try (
            InputStream rawInput = getContentResolver().openInputStream(source);
            BufferedInputStream input = rawInput != null ? new BufferedInputStream(rawInput) : null;
            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination))
        ) {
            if (input == null) {
                throw new IOException("Content resolver returned no input stream");
            }

            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
        } catch (IOException exception) {
            if (destination.exists() && !destination.delete()) {
                Log.w(TAG, "Incomplete import could not be removed");
            }
            throw exception;
        }
    }
}
