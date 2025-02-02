package com.onrpiv.uploadmedia.Utilities;

import android.app.ProgressDialog;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.ExecuteCallback;
import com.arthenica.mobileffmpeg.FFmpeg;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;

public class FrameExtractor {

    /**
     * Command for extracting images from video
     */
    public static void generateFrames(Context context, String userName, String videoPath, String fps,
                                      float videoStart, float videoEnd, Callable<Void> successCallback){
        String fileExtn = ".jpg";

        String timeStamp = new SimpleDateFormat("yyyy_MM_dd_HH_mm").format(new Date());
        String filePrefix = "EXTRACT_" + timeStamp + "_";

        // create and retrieve the new frames directory
        int totalFrameDirs = (PersistedData.getTotalFrameDirectories(context, userName) + 1);
        File framesNumDir = PathUtil.getFramesNumberedDirectory(userName, totalFrameDirs);
        PersistedData.setTotalFrameDirectories(context, userName, totalFrameDirs);

        if (!framesNumDir.exists()) framesNumDir.mkdirs();

        // persist fps for this frame dir
        PersistedData.setFrameDirFPS(context, userName, totalFrameDirs, Integer.parseInt(fps));

        // persist path for frame dir
        PersistedData.setFrameDirPath(context, userName,
                framesNumDir.getAbsolutePath(), totalFrameDirs);

        File jpegFile = new File(framesNumDir, filePrefix + "%03d" + fileExtn);

        /* https://ffmpeg.org/ffmpeg.html
        ffmpeg command line options
        -y  overwrite any existing files
        -i  input video path
        -an blocks all audio streams of a file from being mapped for any output
        -r  force the frame rate of output file
        -ss where to start processing.
            The value is a time duration See more https://ffmpeg.org/ffmpeg-utils.html#Time-duration.
        -t  total duration or when to stop processing.
            The value is a time duration. See more https://ffmpeg.org/ffmpeg-utils.html#Time-duration.
         */
        String[] complexCommand = {"-y", "-i", videoPath, "-an", "-r", fps, "-ss", "" + videoStart, "-t", "" + (videoEnd - videoStart), jpegFile.getAbsolutePath()};
        /*   Remove -r 1 if you want to extract all video frames as images from the specified time duration.*/
        execFFmpegBinary(complexCommand, context, successCallback);
    }


    /**
     * Executing ffmpeg binary
     */
    private static void execFFmpegBinary(final String[] command, final Context context, final Callable<Void> successCallback) {

        // Progress dialog
        final ProgressDialog pDialog = new ProgressDialog(context);
        pDialog.setMessage("Extracting Frames...");
        pDialog.setCancelable(true);
        if (!pDialog.isShowing()) pDialog.show();

        FFmpeg.executeAsync(command, new ExecuteCallback() {
            @Override
            public void apply(long executionId, int returnCode) {
                if (returnCode == Config.RETURN_CODE_SUCCESS) {
                    Toast.makeText(context, "Frames Generation Completed", Toast.LENGTH_SHORT).show();
                    try {
                        successCallback.call();
                    } catch (Exception e) {
                        Log.e("FFMPEG", "Unable to call success callback!");
                        e.printStackTrace();
                    }
                    if (pDialog.isShowing()) pDialog.dismiss();
                } else if (returnCode == Config.RETURN_CODE_CANCEL) {
                    Log.d("FFMPEG", "Frame extraction cancelled!");
                } else {
                    Toast.makeText(context, "Frames Generation FAILED", Toast.LENGTH_SHORT).show();
                    Log.e("FFMPEG", "Async frame extraction failed with return code = " + returnCode);
                }
            }
        });
    }
}
