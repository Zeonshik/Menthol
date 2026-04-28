package org.telegram.messenger;

public class MentholIdentityController {
    public static final long DEVELOPER_USER_ID = 8160766527L;

    public static boolean isDeveloper(long userId) {
        return userId == DEVELOPER_USER_ID;
    }
}
