package me.acardia.amalor.ui.navigation;

import java.util.HashSet;
import java.util.List;
import top.yukonga.miuix.kmp.nav.core.NavBackStackKt;
import top.yukonga.miuix.kmp.nav.core.NavController;

/** Exercise the same controller and snapshot-backed stack used by the application. */
class NavigationActionsCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void open(NavController nav, AmalorRoute route) {
        NavigationActionsKt.navigateTo(nav, route);
        check(new HashSet<>(nav.getBackStack()).size() == nav.getBackStack().size(), "content keys must be unique");
    }

    public static void main(String[] args) {
        var main = AmalorRoute.Main.INSTANCE;
        var weType = AmalorRoute.WeType.INSTANCE;
        var stack = NavBackStackKt.navBackStackOf(main);
        var nav = new NavController(stack);
        for (int i = 0; i < 100; i++) open(nav, weType);
        check(List.copyOf(stack).equals(List.of(main, weType)), "rapid repeat clicks append exactly one WeType entry: " + List.copyOf(stack));

        open(nav, AmalorRoute.About.INSTANCE);
        open(nav, AmalorRoute.Licenses.INSTANCE);
        open(nav, weType);
        check(List.copyOf(stack).equals(List.of(main, weType)), "an existing destination is reused even below the top");
        check(stack.get(1) == weType, "reuse keeps the existing route identity");

        check(nav.pop() && List.copyOf(stack).equals(List.of(main)), "one back returns to the list");
        open(nav, weType);
        check(List.copyOf(stack).equals(List.of(main, weType)), "returning then reopening is not blocked by a timer");
        for (int i = 0; i < 100; i++) {
            open(nav, AmalorRoute.Launcher.INSTANCE);
            open(nav, weType);
        }
        check(List.copyOf(stack).equals(List.of(main, weType)), "alternating queued destinations cannot leave duplicate entries");

        open(nav, main);
        check(List.copyOf(stack).equals(List.of(main)) && !nav.pop(), "root navigation never removes the root");
        open(nav, AmalorRoute.SystemUiStyle.INSTANCE);
        open(nav, AmalorRoute.StatusBar.INSTANCE);
        open(nav, AmalorRoute.NetworkTypeIcon.INSTANCE);
        open(nav, AmalorRoute.StatusBar.INSTANCE);
        check(List.copyOf(stack).equals(List.of(main, AmalorRoute.SystemUiStyle.INSTANCE, AmalorRoute.StatusBar.INSTANCE)),
                "nested settings preserve their existing parent stack");
        System.out.println("Navigation repeat-click/reuse/back/re-entry checks passed");
    }
}
