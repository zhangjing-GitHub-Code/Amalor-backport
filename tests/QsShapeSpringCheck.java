package me.acardia.amalor.xposed.systemui;

class QsShapeSpringCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        long start = 1_000_000_000L;
        QsShapeSpring spring = new QsShapeSpring(0f);
        spring.retarget(1f, start, 1f);
        spring.sample(start + 80_000_000L);
        float position = spring.getValue();
        float velocity = spring.getVelocity();
        check(position > 0f && spring.isRunning() && velocity > 0f, "state change has real intermediate frames");
        spring.retarget(0f, start + 80_000_000L, 1f);
        check(spring.getValue() == position && spring.getVelocity() == velocity, "reversal inherits both position and velocity");
        spring.sample(start + 81_000_000L);
        check(Math.abs(spring.getValue() - position) < 0.1f, "first reversed frame does not jump to an endpoint");
        spring.sample(start + 10_000_000_000L);
        check(spring.getValue() == 0f && spring.getVelocity() == 0f && !spring.isRunning(), "spring finishes exactly at the latest target");

        QsShapeSpring normal = new QsShapeSpring(0f);
        QsShapeSpring slow = new QsShapeSpring(0f);
        normal.retarget(1f, start, 1f);
        slow.retarget(1f, start, 2f);
        normal.sample(start + 50_000_000L);
        slow.sample(start + 100_000_000L);
        check(Math.abs(normal.getValue() - slow.getValue()) < 0.0001f, "system duration scale changes the clock, not the shape");
        QsShapeSpring reference = new QsShapeSpring(0f);
        reference.retarget(1f, start, 1f);
        normal.retarget(1f, start + 60_000_000L, 1f);
        normal.sample(start + 100_000_000L);
        reference.sample(start + 100_000_000L);
        check(normal.getValue() == reference.getValue(), "repeated binding of the same state does not restart motion");
        slow.retarget(0f, start + 110_000_000L, 0f);
        check(slow.getValue() == 0f && !slow.isRunning(), "removed animations snap immediately");
        normal.snapTo(0f);
        check(normal.getValue() == 0f && normal.getVelocity() == 0f && !normal.isRunning(), "detach/reset leaves no running animation");
        System.out.println("QS Expressive spring reversal/duration-scale checks passed");
    }
}
