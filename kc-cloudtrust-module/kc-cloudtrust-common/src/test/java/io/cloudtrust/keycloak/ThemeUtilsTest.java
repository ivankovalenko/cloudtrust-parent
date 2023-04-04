package io.cloudtrust.keycloak;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.theme.Theme;
import org.keycloak.theme.Theme.Type;
import org.keycloak.theme.ThemeProvider;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class ThemeUtilsTest {
	@Mock
	KeycloakSession keycloakSession;

	@Mock
	KeycloakContext context;

	@Mock
	RealmModel realm;

	@Mock
	Theme accountTheme;

	@Mock
	Theme adminTheme;

	@Mock
	Theme emailTheme;

	@Mock
	Theme loginTheme;

	@Mock
	ThemeProvider accountAndAdminThemeProvider;

	@Mock
	ThemeProvider emailAndLoginThemeProvider;

	@BeforeEach
	public void setup() throws IOException {
		MockitoAnnotations.initMocks(this);

		Mockito.when(keycloakSession.getContext()).thenReturn(context);
		Mockito.when(context.getRealm()).thenReturn(realm);

		Mockito.when(realm.getAccountTheme()).thenReturn("accountTheme");
		Mockito.when(realm.getAdminTheme()).thenReturn("adminTheme");
		Mockito.when(realm.getEmailTheme()).thenReturn("emailTheme");
		Mockito.when(realm.getLoginTheme()).thenReturn("loginTheme");

		Mockito.when(accountAndAdminThemeProvider.hasTheme("accountTheme", Type.ACCOUNT)).thenReturn(true);
		Mockito.when(accountAndAdminThemeProvider.hasTheme("adminTheme", Type.ADMIN)).thenReturn(true);
		Mockito.when(emailAndLoginThemeProvider.hasTheme("emailTheme", Type.EMAIL)).thenReturn(true);
		Mockito.when(emailAndLoginThemeProvider.hasTheme("loginTheme", Type.LOGIN)).thenReturn(true);

		Mockito.when(accountAndAdminThemeProvider.getTheme("accountTheme", Type.ACCOUNT)).thenReturn(accountTheme);
		Mockito.when(accountAndAdminThemeProvider.getTheme("adminTheme", Type.ADMIN)).thenReturn(adminTheme);
		Mockito.when(emailAndLoginThemeProvider.getTheme("emailTheme", Type.EMAIL)).thenReturn(emailTheme);
		Mockito.when(emailAndLoginThemeProvider.getTheme("loginTheme", Type.LOGIN)).thenReturn(loginTheme);

		Mockito.when(accountTheme.getName()).thenReturn("account");
		Mockito.when(adminTheme.getName()).thenReturn("admin");
		Mockito.when(emailTheme.getName()).thenReturn("email");
		Mockito.when(loginTheme.getName()).thenReturn("login");
	}

	@ParameterizedTest
	@MethodSource("findThemeSamples")
	void findThemeTest(Type themeType, BiFunction<ThemeProvider, ThemeProvider, Set<ThemeProvider>> providersFunc, String expectedTheme) throws IOException {
		Set<ThemeProvider> providers = providersFunc.apply(accountAndAdminThemeProvider, emailAndLoginThemeProvider);
		Mockito.when(keycloakSession.getAllProviders(ThemeProvider.class)).thenReturn(providers);

		Theme theme = ThemeUtils.findTheme(keycloakSession, themeType);
		if (expectedTheme==null) {
			Assertions.assertNull(theme);
			return;
		}
		Assertions.assertEquals(expectedTheme, theme.getName());
	}

	public static Stream<Arguments> findThemeSamples() {
		BiFunction<ThemeProvider, ThemeProvider, Set<ThemeProvider>> emptyProviders = (p1, p2) -> Collections.emptySet();
		BiFunction<ThemeProvider, ThemeProvider, Set<ThemeProvider>> accountAdminProviders = (p1, p2) -> Collections.singleton(p1);
		BiFunction<ThemeProvider, ThemeProvider, Set<ThemeProvider>> emailLoginProviders = (p1, p2) -> Collections.singleton(p2);
		BiFunction<ThemeProvider, ThemeProvider, Set<ThemeProvider>> allProviders = (p1, p2) -> Stream.of(p1,p2).collect(Collectors.toSet()); //Set.<ThemeProvider>of(p1, p2);

		return Stream.of(
				Arguments.of(Type.ACCOUNT, emptyProviders, null),
				Arguments.of(Type.ACCOUNT, emailLoginProviders, null),
				Arguments.of(Type.ACCOUNT, allProviders, "account"),
				Arguments.of(Type.ADMIN, accountAdminProviders, "admin"),
				Arguments.of(Type.EMAIL, emailLoginProviders, "email"),
				Arguments.of(Type.LOGIN, emailLoginProviders, "login"),
				Arguments.of(Type.COMMON, allProviders, null)
		);
	}
}
