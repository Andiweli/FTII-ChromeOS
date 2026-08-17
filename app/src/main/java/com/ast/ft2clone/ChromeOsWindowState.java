package com.ast.ft2clone;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

final class ChromeOsWindowState {
    private static final String TAG = "FT2ChromeOS";
    private static final String FULLSCREEN_MARKER = "ft2-fullscreen-enabled";

    private ChromeOsWindowState() {
    }

    static boolean isFullscreenPreferred(Context context) {
        return new File(context.getFilesDir(), FULLSCREEN_MARKER).isFile();
    }

    static void setFullscreenPreferred(Context context, boolean enabled) {
        setMarker(new File(context.getFilesDir(), FULLSCREEN_MARKER), enabled);
        Log.i(TAG, "Remembered FT2 fullscreen preference=" + enabled);
    }

    private static void setMarker(File marker, boolean enabled) {
        if (enabled) {
            try (FileOutputStream output = new FileOutputStream(marker, false)) {
                output.write(1);
                output.getFD().sync();
            } catch (IOException exception) {
                Log.w(TAG, "Could not write ChromeOS state marker " + marker.getName(), exception);
            }
        } else if (marker.exists() && !marker.delete()) {
            Log.w(TAG, "Could not clear ChromeOS state marker " + marker.getName());
        }
    }
}
