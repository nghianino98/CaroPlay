package cnpm31.nhom10.caroplay.GameBoard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import cnpm31.nhom10.caroplay.Gestures.MoveGestureDetector;
import cnpm31.nhom10.caroplay.Gestures.MoveListener;
import cnpm31.nhom10.caroplay.Gestures.ScaleListener;
import cnpm31.nhom10.caroplay.MainActivity;
import cnpm31.nhom10.caroplay.R;

public class GameBoard {

    // Định nghĩa các trạng thái của một ô
    public static char STATUS_EMPTY = 0;
    public static char STATUS_USER1 = 1;
    public static char STATUS_USER2 = 2;

    // Các đối tượng để hiện thị Grid view
    public GridView gvGame;
    public GameGridViewAdapter gridViewAdapter;
    public List<ItemStatus> boardGame;
    boolean isWaiting;

    // Các Gesture Detector xử lý zoom và move
    private ScaleGestureDetector mScaleDetector;
    private MoveGestureDetector mMoveDetector;

    Context _context;

    // Constructor
    @SuppressLint("ClickableViewAccessibility")
    public GameBoard(Context context, boolean _isWaiting) {

        _context = context;
        isWaiting = _isWaiting;

        // Ánh xạ control
        gvGame = ((Activity)_context).findViewById(R.id.gvGame);
        boardGame = new ArrayList<>();
        for (int i = 0; i < 400; i++) boardGame.add(new ItemStatus(STATUS_EMPTY, R.drawable.status_empty));

        // Thiết lập adapter
        gridViewAdapter = new GameGridViewAdapter(_context, R.layout.grid_view_item, boardGame);
        gvGame.setAdapter(gridViewAdapter);

        // Thiết lập Detectors Gesture
        mScaleDetector = new ScaleGestureDetector(_context, new ScaleListener());
        mMoveDetector = new MoveGestureDetector(_context, new MoveListener());

        // Thiết lập sự kiện zoom và di chuyển
        gvGame.setOnTouchListener((v, event) -> {

            // Gọi hai phương thức sau để lấy scale và tọa độ
            mScaleDetector.onTouchEvent(event);
            mMoveDetector.onTouchEvent(event);

            // Cập nhật scale
            gvGame.setScaleX(ScaleListener.mScaleFactor);
            gvGame.setScaleY(ScaleListener.mScaleFactor);

            // Cập nhậ tọa độ
            gvGame.setTranslationX(MoveListener.mFocusX);
            gvGame.setTranslationY(MoveListener.mFocusY);
            return false;
        });

        // Sự kiện chọn ô
        gvGame.setOnItemClickListener((parent, view, position, id) -> {

            // Nếu đang chờ đối thủ đánh thì thôi
            if (isWaiting) return;

            // Nếu ô click còn trống
            if (boardGame.get(position).getStatus() == STATUS_EMPTY) {
                boardGame.get(position).setStatus(STATUS_USER1);
                gridViewAdapter.notifyDataSetChanged();
                isWaiting = true;
                MainActivity.imgUser1.setBackgroundResource(0);
                MainActivity.imgUser2.setBackgroundResource(R.drawable.effect);

                // Chuyền dữ liệu
                String message = new String("~" + position);
                MainActivity.bluetoothSPP.send(message, true);

                // Kiểm tra mình thắng hay không
                if (isWin(position, STATUS_USER1)) {

                    AlertDialog.Builder b = new AlertDialog.Builder(view.getContext());
                    b.setTitle("Chúc mừng");
                    b.setMessage("Bạn đã chiến thắng!");
                    b.show();
                }
            }
        });
    }

    // Phương thức chọn ô của đối thủ
    public void rivalMove(int position) {

        // Cập nhật bàn cờ
        boardGame.get(position).setStatus(STATUS_USER2);
        gridViewAdapter.notifyDataSetChanged();
        isWaiting = false;
        MainActivity.imgUser2.setBackgroundResource(0);
        MainActivity.imgUser1.setBackgroundResource(R.drawable.effect);

        // Kiểm tra đối thủ thắng hay không
        if (isWin(position, STATUS_USER2)) {
            Toast.makeText(_context, "Đối thủ đã thắng !", Toast.LENGTH_SHORT).show();
            isWaiting = true;

            AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle("Chia buồn");
            b.setMessage("Bạn đã thua cuộc!");
            b.show();
        }
    }

    // Phương thức kiểm tra mình thắng hay thua
    public boolean isWin(int position, char user) {

        // Kiểm tra cột
        int toadoX = position / 20;
        int toadoY = position % 20;

        int countCol = 1;
        int countRow = 1;
        int countMainDiagonal = 1;
        int countSkewDiagonal = 1;

        while(--toadoX >= 0 && boardGame.get(toadoX * 20 + toadoY).getStatus() == user) countCol++;
        toadoX = position / 20;
        while(++toadoX <= 19 && boardGame.get(toadoX * 20 + toadoY).getStatus() == user) countCol++;
        toadoX = position / 20;
        if (countCol >= 5) return true;

        // Kiểm tra dòng
        while(--toadoY >= 0 && boardGame.get(toadoX * 20 + toadoY).getStatus() == user) countRow++;
        toadoY = position % 20;
        while(++toadoY <= 19 && boardGame.get(toadoX * 20 + toadoY).getStatus() == user) countRow++;
        toadoY = position % 20;
        if (countRow >= 5) return true;

        // Kiểm tra chéo chính
        while(--toadoX >= 0 && --toadoY >= 0 && boardGame.get(toadoX * 20 + toadoY).getStatus() == user) countMainDiagonal++;
        toadoX = position / 20;
        toadoY = position % 20;
        while(++toadoX <= 19 && ++toadoY <= 19 && boardGame.get(toadoX * 20 + toadoY).getStatus() == user) countMainDiagonal++;
        toadoX = position / 20;
        toadoY = position % 20;
        if (countMainDiagonal >= 5) return true;

        // Kiểm tra chéo phụ
        while(--toadoX >= 0 && ++toadoY >= 0 && boardGame.get(toadoX * 20 + toadoY).getStatus() == user) countSkewDiagonal++;
        toadoX = position / 20;
        toadoY = position % 20;
        while(++toadoX <= 19 && --toadoY <= 19 && boardGame.get(toadoX * 20 + toadoY).getStatus() == user) countSkewDiagonal++;
        if (countSkewDiagonal >= 5) return true;

        return false;
    }

    // Phương thức reset bàn cờ
    public void reSet() {
        for (int i = 0; i < 400; i++) boardGame.get(i).setStatus(STATUS_EMPTY);
        gridViewAdapter.notifyDataSetChanged();
        isWaiting = false;
    }

}