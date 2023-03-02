package io.cloudtrust.keycloak.models;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.keycloak.credential.CredentialModel;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public class CtAuthenticatorBean<T> {
    private final String selectedCredentialId;
    private final List<T> userCredentials;

    public CtAuthenticatorBean(String selectedCredentialId) {
        this.selectedCredentialId = selectedCredentialId;
        this.userCredentials = new ArrayList<>();
    }

    public CtAuthenticatorBean(KeycloakSession session, UserModel user, String selectedCredentialId, String credentialModelType,
            String credProviderFactoryProviderId, Function<CredentialModel, T> converter) {
        RealmModel realm = session.getContext().getRealm();
        this.userCredentials = session.userCredentialManager().getStoredCredentialsByTypeStream(realm, user, credentialModelType)
                .map(converter)
                .collect(Collectors.toList());

        // This means user did not yet manually selected any OTP credential through the UI. So just go with the default one with biggest priority
        if (selectedCredentialId == null || selectedCredentialId.isEmpty()) {
            CredentialProvider<?> smsCredentialProvider = session.getProvider(CredentialProvider.class, credProviderFactoryProviderId);
            CredentialModel smsCredential = smsCredentialProvider.getDefaultCredential(session, realm, user);

            selectedCredentialId = smsCredential==null ? null : smsCredential.getId();
        }

        this.selectedCredentialId = selectedCredentialId;
    }

    public List<T> getUserCredentials() {
        return userCredentials;
    }

    public String getSelectedCredentialId() {
        return selectedCredentialId;
    }

    public static class DefaultCtCredential {
        private final String id;
        private final String userLabel;

        public DefaultCtCredential(CredentialModel credentialModel) {
            this(credentialModel.getId(), credentialModel.getUserLabel() == null || credentialModel.getUserLabel().isEmpty() ? "sans.nom" : credentialModel.getUserLabel());
        }

        public DefaultCtCredential(String id, String userLabel) {
            this.id = id;
            this.userLabel = userLabel;
        }

        public String getId() {
            return id;
        }

        public String getUserLabel() {
            return userLabel;
        }
    }

    public static CtAuthenticatorBean<DefaultCtCredential> createGenericBeans(KeycloakSession session, UserModel user, String selectedCredentialId,
            String credentialModelType, String credProviderFactoryProviderId) {
        return new CtAuthenticatorBean<>(session, user, selectedCredentialId, credentialModelType, credProviderFactoryProviderId, DefaultCtCredential::new);
    }
}
