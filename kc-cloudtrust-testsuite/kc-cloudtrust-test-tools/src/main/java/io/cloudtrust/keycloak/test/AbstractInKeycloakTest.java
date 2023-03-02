package io.cloudtrust.keycloak.test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudtrust.keycloak.test.container.KeycloakDeploy;
import io.cloudtrust.keycloak.test.container.KeycloakQuarkusContainer;
import io.cloudtrust.keycloak.test.container.SystemEnv;
import io.cloudtrust.keycloak.test.events.EventsManager;
import io.cloudtrust.keycloak.test.http.HttpServerManager;
import io.cloudtrust.keycloak.test.init.InjectionException;
import io.cloudtrust.keycloak.test.init.TestInitializer;
import io.cloudtrust.keycloak.test.matchers.EventMatchers;
import io.cloudtrust.keycloak.test.util.ConsumerExcept;
import io.cloudtrust.keycloak.test.util.FlowUtil;
import io.cloudtrust.keycloak.test.util.JsonToolbox;
import io.cloudtrust.keycloak.test.util.OidcTokenProvider;
import io.undertow.server.HttpHandler;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.AuthenticationManagementResource;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.IdentityProviderResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.AdminEventRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RealmEventsConfigRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;

public abstract class AbstractInKeycloakTest {
    private static final Logger LOG = Logger.getLogger(AbstractInKeycloakTest.class);

    protected ObjectMapper mapper = new ObjectMapper();
    private ExtensionApi extensionApi;
    private String defaultRealmName = null;
    private String defaultUserPassword = "password+";

    private TestInitializer testInitializer;

    protected HttpServerManager http() {
        return HttpServerManager.getDefault();
    }

    /**
     * Deprecated: please use http().start(BasicHttpHandler instead)
     *
     * @param handler HttpHandler
     */
    @Deprecated
    protected static void startHttpServer(HttpHandler handler) {
        HttpServerManager.getDefault().startHttpServer(handler);
    }

    /**
     * Deprecated: please use http().start(int port, BasicHttpHandler handler) instead
     *
     * @param handler HttpHandler
     */
    @Deprecated
    protected static void startHttpServer(HttpHandler handler, int port) {
        HttpServerManager.getDefault().startHttpServer(port, handler);
    }

    /**
     * Deprecated: please use http().stop() instead
     */
    @Deprecated
    protected static void stopHttpServer() {
        HttpServerManager.getDefault().stop();
    }

    /**
     * Deprecated: please use http().stop(int port) instead
     */
    @Deprecated
    protected static void stopHttpServer(int port) {
        HttpServerManager.getDefault().stop(port);
    }

    /**
     * Returns the used Keycloak container... don't forget to override if you don't use KeycloakDeploy
     *
     * @return the start container
     */
    public KeycloakQuarkusContainer getContainer() {
        return KeycloakDeploy.getContainer();
    }

    public String getKeycloakURL() {
        return this.getContainer().getBaseUrl();
    }

    public void withKeycloakEnvironment(SystemEnv.RunnableEx runnable) {
        SystemEnv.withKeycloakEnvironment(runnable);
    }

    public <T> T withKeycloakEnvironment(SystemEnv.FunctionEx<T> runnable) {
        return SystemEnv.withKeycloakEnvironment(runnable);
    }

    public void createRealm(String filename) throws IOException {
        createRealm(null, filename, r -> {
        });
    }

    public void createRealm(String realmName, String filename) throws IOException {
        createRealm(realmName, filename, r -> {
        });
    }

    public void createRealm(String filename, ConsumerExcept<RealmResource, IOException> whenCreated) throws IOException {
        createRealm(null, filename, whenCreated);
    }

    public void createRealm(String realmName, String filename, ConsumerExcept<RealmResource, IOException> whenCreated) throws IOException {
        Keycloak kc = this.getContainer().getKeycloakAdminClient();
        RealmResource testRealm = importTestRealm(kc, realmName, filename);
        whenCreated.accept(testRealm);
    }

    protected RealmResource importTestRealm(Keycloak keycloak, String realmName, String realmFilePath) throws IOException {
        RealmRepresentation realmRepresentation = new ObjectMapper().readValue(
                getClass().getResourceAsStream(realmFilePath), RealmRepresentation.class);
        if (realmName == null) {
            realmName = realmRepresentation.getRealm();
        }
        LOG.debugf("Creating realm %s", realmName);
        events().onRealmRemoved(realmName);
        adminEvents().onRealmRemoved(realmName);
        RealmResource realm = keycloak.realm(realmName);
        try {
            realm.remove();
        } catch (Exception e) {
            // Ignore
        }
        keycloak.realms().create(realmRepresentation);
        if (this.defaultRealmName == null) {
            this.defaultRealmName = realmName;
        }
        return keycloak.realm(realmName);
    }

