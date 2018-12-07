package com.zf.annotationapi;

public interface ViewBinder<T> {
    void bindView(T host, Object object, ViewFinder finder);

    void unBindView(T host);
}
