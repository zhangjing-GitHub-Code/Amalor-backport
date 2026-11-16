package me.acardia.amalor.xposed.systemui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class DataSimSignalRulesCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        DataSimSignalRules rules = DataSimSignalRules.INSTANCE;
        ArrayList<Integer> source = new ArrayList<>(List.of(7, 2));
        check(rules.select(source, true, 7).equals(List.of(7)), "select first SIM by subscription ID");
        check(rules.select(source, true, 2).equals(List.of(2)), "follow a data-SIM switch");
        check(rules.select(List.of(2, 7), true, 7).equals(List.of(7)), "slot order does not determine data SIM");
        check(rules.select(source, false, 2).equals(source), "disabled feature preserves both icons");
        check(rules.select(source, true, -1).equals(source), "invalid default data ID preserves native icons");
        check(rules.select(source, true, 9).equals(source), "absent default data ID preserves native icons");
        check(rules.select(List.of(7), true, 2).equals(List.of(7)), "single SIM is not hidden");
        check(rules.select(List.of(), true, 7).isEmpty(), "stacked mode never creates an independent holder");
        ArrayList<Integer> nativeInput = rules.select(source, true, 2);
        Collections.reverse(nativeInput);
        nativeInput.clear();
        check(source.equals(List.of(7, 2)), "native in-place mutation cannot change the source snapshot");
        check(rules.select(source, true, 7).equals(List.of(7)), "source still supports the next data-SIM switch");
        System.out.println("Independent data-SIM filtering checks passed");
    }
}
