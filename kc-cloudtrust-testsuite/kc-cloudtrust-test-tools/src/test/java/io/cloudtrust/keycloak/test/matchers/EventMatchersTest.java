package io.cloudtrust.keycloak.test.matchers;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.keycloak.events.EventType;
import org.keycloak.representations.idm.EventRepresentation;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class EventMatchersTest {
    private void checkDescription(BaseMatcher<EventRepresentation> matcher, EventRepresentation event) {
        checkDescription(matcher, event, " when expected value is ");
    }

    private void checkDescription(BaseMatcher<EventRepresentation> matcher, EventRepresentation event, String expectedText) {
        Description desc = Mockito.mock(Description.class);
        matcher.describeTo(desc);
        if (event == null) {
            Mockito.verify(desc, Mockito.times(1)).appendText("Input is null");
        } else {
            Mockito.verify(desc, Mockito.times(1)).appendText(Mockito.contains(expectedText));
        }
    }

    @ParameterizedTest
    @MethodSource("isRealmSamples")
    void isRealmTest(EventRepresentation event, String realm, boolean matches) {
        BaseMatcher<EventRepresentation> matcher = EventMatchers.isRealm(realm);
        assertThat(matcher.matches(event), is(matches));

        if (!matches) {
            // Does not make sens to test describeTo when assertion matches
            checkDescription(matcher, event);
        }
    }

    private static Stream<Arguments> isRealmSamples() {
        EventRepresentation eventInTestRealm = eventBuilder().realm("test").build();
        EventRepresentation eventInDemoRealm = eventBuilder().realm("demo").build();

        return Stream.of(
                Arguments.of(eventInTestRealm, "test", true),
                Arguments.of(eventInDemoRealm, "test", false),
                Arguments.of(eventInTestRealm, "demo", false),
                Arguments.of(eventInDemoRealm, "demo", true),
                Arguments.of(null, "test", false)
        );
    }

    @ParameterizedTest
    @MethodSource("isKeycloakTypeSamples")
    void isKeycloakTypeTest(EventRepresentation event, EventType type, boolean matches) {
        BaseMatcher<EventRepresentation> matcher = EventMatchers.isKeycloakType(type);
        assertThat(matcher.matches(event), is(matches));

        if (!matches) {
            // Does not make sens to test describeTo when assertion matches
            checkDescription(matcher, event);
        }
    }

    private static Stream<Arguments> isKeycloakTypeSamples() {
        EventRepresentation eventLogin = eventBuilder().type(EventType.LOGIN).build();
        EventRepresentation eventLogout = eventBuilder().type(EventType.LOGOUT).build();

        return Stream.of(
                Arguments.of(eventLogin, EventType.LOGIN, true),
                Arguments.of(eventLogout, EventType.LOGIN, false),
                Arguments.of(eventLogin, EventType.LOGOUT, false),
                Arguments.of(eventLogout, EventType.LOGOUT, true),
                Arguments.of(null, EventType.LOGIN, false)
        );
    }

    @ParameterizedTest
    @MethodSource("isCloudtrustTypeSamples")
    void isCloudtrustTypeTest(EventRepresentation event, String ctType, boolean matches) {
        BaseMatcher<EventRepresentation> matcher = EventMatchers.isCloudtrustType(ctType);
        assertThat(matcher.matches(event), is(matches));

        if (!matches) {
            // Does not make sens to test describeTo when assertion matches
            checkDescription(matcher, event, "Cloudtrust event type is");
        }
    }

    private static Stream<Arguments> isCloudtrustTypeSamples() {
        EventRepresentation eventNone = eventBuilder().build();
        EventRepresentation eventOtp = eventBuilder().ctType("otp").build();
        EventRepresentation eventSms = eventBuilder().ctType("sms").build();
        EventRepresentation eventOtherDetails = eventBuilder().detail("anyKey", "anyValue").build();

        return Stream.of(
                Arguments.of(eventNone, "otp", false),
                Arguments.of(eventOtherDetails, "otp", false),
                Arguments.of(eventOtp, "otp", true),
                Arguments.of(eventSms, "otp", false),
                Arguments.of(eventOtp, "sms", false),
                Arguments.of(eventSms, "sms", true),
                Arguments.of(null, "otp", false)
        );
    }

    @ParameterizedTest
    @MethodSource("hasDetailSamples")
    void hasDetailTest(EventRepresentation event, String expectedValue, boolean matches, String expectedMessagePart) {
        BaseMatcher<EventRepresentation> matcher = EventMatchers.hasDetail("anyKey", expectedValue);
        assertThat(matcher.matches(event), is(matches));

        if (!matches) {
            // Does not make sens to test describeTo when assertion matches
            checkDescription(matcher, event, expectedMessagePart);
        }
    }

    private static Stream<Arguments> hasDetailSamples() {
        EventRepresentation eventNoDetails = eventBuilder().build();
        EventRepresentation eventOtherDetail = eventBuilder().detail("anotherKey", "value").build();
        EventRepresentation eventDetailValue1 = eventBuilder().detail("anyKey", "value1").build();
        EventRepresentation eventDetailValue2 = eventBuilder().detail("anyKey", "value2").build();

        return Stream.of(
                Arguments.of(null, "value1", false, null),
                Arguments.of(eventNoDetails, "value1", false, " is missing in event details"),
                Arguments.of(eventOtherDetail, "value", false, " is missing in event details"),
                Arguments.of(eventDetailValue1, "value1", true, null),
                Arguments.of(eventDetailValue1, "value2", false, " when expected value is "),
                Arguments.of(eventDetailValue2, "value2", true, null)
        );
    }

    @ParameterizedTest
    @MethodSource("hasNonEmptyDetailSamples")
    void hasNonEmptyDetailTest(EventRepresentation event, boolean matches, String expectedMessagePart) {
        BaseMatcher<EventRepresentation> matcher = EventMatchers.hasNonEmptyDetail("anyKey");
        assertThat(matcher.matches(event), is(matches));

        if (!matches) {
            // Does not make sens to test describeTo when assertion matches
            checkDescription(matcher, event, expectedMessagePart);
        }
    }

    private static Stream<Arguments> hasNonEmptyDetailSamples() {
        EventRepresentation eventNoDetails = eventBuilder().build();
        EventRepresentation eventOtherDetail = eventBuilder().detail("anotherKey", "value").build();
        EventRepresentation eventHasDetail = eventBuilder().detail("anyKey", "value").build();
        EventRepresentation eventHasNullDetail = eventBuilder().detail("anyKey", null).build();
        EventRepresentation eventHasEmptyDetail = eventBuilder().detail("anyKey", "   ").build();

        return Stream.of(
                Arguments.of(null, false, null),
                Arguments.of(eventNoDetails, false, " is missing in event details"),
                Arguments.of(eventOtherDetail, false, " is missing in event details"),
                Arguments.of(eventHasDetail, true, null),
                Arguments.of(eventHasNullDetail, false, " is missing, null or empty"),
                Arguments.of(eventHasEmptyDetail, false, " is missing, null or empty")
        );
    }

    @ParameterizedTest
    @MethodSource("hasUserIdSamples")
    void hasUserIdTest(EventRepresentation event, String expectedUser, boolean matches) {
        BaseMatcher<EventRepresentation> matcher = expectedUser == null ? EventMatchers.hasUserId() : EventMatchers.hasUserId(expectedUser);
        assertThat(matcher.matches(event), is(matches));

        if (!matches) {
            // Does not make sens to test describeTo when assertion matches
            checkDescription(matcher, event, "Event type ");
        }
    }

    private static Stream<Arguments> hasUserIdSamples() {
        EventRepresentation eventNoUser = eventBuilder().build();
        EventRepresentation eventUser = eventBuilder().user("123").build();

        return Stream.of(
                Arguments.of(null, null, false),
                Arguments.of(eventNoUser, null, false),
                Arguments.of(eventUser, null, true),
                Arguments.of(eventUser, "123", true),
                Arguments.of(eventUser, "456", false)
        );
    }

    @ParameterizedTest
    @MethodSource("hasNoErrorSamples")
    void hasNoErrorTest(EventRepresentation event, boolean matches) {
        BaseMatcher<EventRepresentation> matcher = EventMatchers.hasNoError();
        assertThat(matcher.matches(event), is(matches));

        if (!matches) {
            // Does not make sens to test describeTo when assertion matches
            checkDescription(matcher, event, "Event type reports ");
        }
    }

    private static Stream<Arguments> hasNoErrorSamples() {
        EventRepresentation eventNoError = eventBuilder().build();
        EventRepresentation eventError = eventBuilder().error("error value").build();

        return Stream.of(
                Arguments.of(null, false),
                Arguments.of(eventNoError, true),
                Arguments.of(eventError, false)
        );
    }

    @ParameterizedTest
    @MethodSource("hasErrorSamples")
    void hasErrorTest(EventRepresentation event, String expectedError, boolean matches) {
        BaseMatcher<EventRepresentation> matcher = expectedError == null ? EventMatchers.hasError() : EventMatchers.hasError(expectedError);
        assertThat(matcher.matches(event), is(matches));

        if (!matches) {
            // Does not make sens to test describeTo when assertion matches
            checkDescription(matcher, event, "Event type ");
        }
    }

    private static Stream<Arguments> hasErrorSamples() {
        EventRepresentation eventNoError = eventBuilder().build();
        EventRepresentation eventError = eventBuilder().error("error value").build();

        return Stream.of(
                Arguments.of(null, null, false),
                Arguments.of(eventNoError, null, false),
                Arguments.of(eventError, null, true),
                Arguments.of(eventError, "error value", true),
                Arguments.of(eventError, "another value", false)
        );
    }

    @ParameterizedTest
    @MethodSource("doesNotHaveErrorSamples")
    void doesNotHaveErrorTest(EventRepresentation event, String unexpectedError, boolean matches) {
        BaseMatcher<EventRepresentation> matcher = EventMatchers.doesNotHaveError(unexpectedError);
        assertThat(matcher.matches(event), is(matches));

        if (!matches) {
            // Does not make sens to test describeTo when assertion matches
            checkDescription(matcher, event, unexpectedError);
        }
    }

    private static Stream<Arguments> doesNotHaveErrorSamples() {
        EventRepresentation eventNoError = eventBuilder().build();
        EventRepresentation eventUndesiredError = eventBuilder().error("undesired").build();

        return Stream.of(
                Arguments.of(null, null, false),
                Arguments.of(eventNoError, "undesired", true),
                Arguments.of(eventUndesiredError, "undesired", false)
        );
    }

    @ParameterizedTest
    @MethodSource("existsSamples")
    void existsTest(EventRepresentation event, boolean exists) {
        BaseMatcher<EventRepresentation> matcher = EventMatchers.exists();
        assertThat(matcher.matches(event), is(exists));

        if (!exists) {
            // Does not make sens to test describeTo when assertion matches
            checkDescription(matcher, event, "xxx");
        }
    }

    @ParameterizedTest
    @MethodSource("existsSamples")
    void doesNotExistTest(EventRepresentation event, boolean exists) {
        BaseMatcher<EventRepresentation> matcher = EventMatchers.doesNotExist();
        assertThat(matcher.matches(event), is(!exists));

        if (exists) {
            // Does not make sens to test describeTo when assertion matches
            checkDescription(matcher, event, "No event was expected but found ");
        }
    }

    private static Stream<Arguments> existsSamples() {
        EventRepresentation anyEvent = eventBuilder().build();

        return Stream.of(
                Arguments.of(null, false),
                Arguments.of(anyEvent, true)
        );
    }

    private static EventRepresentationBuilder eventBuilder() {
        return new EventRepresentationBuilder();
    }

    private static class EventRepresentationBuilder {
        private final EventRepresentation event = new EventRepresentation();

        public EventRepresentation build() {
            return event;
        }

        public EventRepresentationBuilder realm(String realm) {
            event.setRealmId(realm);
            return this;
        }

        public EventRepresentationBuilder type(EventType type) {
            event.setType(type.name());
            return this;
        }

        public EventRepresentationBuilder ctType(String ctType) {
            return detail(EventMatchers.CT_EVENT_TYPE, ctType);
        }

        public EventRepresentationBuilder detail(String name, String value) {
            if (event.getDetails() == null) {
                event.setDetails(new HashMap<>());
            }
            event.getDetails().put(name, value);
            return this;
        }

        public EventRepresentationBuilder user(String userId) {
            event.setUserId(userId);
            return this;
        }

        public EventRepresentationBuilder error(String error) {
            event.setError(error);
            return this;
        }
    }
}
