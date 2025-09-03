package com.hjq.permissions.permission.dangerous;

import android.Manifest.permission;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.hjq.device.compat.DeviceOs;
import com.hjq.permissions.manifest.AndroidManifestInfo;
import com.hjq.permissions.manifest.node.PermissionManifestInfo;
import com.hjq.permissions.permission.PermissionNames;
import com.hjq.permissions.permission.PermissionPageType;
import com.hjq.permissions.permission.PermissionChannel;
import com.hjq.permissions.permission.base.IPermission;
import com.hjq.permissions.permission.common.DangerousPermission;
import com.hjq.permissions.tools.PermissionSettingPage;
import com.hjq.permissions.tools.PermissionVersion;
import java.util.ArrayList;
import java.util.List;

/**
 *    author : Android 轮子哥
 *    github : https://github.com/getActivity/XXPermissions
 *    time   : 2025/06/11
 *    desc   : 读取应用列表权限类
 */
public final class GetInstalledAppsPermission extends DangerousPermission {

    /** 当前权限名称，注意：该常量字段仅供框架内部使用，不提供给外部引用，如果需要获取权限名称的字符串，请直接通过 {@link PermissionNames} 类获取 */
    public static final String PERMISSION_NAME = PermissionNames.GET_INSTALLED_APPS;

    private static final String MIUI_OP_GET_INSTALLED_APPS_FIELD_NAME = "OP_GET_INSTALLED_APPS";
    private static final int MIUI_OP_GET_INSTALLED_APPS_DEFAULT_VALUE = 10022;

    private static final String ONE_UI_GET_APP_LIST_PERMISSION_NAME = "com.samsung.android.permission.GET_APP_LIST";

    public static final Parcelable.Creator<GetInstalledAppsPermission> CREATOR = new Parcelable.Creator<GetInstalledAppsPermission>() {

        @Override
        public GetInstalledAppsPermission createFromParcel(Parcel source) {
            return new GetInstalledAppsPermission(source);
        }

        @Override
        public GetInstalledAppsPermission[] newArray(int size) {
            return new GetInstalledAppsPermission[size];
        }
    };

    public GetInstalledAppsPermission() {
        // default implementation ignored
    }

    private GetInstalledAppsPermission(Parcel in) {
        super(in);
    }

    @NonNull
    @Override
    public String getPermissionName() {
        return PERMISSION_NAME;
    }

    @Override
    public String getRequestPermissionName(Context context) {
        if (PermissionVersion.isAndroid6() &&
            !isSupportRequestPermissionBySystem(context) &&
            isSupportRequestPermissionByOneUi(context)) {
            return ONE_UI_GET_APP_LIST_PERMISSION_NAME;
        }
        return super.getRequestPermissionName(context);
    }

    @NonNull
    @Override
    public PermissionChannel getPermissionChannel(@NonNull Context context) {
        if (PermissionVersion.isAndroid6() && (isSupportRequestPermissionBySystem(context) || isSupportRequestPermissionByOneUi(context))) {
            return PermissionChannel.REQUEST_PERMISSIONS;
        }
        return PermissionChannel.START_ACTIVITY_FOR_RESULT;
    }

    @NonNull
    @Override
    public PermissionPageType getPermissionPageType(@NonNull Context context) {
        if (this.getPermissionChannel(context) == PermissionChannel.REQUEST_PERMISSIONS) {
            return PermissionPageType.TRANSPARENT_ACTIVITY;
        }
        return PermissionPageType.OPAQUE_ACTIVITY;
    }

    @Override
    public int getFromAndroidVersion(@NonNull Context context) {
        return PermissionVersion.ANDROID_4_2;
    }

    @Override
    public boolean isSupportRequestPermission(@NonNull Context context) {
        // 获取父类方法的返回值，看看它是不是支持申请的，这个是前提条件
        boolean superMethodSupportRequestPermission = super.isSupportRequestPermission(context);
        if (superMethodSupportRequestPermission) {
            if (PermissionVersion.isAndroid6() && (isSupportRequestPermissionBySystem(context) || isSupportRequestPermissionByOneUi(context))) {
                // 表示支持申请
                return true;
            }

            if (PermissionVersion.isAndroid4_4() && DeviceOs.isMiui() && isSupportRequestPermissionByMiui()) {
                // 通过 MIUI 优化开关来决定是不是支持开启
                return DeviceOs.isMiuiOptimization();
            }
        }
        return superMethodSupportRequestPermission;
    }

