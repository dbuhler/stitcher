package dbuhler.stitcher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * This is a modified ImageView that adjusts its size to the loaded image, which in turn is scaled
 * as much as possible to fit within the view's size constraints.
 *
 * @author  Dan Buhler
 * @version 2015-02-15
 */
public class FitImageView extends ImageView
{
    public FitImageView(Context context)
    {
        super(context);
    }

    public FitImageView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }
    
    public FitImageView(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Scales the view's image as much as possible without changing its aspect ratio. Sets the width
     * and height of the view such that it fits the scaled image.
     *
     * @param widthMeasureSpec  Horizontal space requirements as imposed by the parent.
     * @param heightMeasureSpec Vertical space requirements as imposed by the parent.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        Drawable drawable = getDrawable();

        if (drawable == null)
        {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
        else
        {
            int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
            int maxHeight = MeasureSpec.getSize(heightMeasureSpec);
            int imgWidth = drawable.getIntrinsicWidth();
            int imgHeight = drawable.getIntrinsicHeight();

            if (imgWidth * maxHeight >= maxWidth * imgHeight)
            {
                setMeasuredDimension(maxWidth, imgHeight * maxWidth / imgWidth);
            }
            else
            {
                setMeasuredDimension(imgWidth * maxHeight / imgHeight, maxHeight);
            }
        }
    }
}