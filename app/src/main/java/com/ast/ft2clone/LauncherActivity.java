package com.ast.ft2clone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public final class LauncherActivity extends Activity {
    private static final String TAG = "FT2ChromeOS";
    private static final int STORAGE_PERMISSION_REQUEST = 1201;
    private static final int CONFIG_WINDOW_FLAGS_OFFSET = 223;
    private static final int START_IN_FULLSCREEN_FLAG = 128;
    public static final String EXTRA_START_FULLSCREEN =
        "com.ast.ft2clone.extra.START_FULLSCREEN";

    private boolean waitingForStorageSettings;
    private boolean trackerStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Workspace.hasSharedStorageAccess(this)) {
            launchTracker();
        } else {
            showStorageAccessDialog();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForStorageSettings && !trackerStarted) {
            waitingForStorageSettings = false;
            if (Workspace.hasSharedStorageAccess(this)) {
                launchTracker();
            } else {
                showStorageAccessDialog();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            launchTracker();
        }
    }

    private void showStorageAccessDialog() {
        if (isFinishing() || trackerStarted) {
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle(R.string.storage_access_title)
            .setMessage(R.string.storage_access_message)
            .setPositiveButton(R.string.storage_access_allow, (dialog, which) -> requestStorageAccess())
            .setNegativeButton(R.string.storage_access_workspace_only, (dialog, which) -> launchTracker())
            .setOnCancelListener(dialog -> launchTracker())
            .show();
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            waitingForStorageSettings = true;
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            try {
                startActivity(intent);
            } catch (RuntimeException exception) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(
                new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                STORAGE_PERMISSION_REQUEST
            );
        }
    }

    private void launchTracker() {
        if (trackerStarted) {
            return;
        }
        trackerStarted = true;

        try {
            Workspace.migratePrivateWorkspace(this);
        } catch (IOException exception) {
            Log.w(TAG, "Could not migrate the private workspace", exception);
        }

        boolean startFullscreen = isStartFullscreenEnabled()
            || ChromeOsWindowState.isFullscreenPreferred(this);
        Intent intent = new Intent(this, Ft2Activity.class);
        intent.putExtra(EXTRA_START_FULLSCREEN, startFullscreen);
        Log.i(TAG, "Starting maximized FT II fullscreen=" + startFullscreen);

        /* LauncherActivity itself is the maximized ChromeOS root. Starting the
         * tracker in the same task preserves that window container. This is
         * intentionally not a NEW_TASK launch: on ChromeOS the original root
         * window controls the size of the complete Activity stack.
         */
        startActivity(intent);
        finish();
    }

    private boolean isStartFullscreenEnabled() {
        File config = new File(getFilesDir(), "FT2.CFG");
        if (!config.isFile() || config.length() <= CONFIG_WINDOW_FLAGS_OFFSET) {
            return false;
        }

        try (RandomAccessFile input = new RandomAccessFile(config, "r")) {
            input.seek(CONFIG_WINDOW_FLAGS_OFFSET);
            int encrypted = input.readUnsignedByte();
            int xorKey = (CONFIG_WINDOW_FLAGS_OFFSET * 7) & 0xFF;
            int windowFlags = encrypted ^ xorKey;
            return (windowFlags & START_IN_FULLSCREEN_FLAG) != 0;
        } catch (IOException exception) {
            Log.w(TAG, "Could not read FT2 fullscreen preference", exception);
            return false;
        }
    }
}
