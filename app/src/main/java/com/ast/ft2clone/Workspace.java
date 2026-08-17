package com.ast.ft2clone;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;

final class Workspace {
    static final String DIRECTORY_NAME = "workspace";

    private Workspace() {
    }

    static File getDirectory(Context context) throws IOException {
        File directory;
        if (hasSharedStorageAccess(context)) {
            directory = new File(Environment.getExternalStorageDirectory(), "FT II");
        } else {
            directory = getPrivateDirectory(context);
        }

        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("FT2 workspace could not be created");
        }
        return directory.getCanonicalFile();
    }

    static boolean hasSharedStorageAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    static void migratePrivateWorkspace(Context context) throws IOException {
        if (!hasSharedStorageAccess(context)) {
            return;
        }

        File source = getPrivateDirectory(context);
        File destination = getDirectory(context);
        copyMissingFiles(source, destination);
    }

    private static File getPrivateDirectory(Context context) throws IOException {
        File directory = new File(context.getFilesDir(), DIRECTORY_NAME);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Private FT2 workspace could not be created");
        }
        return directory.getCanonicalFile();
    }

    private static void copyMissingFiles(File source, File destination) throws IOException {
        File[] children = source.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            File target = new File(destination, child.getName());
            if (child.isDirectory()) {
                if (!target.isDirectory() && !target.mkdirs()) {
                    throw new IOException("Could not create " + target);
                }
                copyMissingFiles(child, target);
            } else if (!target.exists()) {
                copyFile(child, target);
            }
        }
    }

    private static void copyFile(File source, File destination) throws IOException {
        try (
            FileInputStream input = new FileInputStream(source);
            FileOutputStream output = new FileOutputStream(destination)
        ) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read > 0) {
                    output.write(buffer, 0, read);
                }
            }
        }
    }

    static String sanitizeFilename(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "imported-file";
        }

        String clean = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (clean.equals(".") || clean.equals("..") || clean.isEmpty()) {
            return "imported-file";
        }

        return clean.length() > 180 ? clean.substring(clean.length() - 180) : clean;
    }

    static File uniqueFile(File directory, String requestedName) {
        String name = sanitizeFilename(requestedName);
        File candidate = new File(directory, name);
        if (!candidate.exists()) {
            return candidate;
        }

        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int index = 2; index < 10_000; index++) {
            candidate = new File(directory, stem + " (" + index + ")" + extension);
            if (!candidate.exists()) {
                return candidate;
            }
        }

        return new File(directory, System.currentTimeMillis() + "-" + name);
    }
}
