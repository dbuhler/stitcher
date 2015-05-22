package dbuhler.stitcher;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import java.io.File;

/**
 * This activity lets the user choose the images for stitching. The user may either browse for the
 * images on the device or take pictures with the device's camera.
 *
 * @author  Dan Buhler
 * @version 2015-04-06
 */
public final class MainActivity extends Activity
{
    private ImageView imageViewLeft;
    private ImageView imageViewRight;
    private Uri       imageUriLeft;
    private Uri       imageUriRight;
    private boolean   showAcceptButton;

    /**
     * Called when the activity is starting. Inflates the activity's UI.
     *
     * @param savedInstanceState Contains the data in onSaveInstanceState(Bundle) if applicable.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageViewLeft    = (ImageView) findViewById(R.id.image_view_left);
        imageViewRight   = (ImageView) findViewById(R.id.image_view_right);
        showAcceptButton = false;
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
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * Prepare the activity's options menu to be displayed. This is called right before the menu is
     * shown, every time it is shown. Sets the visibility of the accept button.
     *
     * @param menu The options menu as last shown or first initialized by onCreateOptionsMenu().
     * @return True if the menu is to be displayed; false if it will not be shown.
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        menu.findItem(R.id.action_done).setVisible(showAcceptButton);
        return super.onPrepareOptionsMenu(menu);
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
        if (item.getItemId() == R.id.action_done)
        {
            startNextActivity();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Called when an activity exits. Processes the results of activities that are triggered by the
     * image buttons, i.e. updates the images in the image views.
     *
     * @param requestCode The integer request code originally supplied to startActivityForResult().
     * @param resultCode  The integer result code returned by the child activity via setResult().
     * @param resultData  An Intent, which can return result data to the caller.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData)
    {
        if (resultCode == Activity.RESULT_OK)
        {
            switch (requestCode)
            {
                case R.id.button_browse_left:

                    if (resultData != null)
                    {
                        imageUriLeft = resultData.getData();
                        imageViewLeft.setImageURI(imageUriLeft);
                    }
                    break;

                case R.id.button_browse_right:

                    if (resultData != null)
                    {
                        imageUriRight = resultData.getData();
                        imageViewRight.setImageURI(imageUriRight);
                    }
                    break;

                case R.id.button_camera_left:

                    if (imageUriLeft != null)
                    {
                        imageViewLeft.setImageURI(null);
                        imageViewLeft.setImageURI(imageUriLeft);
                    }
                    break;

                case R.id.button_camera_right:

                    if (imageUriRight != null)
                    {
                        imageViewRight.setImageURI(null);
                        imageViewRight.setImageURI(imageUriRight);
                    }
                    break;

                default:
                    super.onActivityResult(requestCode, resultCode, resultData);
                    return;
            }

            // If both images have been loaded, show the accept button.
            if (imageUriLeft != null && imageUriRight != null)
            {
                showAcceptButton = true;
                invalidateOptionsMenu();
            }
        }

        super.onActivityResult(requestCode, resultCode, resultData);
    }

    /**
     * Starts an ACTION_OPEN_DOCUMENT activity that lets the user browse for an image file.
     *
     * @param view The view that triggered this event.
     */
    public void buttonBrowse_Click(View view)
    {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, view.getId());
    }

    /**
     * Starts an ACTION_IMAGE_CAPTURE activity that lets the user take a picture with the camera.
     *
     * @param view The view that triggered this event.
     */
    public void buttonCamera_Click(View view)
    {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        switch (view.getId())
        {
            case R.id.button_camera_left:

                imageUriLeft = Uri.fromFile(new File(getExternalFilesDir(null), "image1"));
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUriLeft);
                break;

            case R.id.button_camera_right:

                imageUriRight = Uri.fromFile(new File(getExternalFilesDir(null), "image2"));
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUriRight);
                break;
        }

        startActivityForResult(intent, view.getId());
    }

    /**
     * Starts the next activity and passes the URIs of the selected images to it.
     */
    private void startNextActivity()
    {
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("imageUriLeft",  imageUriLeft);
        intent.putExtra("imageUriRight", imageUriRight);
        startActivity(intent);
    }
}