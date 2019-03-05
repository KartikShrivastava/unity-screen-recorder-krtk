package com.techgeekfreax.screenrecorder.screenrecorderlib;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AACTrackImpl;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;
import com.unity3d.player.UnityPlayer;
import com.unity3d.player.UnityPlayerActivity;
import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.widget.Toast;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Objects;

public class AndroidUtils extends UnityPlayerActivity {

    public static MediaRecorder mRecorder;

    private MediaProjection mMediaProjection;
    private MediaProjectionManager mProjectionManager;
    private VirtualDisplay mVirtualDisplay;
    private DisplayMetrics mDisplayMetrics;
    private String mFilePath, mFileName, mAppDir, mGameObject, mMethodName;
    private int mBitRate, mFps,screenWidth,screenHeight;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.mDisplayMetrics = new DisplayMetrics();
        this.mProjectionManager = ((MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE));
        this.mAppDir = Objects.requireNonNull(getApplicationContext().getExternalFilesDir(null)).getAbsolutePath();

        getWindowManager().getDefaultDisplay().getMetrics(this.mDisplayMetrics);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200) {
            if (resultCode != -1) {
                Toast.makeText(this, "Can't init recorder", Toast.LENGTH_SHORT).show();
                //UnityPlayer.UnitySendMessage(this.mGameObject, this.mMethodName, "init_record_error");
            }else {
                //UnityPlayer.UnitySendMessage(this.mGameObject, this.mMethodName, "init_record_success");
                this.mMediaProjection = this.mProjectionManager.getMediaProjection(resultCode, data);
                this.mVirtualDisplay = createVirtualDisplay();
            }
        }
    }

    public void setupVideo(int width,int height,int bitRate, int fps) {    //this func is used by Unity side to set video bitrate and fps. bitrates=width*height/168000

        this.screenWidth=width;
        this.screenHeight=height;
        this.mBitRate = bitRate;
        this.mFps = fps;
    }

    public void setFileName(String fileName) {    //this func is used by Unity side to set video name

        this.mFileName = fileName;
        this.mFilePath = (this.mAppDir + "/" + fileName + ".mp4");
    }

    public void setCallback(String gameObject, String methodName) {    //this func is used by Unity side to set callback when record status changed

        this.mGameObject = gameObject;
        this.mMethodName = methodName;
    }

    public void prepareRecorder() {
        try {
            initRecorder();
            shareScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initRecorder() throws IOException {    //this func prepare the mediarecorder to record audio from mic and video from screen

        if(mRecorder == null){
            mRecorder = new MediaRecorder();
        }
        mRecorder.setVideoSource(2);
        mRecorder.setOutputFormat(2);
        mRecorder.setVideoEncoder(2);
        mRecorder.setOutputFile(this.mFilePath);
        mRecorder.setVideoSize(screenWidth, screenHeight);
        mRecorder.setVideoFrameRate(this.mFps);
        mRecorder.setVideoEncodingBitRate(4000000);     //1000000
        mRecorder.prepare();
        mRecorder.start();
    }

    private void shareScreen() {    //this func init thr ProjectionManager to create a virtual Display and start record screen

        if (this.mMediaProjection == null) {
            startActivityForResult(this.mProjectionManager.createScreenCaptureIntent(), 200);
            return;
        }
        this.mVirtualDisplay = createVirtualDisplay();
        UnityPlayer.UnitySendMessage(mGameObject,mMethodName, "FLAG_StartRecorder");
    }

    private VirtualDisplay createVirtualDisplay() {
        return this.mMediaProjection.createVirtualDisplay("AndroidUtils", screenWidth, screenHeight, this.mDisplayMetrics.densityDpi, 16, mRecorder.getSurface(), null, null);
    }

    public void startRecording() {    //this func is used by Unity side to start recording
        mRecorder.start();
        //UnityPlayer.UnitySendMessage(mGameObject,mMethodName, "start_record");
    }

    public void stopRecording() {    //this func is used by Unity side to stop recording
        StopRecorderRunnable runnable = new StopRecorderRunnable();
        new Thread(runnable).start();
    }

    public void cleanUpRecorder() {    //use this function when you don't use record anymore
        /*this.mVirtualDisplay.release();
        if (this.mMediaProjection != null) {
            this.mMediaProjection.stop();
            this.mMediaProjection = null;
        }
        mRecorder.release();
        mRecorder = null;*/
    }

    private void addRecordingToMediaLibrary() {    //this func move the recorded video to gallery

        ContentValues values = new ContentValues(3);
        values.put("title", this.mFileName);
        values.put("mime_type", "video/mp4");
        values.put("_data", this.mFilePath);
        getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        Toast.makeText(this, "Video is saved to gallery", Toast.LENGTH_SHORT).show();
    }

    public void openGallery(){
        Intent intent=new Intent();
        intent.setAction("android.intent.action.VIEW");
        intent.setType("image/*");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        UnityPlayer.currentActivity.startActivity(intent);
    }

    @TargetApi(23)
    public void requestPermission(String permissionStr)
    {
        if ((!hasPermission(permissionStr)) && (android.os.Build.VERSION.SDK_INT >= 23)) {
            UnityPlayer.currentActivity.requestPermissions(new String[] { permissionStr }, 0);
        }
    }
    @TargetApi(23)
    public boolean hasPermission(String permissionStr)
    {
        if (android.os.Build.VERSION.SDK_INT < 23)
            return true;
        Context context = UnityPlayer.currentActivity.getApplicationContext();
        return context.checkCallingOrSelfPermission(permissionStr) == PackageManager.PERMISSION_GRANTED;
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode)
        {
            case 0:
                if (grantResults[0] == 0) {
                    UnityPlayer.UnitySendMessage(mGameObject,"OnAllow","");
                } else if (android.os.Build.VERSION.SDK_INT >= 23) {
                    if (shouldShowRequestPermissionRationale(permissions[0])) {
                        UnityPlayer.UnitySendMessage(mGameObject,"OnDeny","");
                    } else {
                        UnityPlayer.UnitySendMessage(mGameObject,"OnDenyAndNeverAskAgain","");
                    }
                }
                break;
        }
    }

    public void convertToAAC(String wavPath){
        Log.e("ANDROID_UTILS_NATIVE",wavPath);
        MediaEncoder aacEncoder = new MediaEncoder();
        aacEncoder.encode(wavPath);

        //Create a new unique file
        File theDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Pikamoji_Moments");
        if (!theDir.exists())
            theDir.mkdirs();
        Log.e("ANDROID_UTILS_NATIVE",Environment.getExternalStorageDirectory().getAbsolutePath() + "/Pikamoji_Moments");
        int i=0;
        String outputFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Pikamoji_Moments" + "/pikamoji_record";

        File outputFile = new File(outputFilePath + i + ".mp4");
        while(outputFile.exists()) {
            i++;
            outputFile = new File(outputFilePath + i + ".mp4");
        }

        mergeClips(this.mAppDir + "/visual.mp4",this.mAppDir+"/sound.aac",outputFile);
    }

    private void mergeClips(String visual,String sound, File outputFile){
        try {
            /* Load a MP4 file as movie */
            Movie movieVideo = MovieCreator.build(visual);
            Track videoTrack = movieVideo.getTracks().get(0);

            /* Fetch needed audio track and video track from MP4 file */
            Track audioTrack = new AACTrackImpl(new FileDataSourceImpl(sound));
            Log.e("ANDROID_UTILS",audioTrack.toString());

            double t1 = 1.0 * videoTrack.getDuration() / videoTrack.getTrackMetaData().getTimescale();
            double t2 = 1.0 * audioTrack.getDuration() / audioTrack.getTrackMetaData().getTimescale();
            Log.d("First log", t1 + " " + t2);

            double factor = (t1 * audioTrack.getTrackMetaData().getTimescale()) / audioTrack.getDuration();
            Log.d("Second log", "factor = " + factor);

            /* Construct a movie */
            Movie movie = new Movie();
            if (factor < 1.0) {
                long trackSize = audioTrack.getSamples().size();
                long sampleNeeded = (long) (trackSize * factor);

                Log.d("Third log", (trackSize - sampleNeeded - 1) + " " + trackSize);

                movie.addTrack(videoTrack);
                movie.addTrack(new CroppedTrack(audioTrack, trackSize - sampleNeeded - 1, trackSize));
            } else {
                Log.d("Fourth log", "mergeMP4withAAC 1");
                long trackSize = videoTrack.getSamples().size();
                long sampleNeeded = (long) (trackSize / factor);
                movie.addTrack(new CroppedTrack(videoTrack, trackSize - sampleNeeded - 1, trackSize));
                Log.d("Fifth log", "mergeMP4withAAC 2");
                movie.addTrack(audioTrack);
                Log.d("Sixth log", "mergeMP4withAAC 3");
            }

            /* Build it */
            Container mp4file = new DefaultMp4Builder().build(movie);
            Log.d("Seventh", "mergeMP4withAAC 4");
            /* Write resulted MP4 to file */
            FileChannel fc = new FileOutputStream(outputFile).getChannel();
            Log.d("Eighth", "mergeMP4withAAC 5 " + mp4file.getBoxes().size());
            mp4file.writeContainer(fc); // the problem I guess may be here
            Log.d("Ninth", "mergeMP4withAAC 6");
            fc.close();
            Log.d("Tenth", "mergeMP4withAAC 7");

            //Toast.makeText(this, "Video is saved to gallery", Toast.LENGTH_SHORT).show();

            refreshGallery(outputFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e("YOUR_APP_LOG_TAG", "I got an error", e);
            //Toast.makeText(this, "Failed to save video", Toast.LENGTH_SHORT).show();
        }
        //return outputFile.exists();
    }

    public void refreshGallery(String filePath){
        MediaScannerConnection.scanFile(UnityPlayer.currentActivity,
                new String[] { filePath }, null,
                new MediaScannerConnection.OnScanCompletedListener()
                {
                    public void onScanCompleted(String path, Uri uri)
                    {
                        Log.i("TAG", "Finished scanning " + path);
                    }
                });

        UnityPlayer.UnitySendMessage(this.mGameObject, this.mMethodName, filePath);
    }

    class StopRecorderRunnable implements Runnable{
        @Override
        public void run(){
            mVirtualDisplay.release();
            if (mMediaProjection != null) {
                mMediaProjection.stop();
                mMediaProjection = null;
            }
            mRecorder.stop();
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null;

            convertToAAC(mAppDir + "/sound.wav");
            //UnityPlayer.UnitySendMessage(mGameObject, mMethodName, "FLAG_VideoSaved");
        }
    }
}
