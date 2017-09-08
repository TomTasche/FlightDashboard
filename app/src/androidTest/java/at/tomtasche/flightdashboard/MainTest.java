package at.tomtasche.flightdashboard;

import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;

import org.hamcrest.core.IsNull;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertThat;

/**
 * Instrumentation test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class MainTest {

    private static final int TIMEOUT = 5000;
    private static final String PACKAGE = "at.tomtasche.flightdashboard";

    private UiDevice mDevice;

    @Test
    public void enableLocation() {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start from the home screen
        mDevice.pressHome();

        // Wait for launcher
        final String launcherPackage = mDevice.getLauncherPackageName();
        assertThat(launcherPackage, IsNull.notNullValue());
        mDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), TIMEOUT);

        // Launch the app
        Context context = InstrumentationRegistry.getContext();
        final Intent intent = context.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        // Clear out any previous instances
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);

        // Wait for the app to appear
        mDevice.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), TIMEOUT);

        // permission
        try {
            UiObject locationSnackbarButton = mDevice.findObject(new UiSelector().resourceId("at.tomtasche.flightdashboard:id/snackbar_action"));
            locationSnackbarButton.click();
        } catch (UiObjectNotFoundException e) {
            throw new RuntimeException(e);
        }

        try {
            UiObject permissionButton = mDevice.findObject(new UiSelector().resourceId("com.android.packageinstaller:id/permission_allow_button"));
            permissionButton.click();
        } catch (UiObjectNotFoundException e) {
            throw new RuntimeException(e);
        }

        // gps
        try {
            UiObject locationSnackbarButton = mDevice.findObject(new UiSelector().resourceId("at.tomtasche.flightdashboard:id/snackbar_action"));
            locationSnackbarButton.clickAndWaitForNewWindow(TIMEOUT);
        } catch (UiObjectNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
