package cnpm31.nhom10.caroplay;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaPlayer;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.facebook.share.model.ShareHashtag;
import com.facebook.share.model.SharePhoto;
import com.facebook.share.model.SharePhotoContent;
import com.facebook.share.widget.ShareDialog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import cnpm31.nhom10.caroplay.Bluetooth.ConnectedBluetooth;
import cnpm31.nhom10.caroplay.Bluetooth.ServerBluetooth;
import cnpm31.nhom10.caroplay.GameBoard.GameBoard;
import cnpm31.nhom10.caroplay.GameBoard.SingletonSharePrefs;

import static cnpm31.nhom10.caroplay.EditProfileFragment.getCircledBitmap;
import static cnpm31.nhom10.caroplay.ScreenShot.getScreenShot;
import static cnpm31.nhom10.caroplay.WelcomeFragment.btnEditProfile;

public class MainActivity extends Activity {

    // region CÁC KHAI BÁO LIÊN QUAN ĐẾN BLUETOOTH
    public static List<byte[]> queueData = new ArrayList<>();   /* Queue chứa các phân mảnh file */
    public static int pos = 0;                                  /* Index của phân mảnh sắp gửi */
    public static boolean isSentAvatar = false;                 /* Xác định đã gửi avatar hay chưa */
    public static String TAG = "CaroPlay_DEBUG";                /* Hỗ trợ Debug */
    public static Context mainContext;                          /* Context của UI */
    public static String oldBluetoothName;                      /* Tên cũ của Bluetooth, dùng để khôi phục */
    public static UUID defaultUUID;                             /* Mã UUID mặc định của Bluetooth */
    public static BluetoothAdapter bluetoothAdapter;            /* BluetoothAdapter mặc định */
    public static ServerBluetooth serverBluetooth;              /* Tiểu trình đóng vai trò làm server */
    public static ConnectedBluetooth connectedBluetooth;        /* Tiểu trình quản lý kết nối */
    @SuppressLint("HandlerLeak")
    public static android.os.Handler connectionHandler          /** QUẢN LÝ THÔNG ĐIỆP GỬI NHẬN TẠI ĐÂY **/
            = new android.os.Handler() {

        @SuppressLint("ResourceType")
        @Override
        public void handleMessage(Message msg) {

            // Trích chuỗi thông điệp (nhị phân) nhận được
            byte[] data = new byte[msg.arg1];
            for (int i = 0; i < msg.arg1; i++) data[i] = ((byte[]) msg.obj)[i];
            String message = new String(data);

            // region Thông điệp báo đã nhận file
            if (message.length() > 15 && message.substring(0, 16).equals("R@E@C@E@I@V@E@D@")){

                // Thông điệp báo là bên kia đã nhận phân mảnh
                if (message.length() == 16) {
                    connectedBluetooth.sendData(queueData.get(pos));
                    if(++pos == queueData.size()) {
                        queueData.clear();
                        pos = 0;
                    }
                    else Log.e("TQN", "sent: " + pos + "-" + queueData.get(pos).length);
                }

                // Thông điệp báo mình đã nhận được file nhắn thoại
                else if (message.substring(16).equals("V@O@I@C@E@")) {
                    btnPlay.setVisibility(View.VISIBLE);
                    CanPlay = true;
                }

                // Thông điệp báo mình đã nhận được file avatar
                else if (message.substring(16).equals("A@V@A@T@A@R@")) {

                    // Lấy bitmap từ file và gán vào ImageView
                    Bitmap bitmap = BitmapFactory.decodeFile(
                            SingletonSharePrefs.getInstance().get("caroPlayPath", String.class)
                                    + "/avatarUser2.jpg");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        avatarUser2.setImageBitmap(getCircledBitmap(bitmap));
                    }

                    // Nếu mình chưa gửi avatar thì gửi
                    if (!isSentAvatar) {
                        try {
                            connectedBluetooth.sendFile(
                                    SingletonSharePrefs.getInstance().get("caroPlayPath", String.class)
                                            + "/avatar.jpg", "A@V@A@T@A@R@");
                        } catch (IOException ignored) {}
                        finally {
                            connectedBluetooth.sendData(queueData.get(pos));
                            pos++;
                        }
                    }
                }

            }
            // endregion

            // region Thông điệp gửi tên
            if (message.length() > 8 && message.substring(0, 8).equals("N@A@M@E@")) {

                // Nếu mình là chủ phòng, và đã nhận tên của đối phương, thì bắt đầu gửi ảnh
                if (message.length() > 16 && message.substring(8, 16).equals("B@O@T@H@")) {
                    nameUser2.setText(message.substring(16));
                    try {
                        connectedBluetooth.sendFile(
                                SingletonSharePrefs.getInstance().get("caroPlayPath", String.class)
                                        + "/avatar.jpg", "A@V@A@T@A@R@");
                    } catch (IOException ignored) {}
                    finally {
                        connectedBluetooth.sendData(queueData.get(pos));
                        pos++;
                        // Đánh dấu là đã gửi avatar
                        isSentAvatar = true;
                    }
                }
                // Nếu chủ phòng vừa gửi tên cho mình
                else {
                    nameUser2.setText(message.substring(8));

                    // Gửi lại tên cho chủ phòng
                    connectedBluetooth.sendData(("N@A@M@E@B@O@T@H@" + nameUser1.getText().toString()).getBytes());
                }
            }
            // endregion

            // region Thông điệp liên quan đến kết nối thành công/thất bại
            if (message.equals(mainContext.getString(R.string.CLIENTCONFIRM))) {
                // Nếu mình đang là server, và nhận được confirm từ client

                AlertDialog.Builder b = new AlertDialog.Builder(imgExit.getContext());
                b.setTitle("Đối thủ đã vào phòng!");
                b.setMessage("Bạn là người chơi trước.");
                b.setNegativeButton("Ok", (dialog, which) -> {
                    dialog.cancel();
                }).show();

                // Mình có thể đi nước đầu tiên
                isPlaying = true;
                gameBoard.isWaiting = false;
                avatarUser2.setBackgroundResource(0);

                // Gửi tên cho đối phương
                connectedBluetooth.sendData(("N@A@M@E@" + nameUser1.getText().toString()).getBytes());
            }
            else if (message.equals(mainContext.getString(R.string.SERVERCONFIRM))) {
                // Nếu mình đang là client, và nhận được confirm từ server

                AlertDialog.Builder b = new AlertDialog.Builder(imgExit.getContext());
                b.setTitle("Bạn đã vào phòng!");
                b.setMessage("Chủ phòng là người chơi trước.");
                b.setNegativeButton("Ok", (dialog, which) -> {
                    dialog.cancel();
                }).show();

                // Tắt giao diện Welcome
                isPlaying = true;
                gameBoard.isWaiting = true;
                fragmentManager.beginTransaction()
                        .setCustomAnimations(R.anim.zoom_out_animation, R.anim.zoom_out_animation)
                        .remove(welcomeFragment).commit();
                avatarUser1.setBackgroundResource(0);
            }
            // Nếu nhận được tin CONNECT FAILED, thì khi đó mình không thể kết nối
            else if (message.equals("C@O@N@N@E@C@T@ @F@A@I@L@E@D@")) {
                AlertDialog.Builder b = new AlertDialog.Builder(imgExit.getContext());
                b.setTitle("Không thể kết nối!");
                b.setMessage("Vui lòng thử lại.");
                b.setNegativeButton("Ok", (dialog, which) -> {
                    dialog.cancel();
                }).show();
            }
            // endregion

            // region Thông điệp tọa độ nước đi của đối thủ
            else if (message.charAt(0) =='[' && message.charAt(message.length() - 1) ==']') {
                /* Nếu ký tự đầu là "[" và ký tự cuối là "]" */

                // Lấy tọa độ và đặt nước đi lên bàn cờ
                int position = Integer.parseInt(message.substring(1, message.length() - 1));
                gameBoard.rivalMove(position);
            }
            // endregion

            // region Thông điệp đối thủ thoát khỏi phòng
            else if (message.equals("S@U@R@R@E@N@D@E@R@")) {
                if (!isPlaying) return;
                AlertDialog.Builder b = new AlertDialog.Builder(imgExit.getContext());
                b.setTitle("Đối thủ đã thoát!");
                b.setMessage("Nhấn OK để chờ đối thủ mới.\nNhấn Cancel để xem lại trận đánh.");
                b.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
                b.setPositiveButton("Ok", (dialog, which) -> {
                    gameBoard.reSet();
                    serverBluetooth = new ServerBluetooth();
                    serverBluetooth.start();
                }).show();

                /* Tắt luôn kết nối */
                MainActivity.connectedBluetooth.cancel();
                isPlaying = false;
                gameBoard.isWaiting = true;
                isSentAvatar = false;
                avatarUser2.setImageResource(R.drawable.avatar_user1);
                avatarUser2.setBackgroundResource(0);
                avatarUser1.setBackgroundResource(R.drawable.effect);
                nameUser2.setText("");
            }
            // endregion

            // region Thông điệp nhắn tin text
            else if (message.length() > 8 && message.substring(0, 8).equals("T@E@X@T@")) {
                ReceiveFragment receiveFragment = new ReceiveFragment();
                Bundle bundle = new Bundle();
                bundle.putString("text", message.substring(8));
                receiveFragment.setArguments(bundle);

                // Tự động ẩn trong 3s
                new CountDownTimer(3000, 1000) {
                    @SuppressLint("ResourceType")
                    public void onTick(long millisUntilFinished) {
                        fragmentManager.beginTransaction()
                                .setCustomAnimations(R.anim.zoom_in_animation, R.anim.zoom_out_animation)
                                .replace(R.id.tooltipReceive, receiveFragment).commit();
                    }
                    @SuppressLint("ResourceType")
                    public void onFinish() {
                        fragmentManager.beginTransaction()
                                .setCustomAnimations(R.anim.zoom_out_animation, R.anim.zoom_out_animation)
                                .remove(receiveFragment).commit();
                    }
                }.start();
            }
            if (message.length() > 14 && message.substring(0, 14).equals("S@T@I@C@K@E@R@")) {
                String sticker = message.substring(14);

                // Tự động ẩn trong 3s
                new CountDownTimer(3000, 1000) {
                    public void onTick(long millisUntilFinished) {
                        if (sticker.equals("A@F@T@E@R@B@O@O@M@")) imgSticker.setBackgroundResource(R.drawable.after_boom_sticker);
                        if (sticker.equals("B@E@A@T@B@R@I@C@K@")) imgSticker.setBackgroundResource(R.drawable.beat_brick_sticker);
                        if (sticker.equals("B@I@G@S@M@I@L@E@")) imgSticker.setBackgroundResource(R.drawable.big_smile_sticker);
                        if (sticker.equals("B@O@S@S@")) imgSticker.setBackgroundResource(R.drawable.boss_sticker);
                        if (sticker.equals("D@R@I@B@B@L@E@")) imgSticker.setBackgroundResource(R.drawable.dribble_sticker);
                        if (sticker.equals("H@E@L@L@B@O@Y@")) imgSticker.setBackgroundResource(R.drawable.hell_boy_sticker);
                    }
                    public void onFinish() {
                        imgSticker.setBackgroundResource(0);
                    }
                }.start();
            }
            // endregion
        }
    };
    // endregion

    // region CÁC KHAI BÁO LIÊN QUAN ĐẾN GIAO DIỆN CHƠI CỜ
    public static GameBoard gameBoard;
    public static ImageView imgExit;
    public static boolean isPlaying;
    public static TextView nameUser1;
    public static TextView nameUser2;
    public static ImageView avatarUser1;
    public static ImageView imgSticker;
    public static ImageView avatarUser2;
    public static ImageButton btnPlay;
    public static boolean CanPlay = false;
    // endregion

    // region CÁC KHAI BÁO LIÊN QUAN ĐẾN GIAO DIỆN KHÁC
    public static WelcomeFragment welcomeFragment;
    public static FragmentManager fragmentManager;

    //Phần liên quan đến Login và share
    private CallbackManager callbackManager;
    private LoginButton loginButton;
    public ImageButton ShareButton;
    ShareDialog shareDialog;
    Bitmap bitmap;

    // endregion

    @SuppressLint("ResourceType")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainContext = getApplicationContext();

        // region Xử lý các biến bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        defaultUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        oldBluetoothName = BluetoothAdapter.getDefaultAdapter().getName();

        /* Nếu điện thoại không hỗ trợ Bluetooth thì thông báo */
        if (bluetoothAdapter == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Thông báo")
                    .setMessage("Điện thoại của bạn không hỗ trợ Bluetooth")
                    .setPositiveButton("Exit", (dialog, which) -> System.exit(0))
                    .show();
        }

        /* Nếu chưa bật Bluetooth trong điện thoại */
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBT = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBT, 1);
        }
        // endregion

        /* Kiểm tra quyền sử dụng microphone */
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            }
        }

        /* Khởi tạo GameBoard */
        gameBoard = new GameBoard(this, true);
        isPlaying = false;

        /* Khởi tạo giao diện Welcome */
        welcomeFragment = new WelcomeFragment();
        fragmentManager = getFragmentManager();

        /* Hiển thị giao diện Welcome */
        fragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.zoom_in_animation, R.anim.zoom_in_animation)
                .replace(R.id.frameWelcome, welcomeFragment)
                .commit();

        /* Ánh xạ giao diện */
        avatarUser1 = findViewById(R.id.avatarUser1);
        avatarUser2 = findViewById(R.id.avatarUser2);
        imgSticker = findViewById(R.id.imgSticker);
        nameUser1 = findViewById(R.id.nameUser1);
        nameUser2 = findViewById(R.id.nameUser2);
        imgExit = findViewById(R.id.exit);
        btnPlay = findViewById(R.id.btnPlay);

        /* Cập nhật ảnh đại diện và tên */
        nameUser1.setText(SingletonSharePrefs.getInstance().get("name", String.class));
        String savedDirectory = SingletonSharePrefs.getInstance().get("caroPlayPath", String.class);
        if (!savedDirectory.equals("")){
            try {
                File savedAvatar =  new File(savedDirectory, "avatar.jpg");
                Bitmap savedBitmap = BitmapFactory.decodeStream(new FileInputStream(savedAvatar));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    savedBitmap = getCircledBitmap(savedBitmap);
                }
                avatarUser1.setImageBitmap(savedBitmap);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        /* Sự kiện thoát khỏi phòng */
        imgExit.setOnClickListener(v -> {

            AlertDialog.Builder b = new AlertDialog.Builder(v.getContext());
            b.setTitle("Xác nhận");
            b.setMessage("Bạn có muốn thoát?");

            // Nếu cancel
            b.setNegativeButton("Cancel", (dialog, id) -> dialog.cancel());

            // Nếu OK - đồng ý đăng ký
            b.setPositiveButton("Ok", (dialog, id) -> {

                /* Hiển thị lại giao diện Welcome */
                fragmentManager.beginTransaction()
                        .setCustomAnimations(R.anim.zoom_in_animation, R.anim.zoom_in_animation)
                        .replace(R.id.frameWelcome, welcomeFragment)
                        .commit();

                /* Reset bàn cờ và cập nhật số trận đánh */
                gameBoard.reSet();

                /* Nếu đang chơi, thì gửi thông điệp đầu hàng */
                if (isPlaying) {
                    MainActivity.connectedBluetooth.sendData("S@U@R@R@E@N@D@E@R@".getBytes());

                    /* Tắt luôn kết nối */
                    MainActivity.connectedBluetooth.cancel();
                    isSentAvatar = false;
                    isPlaying = false;
                }
            }).show();
        });

        /* Sự kiện nhắn tin */
        AtomicBoolean isChatFragmentShowed = new AtomicBoolean(false);
        ChatFragment chatFragment = new ChatFragment();
        avatarUser1.setOnClickListener(v -> {
            if (!isChatFragmentShowed.get()){
                isChatFragmentShowed.set(true);
                fragmentManager.beginTransaction()
                        .setCustomAnimations(R.anim.zoom_in_animation, R.anim.zoom_in_animation)
                        .replace(R.id.tooltipChat, chatFragment)
                        .commit();
            }
            else {
                isChatFragmentShowed.set(false);
                fragmentManager.beginTransaction()
                        .setCustomAnimations(R.anim.zoom_out_animation, R.anim.zoom_out_animation)
                        .remove(chatFragment)
                        .commit();
            }
        });

        /* Sự kiện phát tin nhắn thoại nhận được */
        btnPlay.setOnClickListener(v -> {
            if (!CanPlay) return;

            MediaPlayer mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setDataSource(SingletonSharePrefs
                        .getInstance().get("caroPlayPath", String.class) + "/voice.3gp");
                mediaPlayer.prepare();
                mediaPlayer.start();
            } catch (Exception ignored) {}
            finally {
                btnPlay.setVisibility(View.GONE);
            }
        });

        /* Nếu đây là lần chạy đầu tiên thì cài đặt profile */
        EditProfileFragment editProfileFragment = new EditProfileFragment();
        if (SingletonSharePrefs.getInstance().get("isFirstLaunch", String.class).equals("")) {
            fragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.zoom_in_animation, R.anim.zoom_in_animation)
                    .add(R.id.frameWelcome, editProfileFragment)
                    .commit();
            SingletonSharePrefs.getInstance().put("isFirstLaunch", "TQN");
        }

        /**Login fb**/

        callbackManager = CallbackManager.Factory.create();

        loginButton = (LoginButton) findViewById(R.id.login_button);
        loginButton.setReadPermissions("email");
        // If using in a fragment
        //loginButton.setFragment(this);

        // Callback registration
        loginButton.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                // App code
            }

            @Override
            public void onCancel() {
                // App code
            }

            @Override
            public void onError(FacebookException exception) {
                // App code
            }
        });

        //Nút share
        shareDialog = new ShareDialog(this);
        ShareButton = (ImageButton)findViewById(R.id.fb_share);
        //Share từ thư viện
//        ShareButton.setOnClickListener(v -> {
//            Intent intent = new Intent(Intent.ACTION_PICK);
//            intent.setType("image/*");
//            startActivityForResult(intent, Select_Image);
//        });
        ShareButton.setOnClickListener(v -> {
            View rootView = getWindow().getDecorView().findViewById(android.R.id.content);
            bitmap=getScreenShot(rootView);
            SharePhoto photo = new SharePhoto.Builder()
                    .setBitmap(bitmap)
                    .build();
            SharePhotoContent content = new SharePhotoContent.Builder()
                    .addPhoto(photo)
                    .setShareHashtag(new ShareHashtag.Builder()
                            .setHashtag("#CaroPlayNhom10Android")
                            .build())
                    .build();
            shareDialog.show(content);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    // region FULL SCREEN
    // Source: https://developer.android.com/training/system-ui/immersive
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    // Shows the system bars by removing all the flags
    // except for the ones that make the content appear under the system bars.
    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);
//        AccessToken accessToken = AccessToken.getCurrentAccessToken();
//        boolean isLoggedIn = accessToken != null && !accessToken.isExpired();
//
//        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("public_profile"));
    }
    // endregion
}
