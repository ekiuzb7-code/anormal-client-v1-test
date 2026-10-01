package com.anormal.client.module.impl.client;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

import java.util.HashSet;
import java.util.Set;

public class Friends extends Module {
    private static final Set<String> friendList = new HashSet<>();

    public Friends() {
        super("Friends", "Manage friendly players to exclude them from combat targeting", Category.CLIENT);
    }

    public static boolean isFriend(String name) {
        return friendList.contains(name.toLowerCase());
    }

    public static void addFriend(String name) {
        friendList.add(name.toLowerCase());
    }

    public static void removeFriend(String name) {
        friendList.remove(name.toLowerCase());
    }
}
