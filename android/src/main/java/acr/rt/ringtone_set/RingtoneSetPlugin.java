package acr.rt.ringtone_set;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import io.flutter.embedding.engine.plugins.FlutterPlugin;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.BinaryMessenger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.Manifest;
import android.content.Intent;
import android.database.Cursor;
import android.app.Activity;
import android.content.Context;
import android.content.ContentUris;
import android.media.RingtoneManager;
import android.net.Uri;
import android.content.ContentValues;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.Settings;
import android.webkit.MimeTypeMap;

/**
 * RingtoneSetPlugin
 */
public class RingtoneSetPlugin implements FlutterPlugin, MethodCallHandler {
    private static RingtoneSetPlugin instance;
    private MethodChannel channel;
    private Context mContext;

    public static void registerWith(Registrar registrar) {
        if (instance == null) {
            instance = new RingtoneSetPlugin();
        }
        instance.onAttachedToEngine(registrar.context(), registrar.messenger());
    }

    @Override
    public void onAttachedToEngine(FlutterPluginBinding binding) {
        onAttachedToEngine(binding.getApplicationContext(), binding.getBinaryMessenger());
    }

    public void onAttachedToEngine(Context applicationContext, BinaryMessenger messenger) {
        if (channel != null) {
            return;
        }
        this.mContext = applicationContext;

        channel =
                new MethodChannel(
                        messenger, "ringtone_set");

        channel.setMethodCallHandler(this);
    }

    private boolean isSystemWritePermissionGranted() {
        boolean retVal = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            retVal = Settings.System.canWrite(mContext);
        }
        return retVal;
    }

    private void requestSystemWritePermission() {
        boolean retVal = isSystemWritePermissionGranted();
        if (!retVal) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
                String both = "package:" + mContext.getPackageName();
                intent.setData(Uri.parse(both));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            }
        }
    }

    public static String getMIMEType(String url) {
        String mType = null;
        String fileExtension = "";
        try {
            int i = url.lastIndexOf('.');
            if (i > 0) {
                fileExtension = url.substring(i + 1);
            }
            if (fileExtension != "") {
                mType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
            }
        } catch (Exception ignored) {

        }
        if (mType == null) {
            return "audio/*";
        }
        return mType;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void setThings(String path, boolean isRingt, boolean isNotif, boolean isAlarm) {
        requestSystemWritePermission();
        String s = path;
        File mFile = new File(s);  // set File from path
        if (mFile.exists()) {
            // Android 10 or newer
            if (android.os.Build.VERSION.SDK_INT > 28) {// file.exists
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DATA, mFile.getAbsolutePath());
                values.put(MediaStore.MediaColumns.TITLE, "Custom ringtone");
                values.put(MediaStore.MediaColumns.MIME_TYPE, getMIMEType(path));
                values.put(MediaStore.MediaColumns.SIZE, mFile.length());
                values.put(MediaStore.Audio.Media.ARTIST, "Ringtone app");
                values.put(MediaStore.Audio.Media.IS_RINGTONE, isRingt);
                values.put(MediaStore.Audio.Media.IS_NOTIFICATION, isNotif);
                values.put(MediaStore.Audio.Media.IS_ALARM, isAlarm);
                values.put(MediaStore.Audio.Media.IS_MUSIC, false);

                Uri newUri = mContext.getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);

                try (OutputStream os = mContext.getContentResolver().openOutputStream(newUri)) {
                    int size = (int) mFile.length();
                    byte[] bytes = new byte[size];
                    try {
                        BufferedInputStream buf = new BufferedInputStream(new FileInputStream(mFile));
                        buf.read(bytes, 0, bytes.length);
                        buf.close();

                        os.write(bytes);
                        os.close();
                        os.flush();
                    } catch (IOException e) {
                    }
                } catch (Exception ignored) {
                    ignored.printStackTrace();
                }
                if (isNotif) {
                    RingtoneManager.setActualDefaultRingtoneUri(
                            mContext, RingtoneManager.TYPE_NOTIFICATION,
                            newUri);
                }
                if (isRingt) {
                    RingtoneManager.setActualDefaultRingtoneUri(
                            mContext, RingtoneManager.TYPE_RINGTONE,
                            newUri);
                }
                if (isAlarm) {
                    RingtoneManager.setActualDefaultRingtoneUri(
                            mContext, RingtoneManager.TYPE_ALARM,
                            newUri);
                }
            } else {
                // Android 9 or older
                final String absolutePath = mFile.getAbsolutePath();

                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DATA, absolutePath);
                values.put(MediaStore.MediaColumns.TITLE, "Custom ringtone");
                values.put(MediaStore.MediaColumns.SIZE, mFile.length());
                values.put(MediaStore.Audio.Media.ARTIST, "Ringtone app");
                values.put(MediaStore.Audio.Media.IS_RINGTONE, isRingt);
                values.put(MediaStore.Audio.Media.IS_NOTIFICATION, isNotif);
                values.put(MediaStore.Audio.Media.IS_ALARM, isAlarm);
                values.put(MediaStore.Audio.Media.IS_MUSIC, false);

                // insert it into the database
                Uri uri = MediaStore.Audio.Media.getContentUriForPath(absolutePath);

                // delete the old one first
                mContext.getContentResolver().delete(uri, MediaStore.MediaColumns.DATA + "=\"" + absolutePath + "\"", null);

                // insert a new record
                Uri newUri = mContext.getContentResolver().insert(uri, values);

                if (isNotif) {
                    RingtoneManager.setActualDefaultRingtoneUri(
                            mContext, RingtoneManager.TYPE_NOTIFICATION,
                            newUri);
                }
                if (isRingt) {
                    RingtoneManager.setActualDefaultRingtoneUri(
                            mContext, RingtoneManager.TYPE_RINGTONE,
                            newUri);
                }
                if (isAlarm) {
                    RingtoneManager.setActualDefaultRingtoneUri(
                            mContext, RingtoneManager.TYPE_ALARM,
                            newUri);
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (call.method.equals("getPlatformVersion")) {
            result.success("Android " + android.os.Build.VERSION.RELEASE);
        }
        if (call.method.equals("setRingtone")) {
            String path = call.argument("path");
            setThings(path, true, false, false);

            result.success(true);
            return;
        } else if (call.method.equals("setNotification")) {
            String path = call.argument("path");
            setThings(path, false, true, false);

            result.success(true);
            return;
        } else if (call.method.equals("setAlarm")) {
            String path = call.argument("path");
            setThings(path, false, false, true);

            result.success(true);
            return;
        } else if (call.method.equals("isWriteGranted")) {
            boolean granted = isSystemWritePermissionGranted();
            result.success(granted);
        }

        result.notImplemented();
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }
}
