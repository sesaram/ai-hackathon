package ai.hackathon.camera2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // 권한 관련 변수 값
    private int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA"};

    // 뷰 객체
    private TextureView textureView;
    private TextView box0,box1,box2,box3,score,classIndex,distancePerson, directionPerson;

    // 화면 각도 상수
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    // 카메라2 변수 공간
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest captureRequest;
    private CaptureRequest.Builder captureRequestBuilder;

    // 이미지 저장 변수 공간
    private Size imageDimensions;
    private ImageReader imageReader;
    private File file;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    //음성 처리 객체
    MediaPlayer mediaPlayer;

    //AI Class 값 목록
    String[] indexToClass= {"사람", "자전거", "자동차", "오토바이", "비행기", "버스", "기차", "트럭", "boat", "신호등",
            "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
            "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
            "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"};

    //거리 계산
    Distance distClass;

    // 액티비티 생명주기
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.textureView);
        box0 = findViewById(R.id.box0);
        box1 = findViewById(R.id.box1);
        box2 = findViewById(R.id.box2);
        box3 = findViewById(R.id.box3);
        score = findViewById(R.id.score);
        classIndex = findViewById(R.id.classIndex);
        distancePerson = findViewById(R.id.distance);
        directionPerson = findViewById(R.id.direction);
        distClass = new Distance();
        if (allPermissionsGranted()) {
            startCamera(); //start camera if permission has been granted by user
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();

        if (textureView.isAvailable()) {
            try {
                openCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            stopBackgroundThread();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                this.finish();
            }
        }
    }

    // 유틸 함수
    private boolean allPermissionsGranted() {

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void startCamera() {
        textureView.setSurfaceTextureListener(textureListener);
    }

    private void openCamera() throws CameraAccessException {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        cameraId = manager.getCameraIdList()[0];
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        imageDimensions = map.getOutputSizes(SurfaceTexture.class)[0];

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            manager.openCamera(cameraId, stateCallback, null);
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

    }

    private void createCameraPreview() throws CameraAccessException {
        SurfaceTexture texture = textureView.getSurfaceTexture();
        texture.setDefaultBufferSize(imageDimensions.getWidth(), imageDimensions.getHeight());
        Surface surface = new Surface(texture);

        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        captureRequestBuilder.addTarget(surface);

        cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                if (cameraDevice == null) {
                    return;
                }

                cameraCaptureSession = session;
                try {
                    updatePreview();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                Toast.makeText(getApplicationContext(), "Configuration Changed", Toast.LENGTH_LONG).show();
            }
        }, null);
    }

    private void updatePreview() throws CameraAccessException {
        if (cameraDevice == null) {
            return;
        }

        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);

    }

    protected void stopBackgroundThread() throws InterruptedException {
        mBackgroundThread.quitSafely();
        mBackgroundThread.join();
        mBackgroundThread = null;
        mBackgroundHandler = null;
    }

    // 리스너 콜백 함수
    private TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        boolean processing;

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            try {
                openCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            if(processing){
                return ;
            }

            if(mediaPlayer!=null){
                return ;
            }

            processing = true;

            Bitmap photo = textureView.getBitmap();
            photo = getResizedBitmap(photo,320);
            Bitmap bmp = photo;

            new ImageTask(photo, new ImageResponse(){
                @Override
                public void processFinished(){
                    float[][][][] input = new float[1][320][320][3];
                    float[][][] output = new float[1][6300][85];
                    ArrayList<Float> classFilter = new ArrayList<>();
                    for(int y=0; y<320; y++){
                        for(int x=0; x<320; x++){
                            int pixel = bmp.getPixel(x,y);
                            input[0][y][x][0] = Color.red(pixel)/255.0f;
                            input[0][y][x][1] = Color.green(pixel)/255.0f;
                            input[0][y][x][2] = Color.blue(pixel)/255.0f;
                        }
                    }
                    Interpreter lite = getTfliteInterpreter("untrained_model.tflite");
                    lite.run(input,output);
                    for(int i=0; i<6300; i++){ //[0,1,2,3] = [x,y,w,h]
                        output[0][i][0] *= 320;
                        output[0][i][1] *= 320;
                        output[0][i][2] *= 320;
                        output[0][i][3] *= 320;
                    }
                    //confidence 한번 거르는 작업을 했음.
                    ArrayList<Float> tmp = new ArrayList<>();
                    ArrayList<Object[]> filter = new ArrayList<>();
                    for(int i=0; i<6300; i++){
                        if(output[0][i][4]>0.25){
                            for(int j=0; j<5; j++){
                                tmp.add(output[0][i][j]);
                            }
                            for(int j=5; j<85; j++){
                                tmp.add(output[0][i][j]*output[0][i][4]);

                            }
                            filter.add(tmp.toArray());
                            tmp.clear();
                        }
                    }
                    ArrayList<Object[]> box = new ArrayList<>();
                    for(int i=0; i<filter.size(); i++){
                        tmp.add((float)filter.get(i)[0]-((float)filter.get(i)[2])/2);
                        tmp.add((float)filter.get(i)[1]-((float)filter.get(i)[3])/2);
                        tmp.add((float)filter.get(i)[0]+((float)filter.get(i)[2])/2);
                        tmp.add((float)filter.get(i)[1]+((float)filter.get(i)[3])/2);
                        box.add(tmp.toArray());
                        tmp.clear();
                    }

                    ArrayList<Object[]> result = new ArrayList();
                    for(int i=0; i<filter.size(); i++){
                        float maxValue=0,index=0;
                        boolean isRightRange = true;
                        for(int j=0; j<4; j++){
                            float value = (float) box.get(i)[j];
                            if(value<0.0||value>320.0){
                                isRightRange = false;
                                break;
                            }
                            tmp.add((float)box.get(i)[j]);
                        }

                        if(isRightRange == false)continue;
                        for(int j=0; j<80; j++){
                            float currValue = (float)filter.get(i)[5+j];
                            if(currValue > maxValue){
                                maxValue = currValue;
                                index = j;
                            }
                        }
                        if(maxValue > 0.70){ //0.45에서 0.70로 올렸음 너무 이상한거 많이 나옴
                            tmp.add(maxValue);
                            tmp.add(index);
                            float dist = distClass.getDistance(tmp.get(0),tmp.get(1),tmp.get(2),tmp.get(3));
                            float direct = distClass.getDirection(tmp.get(0),tmp.get(2));
                            tmp.add(dist);
                            tmp.add(direct);
                            if(classFilter.contains(index)==false&&index<80.0&&dist<6.0&&direct<14&&direct>9){//5m정도로 거름
                                result.add(tmp.toArray());
                                classFilter.add(index);
                            }
                        }
                        tmp.clear();
                    }
                    runOnUiThread(new Runnable() { //UI바꾸는건 mainThread에서 해야하기 때문에 이 쓰레드에서 작업을 해주어야 오류가 안남.
                        @Override
                        public void run() {
                            for(int i=0; i<result.size(); i++){
                                if((float)result.get(i)[5]>80.0){
                                    continue;
                                }
                                float indexFloat = (float) result.get(i)[5];
                                int indexInt = (int)indexFloat;
                                if(indexInt>=10){
                                    return ;
                                }
                                if(indexInt == 0){
                                    distancePerson.setText(String.format("%.3f", result.get(i)[6])+"m");
                                    directionPerson.setText(""+Math.round((float)result.get(i)[7])+"시 방향");
                                }
                                //출력 된 놈들 중에 확률 제일 높은 놈이 배열의 맨 앞에 오도록 계산하기.
                                box0.setText(String.format("%.3f", result.get(i)[0]));
                                box1.setText(String.format("%.3f", result.get(i)[1]));
                                box2.setText(String.format("%.3f", result.get(i)[2]));
                                box3.setText(String.format("%.3f", result.get(i)[3]));
                                score.setText(String.format("%.3f", result.get(i)[4]));
                                classIndex.setText(indexToClass[indexInt]);

                                if(mediaPlayer!=null){
                                    return ;
                                }
                                if(indexInt<10){
                                    if(indexInt==0){
//                                        float dist = (float)result.get(i)[6];
//                                        int direct = (int)((float)result.get(i)[7]);
//                                        switch (direct){
//                                            case 10:
//                                                mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.clock10);
//                                                break;
//                                            case 11:
//                                                mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.clock11);
//                                                break;
//                                            case 12:
//                                                mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.clock12);
//                                                break;
//                                            case 13:
//                                                mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.clock1);
//                                                break;
//                                            case 14:
//                                                mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.clock2);
//                                                break;
//                                        }
//                                        mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(1.25f));
//                                        mediaPlayer.start();
//                                        while(mediaPlayer.isPlaying()){}
//                                        mediaPlayer.reset();
//                                        mediaPlayer.release();
//                                        if(dist<2){
//                                            mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.about1m);
//                                        }else if(dist<3){
//                                            mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.about2m);
//                                        }else if(dist<4){
//                                            mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.about3m);
//                                        }else if(dist<5){
//                                            mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.about4m);
//                                        }else {
//                                            mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.about5m);
//                                        }
//                                        mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(1.25f));
//                                        mediaPlayer.start();
//                                        while(mediaPlayer.isPlaying()){}
//                                        mediaPlayer.reset();
//                                        mediaPlayer.release();
                                        mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.thereisperson);
                                    }else if(indexInt==1){
                                        mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.bicycle);
                                    }else if(indexInt==2) {
                                        mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.car);
                                    }else if(indexInt==3){
                                        mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.motorcycle);
                                    }else if(indexInt==5){
                                        mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.bus);
                                    }else if(indexInt==7){
                                        mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.truck);
                                    }else if(indexInt==9){
                                        mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.trafficlight);
                                    }else{
                                        return ;
                                    }
                                    mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(1.25f));
                                    mediaPlayer.start();
                                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                        @Override
                                        public void onCompletion(MediaPlayer mp) {
                                            mp.stop();
                                            mp.reset();
                                            if(mediaPlayer!=null){
                                                mediaPlayer.release();
                                                mediaPlayer = null;
                                            }
                                        }
                                    });
                                }
                            }
                        }
                    });
                    processing = false;
                }
            }).execute();
        }
    };

    private interface ImageResponse{
        void processFinished();
    }

    private class ImageTask extends AsyncTask<Void, Void, Exception> {
        private Bitmap photo;
        private ImageResponse imageResponse;
        ImageTask(Bitmap photo, ImageResponse imageResponse) {
            this.photo = photo;
            this.imageResponse = imageResponse;
        }

        @Override
        protected Exception doInBackground(Void... params) {
            imageResponse.processFinished();
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {

        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            try {
                createCameraPreview();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    // AI 처리 함수
    private Interpreter getTfliteInterpreter(String modelPath){
        try{
            return new Interpreter(loadModelFile(MainActivity.this, modelPath));
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public MappedByteBuffer loadModelFile(Activity activity, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public Bitmap getResizedBitmap(Bitmap image, int maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        float bitmapRatio = (float)width / (float) height;
        if (bitmapRatio > 1) {
            width = maxSize;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxSize;
            width = (int) (height * bitmapRatio);
        }
        return Bitmap.createScaledBitmap(image, width, height, true);
    }
}
