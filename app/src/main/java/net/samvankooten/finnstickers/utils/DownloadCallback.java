package net.samvankooten.finnstickers.utils;

import android.content.Context;

public interface DownloadCallback<T> {
    /**
     * Indicates that the callback handler needs to update its appearance or information based on
     * the result of the task. Expected to be called from the main thread.
     */
    void updateFromDownload(T result, Context context);
    
    /**
     * Indicates that the download operation has finished. This method is called even if the
     * download hasn't completed successfully.
     */
    void finishDownloading();
}