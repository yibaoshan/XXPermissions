package com.hjq.permissions;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hjq.permissions.fragment.factory.PermissionFragmentFactory;
import com.hjq.permissions.fragment.factory.PermissionFragmentFactoryByApp;

import com.hjq.permissions.manifest.AndroidManifestParser;
import com.hjq.permissions.permission.PermissionChannel;
import com.hjq.permissions.permission.base.IPermission;
import com.hjq.permissions.start.StartActivityAgent;
import com.hjq.permissions.tools.PermissionApi;
import com.hjq.permissions.tools.PermissionChecker;
import com.hjq.permissions.tools.PermissionSettingPage;
import com.hjq.permissions.tools.PermissionUtils;
import java.util.ArrayList;
import java.util.List;

/**
 *    author : Android 轮子哥
 *    github : https://github.com/getActivity/XXPermissions
 *    time   : 2018/06/15
 *    desc   : Android 权限请求入口类
 */
@SuppressWarnings({"unused", "deprecation"})
public final class XXPermissions {

    /** 权限设置页跳转请求码 */
    public static final int REQUEST_CODE = 1024 + 1;

    /** 权限申请拦截器的类型（全局生效） */
    private static Class<? extends OnPermissionInterceptor> sPermissionInterceptorClass;

    /** 权限请求描述器的类型（全局生效） */
    private static Class<? extends OnPermissionDescription> sPermissionDescriptionClass;

    /** 是否为检查模式（全局生效） */
    private static Boolean sCheckMode;

    /**
     * 设置请求的对象
     *
     * @param context          当前 Activity，可以传入栈顶的 Activity
     */
    public static XXPermissions with(@NonNull Context context) {
        return new XXPermissions(context);
    }

    public static XXPermissions with(@NonNull Fragment appFragment) {
        return new XXPermissions(appFragment);
    }



    /**
     * 设置是否开启错误检测模式（全局设置）
     */
    public static void setCheckMode(boolean checkMode) {
        sCheckMode = checkMode;
    }

    /**
     * 设置权限申请拦截器（全局设置）
     */
    public static void setPermissionInterceptor(Class<? extends OnPermissionInterceptor> clazz) {
        sPermissionInterceptorClass = clazz;
    }

