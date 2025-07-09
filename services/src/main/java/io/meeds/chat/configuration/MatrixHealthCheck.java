package io.meeds.chat.configuration;

import java.net.HttpURLConnection;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class MatrixHealthCheck {

    private static final Logger LOG                    = LoggerFactory.getLogger(MatrixHealthCheck.class);

    private static final String DEFAULT_MATRIX_URL     = "http://localhost:8008";

    private static final String HEALTH_ENDPOINT        = "/_matrix/client/versions";

    private static final int    DEFAULT_MAX_RETRIES    = 30;

    private static final long   DEFAULT_RETRY_DELAY_MS = 1000;

    @Bean
    @Lazy(false)
    public String checkMatrixServer() {
        String serverUrl = getServerUrl();
        String healthUrl = serverUrl + HEALTH_ENDPOINT;
        int maxRetries = getMaxRetries();
        long retryDelay = getRetryDelay();

        int attempt = 0;
        Exception lastError = null;

        while (attempt < maxRetries) {
            attempt++;
            try {
                HttpURLConnection connection = createConnection(healthUrl);
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    LOG.info("Matrix server is available at {} (attempt {}/{})", serverUrl, attempt, maxRetries);
                    return serverUrl;
                } else {
                    LOG.warn("Matrix server not healthy (HTTP {}), retrying... (attempt {}/{})",
                             responseCode,
                             attempt,
                             maxRetries);
                }
            } catch (Exception e) {
                lastError = e;
                LOG.warn("Matrix server connection failed, retrying... (attempt {}/{})", attempt, maxRetries, e);
            }

            if (attempt < maxRetries) {
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for Matrix server", ie);
                }
            }
        }

        throw new IllegalStateException(String.format("Matrix server not available after %d attempts at %s",
                                                      maxRetries,
                                                      healthUrl),
                                        lastError);
    }

    protected String getServerUrl() {
        return System.getProperty("meeds.matrix.server.url", DEFAULT_MATRIX_URL);
    }

    protected int getMaxRetries() {
        return getIntProperty("meeds.matrix.retry.max", DEFAULT_MAX_RETRIES);
    }

    protected long getRetryDelay() {
        return getLongProperty("meeds.matrix.retry.delay", DEFAULT_RETRY_DELAY_MS);
    }

    protected HttpURLConnection createConnection(String healthUrl) throws Exception {
        URL url = new URL(healthUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        return connection;
    }

    private int getIntProperty(String key, int defaultValue) {
        try {
            String value = System.getProperty(key);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            LOG.warn("Invalid value for property {}, using default {}", key, defaultValue);
            return defaultValue;
        }
    }

    private long getLongProperty(String key, long defaultValue) {
        try {
            String value = System.getProperty(key);
            return value != null ? Long.parseLong(value) : defaultValue;
        } catch (NumberFormatException e) {
            LOG.warn("Invalid value for property {}, using default {}", key, defaultValue);
            return defaultValue;
        }
    }
}
