package com.giorgimode.spotmystatus.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@SuppressWarnings({"ConstantConditions", "rawtypes", "unchecked"})
@ExtendWith(MockitoExtension.class)
class RestHelperTest {

    private static final String TEST_COOKIE = "cookie_monster";
    @Mock
    private RestTemplate restTemplate;

    @Captor
    private ArgumentCaptor<String> uriCaptor;

    @Captor
    private ArgumentCaptor<HttpEntity<List<UUID>>> httpEntityCaptor;

    @Captor
    private ArgumentCaptor<Class> responseTypeCaptor;


    @Test
    public void testPostWithBody() {
        List<UUID> uuids = List.of(UUID.randomUUID());
        testPost(uuids);
    }

    @Test
    public void testPostWithoutBody() {
        testPost(null);
    }

    private void testPost(List<UUID> uuids) {
        String tenantId = "smart-upload";
        buildRestHelper(uuids)
            .withQueryParam("tenantId", tenantId)
            .withQueryParam("userId", "userId_123")
            .withHeader("header1", "test_header")
            .withHeader("header2", List.of("value1", "value2"))
            .post(restTemplate, String.class);

        verify(restTemplate).postForEntity(uriCaptor.capture(), httpEntityCaptor.capture(), responseTypeCaptor.capture());
        assertEquals("http://document:8080/documents/search/?tenantId=" + tenantId + "&userId=userId_123", uriCaptor.getValue());
        assertEquals(String.class, responseTypeCaptor.getValue());
        HttpEntity<List<UUID>> httpEntity = httpEntityCaptor.getValue();
        assertEquals(uuids, httpEntity.getBody());
        assertTrue(httpEntity.getHeaders().getAccept().contains(MediaType.APPLICATION_JSON));
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, httpEntity.getHeaders().getContentType());
        assertTrue(httpEntity.getHeaders().get("Cookie").contains(TEST_COOKIE));
        assertTrue(httpEntity.getHeaders().get("Authorization").contains("Bearer 1234"));
        assertTrue(httpEntity.getHeaders().get("header1").contains("test_header"));
        assertTrue(httpEntity.getHeaders().get("header2").containsAll(List.of("value1", "value2")));
    }

    @Test
    public void testPostAndGetBody() {
        String testValue = "testValue";
        when(restTemplate.postForEntity(startsWith("http://document:8080"), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(testValue, HttpStatus.OK));
        String tenantId = "smart-upload";
        String responseBody = buildRestHelper(null)
            .withQueryParam("tenantId", tenantId)
            .withQueryParam("userId", "userId_123")
            .withHeader("header1", "test_header")
            .withHeader("header2", List.of("value1", "value2"))
            .postAndGetBody(restTemplate, String.class);

        assertEquals(testValue, responseBody);
        verify(restTemplate).postForEntity(uriCaptor.capture(), httpEntityCaptor.capture(), responseTypeCaptor.capture());
        assertEquals("http://document:8080/documents/search/?tenantId=" + tenantId + "&userId=userId_123", uriCaptor.getValue());
        assertEquals(String.class, responseTypeCaptor.getValue());
        HttpEntity<List<UUID>> httpEntity = httpEntityCaptor.getValue();
        assertTrue(httpEntity.getHeaders().getAccept().contains(MediaType.APPLICATION_JSON));
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, httpEntity.getHeaders().getContentType());
        assertTrue(httpEntity.getHeaders().get("Cookie").contains(TEST_COOKIE));
        assertTrue(httpEntity.getHeaders().get("Authorization").contains("Bearer 1234"));
        assertTrue(httpEntity.getHeaders().get("header1").contains("test_header"));
        assertTrue(httpEntity.getHeaders().get("header2").containsAll(List.of("value1", "value2")));
    }

    @Test
    public void testGet() {
        List<UUID> uuids = List.of(UUID.randomUUID());
        buildRestHelper(uuids)
            .withPath(null)
            .withBearer(null)
            .withBasicAuth("user", "password")
            .get(restTemplate, String.class);

        verify(restTemplate).exchange(uriCaptor.capture(), eq(HttpMethod.GET), httpEntityCaptor.capture(), responseTypeCaptor.capture());
        assertEquals("http://document:8080", uriCaptor.getValue());
        assertEquals(String.class, responseTypeCaptor.getValue());
        HttpEntity<List<UUID>> httpEntity = httpEntityCaptor.getValue();
        assertNull(httpEntity.getBody());
        assertTrue(httpEntity.getHeaders().getAccept().contains(MediaType.APPLICATION_JSON));
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, httpEntity.getHeaders().getContentType());
        assertTrue(httpEntity.getHeaders().get("Cookie").contains(TEST_COOKIE));
        assertTrue(httpEntity.getHeaders().get("Authorization").contains("Basic dXNlcjpwYXNzd29yZA=="));
    }

    @Test
    public void testGetBody() {
        String testValue = "testValue";
        when(restTemplate.exchange(eq("http://document:8080"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>(testValue, HttpStatus.OK));

        List<UUID> uuids = List.of(UUID.randomUUID());
        String responseBody = buildRestHelper(uuids)
            .withPath(null)
            .withBearer(null)
            .withBasicAuth("user", "password")
            .getBody(restTemplate, String.class);

        assertEquals(testValue, responseBody);

        verify(restTemplate).exchange(uriCaptor.capture(), eq(HttpMethod.GET), httpEntityCaptor.capture(), responseTypeCaptor.capture());
        assertEquals("http://document:8080", uriCaptor.getValue());
        assertEquals(String.class, responseTypeCaptor.getValue());
        HttpEntity<List<UUID>> httpEntity = httpEntityCaptor.getValue();
        assertNull(httpEntity.getBody());
        assertTrue(httpEntity.getHeaders().getAccept().contains(MediaType.APPLICATION_JSON));
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, httpEntity.getHeaders().getContentType());
        assertTrue(httpEntity.getHeaders().get("Cookie").contains(TEST_COOKIE));
        assertTrue(httpEntity.getHeaders().get("Authorization").contains("Basic dXNlcjpwYXNzd29yZA=="));
    }

    @Test
    public void testDelete() {
        List<UUID> uuids = List.of(UUID.randomUUID());

        buildRestHelper(uuids)
            .withBearer(null)
            .delete(restTemplate);

        verify(restTemplate).exchange(uriCaptor.capture(), eq(HttpMethod.DELETE), httpEntityCaptor.capture(), responseTypeCaptor.capture());
        assertEquals("http://document:8080/documents/search/", uriCaptor.getValue());
        HttpEntity<List<UUID>> httpEntity = httpEntityCaptor.getValue();
        assertEquals(Void.class, responseTypeCaptor.getValue());
        assertNull(httpEntity.getBody());
        assertTrue(httpEntity.getHeaders().getAccept().contains(MediaType.APPLICATION_JSON));
        assertEquals(MediaType.APPLICATION_FORM_URLENCODED, httpEntity.getHeaders().getContentType());
        assertTrue(httpEntity.getHeaders().get("Cookie").contains(TEST_COOKIE));
        assertNull(httpEntity.getHeaders().get("Authorization"));
    }

    private RestHelper buildRestHelper(List<UUID> uuids) {
        return RestHelper.builder()
                         .withBaseUrl("http://document:8080")
                         .withPath("/documents/search/")
                         .withBody(uuids)
                         .withAcceptType(MediaType.APPLICATION_JSON_VALUE)
                         .withContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                         .withCookie(TEST_COOKIE)
                         .withBearer("1234");
    }
}