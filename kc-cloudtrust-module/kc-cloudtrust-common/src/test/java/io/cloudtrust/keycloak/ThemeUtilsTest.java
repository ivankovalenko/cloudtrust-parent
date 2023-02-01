package io.cloudtrust.keycloak;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

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
		MockitoAnnotations.openMocks(this);

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
	void findThemeTest(Type themeType, int expectedThemes, String expectedTheme) throws IOException {
		Set<ThemeProvider> providers = new HashSet<>();
		if ((expectedThemes&ACCOUNT_AND_ADMIN_THEME_PROVIDER)!=0) {
			providers.add(accountAndAdminThemeProvider);
		}
		if ((expectedThemes&EMAIL_AND_LOGIN_THEME_PROVIDER)!=0) {
			providers.add(emailAndLoginThemeProvider);
		}
		Mockito.when(keycloakSession.getAllProviders(ThemeProvider.class)).thenReturn(providers);

		Theme theme = ThemeUtils.findTheme(keycloakSession, themeType);
		if (expectedTheme==null) {
			Assertions.assertNull(theme);
			return;
		}
		Assertions.assertEquals(expectedTheme, theme.getName());
	}

	private static final int NO_THEME_PROVIDER = 0;
	private static final int ACCOUNT_AND_ADMIN_THEME_PROVIDER = 2;
	private static final int EMAIL_AND_LOGIN_THEME_PROVIDER = 1;

	public static Stream<Arguments> findThemeSamples() {
		return Stream.of(
				Arguments.of(Type.ACCOUNT, NO_THEME_PROVIDER, null),
				Arguments.of(Type.ACCOUNT, EMAIL_AND_LOGIN_THEME_PROVIDER, null),
				Arguments.of(Type.ACCOUNT, ACCOUNT_AND_ADMIN_THEME_PROVIDER | EMAIL_AND_LOGIN_THEME_PROVIDER, "account"),
				Arguments.of(Type.ADMIN, ACCOUNT_AND_ADMIN_THEME_PROVIDER, "admin"),
				Arguments.of(Type.EMAIL, EMAIL_AND_LOGIN_THEME_PROVIDER, "email"),
				Arguments.of(Type.LOGIN, EMAIL_AND_LOGIN_THEME_PROVIDER, "login"),
				Arguments.of(Type.COMMON, ACCOUNT_AND_ADMIN_THEME_PROVIDER | EMAIL_AND_LOGIN_THEME_PROVIDER, null)
		);
	}
}
