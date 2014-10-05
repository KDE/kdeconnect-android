package org.kde.kdeconnect.UserInterface;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.ImageButton;
import android.widget.LinearLayout;

public class MaxWidthImageButton extends ImageButton {

    public MaxWidthImageButton(Context context) {

        super(context);
    }

    public MaxWidthImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxWidth = getMaxWidth();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if(getMeasuredWidth() > maxWidth){
            setMeasuredDimension(maxWidth, getMeasuredHeight());
        }
    }
}

