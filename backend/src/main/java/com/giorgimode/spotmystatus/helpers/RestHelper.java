package com.giorgimode.spotmystatus.helpers;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.web.util.UriComponentsBuilder.fromHttpUrl;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@SuppressWarnings("unused")
public final class RestHelper {

    private String cookie;
    private String contentType;
    private String acceptType;
    private String baseUrl;
    private String bearer;
    private String username;
    private String password;
    private Object body;
    private final Map<String, Object> queryParams;
    private final Map<String, List<String>> headers;
    private String path;

    private RestHelper() {
        queryParams = new HashMap<>();
        headers = new HashMap<>();
    }

    public static RestHelper builder() {
        return new RestHelper();
    }

    public RestHelper withCookie(String cookie) {
        this.cookie = cookie;
        return this;
    }

    public RestHelper withContentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public RestHelper withAcceptType(String acceptType) {
        this.acceptType = acceptType;
        return this;
    }

    public RestHelper withBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public RestHelper withBearer(String bearer) {
        this.bearer = bearer;
        return this;
    }

    public RestHelper withBasicAuth(String username, String password) {
        this.username = username;
        this.password = password;
        return this;
    }

    public RestHelper withBody(Object body) {
        this.body = body;
        return this;
    }

    public RestHelper withPath(String path) {
        this.path = path;
        return this;
    }

    public RestHelper withQueryParam(String queryParamKey, Object queryParamValue) {
        queryParams.put(queryParamKey, queryParamValue);
        return this;
    }

    public RestHelper withHeader(String headerKey, String headerValue) {
        headers.put(headerKey, List.of(headerValue));
        return this;
    }

    public RestHelper withHeader(String headerKey, List<String> headerValues) {
        headers.put(headerKey, headerValues);
        return this;
    }

    public <T> ResponseEntity<T> post(RestTemplate restTemplate, Class<T> responseType) {
        requireNonNull(baseUrl);
        if (body != null) {
            return restTemplate.postForEntity(createUri(), new HttpEntity<>(body, createHeaders()), responseType);
        } else {
            return restTemplate.postForEntity(createUri(), new HttpEntity<>(createHeaders()), responseType);
        }
    }

    public <T> T postAndGetBody(RestTemplate restTemplate, Class<T> responseType) {
        return post(restTemplate, responseType).getBody();
    }

    public <T> ResponseEntity<T> get(RestTemplate restTemplate, Class<T> responseType) {
        return restTemplate.exchange(createUri(), HttpMethod.GET, new HttpEntity<>(createHeaders()), responseType);
    }

    public <T> T getBody(RestTemplate restTemplate, Class<T> responseType) {
        return restTemplate.exchange(createUri(), HttpMethod.GET, new HttpEntity<>(createHeaders()), responseType).getBody();
    }

    public ResponseEntity<Void> delete(RestTemplate restTemplate) {
        return restTemplate.exchange(createUri(), HttpMethod.DELETE, new HttpEntity<>(createHeaders()), Void.class);
    }


    public String createUri() {
        UriComponentsBuilder uriBuilder = fromHttpUrl(baseUrl);
        if (!CollectionUtils.isEmpty(queryParams)) {
            queryParams.forEach(uriBuilder::queryParam);
        }
        if (StringUtils.isNotBlank(path)) {
            uriBuilder.path(path);
        }
        return uriBuilder.toUriString();
    }

    private HttpHeaders createHeaders() {
        HttpHeaders httpHeaders = new HttpHeaders();
        if (isNotBlank(contentType)) {
            httpHeaders.set(CONTENT_TYPE, contentType);
        }

        if (isNotBlank(bearer)) {
            httpHeaders.setBearerAuth(bearer);
        } else if (isNotBlank(username) || isNotBlank(password)) {
            httpHeaders.setBasicAuth(username, password);
        }

        if (isNotBlank(cookie)) {
            httpHeaders.set(HttpHeaders.COOKIE, cookie);
        }
        httpHeaders.set(ACCEPT, acceptType);
        httpHeaders.putAll(headers);

        return httpHeaders;
    }
}