package io.cloudtrust.keycloak.test.matchers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.Duration;
import java.util.function.BooleanSupplier;

import org.hamcrest.Description;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ConditionMatcherTest {
    class ChangeStatusAfterTimeout implements BooleanSupplier {
        private boolean initialStatus;
        private long timestamp;

        public ChangeStatusAfterTimeout(boolean initialStatus, long timeout) {
            this.initialStatus = initialStatus;
            this.timestamp = System.currentTimeMillis() + timeout;
        }

        @Override
        public boolean getAsBoolean() {
            return System.currentTimeMillis()>=timestamp ? !initialStatus : initialStatus;
        }
    }

    @Test
    void invalidTest() {
        assertThat(ConditionMatcher.isTrue().matches(null), is(false));
        assertThat(ConditionMatcher.isTrue().matches("not-the-expected-type"), is(false));
    }

    @Test
    void isTrueTest() {
        assertThat(ConditionMatcher.isTrue().matches(new ChangeStatusAfterTimeout(true, 100L)), is(true));
        assertThat(ConditionMatcher.isTrue().matches(new ChangeStatusAfterTimeout(false, 100L)), is(false));

        assertThat(ConditionMatcher.isTrue(Duration.ofMillis(50)).matches(new ChangeStatusAfterTimeout(true, 100L)), is(true));
        assertThat(ConditionMatcher.isTrue(Duration.ofMillis(50)).matches(new ChangeStatusAfterTimeout(false, 100L)), is(false));
        assertThat(ConditionMatcher.isTrue(Duration.ofMillis(150)).matches(new ChangeStatusAfterTimeout(false, 100L)), is(true));

        assertThat(ConditionMatcher.isTrue(Duration.ofMillis(50), 10).matches(new ChangeStatusAfterTimeout(true, 100L)), is(true));
        assertThat(ConditionMatcher.isTrue(Duration.ofMillis(50), 10).matches(new ChangeStatusAfterTimeout(false, 100L)), is(false));
        assertThat(ConditionMatcher.isTrue(Duration.ofMillis(150), 10).matches(new ChangeStatusAfterTimeout(false, 100L)), is(true));
    }

    @Test
    void isFalseTest() {
        BooleanSupplier alwaysTrue = () -> true;
        BooleanSupplier alwaysFalse = () -> false;
        assertThat(ConditionMatcher.isFalse().matches(alwaysTrue), is(false));
        assertThat(ConditionMatcher.isFalse().matches(alwaysFalse), is(true));

        assertThat(ConditionMatcher.isFalse(Duration.ofMillis(50)).matches(new ChangeStatusAfterTimeout(false, 100L)), is(true));
        assertThat(ConditionMatcher.isFalse(Duration.ofMillis(50)).matches(new ChangeStatusAfterTimeout(true, 100L)), is(false));
        assertThat(ConditionMatcher.isFalse(Duration.ofMillis(150)).matches(new ChangeStatusAfterTimeout(true, 100L)), is(true));

        assertThat(ConditionMatcher.isFalse(Duration.ofMillis(50), 10).matches(new ChangeStatusAfterTimeout(false, 100L)), is(true));
        assertThat(ConditionMatcher.isFalse(Duration.ofMillis(50), 10).matches(new ChangeStatusAfterTimeout(true, 100L)), is(false));
        assertThat(ConditionMatcher.isFalse(Duration.ofMillis(150), 10).matches(new ChangeStatusAfterTimeout(true, 100L)), is(true));
    }

    @Test
    void describeToTest() {
        Description desc = Mockito.mock(Description.class);
        ConditionMatcher.isFalse().describeTo(desc);
        Mockito.verify(desc, Mockito.times(1)).appendText(Mockito.contains("be false"));

        desc = Mockito.mock(Description.class);
        ConditionMatcher.isTrue().describeTo(desc);
        Mockito.verify(desc, Mockito.times(1)).appendText(Mockito.contains("be true"));

        desc = Mockito.mock(Description.class);
        ConditionMatcher.isFalse(Duration.ofMinutes(1)).describeTo(desc);
        Mockito.verify(desc, Mockito.times(1)).appendText(Mockito.contains("less than 60000ms"));
    }
}
