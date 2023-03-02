package io.cloudtrust.keycloak.test.http;

import com.fasterxml.jackson.core.type.TypeReference;
import io.cloudtrust.keycloak.test.util.ConsumerExcept;
import io.cloudtrust.keycloak.test.util.JsonToolbox;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class HttpServerManagerTest {
    private static final int LISTEN_PORT = 9995;

    @Test
    void noServerStarted() {
        assertThat(HttpServerManager.getDefault().getActiveServerPorts().isEmpty(), is(true));
    }

    @Test
    @Disabled("To be fixed - does not work on new CI -since 2023/02-")
    void multipleStartStopOfServer() {
        HttpServerManager mgr = new HttpServerManager();
        int port = 9997;
        try {
            for (int i = 0; i < 5; i++) {
                mgr.start(port, x -> x.statusCode(204));
                assertThat(mgr.getActiveServerPorts().size(), is(1));
            }

            for (int i = 0; i < 10; i++) {
                mgr.stop(port);
                assertThat(mgr.getActiveServerPorts().size(), is(0));
            }
        } finally {
            mgr.stop(port);
        }
    }

    @Test
    void serverSuccessTest() {
        HttpServerManager mgr = new HttpServerManager();
        try {
            assertThat(mgr.getActiveServerPorts().isEmpty(), is(true));
            mgr.startHttpServer(e -> {
            });
            assertThat(mgr.getActiveServerPorts().size(), is(1));
            assertThat(mgr.getActiveServerPorts().contains(LISTEN_PORT), is(true));
            mgr.stop();
            assertThat(mgr.getActiveServerPorts().isEmpty(), is(true));
        } finally {
            mgr.stop();
        }
    }

    private ConsumerExcept<HttpRequestProcessor, Exception> createMyTestHandler() {
        TypeReference<Map<String, String>> mapTypeRef = new TypeReference<>() {
        };
        Pattern code = Pattern.compile("(\\d+)$");
        return hrp -> {
            Matcher m = code.matcher(hrp.path());
            int status = m.find() ? Integer.parseInt(m.group(1)) : 404;
            hrp.statusCode(status);
            if (status >= 200 && status < 400) {
                Map<String, String> response = new HashMap<>();
                response.put("method", hrp.method());
                response.put("path", hrp.path());
                response.put("status", Integer.toString(status));
                hrp.headers().getHeaderNames().forEach(name -> response.put(name, hrp.headers().getFirstHeader(name)));
                Map<String, String> body = hrp.body(mapTypeRef);
                if (body != null) {
                    body.putAll(response);
                }
                hrp.writeJson(response);
            }
        };
    }

    @ParameterizedTest
    @MethodSource("serverContentSamples")
    void serverContentTest(String method, String path, Object body, int expectedStatus, Collection<String> contains) throws IOException, URISyntaxException {
        HttpServerManager mgr = new HttpServerManager();
        try {
            mgr.start(createMyTestHandler());
            StringEntity content = new StringEntity(JsonToolbox.toString(body));
            Pair<Integer, String> res = query(method, path, content);
            assertThat(res.getLeft(), is(expectedStatus));
            contains.forEach(v -> assertThat(res.getRight().contains(v), is(true)));
        } finally {
            mgr.stop();
        }
    }

    public static Stream<Arguments> serverContentSamples() {
        return Stream.of(
                Arguments.of("GET", "http://localhost:9995/status/302", null, 302, Collections.singletonList("GET")),
                Arguments.of("POST", "http://localhost:9995/", null, 404, Collections.emptyList())
        );
    }

    private Pair<Integer, String> query(String method, String path, HttpEntity body, String... params) throws IOException, URISyntaxException {
        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            URIBuilder uriBuilder = new URIBuilder(path);
            if (params != null) {
                for (int i = 0; i + 1 < params.length; i += 2) {
                    uriBuilder.addParameter(params[i], params[i + 1]);
                }
            }
            HttpRequestBase request = createHttpRequest(method, uriBuilder.build(), body);
            request.addHeader("Authorization", "Bearer my-token");

            HttpResponse response = client.execute(request);
            if (response.getEntity() != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                    return Pair.of(response.getStatusLine().getStatusCode(), reader.lines().collect(Collectors.joining()));
                }
            }
            return Pair.of(response.getStatusLine().getStatusCode(), null);
        }
    }

    private HttpRequestBase createHttpRequest(String method, URI uri, HttpEntity entity) throws HttpResponseException {
        switch (method) {
            case "GET":
                return new HttpGet(uri);
            case "POST":
                return addBodyToHttpRequest(new HttpPost(uri), entity);
            case "PUT":
                return addBodyToHttpRequest(new HttpPut(uri), entity);
            default:
                throw new HttpResponseException(405, "Unsupported method " + method);
        }
    }

    private HttpRequestBase addBodyToHttpRequest(HttpEntityEnclosingRequestBase httpRequest, HttpEntity entity) {
        if (entity != null) {
            httpRequest.setEntity(entity);
        }
        return httpRequest;
    }
}
