package me.acardia.amalor.xposed.systemui;

import android.view.Display;
import static me.acardia.amalor.xposed.systemui.NotificationAppearanceRules.TextRole.*;

/** Scope regressions: app resource collisions, custom content and generated reply actions. */
public final class NotificationAppearanceRulesCheck {
    public static void main(String[] args) {
        var rules = NotificationAppearanceRules.INSTANCE;
        require(rules.materialDisplayState(null), "Inflating notification can be prepared before display attachment");
        for (int state : new int[]{Display.STATE_ON, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND}) {
            require(rules.materialDisplayState(state), "Notification surface and text share display eligibility: " + state);
        }
        for (int state : new int[]{Display.STATE_OFF, Display.STATE_UNKNOWN, -1}) {
            require(!rules.materialDisplayState(state), "Inactive display is not a notification rendering target: " + state);
        }
        for (String id : new String[]{"clock", "aod_clock", "keyguard_clock", "date", "owner_info"}) {
            require(rules.textRole("com.android.systemui", id, false, false) == null,
                    "AOD support must not broaden notification text scope to " + id);
        }
        for (String namespace : new String[]{"com.example.chat", "com.example.mail", ""}) {
            for (String name : new String[]{"title", "alt_title", "text", "header_label", "notification_title",
                    "oplus_notification_expand_group_header_title", "inbox_text0", "actions"}) {
                require(rules.textRole(namespace, name, false, false) == null,
                        "App resource collision must not recolor custom content: " + namespace + ":" + name);
                require(!rules.actionContainer(namespace, name, "android.widget.LinearLayout"),
                        "An app actions ID is not a native action container");
            }
        }
        require(rules.textRole("android", "title", false, false) == Primary, "Template title");
        require(rules.textRole("android", "alt_title", false, false)
                == rules.textRole("android", "title", false, false),
                "The expanded notification source uses alt_title on the target device");
        require(rules.textRole("android", "message_text", false, false) == Secondary, "Message body");
        require(rules.textRole("com.android.systemui", "oplus_notification_expand_group_header_title", false, false)
                == Primary, "Expanded group title");
        require(rules.textRole("com.android.systemui", "header_label", false, false) == Secondary, "Section heading");
        for (int line = 0; line < 7; line++) {
            require(rules.textRole("android", "inbox_text" + line, false, false) == Secondary, "Inbox line " + line);
        }
        for (String invalid : new String[]{"inbox_text", "inbox_text7", "inbox_text0_extra", "right_icon"}) {
            require(rules.textRole("android", invalid, false, false) == null, "Do not broaden template scope: " + invalid);
        }
        require(rules.actionContainer("android", "actions", "android.widget.LinearLayout"), "Framework action row");
        require(rules.actionContainer("com.android.systemui", "", "com.android.systemui.statusbar.policy.SmartReplyView"),
                "Dynamically created smart replies");
        // A generated Notification.Action may have no usable ID. Its native container is the authority.
        require(rules.textRole("", "", true, false) == Action, "Floating-window reply and dynamic action");
        require(rules.textRole("android", "title", true, true) == Action, "Action role takes priority over a reused text ID");
        require(rules.textRole("com.example.chat", "custom_message", false, true) == Primary,
                "OEM-approved custom text can follow the card foreground");
        require(rules.textRole("com.example.chat", "custom_message", false, false) == null,
                "Custom text with its own background stays outside the generic scope");
        require(rules.groupTitle("com.android.systemui", "header_label"), "Section-title shadow scope");
        require(rules.groupTitle("com.android.systemui", "oplus_notification_expand_group_header_title"),
                "Expanded group-title shadow scope");
        require(!rules.groupTitle("android", "title"), "Do not remove arbitrary notification-content shadows");
        require(!rules.groupTitle("com.example.chat", "header_label"), "Do not touch app text with a colliding ID");
        require(rules.groupCollapseButton("com.android.systemui", "oplus_notification_expand_group_header_button"),
                "Identify the collapse button without relying on a previous native color callback");
        require(!rules.groupCollapseButton("com.example.chat", "oplus_notification_expand_group_header_button"),
                "Do not turn a custom app image into a collapse control");
        require(rules.headsUpHandle("com.android.systemui", "drag_view"), "Register the real notification handle independently of wrappers");
        require(!rules.headsUpHandle("com.android.systemui", "drag_bar_view"), "QS deformation handles are not notification handles");
        require(!rules.headsUpHandle("com.example.chat", "drag_view"), "Custom app drag views must not be repainted");
        System.out.println("Notification appearance scope checks passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