    public RealmResource getRealm() {
        return getRealm(defaultRealmName);
    }

    public RealmResource getRealm(String realmName) {
        return this.getContainer().getKeycloakAdminClient().realm(realmName);
    }

    public void updateRealm(String realmName, Consumer<RealmRepresentation> updater) {
        updateRealm(this.getRealm(realmName), updater);
    }

    public void updateRealm(RealmResource realmResource, Consumer<RealmRepresentation> updater) {
        RealmRepresentation realm = realmResource.toRepresentation();
        updater.accept(realm);
        realmResource.update(realm);
    }

    public boolean deleteRealm() {
        return this.deleteRealm(this.defaultRealmName);
    }

    public boolean deleteRealm(String realmName) {
        try {
            LOG.debugf("Removing realm %s", realmName);
            getRealm(realmName).remove();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void updateIdentityProvider(String idpAlias, Consumer<IdentityProviderRepresentation> updater) {
        this.updateIdentityProvider(defaultRealmName, idpAlias, updater);
    }

    public void updateIdentityProvider(String realmName, String idpAlias, Consumer<IdentityProviderRepresentation> updater) {
        this.updateIdentityProvider(this.getRealm(realmName), idpAlias, updater);
    }

    public void updateIdentityProvider(RealmResource realmResource, String idpAlias, Consumer<IdentityProviderRepresentation> updater) {
        IdentityProviderResource idpRes = realmResource.identityProviders().get(idpAlias);
        IdentityProviderRepresentation idp = idpRes.toRepresentation();
        updater.accept(idp);
        idpRes.update(idp);
    }

    public List<UserRepresentation> searchUsers(String username) {
        return searchUsers(defaultRealmName, username);
    }

    public List<UserRepresentation> searchUsers(String realmName, String username) {
        return searchUsers(getRealm(realmName), username);
    }

    public List<UserRepresentation> searchUsers(RealmResource realm, String username) {
        return realm.users().search(username);
    }

    public UserRepresentation getUserByName(String username) {
        return getUserByName(this.defaultRealmName, username);
    }

    public UserRepresentation getUserByName(String realmName, String username) {
        return getUserByName(getRealm(realmName), username);
    }

    public UserRepresentation getUserByName(RealmResource realm, String username) {
        List<UserRepresentation> users = searchUsers(realm, username);
        return users == null || users.isEmpty() ? null : users.get(0);
    }

    public Map<String, List<String>> getUserAttributes(String username) {
        return getUserAttributes(defaultRealmName, username);
    }

    public Map<String, List<String>> getUserAttributes(String realmName, String username) {
        return searchUsers(realmName, username).get(0).getAttributes();
    }

    public List<String> getUserAttribute(String username, String attributeName) {
        return getUserAttribute(defaultRealmName, username, attributeName);
    }

    public List<String> getUserAttribute(String realmName, String username, String attributeName) {
        Map<String, List<String>> attrbs = getUserAttributes(realmName, username);
        List<String> res = attrbs == null ? null : attrbs.get(attributeName);
        return res == null ? Collections.emptyList() : res;
    }

    public String getUserAttributeAsString(String username, String attributeName) {
        return getUserAttributeAsString(defaultRealmName, username, attributeName);
    }

    public String getUserAttributeAsString(String realmName, String username, String attributeName) {
        List<String> attrbs = getUserAttribute(realmName, username, attributeName);
        return attrbs == null || attrbs.isEmpty() ? null : attrbs.get(0);
    }

    public int getUserAttributeAsInt(String username, String attributeName) {
        return getUserAttributeAsInt(defaultRealmName, username, attributeName);
    }

    public int getUserAttributeAsInt(String realmName, String username, String attributeName) {
        try {
            return Integer.parseInt(getUserAttributeAsString(realmName, username, attributeName));
        } catch (Exception e) {
            return 0;
        }
    }

    public void setUserAttribute(String username, String attributeName, List<String> values) {
        setUserAttribute(defaultRealmName, username, attributeName, values);
    }

    public void setUserAttribute(String realmName, String username, String attributeName, List<String> values) {
        UsersResource kcUsers = getRealm(realmName).users();
        UserRepresentation user = kcUsers.search(username).get(0);
        if (user.getAttributes()==null) {
            user.setAttributes(new HashMap<>());
        }
        user.getAttributes().put(attributeName, values);
        kcUsers.get(user.getId()).update(user);
    }

    public void removeUserAttribute(String username, String attributeName) {
        removeUserAttribute(defaultRealmName, username, attributeName);
    }

    public void removeUserAttribute(String realmName, String username, String attributeName) {
        UsersResource kcUsers = getRealm(realmName).users();
        // remove mobile phone from user
        UserRepresentation user = kcUsers.search(username).get(0);
        user.getAttributes().remove(attributeName);
        kcUsers.get(user.getId()).update(user);
    }

    public Stream<CredentialRepresentation> getUserCredentials(String username, Predicate<? super CredentialRepresentation> predicate) {
        return getUserCredentials(defaultRealmName, username, predicate);
    }

    public Stream<CredentialRepresentation> getUserCredentials(String realmName, String username, Predicate<? super CredentialRepresentation> predicate) {
        RealmResource testRealm = getRealm(realmName);
        return testRealm.users().get(testRealm.users().search(username).get(0).getId()).credentials().stream()
                .filter(predicate);
    }

    public void registerRequiredActions(String realmName, String... requiredActionIds) {
        registerRequiredActions(getRealm(realmName), requiredActionIds);
    }

    public void registerRequiredActions(RealmResource realm, String... requiredActionIds) {
        AuthenticationManagementResource flows = realm.flows();
        List<String> reqActions = Arrays.asList(requiredActionIds);
        flows.getUnregisteredRequiredActions().stream()
                .filter(ra -> reqActions.contains(ra.getProviderId()))
                .forEach(flows::registerRequiredAction);
    }

    public void setBrowserFlow(String flow) {
        setBrowserFlow(defaultRealmName, flow);
    }

    public void setBrowserFlow(String realmName, String flow) {
        RealmResource testRealm = getRealm(realmName);
        RealmRepresentation realm = testRealm.toRepresentation();
        realm.setBrowserFlow(flow);
        testRealm.update(realm);
    }

    /**
     * Gets the default user password
     */
    protected String getDefaultUserPassword() {
        return defaultUserPassword;
    }

    /**
     * Sets the default user password
     */
    protected void setDefaultUserPassword(String defaultUserPassword) {
        this.defaultUserPassword = defaultUserPassword;
    }

    /**
     * Creates an enabled user with email=username+"@test.com" and password="password+"
     *
     * @param username    Username
     * @param userUpdater Lambda used to customize the user before it is created
     */
    protected String createUser(String username, Consumer<UserRepresentation> userUpdater) {
        return this.createUser(this.getRealm(), username, userUpdater);
    }

    protected String createUser(RealmResource realm, String username, Consumer<UserRepresentation> userUpdater) {
        CredentialRepresentation credentialPass = new CredentialRepresentation();
        credentialPass.setType(CredentialRepresentation.PASSWORD);
        credentialPass.setValue(defaultUserPassword);

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setEnabled(true);
        user.setCredentials(new LinkedList<>());
        user.getCredentials().add(credentialPass);
        user.setAttributes(new HashMap<>());
        userUpdater.accept(user);
        try (Response createUser = realm.users().create(user)) {
            Assertions.assertEquals(201, createUser.getStatus());
            String location = createUser.getHeaderString("Location");
            return location.substring(location.lastIndexOf("/") + 1);
        }
    }

    protected Keycloak getKeycloakAdminClient() {
        return this.getContainer().getKeycloakAdminClient();
    }

    protected FlowUtil getFlowUtil() {
        return getFlowUtil(defaultRealmName);
    }

    protected FlowUtil getFlowUtil(String realmName) {
        return getFlowUtil(getRealm(realmName));
    }

    protected FlowUtil getFlowUtil(RealmResource realm) {
        return FlowUtil.inCurrentRealm(realm);
    }

    public ClientResource findClientResourceById(RealmResource realm, String id) {
        for (ClientRepresentation c : realm.clients().findAll()) {
            if (c.getId().equals(id)) {
                return realm.clients().get(c.getId());
            }
        }
        return null;
    }

    public ClientResource findClientByClientId(RealmResource realm, String clientId) {
        for (ClientRepresentation c : realm.clients().findAll()) {
            if (clientId.equals(c.getClientId())) {
                return realm.clients().get(c.getId());
            }
        }
        return null;
    }

    protected void sleep(Duration duration) {
        this.sleep(duration.toMillis());
    }

    protected void sleep(long duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ie) {
            // Ignore exception
        }
    }

    protected void sleep(Duration maxDuration, Duration interval, BooleanSupplier shouldStop) {
        this.sleep(maxDuration.toMillis(), interval.toMillis(), shouldStop);
    }

    protected boolean sleep(Duration maxDuration, long interval, BooleanSupplier shouldStop) {
        return this.sleep(maxDuration.toMillis(), interval, shouldStop);
    }

    protected boolean sleep(Duration maxDuration, BooleanSupplier shouldStop) {
        return sleep(maxDuration.toMillis(), 100, shouldStop);
    }

    protected boolean sleep(long maxDuration, BooleanSupplier shouldStop) {
        return sleep(maxDuration, 100, shouldStop);
    }

    /**
     * Sleep for the given maxDuration. Stops if provided supplier is true
     * @param maxDuration Milliseconds
     * @param interval Interval between supplier evaluation
     * @param shouldStop Supplier which returns true if the sleep period can be interrupted
     * @return True if supplier told to stop, false in case of timeout
     */
    protected boolean sleep(long maxDuration, long interval, BooleanSupplier shouldStop) {
        try {
            long limit = System.currentTimeMillis() + maxDuration;
            while (System.currentTimeMillis() < limit) {
                long maxPause = Math.max(0, limit - System.currentTimeMillis());
                Thread.sleep(Math.min(interval, maxPause));
                maxDuration -= interval;
                if (shouldStop.getAsBoolean()) {
                    return true;
                }
            }
        } catch (InterruptedException ie) {
            // Ignore exception
        }
        return false;
    }

    /**
     * Events management
     */
    private EventsManager<EventRepresentation> eventsManager;
    private EventsManager<AdminEventRepresentation> adminEventsManager;

    public EventsManager<EventRepresentation> events() {
        if (eventsManager == null) {
            eventsManager = new EventsManager<>(
                    (realmName, configConsumer) -> {
                        RealmResource realm = getRealm(realmName);
                        RealmEventsConfigRepresentation conf = realm.getRealmEventsConfig();
                        boolean update = false;
                        if (!conf.isEventsEnabled()) {
                            conf.setEventsEnabled(true);
                            update = true;
                        }
                        if (configConsumer != null) {
                            configConsumer.accept(conf);
                        }
                        if (update) {
                            realm.updateRealmEventsConfig(conf);
                        }
                    },
                    r -> this.getRealm(r).clearEvents(),
                    r -> this.getRealm(r).getEvents(),
                    (e1, e2) -> (int) (e1.getTime() - e2.getTime())
            );
        }
        return this.eventsManager;
    }

    public EventsManager<AdminEventRepresentation> adminEvents() {
        if (adminEventsManager == null) {
            adminEventsManager = new EventsManager<>(
                    (realmName, configConsumer) -> {
                        RealmResource realm = getRealm(realmName);
                        RealmEventsConfigRepresentation conf = realm.getRealmEventsConfig();
                        boolean update = false;
                        if (!Boolean.TRUE.equals(conf.isAdminEventsEnabled())) {
                            conf.setAdminEventsEnabled(true);
                            update = true;
                        }
                        if (configConsumer != null) {
                            configConsumer.accept(conf);
                        }
                        if (update) {
                            realm.updateRealmEventsConfig(conf);
                        }
                    },
                    r -> this.getRealm(r).clearAdminEvents(),
                    r -> this.getRealm(r).getAdminEvents(),
                    (e1, e2) -> (int) (e1.getTime() - e2.getTime())
            );
        }
        return this.adminEventsManager;
    }

    /**
     * Events management: activate events
     *
     * @Deprecated Please use events().activate()
     */
    @Deprecated
    public void activateEvents() {
        events().activate(defaultRealmName);
    }

    /**
     * @param realmName
     * @Deprecated Please use events().activate(realmName)
     */
    @Deprecated
    public void activateEvents(String realmName) {
        events().activate(realmName);
    }

    /**
     * @param realmName
     * @param configConsumer
     * @param adminEventsToo
     * @Deprecated Please use events().activate(realmName, configConsumer) and adminEvents().activate(realmName)
     */
    @Deprecated
    public void activateEvents(String realmName, Consumer<RealmEventsConfigRepresentation> configConsumer, boolean adminEventsToo) {
        events().activate(realmName, configConsumer);
        if (adminEventsToo) {
            adminEvents().activate(realmName);
        }
    }

    /**
     * Event management: clear events
     *
     * @Deprecated Please use events().clear() and adminEvents().clear()
     */
    @Deprecated
    public void clearEvents() {
        events().clear();
        adminEvents().clear();
    }

    /**
     * Event management: poll event
     *
     * @Deprecated Please use events().poll()
     */
    @Deprecated
    protected EventRepresentation pollEvent() {
        return events().poll();
    }

    /**
     * @param number
     * @return
     * @Deprecated Please use events().poll(int)
     */
    @Deprecated
    protected Collection<EventRepresentation> pollEvents(int number) {
        return events().poll(number);
    }

    /**
     * @return
     * @Deprecated Please use adminEvents().poll()
     */
    @Deprecated
    protected AdminEventRepresentation pollAdminEvent() {
        return adminEvents().poll();
    }

    /**
     * @Deprecated Please use EventMatchers.doesNotExist()
     */
    @Deprecated
    protected void assertHasNoEvent() {
        assertThat(pollEvent(), EventMatchers.doesNotExist());
    }

    /**
     * API management
     */
    protected ExtensionApi api() {
        if (extensionApi == null) {
            extensionApi = new ExtensionApi(this.getKeycloakURL(), this.getKeycloakAdminClient());
        }
        return extensionApi;
    }

    /**
     * @Deprecated Please use api().initToken();
     */
    @Deprecated
    protected void initializeToken() {
        api().initToken();
    }

    /**
     * @return
     * @Deprecated please use api().getToken()
     */
    @Deprecated
    protected String getToken() {
        return api().getToken();
    }

    /**
     * @param accessToken
     * @Deprecated : please use api().setToken(String);
     */
    @Deprecated
    protected void setToken(String accessToken) {
        api().setToken(accessToken);
    }

    /**
     * @Deprecated Please use api().query(...)
     */
    @Deprecated
    protected <T> T queryApi(Class<T> clazz, String method, String apiPath) throws IOException, URISyntaxException {
        return api().query(clazz, method, apiPath);
    }

    /**
     * @Deprecated Please use api().query(...)
     */
    @Deprecated
    protected <T> T queryApi(Class<T> clazz, String method, String apiPath, List<NameValuePair> nvps) throws IOException, URISyntaxException {
        return api().query(clazz, method, apiPath, nvps);
    }

    /**
     * @Deprecated Please use api().query(...)
     */
    @Deprecated
    protected <T> T queryApi(TypeReference<T> typeRef, String method, String apiPath, List<NameValuePair> params) throws IOException, URISyntaxException {
        return api().query(typeRef, method, apiPath, params);
    }

    /**
     * @param apiPath
     * @Deprecated Please use api().call(...)
     */
    @Deprecated
    protected String callApi(String apiPath) throws IOException, URISyntaxException {
        return api().call(apiPath);
    }

    /**
     * @Deprecated Please use api().call(...)
     */
    @Deprecated
    protected String callApi(String method, String apiPath) throws IOException, URISyntaxException {
        return api().call(method, apiPath, new ArrayList<>());
    }

    /**
     * @Deprecated Please use api().call(...)
     */
    @Deprecated
    protected String callApi(String method, String apiPath, List<NameValuePair> nvps) throws IOException, URISyntaxException {
        return api().call(method, apiPath, nvps, null);
    }

    /**
     * @Deprecated Please use api().callJSON(...)
     */
    @Deprecated
    protected String callJSON(String method, String apiPath, Object jsonable) throws IOException, URISyntaxException {
        return api().callJSON(method, apiPath, new ArrayList<>(), jsonable);
    }

    /**
     * @Deprecated Please use api().callJSON(...)
     */
    @Deprecated
    protected String callJSON(String method, String apiPath, List<NameValuePair> nvps, Object jsonable) throws IOException, URISyntaxException {
        return api().callJSON(method, apiPath, nvps, jsonable);
    }

    /**
     * @Deprecated Please use api().call(...)
     */
    @Deprecated
    protected String call(String method, String apiPath, List<NameValuePair> nvps, HttpEntity entity) throws IOException, URISyntaxException {
        return api().call(method, apiPath, nvps, entity);
    }

    public OidcTokenProvider createOidcTokenProvider() {
        return createOidcTokenProvider("VPN", "VPN-CLIENT-SECRET");
    }

    public OidcTokenProvider createOidcTokenProvider(String username, String password) {
        return new OidcTokenProvider(getKeycloakURL(), "/realms/test/protocol/openid-connect/token", username, password);
    }

    public void injectComponents() throws InjectionException {
        injectComponents(false);
    }

    public void injectComponents(boolean forceReInit) throws InjectionException {
        if (this.testInitializer == null) {
            this.testInitializer = new TestInitializer();
        }
        testInitializer.init(this, forceReInit);
    }

    public void logObject(String message, Object obj) {
        LOG.debugf("%s: %s", message, JsonToolbox.toString(obj));
    }
}