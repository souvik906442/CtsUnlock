package dev.t225.ctsunlock;

import java.lang.reflect.Method;
import java.util.Optional;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {
    private static final String TAG = "CtsUnlock: ";
    private static final String TARGET = "com.android.systemui.plugins.omni.AssistStateManager";
    // Google's own OmniEntryPoint document says only the CS helper is available,
    // so force just that one (not isOmniAvailable, whose launch target OmnientActivity is missing).
    private static final String[] METHODS = { "isCsHelperAvailable" };

    private volatile boolean hooked = false;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.android.systemui".equals(lpparam.packageName)) return;
        XposedBridge.log(TAG + "loaded in systemui");

        try {
            Class<?> c = XposedHelpers.findClass(TARGET, lpparam.classLoader);
            hookClass(c);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "direct lookup failed, waiting for loadClass: " + t);
        }

        // Fallback: class may be loaded later by another classloader.
        try {
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (hooked) return;
                    Object r = param.getResult();
                    if (r instanceof Class && TARGET.equals(((Class<?>) r).getName())) {
                        hookClass((Class<?>) r);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "loadClass hook failed: " + t);
        }
    }

    private synchronized void hookClass(Class<?> c) {
        if (hooked) return;
        int n = 0;
        for (Method m : c.getDeclaredMethods()) {
            for (String name : METHODS) {
                if (!m.getName().equals(name)) continue;
                final Class<?> rt = m.getReturnType();
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (rt == boolean.class || rt == Boolean.class) {
                            param.setResult(Boolean.TRUE);
                        } else if (rt == Optional.class) {
                            param.setResult(Optional.of(Boolean.TRUE));
                        }
                    }
                });
                XposedBridge.log(TAG + "hooked " + c.getName() + "#" + m.getName() + " -> " + rt.getName());
                n++;
            }
        }
        hooked = n > 0;
        if (!hooked) XposedBridge.log(TAG + "class found but no matching method: " + c.getName());
    }
}
