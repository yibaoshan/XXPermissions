package com.hjq.permissions.tools;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hjq.permissions.permission.PermissionChannel;
import com.hjq.permissions.permission.base.IPermission;
import java.util.ArrayList;
import java.util.List;

/**
 *    author : Android 轮子哥
 *    github : https://github.com/getActivity/XXPermissions
 *    time   : 2021/12/31
 *    desc   : 权限函数工具
 */
public final class PermissionApi {

    /**
     * 判断某个权限是否为健康权限
     */
    public static boolean isHealthPermission(@NonNull IPermission permission) {
        return permission.getPermissionName().startsWith("android.permission.health.");
    }

    /**
     * 判断某个权限集合是否包含需要通过 startActivityForResult 授权的权限
     */
    public static boolean containsPermissionByStartActivityForResult(@NonNull Context context, @Nullable List<IPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }

        for (IPermission permission : permissions) {
            if (permission.getPermissionChannel(context) == PermissionChannel.START_ACTIVITY_FOR_RESULT) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断某些权限是否全部被授予
     */
    public static boolean isGrantedPermissions(@NonNull Context context, @NonNull List<IPermission> permissions) {
        if (permissions.isEmpty()) {
            return false;
        }

        for (IPermission permission : permissions) {
            if (!permission.isGrantedPermission(context)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取已经授予的权限
     */
    public static List<IPermission> getGrantedPermissions(@NonNull Context context, @NonNull List<IPermission> permissions) {
        List<IPermission> grantedList = new ArrayList<>(permissions.size());
        for (IPermission permission : permissions) {
            if (permission.isGrantedPermission(context)) {
                grantedList.add(permission);
            }
        }
        return grantedList;
    }

    /**
     * 获取已经拒绝的权限
     */
    public static List<IPermission> getDeniedPermissions(@NonNull Context context, @NonNull List<IPermission> permissions) {
        List<IPermission> deniedList = new ArrayList<>(permissions.size());
        for (IPermission permission : permissions) {
            if (!permission.isGrantedPermission(context)) {
                deniedList.add(permission);
            }
        }
        return deniedList;
    }

    /**
     * 在权限组中检查是否有某个权限是否被永久拒绝
     *
     * @param permissions            请求的权限
     */
    public static boolean isDoNotAskAgainPermissions(@NonNull Activity activity, @NonNull List<IPermission> permissions) {
        for (IPermission permission : permissions) {
            if (permission.isDoNotAskAgainPermission(activity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据传入的权限自动选择最合适的权限设置页的意图
     */
    @NonNull
    public static List<Intent> getBestPermissionSettingIntent(@NonNull Context context, @Nullable List<IPermission> permissions, boolean skipRequest) {
        // 如果失败的权限里面不包含特殊权限
        if (permissions == null || permissions.isEmpty()) {
            return PermissionSettingPage.getCommonPermissionSettingIntent(context);
        }

        // 创建一个新的集合对象，避免复用对象可能引发外层的冲突
        List<IPermission> realPermissions = new ArrayList<>(permissions);
        for (IPermission permission : permissions) {
            if (permission.getFromAndroidVersion(context) > PermissionVersion.getCurrentVersion()) {
                // 如果当前权限是高版本才出现的权限，则进行剔除
                realPermissions.remove(permission);
                continue;
            }

            List<IPermission> oldPermissions = permission.getOldPermissions(context);
            // 1. 如果旧版本列表不为空，并且当前权限是需要通过 startActivityForResult 授权的权限，就剔除它对应的旧版本权限
            // 例如：MANAGE_EXTERNAL_STORAGE -> READ_EXTERNAL_STORAGE、WRITE_EXTERNAL_STORAGE
            // 2. 如果旧版本列表不为空，并且当前权限对应的旧版本权限包含了需要通过 startActivityForResult 授权的权限，就剔除它对应的旧版本权限
            // 例如：POST_NOTIFICATIONS -> NOTIFICATION_SERVICE
            if (oldPermissions != null && !oldPermissions.isEmpty() &&
                (permission.getPermissionChannel(context) == PermissionChannel.START_ACTIVITY_FOR_RESULT ||
                    containsPermissionByStartActivityForResult(context, oldPermissions))) {
                realPermissions.removeAll(oldPermissions);
            }
        }

        if (realPermissions.isEmpty()) {
            return PermissionSettingPage.getCommonPermissionSettingIntent(context);
        }

        if (realPermissions.size() == 1) {
            return realPermissions.get(0).getPermissionSettingIntents(context, skipRequest);
        }

        List<Intent> prePermissionIntentList = realPermissions.get(0).getPermissionSettingIntents(context, skipRequest);
        for (int i = 1; i < realPermissions.size(); i++) {
            List<Intent> currentPermissionIntentList = realPermissions.get(i).getPermissionSettingIntents(context, skipRequest);
            // 对比这两个 Intent 列表的内容是否一致
            if (!PermissionUtils.equalsIntentList(currentPermissionIntentList, prePermissionIntentList)) {
                // 如果不一致，就结束循环
                break;
            }
            // 当前权限列表在下次循环就是上一个了，记录一下，可以避免重复获取，节省代码性能
            prePermissionIntentList = currentPermissionIntentList;

            // 如果集合中的 Intent 列表都一样，就直接按照当前的 Intent 列表去做跳转
            if (i == realPermissions.size() - 1) {
                return currentPermissionIntentList;
            }
        }
        return PermissionSettingPage.getCommonPermissionSettingIntent(context);
    }

    /**
     * 根据新权限添加旧权限
     */
    public static synchronized void addOldPermissionsByNewPermissions(@NonNull Context context, @NonNull List<IPermission> requestList) {
        // 这里需要将 index 设置成 -1，这样走到下面循环的时候，++i 第一次循环 index 就是 0 了
        int index = -1;
        // ++index 是前置递增（先将 index 的值加 1，再返回增加后的值）
        // index++ 是后置递增（先返回 i 的当前值，再将 i 的值加 1）
        while (++index < requestList.size()) {
            IPermission permission = requestList.get(index);
            // 如果当前运行的 Android 版本大于权限出现的 Android 版本，则证明这个权限在当前设备上不用添加旧权限
            if (PermissionVersion.getCurrentVersion() >= permission.getFromAndroidVersion(context)) {
                continue;
            }
            // 通过新权限查询到对应的旧权限
            List<IPermission> oldPermissions = permission.getOldPermissions(context);
            if (oldPermissions == null || oldPermissions.isEmpty()) {
                continue;
            }
            for (IPermission oldPermission : oldPermissions) {
                // 如果请求列表已经包含此权限，就不重复添加，直接跳过
                if (PermissionUtils.containsPermission(requestList, oldPermission)) {
                    continue;
                }
                // index + 1 是将旧版本的权限添加到新版本的权限后面，这样才能确保不打乱申请的传入顺序
                requestList.add(++index, oldPermission);
            }
        }
    }

    /**
     * 通过权限集合获取最大的间隔时间
     */
    public static int getMaxIntervalTimeByPermissions(@NonNull Context context, @Nullable List<IPermission> permissions) {
        if (permissions == null) {
            return 0;
        }
        int maxWaitTime = 0;
        for (IPermission permission : permissions) {
            int time = permission.getRequestIntervalTime(context);
            if (time == 0) {
                continue;
            }
            maxWaitTime = Math.max(maxWaitTime, time);
        }
        return maxWaitTime;
    }

    /**
     * 通过权限集合获取最大的回调等待时间
     */
    public static int getMaxWaitTimeByPermissions(@NonNull Context context, @Nullable List<IPermission> permissions) {
        if (permissions == null) {
            return 0;
        }
        int maxWaitTime = 0;
        for (IPermission permission : permissions) {
            int time = permission.getResultWaitTime(context);
            if (time == 0) {
                continue;
            }
            maxWaitTime = Math.max(maxWaitTime, time);
        }
        return maxWaitTime;
    }
}