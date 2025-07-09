package io.meeds.chat.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Configuration;

import java.net.HttpURLConnection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatrixHealthCheckTest {

    @Test
    void testSuccessfulCheckOnFirstAttempt() throws Exception {
        MatrixHealthCheck healthCheck = new MatrixHealthCheck() {
            @Override
            protected String getServerUrl() {
                return "http://localhost:8008";
            }

            @Override
            protected HttpURLConnection createConnection(String healthUrl) throws Exception {
                HttpURLConnection mockConnection = mock(HttpURLConnection.class);
                when(mockConnection.getResponseCode()).thenReturn(200);
                return mockConnection;
            }
        };

        String result = healthCheck.checkMatrixServer();
        assertEquals("http://localhost:8008", result);
    }

    @Test
    void testRetryUntilSuccess() throws Exception {
        MatrixHealthCheck healthCheck = new MatrixHealthCheck() {
            private int attempt = 0;

            @Override
            protected String getServerUrl() {
                return "http://localhost:8008";
            }

            @Override
            protected int getMaxRetries() {
                return 5;
            }

            @Override
            protected long getRetryDelay() {
                return 10;
            }

            @Override
            protected HttpURLConnection createConnection(String healthUrl) throws Exception {
                HttpURLConnection mockConnection = mock(HttpURLConnection.class);
                if (attempt++ < 4) {
                    when(mockConnection.getResponseCode()).thenReturn(503);
                } else {
                    when(mockConnection.getResponseCode()).thenReturn(200);
                }
                return mockConnection;
            }
        };

        String result = healthCheck.checkMatrixServer();
        assertEquals("http://localhost:8008", result);
    }

    @Test
    void testMaxRetriesExceeded() throws Exception {
        MatrixHealthCheck healthCheck = new MatrixHealthCheck() {
            @Override
            protected String getServerUrl() {
                return "http://localhost:8008";
            }

            @Override
            protected int getMaxRetries() {
                return 3;
            }

            @Override
            protected long getRetryDelay() {
                return 10;
            }

            @Override
            protected HttpURLConnection createConnection(String healthUrl) throws Exception {
                HttpURLConnection mockConnection = mock(HttpURLConnection.class);
                when(mockConnection.getResponseCode()).thenReturn(503);
                return mockConnection;
            }
        };

        assertThrows(IllegalStateException.class, healthCheck::checkMatrixServer);
    }

    @Test
    void testCustomRetrySettings() throws Exception {
        MatrixHealthCheck healthCheck = new MatrixHealthCheck() {
            @Override
            protected String getServerUrl() {
                return "http://custom:8080";
            }

            @Override
            protected int getMaxRetries() {
                return 15;
            }

            @Override
            protected long getRetryDelay() {
                return 500;
            }

            @Override
            protected HttpURLConnection createConnection(String healthUrl) throws Exception {
                HttpURLConnection mockConnection = mock(HttpURLConnection.class);
                when(mockConnection.getResponseCode()).thenReturn(200);
                return mockConnection;
            }
        };

        String result = healthCheck.checkMatrixServer();
        assertEquals("http://custom:8080", result);
    }

    @Test
    void testConfigurationAnnotation() {
        Configuration annotation = MatrixHealthCheck.class.getAnnotation(Configuration.class);
        assertNotNull(annotation);
    }
}
