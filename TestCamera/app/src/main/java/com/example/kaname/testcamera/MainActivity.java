package com.example.kaname.testcamera;


import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;


public class MainActivity extends AppCompatActivity {

    private SurfaceView mySurfaceView;
    private ImageButton shutterBtn;
    private Camera myCamera; //hardware


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //SurfaceView
        mySurfaceView = (SurfaceView)findViewById(R.id.mySurfaceVIew);

        //クリックされた時の時
        mySurfaceView.setOnClickListener(onSurfaceClickListener);

        shutterBtn = (ImageButton)findViewById(R.id.shutter_btn);
        shutterBtn.setOnClickListener(onSurfaceClickListener);


        //SurfaceHolder(SVの制御に使うInterface）
        SurfaceHolder holder = mySurfaceView.getHolder();
        //コールバックを設定
        holder.addCallback(callback);

    }

    //コールバック
    private SurfaceHolder.Callback callback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            int cameraId = 0;
            //CameraOpen
            myCamera = Camera.open();
            //displayの向き設定
            setCameraDisplayOrientation(cameraId);
            //出力をSurfaceViewに設定
            try{
                myCamera.setPreviewDisplay(surfaceHolder);
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int width, int height) {
            myCamera.startPreview();

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            //終了時に呼ばれる
            myCamera.release();
            myCamera = null;
        }


        //カメラを縦向きにする
        public void  setCameraDisplayOrientation(int cameraId){
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, cameraInfo);
            //ディスプレイ向きの取得
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int degrees = 0;

            switch (rotation){
                case Surface.ROTATION_0:
                    degrees = 0; break;
                case Surface.ROTATION_90:
                    degrees = 90; break;
                case Surface.ROTATION_180:
                    degrees = 180; break;
                case Surface.ROTATION_270:
                    degrees = 270; break;
            }

            int result;
            if (cameraInfo.facing == cameraInfo.CAMERA_FACING_FRONT){
                result = (cameraInfo.orientation + degrees) % 360;
                result = (360 - result) % 360;
            }else {
                result = (cameraInfo.orientation - degrees + 360) % 360;
            }
            myCamera.setDisplayOrientation(result);
        }
    };

    //クリックされた時の処理
    private View.OnClickListener onSurfaceClickListener = new View.OnClickListener(){
        @Override
        public void onClick(View v) {
            if (myCamera != null) {
                myCamera.cancelAutoFocus(); //二回連続で呼ばれないようにするため

                switch (v.getId()){
                    case R.id.mySurfaceVIew: //surfaceviewの動作
                        myCamera.autoFocus(autoFocusCallback);
                        break;
                    case  R.id.shutter_btn: //Buttonの動作
                        myCamera.autoFocus(autoFocusShutterCallBuck);
                        break;
                }
            }
        }
    };

    //オートフォーカスのみ
    private Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            if (success) {
                //フォーカス成功
                Log.e("Focus","Focus Success");
            }else{
                Log.e("Focus", "Focus Faied");
            }
        }
    };

    //フォーカスしてから撮影
    private Camera.AutoFocusCallback autoFocusShutterCallBuck = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            myCamera.takePicture(null, null, picJpegListener);
        }
    };

    private Camera.PictureCallback picJpegListener = new Camera.PictureCallback(){

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            if(data == null){
                return;
            }


            //撮影した画像は回転しているため、修正する
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation){
                case Surface.ROTATION_0:
                    degrees = 0;break;
                case Surface.ROTATION_90:
                    degrees = 90;break;
                case Surface.ROTATION_180:
                    degrees = 180;break;
                case Surface.ROTATION_270:
                    degrees = 270;break;
            }
            Matrix m = new Matrix();
            m.setRotate(90 - degrees);
            Bitmap original = BitmapFactory.decodeByteArray(data, 0, data.length);
            Bitmap roted = Bitmap.createBitmap(original, 0, 0, original.getWidth(), original.getHeight(), m, true);



            String saveDir = Environment.getExternalStorageDirectory().getPath() + "/TestCamera";

            //SDカードフォルダの取得
            File file = new File(saveDir);

            if (!file.exists()){
                if (!file.mkdir()){
                    Log.e("Debug", "Make Dir Error");
                }
            }

            //画像保存パス　今日の日付にしてJpegで保存
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sf = new SimpleDateFormat("yyyyMMdd_HHMMss");
            String imgPath = saveDir + "/" + sf.format(cal.getTime()) + ".jpg";

            //ファイル保存
            FileOutputStream fos = null;
            try{
                fos = new FileOutputStream(imgPath, true);
                roted.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.close();

                //アンドロイドデータベースへ保存
                registAndroidDB(imgPath);
            }catch (Exception e){
                Log.e("Debug", e.getMessage());
            }
            fos = null;
            original.recycle();
            roted.recycle();

            //プレビューの再スタート
            myCamera.startPreview();
        }
    };

    //本体に画像と認識させる
    private void registAndroidDB(String imgPath) {
        ContentValues values = new ContentValues();
        ContentResolver contentResolver = MainActivity.this.getContentResolver();
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put("_data", imgPath);
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }

}