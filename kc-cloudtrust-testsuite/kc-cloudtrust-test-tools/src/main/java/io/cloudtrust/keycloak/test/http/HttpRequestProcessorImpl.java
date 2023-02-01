package io.cloudtrust.keycloak.test.http;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.apache.commons.io.IOUtils;
import org.xnio.streams.BufferedChannelInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public class HttpRequestProcessorImpl implements HttpRequestProcessor {
    private final HttpServerExchange exchange;
    private boolean blockingStarted = false;
    private String body = null;

    public HttpRequestProcessorImpl(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public String method() {
        return this.exchange.getRequestMethod().toString();
    }

    @Override
    public String path() {
        return this.exchange.getRequestPath();
    }

    @Override
    public HeaderHandler headers() {
        return new HeaderHandlerImpl(exchange);
    }

    @Override
    public String param(String name) {
        Map<String, Deque<String>> params = exchange.getQueryParameters();
        if (params==null) {
            return null;
        }
        Deque<String> values = params.get(name);
        if (values==null) {
            return null;
        }
        return values.getFirst();
    }

    @Override
    public List<String> paramValues(String name) {
        return new ArrayList<>(exchange.getQueryParameters().get(name));
    }

    @Override
    public String body() throws IOException {
        if (this.body==null) {
            this.startBlocking();
            InputStream cis = new BufferedChannelInputStream(exchange.getRequestChannel(), 1024);
            this.body = IOUtils.toString(cis, StandardCharsets.UTF_8);
        }
        return this.body;
    }

    @Override
    public void statusCode(int status) {
        this.exchange.setStatusCode(status);
    }

    @Override
    public void setHeader(String name, String value) {
        this.exchange.getResponseHeaders().put(new HttpString(name), value);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        this.exchange.getResponseSender().send(ByteBuffer.wrap(bytes));
    }

    @Override
    @Deprecated
    public OutputStream output() {
        this.startBlocking();
        return this.exchange.getOutputStream();
    }

    private void startBlocking() {
        if (!this.blockingStarted) {
            this.exchange.startBlocking();
            this.blockingStarted = true;
        }
    }
}
