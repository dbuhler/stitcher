package dbuhler.stitcher;

import android.app.Activity;
import android.app.ProgressDialog;

/**
 * This class allows to create a non-cancellable progress dialog that uses the spinner style and has
 * a method that lets you provide a runnable to wait for.
 *
 * @author  Dan Buhler
 * @version 2015-02-15
 */
public final class SpinnerWaitDialog <T extends Activity & SpinnerWaitDialog.OnNotifyListener>
        extends ProgressDialog
{
    private T activity;

    /**
     * The calling activity must implement this interface in order to be able to wait for a runnable
     * to be finished.
     */
    public interface OnNotifyListener
    {
        /**
         * Called when the runnable the SpinnerWaitDialog is waiting for is finished.
         *
         * @param requestId An ID for identifying what has been waiting for.
         */
        void onNotify(int requestId);
    }

    /**
     * Creates a new SpinnerWaitDialog.
     *
     * @param activity The calling activity.
     */
    public SpinnerWaitDialog(T activity)
    {
        super(activity, ProgressDialog.STYLE_SPINNER);
        this.activity = activity;
        setCancelable(false);
    }

    /**
     * Sets the message to display using the given resource ID.
     *
     * @param messageId The resource ID of the message to display.
     */
    public void setMessage(int messageId)
    {
        setMessage(getContext().getResources().getString(messageId));
    }

    /**
     * Displays the dialog until the given runnable has finished, then the dialog is dismissed.
     *
     * @param runnable A runnable during which the dialog is to be shown.
     */
    public void waitFor(final int requestId, final Runnable runnable)
    {
        show();

        // Run the runnable on a separate thread.
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                runnable.run();

                // Dismiss the dialog and notify the calling activity.
                dismiss();
                activity.runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        activity.onNotify(requestId);
                    }
                });
            }
        }).start();
    }
}