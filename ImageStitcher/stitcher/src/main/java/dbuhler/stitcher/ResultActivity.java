package dbuhler.stitcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Images.Media;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;

import org.opencv.android.OpenCVLoader;

/**
 * This activity performs the image stitching and presents the results to the user.
 *
 * @author  Dan Buhler
 * @version 2015-04-06
 */
public final class ResultActivity extends Activity implements SpinnerWaitDialog.OnNotifyListener
{
    private ImageView imageView;
    private Stitcher  stitcher;
    private Bitmap[]  images;

    /**
     * Called when the activity is starting. Inflates the activity's UI and performs the image
     * stitching.
     *
     * @param savedInstanceState Contains the data in onSaveInstanceState(Bundle) if applicable.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        imageView = (ImageView) findViewById(R.id.image_view);
        images    = new Bitmap[Stitcher.NUM_STEPS];

        // Get the two chosen images.
        Intent intent     = getIntent();
        Uri imageUriLeft  = intent.getParcelableExtra("imageUriLeft");
        Uri imageUriRight = intent.getParcelableExtra("imageUriRight");

        // Static OpenCV initialization.
        OpenCVLoader.initDebug();
        stitchImages(imageUriLeft, imageUriRight);
    }

    /**
     * Initialize the contents of the activity's options menu.
     *
     * @param menu The options menu of the activity.
     * @return True if the menu is to be displayed; false if it will not be shown.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu_result, menu);
        return true;
    }

    /**
     * This hook is called whenever an item in the options menu is selected.
     *
     * @param item The menu item that was selected.
     * @return False to allow normal menu processing to proceed, true to consume it here.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        for (int i = 0; i < Stitcher.NUM_STEPS; ++i)
        {
            // Find the correct step index from the item ID.
            int id = getResources().getIdentifier("action_step_" + i, "id", getPackageName());

            if (item.getItemId() == id)
            {
                updateActivity(i);
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Called when the runnable the SpinnerWaitDialog is waiting for is finished.
     *
     * @param requestId An ID for identifying what has been waiting for.
     */
    @Override
    public void onNotify(int requestId)
    {
        for (int i = 0; i < Stitcher.NUM_STEPS; ++i)
        {
            images[i] = stitcher.getStep(i);
        }

        updateActivity(Stitcher.NUM_STEPS - 1);
    }

    /**
     * Changes the activity's title and image according to the specified step.
     *
     * @param stepIndex The index of the step to display.
     */
    private void updateActivity(int stepIndex)
    {
        imageView.setImageBitmap(images[stepIndex]);

        setTitle(getResources().getIdentifier(
                "title_activity_result_" + stepIndex,
                "string", getPackageName()));
    }

    /**
     * Initializes and runs the image stitcher for the two images with the given URIs.
     *
     * @param imageUriLeft  The URI of the left image to stitch.
     * @param imageUriRight The URI of the right image to stitch.
     */
    private void stitchImages(Uri imageUriLeft, Uri imageUriRight)
    {
        final Bitmap bitmapLeft  = createBitmapFromUri(imageUriLeft);
        final Bitmap bitmapRight = createBitmapFromUri(imageUriRight);

        SpinnerWaitDialog<ResultActivity> dialog = new SpinnerWaitDialog<>(this);
        dialog.setTitle(R.string.dialog_wait_title);
        dialog.setMessage(R.string.dialog_wait_message);
        dialog.waitFor(0, new Runnable()
        {
            @Override
            public void run()
            {
                stitcher = new Stitcher(bitmapLeft, bitmapRight);
            }
        });
    }

    /**
     * Creates and returns a bitmap from the image with the given URI.
     *
     * @param uri The URI of the original image.
     * @return The created bitmap.
     */
    private Bitmap createBitmapFromUri(Uri uri)
    {
        try
        {
            return Media.getBitmap(getContentResolver(), uri);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }
}