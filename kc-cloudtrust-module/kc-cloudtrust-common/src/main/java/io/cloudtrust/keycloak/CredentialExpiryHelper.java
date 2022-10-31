package io.cloudtrust.keycloak;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.concurrent.TimeUnit;

public class CredentialExpiryHelper {
    public static final String MAXIMUM_USAGE_DURATION_PROPERTY = "max.usage.duration";

    private CredentialExpiryHelper() {
    }

    public static ProviderConfigProperty createMaximumUsageDurationProperty() {
        return createMaximumUsageDurationProperty(MAXIMUM_USAGE_DURATION_PROPERTY);
    }

    public static ProviderConfigProperty createMaximumUsageDurationProperty(String propertyName) {
        ProviderConfigProperty prop = new ProviderConfigProperty();
        prop.setName(propertyName);
        prop.setLabel("Maximum usage duration (days)");
        prop.setHelpText("Maximum number of days an OTP authenticator can be used before expiring");
        prop.setType(ProviderConfigProperty.STRING_TYPE);
        return prop;
    }

    public static boolean isCredentialExpired(AuthenticatorConfigModel config, CredentialModel credential) {
        return isCredentialExpired(config, credential, MAXIMUM_USAGE_DURATION_PROPERTY);
    }

    public static boolean isCredentialExpired(AuthenticatorConfigModel config, CredentialModel credential, String propertyName) {
        if (config == null || config.getConfig() == null) {
            // No configuration
            return false;
        }
        String strMaxUsageDuration = config.getConfig().get(propertyName);
        if (StringUtils.isBlank(strMaxUsageDuration)) {
            return false;
        }
        long maxUsageDuration = NumberUtils.toLong(strMaxUsageDuration);
        long credentialAge = credential.getCreatedDate() != null ? System.currentTimeMillis() - credential.getCreatedDate() : 0;
        return credentialAge > TimeUnit.DAYS.toMillis(maxUsageDuration);
    }
}
