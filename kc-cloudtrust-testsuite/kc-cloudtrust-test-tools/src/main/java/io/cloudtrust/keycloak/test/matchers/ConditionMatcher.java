package io.cloudtrust.keycloak.test.matchers;

import java.time.Duration;
import java.util.function.BooleanSupplier;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

import io.cloudtrust.exception.CloudtrustRuntimeException;

public class ConditionMatcher extends BaseMatcher<BooleanSupplier> {
    private boolean expected;
    private Duration maxDuration;
    private long interval;

    protected ConditionMatcher(boolean expected) {
        this(expected, Duration.ZERO);
    }

    protected ConditionMatcher(boolean expected, Duration maxDuration) {
        this(expected, maxDuration, 100L);
    }

    protected ConditionMatcher(boolean expected, Duration maxDuration, long interval) {
        this.expected = expected;
        this.maxDuration = maxDuration;
        this.interval = interval;
    }

    @Override
    public boolean matches(Object item) {
        if (!(item instanceof BooleanSupplier)) {
            return false;
        }
        BooleanSupplier boolSupplier = (BooleanSupplier)item;
        long maxTimestamp = System.currentTimeMillis() + maxDuration.toMillis();
        for(;;) {
            if (boolSupplier.getAsBoolean()==this.expected) {
                return true;
            }
            long sleep = Math.min(maxTimestamp - System.currentTimeMillis(), interval);
            if (sleep<0) {
                return false;
            }
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                throw new CloudtrustRuntimeException(e);
            }
        }
    }

    @Override
    public void describeTo(Description description) {
        StringBuilder builder = new StringBuilder().append("Input is expected to be ").append(this.expected);
        if (this.maxDuration.toMillis()>0) {
            builder.append(" in less than ").append(this.maxDuration.toMillis()).append("ms");
        }
        description.appendText(builder.toString());
    }

    public static ConditionMatcher isTrue() {
        return new ConditionMatcher(true);
    }

    public static ConditionMatcher isTrue(Duration timeout) {
        return new ConditionMatcher(true, timeout);
    }

    public static ConditionMatcher isTrue(Duration timeout, long intervalMillis) {
        return new ConditionMatcher(true, timeout, intervalMillis);
    }

    public static ConditionMatcher isFalse() {
        return new ConditionMatcher(false);
    }

    public static ConditionMatcher isFalse(Duration timeout) {
        return new ConditionMatcher(false, timeout);
    }

    public static ConditionMatcher isFalse(Duration timeout, long intervalMillis) {
        return new ConditionMatcher(false, timeout, intervalMillis);
    }
}
