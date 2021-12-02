package io.cloudtrust.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.theme.Theme;
import org.keycloak.theme.ThemeProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

public class ThemeUtils {
    private static final Logger LOG = Logger.getLogger(ThemeUtils.class);

    /**
     * Look for the theme of the current realm
     *
     * @param session The Keycloak session
     * @param type Theme type
     * @return A type (if any was found)
     * @throws IOException
     */
    public static Theme findTheme(KeycloakSession session, Theme.Type type) throws IOException {
        RealmModel realm = session.getContext().getRealm();
        switch (type) {
            case ACCOUNT:
                return findThemeByName(session, type, realm.getAccountTheme());
            case ADMIN:
                return findThemeByName(session, type, realm.getAdminTheme());
            case EMAIL:
                return findThemeByName(session, type, realm.getEmailTheme());
            case LOGIN:
                return findThemeByName(session, type, realm.getLoginTheme());
        }
        return null;
    }

    /**
     * Look for a specific theme
     *
     * @param session The Keycloak session
     * @param type Theme type
     * @param name The theme name
     * @return A type (if any was found)
     * @throws IOException
     */
    public static Theme findThemeByName(KeycloakSession session, Theme.Type type, String name) throws IOException {
        Set<ThemeProvider> providers = session.getAllProviders(ThemeProvider.class);
        // We iterate through all the theme providers
        for (ThemeProvider provider : providers) {
            // If we've found a provider for this login theme...
            if (provider.hasTheme(name, type)) {
                // We return the the theme.
                return provider.getTheme(name, type);
            }
        }
        return null;
    }

    /**
     * Returns a stream to a resource in this realm's theme
     *
     * @param path The path to the resource
     * @return A stream to a resource
     * @throws IOException
     */
    public static InputStream getStreamToResourceImage(KeycloakSession session, String path) throws IOException {
        // We grab the current theme
        Theme theme = findTheme(session, Theme.Type.LOGIN);
        while (theme != null) {
            // We try to get a stream to the image...
            InputStream stream = theme.getResourceAsStream(path);
            // If we could, we're done!
            if (stream != null) {
                return stream;
            } else {
                // If we couldn't, then we will look in the parent theme instead
                theme = findThemeByName(session, Theme.Type.LOGIN, theme.getParentName());
            }
        }
        return null;
    }

    /**
     * Load message stored in the theme
     */
    public static String loadMessageFromTheme(KeycloakSession session, UserModel user, String messageId) {
        Theme theme;
        try {
            theme = session.theme().getTheme(Theme.Type.LOGIN);
        } catch (IOException e) {
            LOG.error("Failed to create theme", e);
            return null;
        }

        Locale locale = user != null ? session.getContext().resolveLocale(user) : Locale.ENGLISH;
        Properties messagesBundle = handleThemeResources(theme, locale);
        return messagesBundle.getProperty(messageId);
    }

    /**
     * Load message bundle (inspired from FreeMarkerLoginFormsProvider).
     */
    public static Properties handleThemeResources(Theme theme, Locale locale) {
        Properties messagesBundle;
        try {
            messagesBundle = theme.getMessages(locale);
        } catch (IOException e) {
            LOG.warn("Failed to load messages", e);
            messagesBundle = new Properties();
        }

        return messagesBundle;
    }
}
