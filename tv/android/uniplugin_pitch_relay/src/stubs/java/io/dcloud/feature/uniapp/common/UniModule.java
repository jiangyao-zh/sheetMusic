package io.dcloud.feature.uniapp.common;

import android.content.Context;

/**
 * 本地 stub：当 ../app/libs 尚未放入 DCloud 离线 SDK 时用于编译。
 * 集成真实 SDK 后会自动排除本 stubs 目录。
 */
public class UniModule {
    protected Context mUniSDKInstance;

    protected Context getContext() {
        return mUniSDKInstance;
    }
}
