package com.cinemahub.service.notification;

import com.cinemahub.model.User;

/** Common interface for every notification type produced by NotificationFactory. */
public interface Notification {

    void send(User recipient, String message);
}