    @Override
    public boolean isGrantedPermission(@NonNull Context context, boolean skipRequest) {
        if (PermissionVersion.isAndroid6() && (isSupportRequestPermissionBySystem(context) || isSupportRequestPermissionByOneUi(context))) {
            return checkSelfPermission(context, getRequestPermissionName(context));
        }

        if (PermissionVersion.isAndroid4_4() && isSupportRequestPermissionByMiui()) {
            if (!DeviceOs.isMiuiOptimization()) {
                // 如果当前没有开启 MIUI 优化，则直接返回 true，表示已经授权，因为在这种情况下
                // 就算跳转 MIUI 权限设置页，用户也授权了，用代码判断权限还是没有授予的状态
                // 所以在没有开启 MIUI 优化的情况下，就告诉外层已经授予了，避免外层去引导用户跳转到权限设置页
                return true;
            }
            // 经过测试发现，OP_GET_INSTALLED_APPS 是小米在 Android 6.0 才加上的，看了 Android 5.0 的 MIUI 并没有出现读取应用列表的权限
            return checkOpPermission(context, MIUI_OP_GET_INSTALLED_APPS_FIELD_NAME, MIUI_OP_GET_INSTALLED_APPS_DEFAULT_VALUE, true);
        }

        // 如果不支持申请，则直接返回 true（代表有这个权限），反正也不会崩溃，顶多就是获取不到第三方应用列表
        return true;
    }

    @Override
    public boolean isDoNotAskAgainPermission(@NonNull Activity activity) {
        if (PermissionVersion.isAndroid6() && (isSupportRequestPermissionBySystem(activity) || isSupportRequestPermissionByOneUi(activity))) {
            // 如果支持申请，那么再去判断权限是否永久拒绝
            return isDoNotAskAgainPermissionByStandardVersion(activity);
        }

        if (PermissionVersion.isAndroid4_4() && DeviceOs.isMiui() && isSupportRequestPermissionByMiui()) {
            if (!DeviceOs.isMiuiOptimization()) {
                return false;
            }
            // 如果在没有授权的情况下返回 true 表示永久拒绝，这样就能走后面的判断，让外层调用者跳转到小米定制的权限设置页面
            return !isGrantedPermission(activity);
        }

        // 如果不支持申请，则直接返回 false（代表没有永久拒绝）
        return false;
    }

    @NonNull
    @Override
    public List<Intent> getPermissionSettingIntents(@NonNull Context context, boolean skipRequest) {
        List<Intent> intentList = new ArrayList<>();
        Intent intent;

        if ((DeviceOs.isHyperOsByChina() && DeviceOs.isHyperOsOptimization()) ||
            (DeviceOs.isMiuiByChina() && DeviceOs.isMiuiOptimization())) {
            intent = PermissionSettingPage.getXiaoMiApplicationPermissionPageIntent(context);
            intentList.add(intent);
        }

        intent = getApplicationDetailsSettingIntent(context);
        intentList.add(intent);

        intent = getManageApplicationSettingIntent();
        intentList.add(intent);

        intent = getApplicationSettingIntent();
        intentList.add(intent);

        intent = getAndroidSettingIntent();
        intentList.add(intent);

        return intentList;
    }

    @Override
    protected void checkSelfByManifestFile(@NonNull Activity activity,
                                           @NonNull List<IPermission> requestList,
                                           @NonNull AndroidManifestInfo manifestInfo,
                                           @NonNull List<PermissionManifestInfo> permissionInfoList,
                                           @Nullable PermissionManifestInfo currentPermissionInfo) {
        super.checkSelfByManifestFile(activity, requestList, manifestInfo, permissionInfoList, currentPermissionInfo);
        // 经过在三星的手机上面的测试，发现不需要在清单文件添加 com.samsung.android.permission.GET_APP_LIST 也能申请成功并且成功读取到应用列表
        // PermissionManifestInfo oneUiGetAppListPermission = findPermissionInfoByList(permissionInfoList, ONE_UI_GET_APP_LIST_PERMISSION_NAME);
        // checkPermissionRegistrationStatus(oneUiGetAppListPermission, ONE_UI_GET_APP_LIST_PERMISSION_NAME, PermissionManifestInfo.DEFAULT_MAX_SDK_VERSION);

        // 当前 targetSdk 必须大于 Android 11，否则停止检查
        if (PermissionVersion.getTargetVersion(activity) < PermissionVersion.ANDROID_11) {
            return;
        }

        String queryAllPackagesPermissionName;
        if (PermissionVersion.isAndroid11()) {
            queryAllPackagesPermissionName = permission.QUERY_ALL_PACKAGES;
        } else {
            queryAllPackagesPermissionName = "android.permission.QUERY_ALL_PACKAGES";
        }

        PermissionManifestInfo permissionInfo = findPermissionInfoByList(permissionInfoList, queryAllPackagesPermissionName);
        if (permissionInfo != null || !manifestInfo.queriesPackageList.isEmpty()) {
            return;
        }

        // 在 targetSdk >= 30 的时候，申请读取应用列表权限需要做一下处理
        // 1. 读取所有的应用：在清单文件中注册 QUERY_ALL_PACKAGES 权限
        // 2. 读取部分特定的应用：添加需要读取应用的包名到 <queries> 标签中
        // 以上两种解决方案需要二选一，否则就算申请 GET_INSTALLED_APPS 权限成功也是白搭，也是获取不到第三方安装列表信息的
        // 一般情况选择第一种解决方案，但是如果你要兼顾 GooglePlay 商店，直接注册 QUERY_ALL_PACKAGES 权限可能没办法上架，那么就需要用到第二种办法
        // Github issue：https://github.com/getActivity/XXPermissions/issues/359
        throw new IllegalStateException("Please register permissions in the AndroidManifest.xml file " +
            "<uses-permission android:name=\"" + queryAllPackagesPermissionName + "\" />, "
            + "or add the app package name to the <queries> tag in the AndroidManifest.xml file");
    }

