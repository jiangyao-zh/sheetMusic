package io.dcloud.feature.uniapp.bridge;

public interface UniJSCallback {
    void invoke(Object data);

    void invokeAndKeepAlive(Object data);
}
