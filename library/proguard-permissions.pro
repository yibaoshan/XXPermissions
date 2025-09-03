# 禁止混淆 IStartActivityDelegate 和 IFragmentMethodNative 接口及实现类涉及的方法名称
# 这是因为框架使用 App 包下的 Fragment 实现了这些接口，
# 这样做的目的是为了将 Fragment 抽象化，然后框架就可以不用关心具体是哪个 Fragment 来申请权限，
# 相关的 issue 地址：https://github.com/getActivity/XXPermissions/issues/371，
# 添加这个混淆规则是为了不让编译器混淆这些方法名，避免运行时出现 AbstractMethodError。
-keepclassmembers interface com.hjq.permissions.start.IStartActivityDelegate {
    <methods>;
}
-keepclassmembers interface com.hjq.permissions.fragment.IFragmentMethodNative {
    <methods>;
}

# 已删除 Support Fragment 相关混淆规则，因为不再使用 Support 库