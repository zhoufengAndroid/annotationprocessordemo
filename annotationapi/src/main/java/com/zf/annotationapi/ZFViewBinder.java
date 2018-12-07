package com.zf.annotationapi;

import android.app.Activity;

import java.util.LinkedHashMap;
import java.util.Map;

public class ZFViewBinder {
    private static final ActivityViewFinder activityFinder = new ActivityViewFinder();//默认声明一个Activity查找器
    private static final Map<String, ViewBinder> binderMap = new LinkedHashMap<>();//

    /**
     * Activity的注解绑定
     *
     * @param activity
     */
    public static void bind(Activity activity) {
        bind(activity, activity, activityFinder);
    }

    /**
     * '注解绑定
     *
     * @param host   表示注解 View 变量所在的类，也就是注解类
     * @param object 表示查找 View 的地方，Activity & View 自身就可以查找，Fragment 需要在自己的 itemView 中查找
     * @param finder ui绑定提供者接口
     */
    public static void bind(Object host, Object object, ViewFinder finder) {
        try {
            ViewBinder binder = findViewBinderForClass(host.getClass());
            if (binder != null) {
                binder.bindView(host, object, finder);
            }
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private static ViewBinder<Object> findViewBinderForClass(Class<?> cls)
            throws IllegalAccessException, InstantiationException {
        if (cls == null) return null;
        String clsName = cls.getName();
        ViewBinder<Object> viewBinder = binderMap.get(clsName);
        if (viewBinder != null) {
            return viewBinder;
        }
        try {
            Class<?> viewBindingClass = Class.forName(clsName + "$$ViewBinder");
            viewBinder = (ViewBinder<Object>) viewBindingClass.newInstance();
        } catch (ClassNotFoundException e) {
            viewBinder = findViewBinderForClass(cls.getSuperclass());
        }
        binderMap.put(clsName, viewBinder);
        return viewBinder;
    }

    public static void unBind(Object host) {
        String className = host.getClass().getName();
        ViewBinder binder = binderMap.get(className);
        if (binder != null) {
            binder.unBindView(host);
        }
        binderMap.remove(className);
    }
}
