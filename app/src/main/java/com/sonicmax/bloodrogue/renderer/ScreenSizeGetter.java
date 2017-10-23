package com.sonicmax.bloodrogue.renderer;

import android.content.res.Resources;

public class ScreenSizeGetter {
    public static int getWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    public static int getHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }
}
