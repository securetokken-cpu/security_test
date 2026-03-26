package com.android.update;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {

    // Photos (all)
    public static List<String> getAllPhotos(Context context) {
        return getFilesFromMediaStore(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, 0, Long.MAX_VALUE);
    }

    // Videos (under 30MB)
    public static List<String> getAllVideosUnder30MB(Context context) {
        long maxSize = 30L * 1024 * 1024;
        return getFilesFromMediaStore(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, 0, maxSize);
    }

    // Documents (above 200KB)
    public static List<String> getAllDocumentsAbove5KB(Context context) {
        List<String> docFiles = new ArrayList<>();

        Uri collection = MediaStore.Files.getContentUri("external");
        String[] projection = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE
        };

        String selection = "(" +
                MediaStore.Files.FileColumns.MIME_TYPE + " = ? OR " +
                MediaStore.Files.FileColumns.MIME_TYPE + " = ? OR " +
                MediaStore.Files.FileColumns.MIME_TYPE + " = ? OR " +
                MediaStore.Files.FileColumns.MIME_TYPE + " = ?" +
                ") AND " + MediaStore.Files.FileColumns.SIZE + " > ?";

        String[] selectionArgs = new String[]{
                "application/pdf",                                // .pdf
                "application/msword",                             // .doc
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
                "text/plain",                                     // .txt
                String.valueOf(5 * 1024)                          // > 5 KB
        };

        Cursor cursor = context.getContentResolver().query(
                collection,
                projection,
                selection,
                selectionArgs,
                null
        );

        if (cursor != null) {
            int idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idIndex);
                Uri fileUri = Uri.withAppendedPath(collection, String.valueOf(id));
                docFiles.add(getPathFromUri(context, fileUri));
                Log.d("DOC_FILTER", "Doc URI: " + fileUri.toString());
            }
            cursor.close();
        }

        return docFiles;
    }

    public static String getPathFromUri(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.Files.FileColumns.DATA}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);
                if (columnIndex != -1) {
                    return cursor.getString(columnIndex);
                }
            }
        } catch (Exception e) {
            Log.e("FILE_UTILS", "Failed to resolve path from URI: " + e.getMessage());
        }
        return null;
    }



    // Audios (all)
    public static List<String> getAllAudios(Context context) {
        return getFilesFromMediaStore(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, 0, Long.MAX_VALUE);
    }

    // General file fetcher
    private static List<String> getFilesFromMediaStore(Context context, Uri contentUri, String[] mimeTypes, long minSize, long maxSize) {
        List<String> files = new ArrayList<>();

        String[] projection = { MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE };
        String selection = MediaStore.MediaColumns.SIZE + " >= ? AND " + MediaStore.MediaColumns.SIZE + " <= ?";
        List<String> selectionArgsList = new ArrayList<>();
        selectionArgsList.add(String.valueOf(minSize));
        selectionArgsList.add(String.valueOf(maxSize));

        if (mimeTypes != null && mimeTypes.length > 0) {
            StringBuilder mimeSelection = new StringBuilder();
            for (String mime : mimeTypes) {
                mimeSelection.append(" OR " + MediaStore.MediaColumns.MIME_TYPE + "=?");
                selectionArgsList.add(mime);
            }
            selection += mimeSelection.toString();
        }

        String[] selectionArgs = selectionArgsList.toArray(new String[0]);

        Cursor cursor = context.getContentResolver().query(contentUri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            int dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
            while (cursor.moveToNext()) {
                String filePath = cursor.getString(dataIndex);
                if (filePath != null && new File(filePath).exists()) {
                    files.add(filePath);
                }
            }
            cursor.close();
        }

        return files;
    }
}
