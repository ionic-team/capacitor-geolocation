package com.capacitorjs.plugins.geolocation;

import static org.junit.Assert.assertTrue;

import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.PermissionCallback;
import java.lang.reflect.Method;
import org.junit.Test;

/**
 * Capacitor invokes permission callbacks with a null call when it no longer has the
 * saved call, for example if the app process was recreated while the permission
 * dialog was open. The callbacks must return before they use the call or the bridge.
 */
public class PermissionCallbackTest {

    @Test
    public void completeCurrentPosition_ignoresMissingCall() throws Exception {
        invokeWithNullCall("completeCurrentPosition");
    }

    @Test
    public void completeWatchPosition_ignoresMissingCall() throws Exception {
        invokeWithNullCall("completeWatchPosition");
    }

    private static void invokeWithNullCall(String methodName) throws Exception {
        Method method = GeolocationPlugin.class.getDeclaredMethod(methodName, PluginCall.class);
        // Capacitor registers methods with this annotation and later calls them with
        // method.invoke(plugin, savedCall), where savedCall may be null.
        assertTrue(method.isAnnotationPresent(PermissionCallback.class));
        method.setAccessible(true);
        method.invoke(new GeolocationPlugin(), (PluginCall) null);
    }
}
