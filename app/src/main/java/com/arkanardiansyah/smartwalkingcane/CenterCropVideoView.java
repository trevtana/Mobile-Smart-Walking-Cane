package com.arkanardiansyah.smartwalkingcane;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

public class CenterCropVideoView extends VideoView {

    public CenterCropVideoView(Context context) {
        super(context);
    }

    public CenterCropVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CenterCropVideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        // Mengubah ukuran video agar ter-crop
        int width = getWidth();
        int height = getHeight();

        // Sesuaikan ukuran dengan perangkat
        int videoWidth = getWidth();
        int videoHeight = getHeight();

        // Mengatur video ke posisi center crop
        if (videoWidth * height > width * videoHeight) {
            // Lebar terlalu besar dibanding tinggi
            int newWidth = (height * videoWidth) / videoHeight;
            int offset = (newWidth - width) / 2;
            setPadding(-offset, 0, -offset, 0);
        } else {
            // Tinggi terlalu besar dibanding lebar
            int newHeight = (width * videoHeight) / videoWidth;
            int offset = (newHeight - height) / 2;
            setPadding(0, -offset, 0, -offset);
        }
    }
}
