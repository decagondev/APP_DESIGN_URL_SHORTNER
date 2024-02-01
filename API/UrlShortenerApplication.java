import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.*;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.util.Date;
import java.util.List;

@SpringBootApplication
public class UrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }
}

@Entity
class UrlMapping {
    @Id
    private String shortCode;
    private String originalUrl;

    public UrlMapping() {
    }

    public UrlMapping(String shortCode, String originalUrl) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }
}

@Entity
class Analytics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String shortCode;
    private String timestamp;
    private String ipAddress;

    public Analytics() {
    }

    public Analytics(String shortCode, String timestamp, String ipAddress) {
        this.shortCode = shortCode;
        this.timestamp = timestamp;
        this.ipAddress = ipAddress;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}

@Repository
interface UrlMappingRepository extends JpaRepository<UrlMapping, String> {
}

@Repository
interface AnalyticsRepository extends JpaRepository<Analytics, Long> {
    @Query("SELECT timestamp, ipAddress FROM Analytics a WHERE a.shortCode = :shortCode")
    List<Object[]> getAnalyticsData(@Param("shortCode") String shortCode);
}

@RestController
@RequestMapping("/")
class UrlShortenerController {

    private final UrlMappingRepository urlMappingRepository;
    private final AnalyticsRepository analyticsRepository;

    public UrlShortenerController(UrlMappingRepository urlMappingRepository, AnalyticsRepository analyticsRepository) {
        this.urlMappingRepository = urlMappingRepository;
        this.analyticsRepository = analyticsRepository;
    }

    @PostMapping("/shorten")
    public ShortUrlResponse shortenUrl(@RequestBody UrlRequest urlRequest) {
        String originalUrl = urlRequest.getOriginalUrl();
        String shortCode = generateUniqueShortCode(originalUrl);

        UrlMapping urlMapping = new UrlMapping(shortCode, originalUrl);
        urlMappingRepository.save(urlMapping);

        String shortUrl = "http://localhost:8080/" + shortCode; // Update with your actual domain and port
        return new ShortUrlResponse(shortUrl);
    }

    @GetMapping("/{shortCode}")
    public void redirectToOriginalUrl(@PathVariable String shortCode) {
        UrlMapping urlMapping = urlMappingRepository.findById(shortCode).orElse(null);

        if (urlMapping != null) {
            String originalUrl = urlMapping.getOriginalUrl();

            // Log analytics data
            String timestamp = new Date().toString();
            String ipAddress = "127.0.0.1"; // Replace with actual IP address if deployed in a production environment

            Analytics analytics = new Analytics(shortCode, timestamp, ipAddress);
            analyticsRepository.save(analytics);

            throw new RedirectException(originalUrl);
        } else {
            throw new ShortUrlNotFoundException();
        }
    }

    @GetMapping("/analytics/{shortCode}")
    public List<Object[]> getAnalytics(@PathVariable String shortCode) {
        return analyticsRepository.getAnalyticsData(shortCode);
    }

    private String generateShortCode(String originalUrl) {
        // Using SHA-256 hash as a simple way to generate a unique short code
        return org.apache.commons.codec.digest.DigestUtils.sha256Hex(originalUrl).substring(0, 8);
    }

    private String generateUniqueShortCode(String originalUrl) {
        String shortCode;
        do {
            shortCode = generateShortCode(originalUrl);
        } while (urlMappingRepository.existsById(shortCode));

        return shortCode;
    }

    static class UrlRequest {
        private String originalUrl;

        public String getOriginalUrl() {
            return originalUrl;
        }

        public void setOriginalUrl(String originalUrl) {
            this.originalUrl = originalUrl;
        }
    }

    static class ShortUrlResponse {
        private String shortUrl;

        public ShortUrlResponse(String shortUrl) {
            this.shortUrl = shortUrl;
        }

        public String getShortUrl() {
            return shortUrl;
        }
    }

    @ResponseStatus(value = org.springframework.http.HttpStatus.NOT_FOUND, reason = "Short URL not found")
    static class ShortUrlNotFoundException extends RuntimeException {
    }

    @ResponseStatus(value = org.springframework.http.HttpStatus.FOUND)
    static class RedirectException extends RuntimeException {
        private final String location;

        public RedirectException(String location) {
            this.location = location;
        }

        public String getLocation() {
            return location;
        }
    }
}
