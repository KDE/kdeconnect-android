package org.kde.kdeconnect.UserInterface;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import org.kde.kdeconnect_tp.R;

public class MaxWidthImageButton extends ImageButton {

    int mMaxWidth = Integer.MAX_VALUE;

    public MaxWidthImageButton(Context context) {

        super(context);
    }

    public MaxWidthImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.MaxWidthImageButton);
        mMaxWidth = a.getDimensionPixelSize(R.styleable.MaxWidthImageButton_maxWidth, Integer.MAX_VALUE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if(getMeasuredWidth() > mMaxWidth){
            setMeasuredDimension(mMaxWidth, getMeasuredHeight());
        }
    }
}

