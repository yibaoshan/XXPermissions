package com.hjq.permissions.start;

import android.content.Intent;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

/**
 *    author : Android 轮子哥
 *    github : https://github.com/getActivity/XXPermissions
 *    time   : 2025/05/20
 *    desc   : startActivity 委托接口
 */
public interface IStartActivityDelegate {

    /**
     * 跳转 Activity
     */
    void startActivity(@NonNull Intent intent);

    /**
     * 跳转 Activity（需要返回结果）
     */
    void startActivityForResult(@NonNull Intent intent, @IntRange(from = 1, to = 65535) int requestCode);
}