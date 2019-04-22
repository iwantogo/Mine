package com.coderpage.lib.update;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;

import java.io.File;

/**
 * @author lc. 2017-09-23 23:51
 * @since 0.5.0
 */

public class DownloadService extends IntentService {

    private static final String TAG = DownloadService.class.getSimpleName();
    private static final int NOTIFY_ID_DOWNLOAD_APK = 10086;

    private static final String ACTION_APK_DOWNLOAD = "com.coderpage.lib.update.service.action.ApkDownload";

    private static final String EXTRA_DOWNLOAD_URL = "extra_download_url";
    private static final String EXTRA_DOWNLOAD_FILE_NAME = "extra_download_file_name";
    private static final String EXTRA_NOTIFY_ICON_RES_ID = "extra_notify_icon_res_id";

    private NotificationManager mNotificationManager;
    private Handler mHandler;
    private int mProgress;
    private int mNotifyIconResId;
    private String mDownloadUrl;
    private String mDownFilename;

    public DownloadService() {
        super("DownloadService");
    }

    public static void startDownloadApk(Context context,
                                        String downloadUrl,
                                        String filename,
                                        int notifyIconResId) {
        Log.i(TAG, "start download apk:" + downloadUrl);
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_APK_DOWNLOAD);
        intent.putExtra(EXTRA_DOWNLOAD_URL, downloadUrl);
        intent.putExtra(EXTRA_DOWNLOAD_FILE_NAME, filename);
        intent.putExtra(EXTRA_NOTIFY_ICON_RES_ID, notifyIconResId);
        context.startService(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_APK_DOWNLOAD.equals(action)) {
                mDownloadUrl = intent.getStringExtra(EXTRA_DOWNLOAD_URL);
                mDownFilename = intent.getStringExtra(EXTRA_DOWNLOAD_FILE_NAME);
                mNotifyIconResId = intent.getIntExtra(EXTRA_NOTIFY_ICON_RES_ID, R.mipmap.ic_launcher);
                handleActionApkDownload();
            }
        }
    }

    private void handleActionApkDownload() {
        mHandler = new Handler(getMainLooper());
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        startDownload();
    }

    private void startDownload() {
        NotifyRunnable notifyRunnable = new NotifyRunnable();
        mHandler.post(notifyRunnable);
        FileDownloader fileDownloader = new FileDownloader(this);
        Result<File, Error> result = fileDownloader.download(
                mDownloadUrl,
                mDownFilename,
                (bytesRead, contentLength, done) -> {
                    mProgress = Float.valueOf(((float) bytesRead / contentLength) * 100).intValue();
                });
        mNotificationManager.cancel(NOTIFY_ID_DOWNLOAD_APK);
        mHandler.removeCallbacks(notifyRunnable);
        if (result.isOk()) {
            Intent install = new Intent(Intent.ACTION_VIEW);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                install.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Uri contentUri = FileProvider.getUriForFile(
                        getApplicationContext(),
                        BuildConfig.APPLICATION_ID + ".fileProvider",
                        result.data());
                install.setDataAndType(contentUri, "application/vnd.android.package-archive");
            } else {
                install.setDataAndType(Uri.fromFile(result.data()),
                        "application/vnd.android.package-archive");
                install.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            startActivity(install);
        } else {
            Log.e(TAG, "download apk failed: " + result.error());
        }
    }

    private class NotifyRunnable implements Runnable {
        NotificationCompat.Builder mBuilder;

        NotifyRunnable() {
            mBuilder = new NotificationCompat.Builder(DownloadService.this);
            mBuilder.setSmallIcon(mNotifyIconResId);
            mBuilder.setLargeIcon(BitmapFactory.decodeResource(getResources(), mNotifyIconResId));
            //禁止用户点击删除按钮删除
            mBuilder.setAutoCancel(false);
            //禁止滑动删除
            mBuilder.setOngoing(true);
            mBuilder.setShowWhen(false);
            mBuilder.setOngoing(true);
            mBuilder.setShowWhen(false);
        }

        @Override
        public void run() {
            if (mProgress < 100) {
                sendNotification();
                mHandler.postDelayed(this, 500);
            } else {
                mHandler.removeCallbacks(this);
            }
        }

        private void sendNotification() {
            mBuilder.setProgress(100, mProgress, false);
            mBuilder.setContentTitle(mDownFilename);
            mBuilder.setContentText(mProgress + "%");
            mBuilder.setShowWhen(true);
            Notification notification = mBuilder.build();
            mNotificationManager.notify(NOTIFY_ID_DOWNLOAD_APK, notification);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