    /**
     * 获取权限申请拦截器（全局）
     */
    @NonNull
    public static OnPermissionInterceptor getPermissionInterceptor() {
        if (sPermissionInterceptorClass != null) {
            try {
                return sPermissionInterceptorClass.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new DefaultPermissionInterceptor();
    }

    /**
     * 设置权限描述器（全局设置）
     *
     * 这里解释一下，为什么不开放普通对象，而是只开放 Class 对象，这是因为如果用普通对象，那么就会导致全局都复用这一个对象
     * 而这个会带来一个后果，就是可能出现类内部字段的使用冲突，为了避免这一个问题，最好的解决方案是不去复用同一个对象
     */
    public static void setPermissionDescription(Class<? extends OnPermissionDescription> clazz) {
        sPermissionDescriptionClass = clazz;
    }

    /**
     * 获取权限描述器（全局）
     */
    @NonNull
    public static OnPermissionDescription getPermissionDescription() {
        if (sPermissionDescriptionClass != null) {
            try {
                return sPermissionDescriptionClass.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new DefaultPermissionDescription();
    }

    /** 申请的权限列表 */
    @NonNull
    private final List<IPermission> mRequestList = new ArrayList<>();

    /** Context 对象 */
    @Nullable
    private final Context mContext;

    /** App 包下的 Fragment 对象 */
    @Nullable
    private Fragment mAppFragment;



    /** 权限请求拦截器 */
    @Nullable
    private OnPermissionInterceptor mPermissionInterceptor;

    /** 权限请求描述 */
    @Nullable
    private OnPermissionDescription mPermissionDescription;

    /** 设置不检查 */
    @Nullable
    private Boolean mCheckMode;

    private XXPermissions(@NonNull Context context) {
        mContext = context;
    }

    private XXPermissions(@NonNull Fragment appFragment) {
        mAppFragment = appFragment;
        mContext = appFragment.getActivity();
    }



    /**
     * 添加单个权限
     */
    public XXPermissions permission(@NonNull IPermission permission) {
        // 这种写法的作用：如果出现重复添加的权限，则以最后添加的权限为主
        mRequestList.remove(permission);
        mRequestList.add(permission);
        return this;
    }

    /**
     * 添加多个权限
     */
    public XXPermissions permissions(@NonNull List<IPermission> permissions) {
        if (permissions.isEmpty()) {
            return this;
        }

        for (int i = 0; i < permissions.size(); i++) {
            permission(permissions.get(i));
        }
        return this;
    }

    public XXPermissions permissions(@NonNull IPermission[] permissions) {
        return permissions(PermissionUtils.asArrayList(permissions));
    }

    /**
     * 设置权限请求拦截器
     */
    public XXPermissions interceptor(@Nullable OnPermissionInterceptor permissionInterceptor) {
        mPermissionInterceptor = permissionInterceptor;
        return this;
    }

    /**
     * 设置权限请求描述
     */
    public XXPermissions description(@Nullable OnPermissionDescription permissionDescription) {
        mPermissionDescription = permissionDescription;
        return this;
    }

    /**
     * 设置不触发错误检测机制
     */
    public XXPermissions unchecked() {
        mCheckMode = false;
        return this;
    }

    /**
     * 请求权限
     */
    public void request(@Nullable OnPermissionCallback callback) {
        if (mContext == null) {
            return;
        }

        if (mPermissionInterceptor == null) {
            mPermissionInterceptor = getPermissionInterceptor();
        }

        if (mPermissionDescription == null) {
            mPermissionDescription = getPermissionDescription();
        }

        final Context context = mContext;

        final Fragment appFragment = mAppFragment;



        final OnPermissionInterceptor permissionInterceptor = mPermissionInterceptor;

        final OnPermissionDescription permissionDescription = mPermissionDescription;

        // 权限请求列表（为什么直接不用字段？因为框架要兼容新旧权限，在低版本下会自动添加旧权限申请，为了避免重复添加）
        List<IPermission> requestList = new ArrayList<>(mRequestList);

        // 从 Context 对象中获得 Activity 对象
        Activity activity = PermissionUtils.findActivity(context);

        if (isCheckMode(context)) {
            // 检查传入的 Activity 或者 Fragment 状态是否正常
            PermissionChecker.checkActivityStatus(activity);
            if (appFragment != null) {
                PermissionChecker.checkAppFragmentStatus(appFragment);
            }
            // 检查传入的权限是否正常
            PermissionChecker.checkPermissionList(activity, requestList, AndroidManifestParser.getAndroidManifestInfo(context));
        }

        // 检查 Activity 是不是不可用
        if (PermissionUtils.isActivityUnavailable(activity)) {
            return;
        }

        // 优化所申请的权限列表
        PermissionApi.addOldPermissionsByNewPermissions(activity, requestList);

        // 判断要申请的权限是否都授予了
        if (PermissionApi.isGrantedPermissions(context, requestList)) {
            // 如果是的话，就不申请权限，而是通知权限申请成功
            permissionInterceptor.onRequestPermissionEnd(activity, true, requestList, requestList, new ArrayList<>(), callback);
            return;
        }

        final PermissionFragmentFactory<?, ?> fragmentFactory;
        if (appFragment != null) {
            if (PermissionUtils.isFragmentUnavailable(appFragment)) {
                return;
            }
            fragmentFactory = generatePermissionFragmentFactory(activity, appFragment);
        } else {
            fragmentFactory = generatePermissionFragmentFactory(activity);
        }

        // 申请没有授予过的权限
        permissionInterceptor.onRequestPermissionStart(activity, requestList, fragmentFactory, permissionDescription, callback);
    }

    /**
     * 当前是否为检测模式
     */
    private boolean isCheckMode(@NonNull Context context) {
        if (mCheckMode == null) {
            if (sCheckMode == null) {
                sCheckMode = PermissionUtils.isDebugMode(context);
            }
            mCheckMode = sCheckMode;
        }
        return mCheckMode;
    }

    /**
     * 判断一个或多个权限是否全部授予了
     */
    public static boolean isGrantedPermission(@NonNull Context context, @NonNull IPermission permission) {
        return permission.isGrantedPermission(context);
    }

    public static boolean isGrantedPermissions(@NonNull Context context, @NonNull IPermission[] permissions) {
        return isGrantedPermissions(context, PermissionUtils.asArrayList(permissions));
    }

    public static boolean isGrantedPermissions(@NonNull Context context, @NonNull List<IPermission> permissions) {
        return PermissionApi.isGrantedPermissions(context, permissions);
    }

    /**
     * 从权限列表中获取已授予的权限
     */
    public static List<IPermission> getGrantedPermissions(@NonNull Context context, @NonNull IPermission[] permissions) {
        return getGrantedPermissions(context, PermissionUtils.asArrayList(permissions));
    }

    public static List<IPermission> getGrantedPermissions(@NonNull Context context, @NonNull List<IPermission> permissions) {
        return PermissionApi.getGrantedPermissions(context, permissions);
    }

    /**
     * 从权限列表中获取没有授予的权限
     */
    public static List<IPermission> getDeniedPermissions(@NonNull Context context, @NonNull IPermission[] permissions) {
        return getDeniedPermissions(context, PermissionUtils.asArrayList(permissions));
    }

    public static List<IPermission> getDeniedPermissions(@NonNull Context context, @NonNull List<IPermission> permissions) {
        return PermissionApi.getDeniedPermissions(context, permissions);
    }

    /**
     * 判断两个权限是否相等
     */
    public static boolean equalsPermission(@NonNull IPermission permission1, @NonNull IPermission permission2) {
        return PermissionUtils.equalsPermission(permission1, permission2);
    }

    public static boolean equalsPermission(@NonNull IPermission permission1, @NonNull String permission2) {
        return PermissionUtils.equalsPermission(permission1, permission2);
    }

    public static boolean equalsPermission(@NonNull String permissionName1, @NonNull String permission2) {
        return PermissionUtils.equalsPermission(permissionName1, permission2);
    }

    /**
     * 判断权限列表中是否包含某个权限
     */
    public static boolean containsPermission(@NonNull List<IPermission> permissions, @NonNull IPermission permission) {
        return PermissionUtils.containsPermission(permissions, permission);
    }

    public static boolean containsPermission(@NonNull List<IPermission> permissions, @NonNull String permissionName) {
        return PermissionUtils.containsPermission(permissions, permissionName);
    }

    /**
     * 判断某个权限是否为健康权限
     */
    public static boolean isHealthPermission(@NonNull IPermission permission) {
        return PermissionApi.isHealthPermission(permission);
    }

    /**
     * 判断一个或多个权限是否被勾选了不再询问的选项
     *
     * 如果判断的权限中包含了危险权限，则需要特别注意：
     * 2. 如果在应用启动后，没有向系统申请过这个危险权限，而是直接去判断它有没有勾选不再询问的选项，这样得到的结果是不准的，建议在权限回调方法中调用，除此之外没有更好的解决方法
     * 3. 如果危险权限在申请的过程中，如果用户不是通过点击《不允许》选项来取消权限，而是通过点击返回键或者点击系统授权框外层区域来取消授权的，这样得到的结果是不准的，这个问题无解
     */
    public static boolean isDoNotAskAgainPermission(@NonNull Activity activity, @NonNull IPermission permission) {
        return permission.isDoNotAskAgainPermission(activity);
    }

    public static boolean isDoNotAskAgainPermissions(@NonNull Activity activity, @NonNull IPermission[] permissions) {
        return isDoNotAskAgainPermissions(activity, PermissionUtils.asArrayList(permissions));
    }

    public static boolean isDoNotAskAgainPermissions(@NonNull Activity activity, @NonNull List<IPermission> permissions) {
        return PermissionApi.isDoNotAskAgainPermissions(activity, permissions);
    }

    /* android.content.Context */

    public static void startPermissionActivity(@NonNull Context context) {
        startPermissionActivity(context, new ArrayList<>(0));
    }

    public static void startPermissionActivity(@NonNull Context context, @NonNull IPermission... permissions) {
        startPermissionActivity(context, PermissionUtils.asArrayList(permissions));
    }

    /**
     * 跳转到应用权限设置页
     *
     * @param permissions           没有授予或者被拒绝的权限组
     */
    public static void startPermissionActivity(@NonNull Context context, @NonNull List<IPermission> permissions) {
        Activity activity = PermissionUtils.findActivity(context);
        if (activity != null) {
            startPermissionActivity(activity, permissions);
            return;
        }
        StartActivityAgent.startActivity(context, PermissionApi.getBestPermissionSettingIntent(context, permissions, true));
    }

    /* android.app.Activity */

    public static void startPermissionActivity(@NonNull Activity activity) {
        startPermissionActivity(activity, new ArrayList<>(0));
    }

    public static void startPermissionActivity(@NonNull Activity activity,
                                               @NonNull IPermission... permissions) {
        startPermissionActivity(activity, PermissionUtils.asArrayList(permissions));
    }

    public static void startPermissionActivity(@NonNull Activity activity,
                                               @NonNull List<IPermission> permissions) {
        startPermissionActivity(activity, permissions, REQUEST_CODE);
    }

    public static void startPermissionActivity(@NonNull Activity activity,
                                               @NonNull List<IPermission> permissions,
                                               @IntRange(from = 1, to = 65535) int requestCode) {
        StartActivityAgent.startActivityForResult(activity,
            PermissionApi.getBestPermissionSettingIntent(activity, permissions, true), requestCode);
    }

    public static void startPermissionActivity(@NonNull Activity activity,
                                               @NonNull IPermission permission,
                                               @Nullable OnPermissionCallback callback) {
        startPermissionActivity(activity, PermissionUtils.asArrayList(permission), callback);
    }

    public static void startPermissionActivity(@NonNull Activity activity,
                                               @NonNull List<IPermission> permissions,
                                               @Nullable OnPermissionCallback callback) {
        if (PermissionUtils.isActivityUnavailable(activity)) {
            return;
        }
        if (permissions.isEmpty()) {
            StartActivityAgent.startActivity(activity, PermissionSettingPage.getCommonPermissionSettingIntent(activity));
            return;
        }
        PermissionFragmentFactory<?, ?> fragmentFactory = generatePermissionFragmentFactory(activity);
        fragmentFactory.createAndCommitFragment(permissions, PermissionChannel.START_ACTIVITY_FOR_RESULT, () -> {
            if (PermissionUtils.isActivityUnavailable(activity)) {
                return;
            }
            dispatchPermissionPageCallback(activity, permissions, callback);
        });
    }

    /* android.app.Fragment */

    public static void startPermissionActivity(@NonNull Fragment appFragment) {
        startPermissionActivity(appFragment, new ArrayList<>(0));
    }

    public static void startPermissionActivity(@NonNull Fragment appFragment,
                                               @NonNull IPermission... permissions) {
        startPermissionActivity(appFragment, PermissionUtils.asArrayList(permissions));
    }

    public static void startPermissionActivity(@NonNull Fragment appFragment,
                                               @NonNull List<IPermission> permissions) {
        startPermissionActivity(appFragment, permissions, REQUEST_CODE);
    }

    public static void startPermissionActivity(@NonNull Fragment appFragment,
                                               @NonNull List<IPermission> permissions,
                                               @IntRange(from = 1, to = 65535) int requestCode) {
        if (PermissionUtils.isFragmentUnavailable(appFragment)) {
            return;
        }
        Activity activity = appFragment.getActivity();
        if (PermissionUtils.isActivityUnavailable(activity) || PermissionUtils.isFragmentUnavailable(appFragment)) {
            return;
        }
        if (permissions.isEmpty()) {
            StartActivityAgent.startActivity(appFragment, PermissionSettingPage.getCommonPermissionSettingIntent(activity));
            return;
        }
        StartActivityAgent.startActivityForResult(appFragment,
            PermissionApi.getBestPermissionSettingIntent(activity, permissions, true), requestCode);
    }

    public static void startPermissionActivity(@NonNull Fragment appFragment,
                                                @NonNull IPermission permission,
                                                @Nullable OnPermissionCallback callback) {
        startPermissionActivity(appFragment, PermissionUtils.asArrayList(permission), callback);
    }

    public static void startPermissionActivity(@NonNull Fragment appFragment,
                                               @NonNull List<IPermission> permissions,
                                               @Nullable OnPermissionCallback callback) {
        if (PermissionUtils.isFragmentUnavailable(appFragment)) {
            return;
        }
        Activity activity = appFragment.getActivity();
        if (PermissionUtils.isActivityUnavailable(activity) || PermissionUtils.isFragmentUnavailable(appFragment)) {
            return;
        }
        if (permissions.isEmpty()) {
            StartActivityAgent.startActivity(appFragment, PermissionSettingPage.getCommonPermissionSettingIntent(activity));
            return;
        }
        PermissionFragmentFactory<?, ?> fragmentFactory = generatePermissionFragmentFactory(activity, appFragment);
        fragmentFactory.createAndCommitFragment(permissions, PermissionChannel.START_ACTIVITY_FOR_RESULT, () -> {
            if (PermissionUtils.isActivityUnavailable(activity) || PermissionUtils.isFragmentUnavailable(appFragment)) {
                return;
            }
            dispatchPermissionPageCallback(activity, permissions, callback);
        });
    }



    /**
     * 创建 Fragment 工厂
     */
    @NonNull
    private static PermissionFragmentFactory<?, ?> generatePermissionFragmentFactory(@NonNull Activity activity) {
        return generatePermissionFragmentFactory(activity, null);
    }



    @NonNull
    private static PermissionFragmentFactory<?, ?> generatePermissionFragmentFactory(@NonNull Activity activity,
                                                                                     @Nullable Fragment appFragment) {
        final PermissionFragmentFactory<?, ?> fragmentFactory;
        if (appFragment != null) {
            // appFragment.getChildFragmentManager 需要 minSdkVersion >=  17
            fragmentFactory = new PermissionFragmentFactoryByApp(appFragment.getActivity(), appFragment.getChildFragmentManager());
        } else {
            fragmentFactory = new PermissionFragmentFactoryByApp(activity, activity.getFragmentManager());
        }
        return fragmentFactory;
    }

    /**
     * 派发权限设置页回调
     */
    private static void dispatchPermissionPageCallback(@NonNull Context context,
                                                       @NonNull List<IPermission> permissions,
                                                       @Nullable OnPermissionCallback callback) {
        if (callback == null) {
            return;
        }
        List<IPermission> grantedList = new ArrayList<>(permissions.size());
        List<IPermission> deniedList = new ArrayList<>(permissions.size());
        // 遍历请求的权限，并且根据权限的授权状态进行分类
        for (IPermission permission : permissions) {
            if (permission.isGrantedPermission(context, false)) {
                grantedList.add(permission);
            } else {
                deniedList.add(permission);
            }
        }
        callback.onResult(grantedList, deniedList);
    }
}