    /**
     * 判断是否支持获取应用列表权限
     */
    @SuppressWarnings("deprecation")
    @RequiresApi(PermissionVersion.ANDROID_6)
    private boolean isSupportRequestPermissionBySystem(Context context) {
        try {
            PermissionInfo permissionInfo = context.getPackageManager().getPermissionInfo(getPermissionName(), 0);
            if (permissionInfo != null) {
                final int protectionLevel;
                if (PermissionVersion.isAndroid9()) {
                    protectionLevel = permissionInfo.getProtection();
                } else {
                    protectionLevel = (permissionInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE);
                }
                return protectionLevel == PermissionInfo.PROTECTION_DANGEROUS;
            }
        } catch (PackageManager.NameNotFoundException e) {
            // 没有这个权限时会抛出：android.content.pm.PackageManager$NameNotFoundException: com.android.permission.GET_INSTALLED_APPS
            e.printStackTrace();
        }

        try {
            // 移动终端应用软件列表权限实施指南：http://www.taf.org.cn/upload/AssociationStandard/TTAF%20108-2022%20%E7%A7%BB%E5%8A%A8%E7%BB%88%E7%AB%AF%E5%BA%94%E7%94%A8%E8%BD%AF%E4%BB%B6%E5%88%97%E8%A1%A8%E6%9D%83%E9%99%90%E5%AE%9E%E6%96%BD%E6%8C%87%E5%8D%97.pdf
            // 这是兜底方案，因为测试了大量的机型，除了荣耀的 Magic UI 有按照这个规范去做，其他厂商（包括华为的 HarmonyOS）都没有按照这个规范去做
            // 虽然可以只用上面那种判断权限是不是危险权限的方式，但是避免不了有的手机厂商用下面的这种，所以两种都写比较好，小孩子才做选择，大人我全都要
            return Settings.Secure.getInt(context.getContentResolver(), "oem_installed_apps_runtime_permission_enable") == 1;
        } catch (Settings.SettingNotFoundException e) {
            // 没有这个系统属性时会抛出：android.provider.Settings$SettingNotFoundException: oem_installed_apps_runtime_permission_enable
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 判断当前 MIUI 版本是否支持申请读取应用列表权限
     */
    @RequiresApi(PermissionVersion.ANDROID_4_4)
    private static boolean isSupportRequestPermissionByMiui() {
        if (!DeviceOs.isMiui()) {
            return false;
        }
        return isExistOpPermission(MIUI_OP_GET_INSTALLED_APPS_FIELD_NAME);
    }

    /**
     * 判断当前 OneUI 版本是否支持申请读取应用列表权限
     */
    @RequiresApi(PermissionVersion.ANDROID_6)
    @SuppressWarnings("deprecation")
    private static boolean isSupportRequestPermissionByOneUi(@NonNull Context context) {
        if (!DeviceOs.isOneUi()) {
            return false;
        }
        try {
            PermissionInfo permissionInfo = context.getPackageManager().getPermissionInfo(ONE_UI_GET_APP_LIST_PERMISSION_NAME, 0);
            if (permissionInfo != null) {
                final int protectionLevel;
                if (PermissionVersion.isAndroid9()) {
                    protectionLevel = permissionInfo.getProtection();
                } else {
                    protectionLevel = (permissionInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE);
                }
                return protectionLevel == PermissionInfo.PROTECTION_DANGEROUS;
            }
        } catch (PackageManager.NameNotFoundException e) {
            // 没有这个权限时会抛出：android.content.pm.PackageManager$NameNotFoundException: com.samsung.android.permission.GET_APP_LIST
            // 实测在 OneUI 5.1 上面没有这个权限，到了 OneUI 5.1.1 才发现有这个权限，
            // 所以基本可以断定是在 OneUI 5.1.1 这个版本才开始支持申请这个权限
            e.printStackTrace();
        }
        return false;
    }
}