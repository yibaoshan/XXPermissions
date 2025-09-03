package com.hjq.permissions.fragment;

import android.app.Activity;
import android.os.Bundle;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hjq.permissions.start.IStartActivityDelegate;

/**
 *    author : Android 轮子哥
 *    github : https://github.com/getActivity/XXPermissions
 *    time   : 2025/05/20
 *    desc   : Fragment 原生接口方法
 */
public interface IFragmentMethodNative<A extends Activity> extends IStartActivityDelegate {

    /** 获得 Activity 对象 */
    @Nullable
    A getActivity();

    /** 请求权限 */
    void requestPermissions(@NonNull String[] permissions, @IntRange(from = 1, to = 65535) int requestCode);

    /** 获得参数集 */
    @Nullable
    Bundle getArguments();

    /** 设置参数集 */
    void setArguments(@NonNull Bundle arguments);

    /** 设置是否保存实例，如果设置保存，则不会因为屏幕方向或配置变化而重新创建 */
    void setRetainInstance(boolean retainInstance);

    /** 当前 Fragment 是否已添加绑定 */
    boolean isAdded();

    /** 当前 Fragment 是否已移除 */
    boolean isRemoving();
}