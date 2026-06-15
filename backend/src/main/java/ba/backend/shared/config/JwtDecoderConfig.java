package ba.backend.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * Configures a JwtDecoder that can connect to Keycloak over HTTPS
 * in Docker/containerized environments where the JVM may not have
 * the full CA chain for sslip.io / Let's Encrypt certificates.
 */
@Configuration
public class JwtDecoderConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    public JwtDecoder jwtDecoder() {
        relaxSslVerification();
        // Discovers JWKS URI automatically from {issuerUri}/.well-known/openid-configuration
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }

    private void relaxSslVerification() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }}, new SecureRandom());
            SSLContext.setDefault(ctx);
            HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((host, session) -> true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to relax SSL verification", e);
        }
    }
}
