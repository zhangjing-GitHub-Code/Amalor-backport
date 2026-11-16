package me.acardia.amalor.xposed.systemui;

/** Standalone check of panel recovery, role alpha and QS detail text classification. */
class QsAppearanceRulesCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        QsAppearanceRules rules = QsAppearanceRules.INSTANCE;
        for (String type : new String[]{"com.oplus.systemui.qs.base.tile.OplusQSTileBaseView",
                "com.oplus.systemui.qs.base.tile.OplusQSHighlightTileView",
                "com.oplus.systemui.qs.slider.detail.widget.QsDetailTileBaseView",
                "com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSResizeableTileView",
                "com.oplus.deviceplugin.sdk.ui.view.separatecardview.SquareEntranceCardView"}) {
            check(rules.tileRootClass(type), "Tile class family retained: " + type);
        }
        for (String type : new String[]{"android.widget.FrameLayout", "android.widget.ImageView",
                "com.oplus.systemui.plugins.qs.customize.viewmodel.TileViewModel",
                "com.oplus.deviceplugin.sdk.ui.view.battery.CompositeBatteryView"}) {
            check(!rules.tileRootClass(type), "Non-tile classes require a live tile ancestor: " + type);
        }
        check(rules.panelVisible(true, true, 1f, true, 0f), "ordinary expanded QS remains visible");
        check(!rules.panelVisible(true, false, 1f, true, 0f), "hidden source window is not an active QS page");
        check(!rules.panelVisible(false, true, 1f, true, 0f), "detached QS page is inactive");
        check(!rules.panelVisible(true, true, 0f, true, 0f), "collapsed QS page is inactive");
        check(!rules.panelVisible(true, true, 1f, false, 0f), "notification-only shade is outside the QS page");
        check(rules.panelVisible(true, true, 0f, false, 1f), "separate QS stays active while ordinary QS is collapsed");
        check(rules.panelVisible(true, true, 0f, false, 0.1f), "separate QS enables blur during expansion, not only at the end");
        check(!rules.panelVisible(true, false, 0f, false, 1f), "hidden separate QS cannot keep its backdrop");
        check(!rules.panelVisible(false, true, 0f, false, 1f), "detached separate QS cannot keep its backdrop");
        check(!rules.panelVisible(true, true, 0f, false, 0f), "separate QS collapse clears backdrop ownership");
        check(rules.shadeBlurAllowed(true, 0, true, false, false), "unlocked shade retains native blur eligibility");
        for (int state : new int[]{1, 2}) {
            check(rules.shadeBlurAllowed(false, state, true, false, false), "visible locked QS uses the same backdrop");
            check(!rules.shadeBlurAllowed(false, state, false, false, false), "lockscreen without QS is not taken over");
            check(!rules.shadeBlurAllowed(false, state, true, true, false), "unlock fade relinquishes the QS backdrop");
            check(!rules.shadeBlurAllowed(false, state, true, false, true), "dozing does not retain stale QS blur");
        }
        check(!rules.shadeBlurAllowed(false, 0, true, false, false), "other native shade restrictions remain effective");
        check(!rules.shadeBlurAllowed(false, 3, true, false, false), "unknown status is not treated as a locked QS panel");
        check(rules.qsActivityCovered(true, false, 0f), "completed QS Activity launch clears its hidden source, including long press");
        check(!rules.qsActivityCovered(false, false, 0f), "end before start does not acquire cleanup");
        check(!rules.qsActivityCovered(true, true, 0f), "cancelled transition does not hide the source window");
        check(!rules.qsActivityCovered(true, false, 0.5f), "uncovered source does not acquire cleanup");
        check(rules.coveredSourceRestored(true, true, 1f, true), "reopened source releases coverage even without a focus callback");
        check(!rules.coveredSourceRestored(true, true, 0f, true), "hidden Activity launch source stays covered");
        check(!rules.coveredSourceRestored(true, true, 1f, false), "stale source state alone cannot release coverage");
        check(!rules.coveredSourceRestored(false, true, 1f, true), "detached old source is not a restored source");
        check(!rules.coveredSourceRestored(true, false, 1f, true), "invisible source cannot restore the background");
        check(rules.qsReopened(0f, 0.1f), "a new QS opening retires cleanup even while the old detail container stays hidden");
        check(!rules.qsReopened(1f, 1f), "stale expanded QS updates cannot retire Activity source cleanup");
        check(!rules.qsReopened(0.1f, 1f), "continuing the old expansion is not a new opening");
        check(!rules.qsReopened(1f, 0f), "QS collapse does not re-enable a covered source");
        for (String id : new String[]{"summary", "text2", "detail_items_summary", "detail_items_summary2",
                "detail_hint_textview", "qs_hint_textview", "empty", "frequency_tag", "wifi_subtitle", "device_description",
                "oplus_media_panel_container_text", "oplus_media_panel_container_default_tip",
                "oplus_media_panel_container_time", "oplus_media_panel_container_all_time", "oplus_media_panel_output_switch_text"}) {
            check(rules.secondaryDetailText(id), id + " uses onSurfaceVariant");
        }
        for (String id : new String[]{"title", "text1", "detail_items_title", "qs_detail_view_title", "settings_button",
                "oplus_media_panel_container_title", ""}) {
            check(!rules.secondaryDetailText(id), id + " uses onSurface");
        }
        check(rules.backgroundAlpha(0x26d6eab4, 255) == 38, "dark SE2 preserves its native 15 percent alpha");
        check(rules.backgroundAlpha(0x8a12140f, 255) == 138, "dark SE1 preserves its native alpha");
        check(rules.backgroundAlpha(0xffbacd9a, 255) == 255, "active primary stays opaque");
        check(rules.backgroundAlpha(0x26d6eab4, 128) == 19, "transition alpha multiplies the role alpha once");
        check(rules.backgroundAlpha(0x26d6eab4, 0) == 0, "hidden drawable has no tint");
        check(rules.backgroundAlpha(0x00d6eab4, 255) == 0, "transparent role is not promoted to opaque");
        for (String id : new String[]{"no_entrance_title", "rectangle_entrance_title", "rectangle_panel_title",
                "no_device_space_icon", "rectangle_entrance_icon", "square_entrance_icon"}) {
            check(Boolean.FALSE.equals(rules.secondaryDeviceSpaceForeground(id)), id + " uses onSurface on the device card");
        }
        for (String id : new String[]{"no_entrance_title_dec", "rectangle_entrance_title_dec",
                "rectangle_main_status", "rectangle_status_image"}) {
            check(Boolean.TRUE.equals(rules.secondaryDeviceSpaceForeground(id)), id + " uses onSurfaceVariant");
        }
        for (String id : new String[]{"no_device_first_icon", "no_device_second_icon", "no_device_third_icon",
                "rectangle_battery_circular", "square_battery_circular", "battery_percentage", "device_photo", "icon", ""}) {
            check(rules.secondaryDeviceSpaceForeground(id) == null, id + " is not a generic chrome tint target");
        }
        check(rules.deviceSpaceUnavailable(2) && rules.deviceSpaceUnavailable(3), "SDK disconnected/unusable glyphs retain a disabled role");
        check(!rules.deviceSpaceUnavailable(null) && !rules.deviceSpaceUnavailable(0) && !rules.deviceSpaceUnavailable(1),
                "Available device glyphs use onSurface in both themes");
        check(!rules.mediaTransitionActive(false, 1f), "Nested-output dismissal cannot suppress the source using a saved fraction");
        check(!rules.mediaTransitionActive(true, null) && !rules.mediaTransitionActive(true, Float.NaN),
                "Ended/invalid media state does not own the source background");
        check(rules.mediaTransitionActive(true, 0f) && rules.mediaTransitionActive(true, 0.5f) && rules.mediaTransitionActive(true, 1f),
                "Only a live media transition owns source-background suppression, including its endpoints");
        check(rules.mediaDetailFraction(0f, true, 0f) == 0f && rules.mediaDetailFraction(0f, true, 1f) == 1f,
                "opening starts with the card role and ends with the detail role");
        for (float start : new float[]{0f, 0.4f, 1f}) {
            check(rules.mediaDetailFraction(start, false, 0f) == start, "Closing begins at the current material, without an alpha jump");
            check(Math.abs(rules.mediaDetailFraction(start, false, 0.5f) - start * 0.5f) < 0.0001f,
                    "Closing continuously approaches the small-card material");
            check(rules.mediaDetailFraction(start, false, 1f) == 0f, "Closing ends at the original tile material");
            check(rules.mediaDetailFraction(start, false, Float.NaN) == start, "Invalid closing progress keeps the current material");
            check(rules.mediaDetailFraction(start, false, -1f) == start, "Closing undershoot retains its starting material");
            check(rules.mediaDetailFraction(start, false, 2f) == 0f, "Closing overshoot cannot darken or brighten the final material");
        }
        check(rules.mediaDetailFraction(0f, true, 0.5f) == 0.5f, "opening still blends into the detail surface");
        check(Math.abs(rules.mediaDetailFraction(0.4f, true, 0.5f) - 0.7f) < 0.0001f, "interrupted reopening keeps a continuous start");
        check(rules.mediaDetailFraction(0.4f, true, Float.NaN) == 0.4f, "invalid progress keeps the current color");
        check(rules.activeTile(2, false), "active proxy wins over stale inactive drawable flags");
        check(!rules.activeTile(1, true), "inactive proxy wins over stale active drawable flags");
        check(!rules.activeTile(0, true), "unavailable proxy is not active");
        check(rules.activeTile(null, true), "unproxied drawables retain their state flags");
        check(!rules.activeTile(rules.resolvedTileState(1, 0), true), "available action tile uses model state instead of a stale proxy or active flag");
        check(!rules.unavailableTile(rules.resolvedTileState(1, 0), true, false), "idle recorder is not dimmed by a stale unavailable proxy");
        check(rules.activeTile(rules.resolvedTileState(2, 1), false), "recording model overrides an old inactive proxy");
        check(rules.unavailableTile(rules.resolvedTileState(0, 2), false, false), "a disabled recorder cannot be promoted by an active proxy");
        check(rules.resolvedTileState(null, 1) == 1, "proxy remains a fallback when no model is available");
        check(rules.resolvedTileState(null, null) == null, "missing sources retain drawable state fallback");
        check(!rules.largeTile(1, 1), "ordinary tile retains its single target role");
        check(rules.largeTile(2, 1) && rules.largeTile(2, 2), "both large tile sizes use the card/icon surface split");
        check(rules.largeTile(1, 2), "tall large tile uses the same split");
        check(Boolean.FALSE.equals(rules.primaryQsButton("settings_button")) && Boolean.FALSE.equals(rules.primaryQsButton("more_button")),
                "settings and more buttons use inactive surface roles");
        check(Boolean.TRUE.equals(rules.primaryQsButton("pm_lite")), "power button uses primary/onPrimary");
        check(rules.primaryQsButton("multi_user_avatar") == null && rules.primaryQsButton("title") == null,
                "avatars and unrelated labels are not button background targets");
        for (String id : new String[]{"qs_edit_button", "quicksettings_settings_button_icon", "add_tile_view", "save_view_container", "qs_settings_btn"}) {
            check(Boolean.FALSE.equals(rules.primaryQsButton(id)), id + " uses paired tile background and foreground roles");
        }
        check(rules.primaryQsButton("quicksettings_settings_button") == null, "style the settings icon's actual background, not its outer layout");
        check(Boolean.FALSE.equals(rules.primaryQsButton("clear_all")), "clear-all uses the same background and foreground roles");
        check(rules.roundNotificationControl("com.oplus.systemui.qs.OplusQsFooterSettingsSpotLightImageView"), "actual notification shortcut uses a direct role fill");
        check(rules.roundNotificationControl("com.oplus.systemui.notification.clearall.OplusClearAllButton"), "clear-all capsule uses a direct role fill");
        check(!rules.roundNotificationControl("android.widget.ImageView") && !rules.roundNotificationControl("com.oplus.systemui.qs.widget.OplusQSCarrierText"),
                "notification background override excludes generic icons and carrier text");
        for (String id : new String[]{"clock", "qs_footer_clock", "oplus_qs_clock", "oplus_date", "oplus_qs_date"}) {
            check(rules.primaryHeaderText(id), id + " uses primary");
        }
        for (String id : new String[]{"qs_footer_carrier", "carrier_text", "mobile_roaming_updated", "last_updated", "keyguard_date"}) {
            check(!rules.primaryHeaderText(id), id + " is outside header text coloring");
        }
        check(rules.clockDateColoring(true, true), "clock/date coloring requires Monet and its own switch");
        check(!rules.clockDateColoring(true, false), "Monet alone does not force clock/date coloring");
        check(!rules.clockDateColoring(false, true) && !rules.clockDateColoring(false, false),
                "blur mode never applies clock/date role colors, including a saved enabled preference");
        Object original = new Object(), applied = new Object(), nativeUpdate = new Object(), textAppearance = new Object();
        var headerColor = new QsHeaderTextColorState<>(original);
        headerColor.markApplied(applied);
        headerColor.observe(applied);
        check(headerColor.getNativeColor() == original, "our applied role must not become the native restore color");
        headerColor.recordNative(nativeUpdate);
        headerColor.markApplied(applied);
        headerColor.observe(applied);
        check(headerColor.getNativeColor() == nativeUpdate, "native theme changes while enabled are retained for disabling");
        headerColor.observe(textAppearance);
        check(headerColor.getNativeColor() == textAppearance, "TextAppearance changes bypassing setters are retained");
        headerColor.markApplied(applied);
        headerColor.recordNative(applied);
        headerColor.observe(applied);
        check(headerColor.getNativeColor() == applied, "a native write matching the override is still a new native source");
        headerColor.markApplied(null);
        headerColor.observe(original);
        check(headerColor.getNativeColor() == original, "disabling releases ownership and keeps subsequent native colors");
        for (boolean dark : new boolean[]{false, true}) {
            var flags = rules.inlineThemeFlags(dark);
            check(flags.get("key_is_dark_theme_color") == dark && flags.get("isDarkThemeColor") == dark,
                    "both remote theme protocols match the detail background, including light mode");
            check(!flags.get("key_is_third_theme") && !flags.get("notification_scrim_bg_mask_color") && !flags.get("isSupportLight"),
                    "pure-color detail does not request wallpaper contrast or spotlight styling");
            check(flags.size() == 5, "inline override contains only verified color/theme flags");
        }
        check(rules.unavailableTile(0, false, false), "unavailable proxy state");
        check(rules.unavailableTile(2, false, true), "policy-disabled active tile");
        check(rules.unavailableTile(101, false, false), "native inoperable state remains weakly colored");
        check(!rules.unavailableTile(2, true, false), "available proxy wins over stale unavailable flags");
        check(rules.unavailableTile(null, true, false), "unproxied unavailable state flags");
        System.out.println("QS panel/color/detail-text checks passed");
    }
}
