# common-logger

Spring Boot friendly logger library that provides correlation id handling and an AOP-based `@Loggable` annotation for consistent, structured method logging.

## Fitur
- **Auto-configuration**: Terdaftar otomatis via Spring Boot `AutoConfiguration.imports`.
- **Correlation ID**: `CorrelationIdFilter` memastikan setiap request HTTP memiliki ID unik yang disimpan di MDC dan dikembalikan di header response.
- **Structured Logging**: `@Loggable` + `LoggingAspect` menghasilkan log JSON terstruktur yang siap dikonsumsi oleh ELK/Splunk/CloudWatch.
- **Customizable**: Tambahkan field dinamis ke log Anda menggunakan `StructuredLogCustomizer`.

## Instalasi

Tambahkan dependency berikut ke `pom.xml` Anda:

```xml
<dependency>
    <groupId>io.github.yahyahouse</groupId>
    <artifactId>common-logger</artifactId>
    <version>1.0.6</version>
</dependency>
```

Jika Anda menggunakan Gradle:

```kotlin
implementation("io.github.yahyahouse:common-logger:1.0.6")
```

## Cara Penggunaan

### 1. Aktifkan Logging pada Method atau Class
Cukup tambahkan anotasi `@Loggable` pada service atau controller Anda.

```java
import com.yahya.commonlogger.Loggable;
import org.springframework.stereotype.Service;

@Service
public class MyService {

    @Loggable
    public String processData(String input) {
        // Log JSON otomatis akan dicetak saat method selesai
        return "Processed: " + input;
    }
}
```

### 2. Konfigurasi (Optional)
Anda dapat menyesuaikan perilaku logger melalui `application.properties` atau `application.yml`:

```properties
# Custom Header untuk Correlation ID (Default: X-Correlation-Id)
common.logger.correlation-id-header=X-Trace-Id

# Key MDC untuk Correlation ID (Default: correlationId)
common.logger.correlation-id-mdc-key=traceId

# Identifier API untuk log (Default: N/A)
common.logger.api-id=MyAwesomeAPI

# Log Level untuk eksekusi sukses (Default: INFO)
common.logger.log-level=DEBUG

# HTTP Status Code Default untuk log (Default: 200/500)
common.logger.success-http-status-code=200
common.logger.error-http-status-code=400
```

### 3. Kustomisasi Log (StructuredLogCustomizer)
Jika Anda ingin menambahkan field tambahan (seperti `userId` atau `tenantId`) ke setiap log secara otomatis:

```java
import com.yahya.commonlogger.StructuredLogCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyLogConfig {

    @Bean
    public StructuredLogCustomizer customFields() {
        return (payload, joinPoint, result, duration, success, failure) -> {
            payload.put("custom_field", "my_value");
            if (!success) {
                payload.put("alert_level", "HIGH");
            }
        };
    }
}
```

### 4. Logging Manual (StructuredLogger)
Untuk skenario di mana Anda ingin melakukan logging secara manual (bukan via AOP), gunakan bean `StructuredLogger`. Ini menyediakan fluent API yang thread-safe.

```java
import com.yahya.commonlogger.StructuredLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class ManualLoggingService {

    @Autowired
    private StructuredLogger logger;

    public void doSomethingManual(String msisdn) {
        long startTime = System.currentTimeMillis();
        try {
            // Logika bisnis Anda
            String transactionId = "TX-123";
            
            logger.newLog()
                    .withTransactionId(transactionId)
                    .withApiId("MY_MANUAL_API")
                    .withRequest(Map.of("msisdn", msisdn))
                    .onSuccess("Success Result", System.currentTimeMillis() - startTime);
                    
        } catch (Exception e) {
            logger.newLog()
                    .withApiId("MY_MANUAL_API")
                    .withHttpStatusCode(500)
                    .onFailure(e, System.currentTimeMillis() - startTime);
        }
    }
}
```

## Format Log Output
Output log berupa JSON satu baris yang memudahkan parsing oleh log aggregator:

```json
{
  "logLevel": "info",
  "apiId": "MyAwesomeAPI",
  "httpStatusCode": 200,
  "logMessage": "MyService-processData Completed",
  "logPoint": "MyService-processData-End",
  "logTimestamp": "2026-03-17T15:00:00.000+07:00",
  "processTime": 45,
  "transactionId": "a1b2c3d4e5f6g7h8"
}
```

Jika terjadi error, field tambahan akan muncul:
```json
{
  ...
  "logLevel": "error",
  "httpStatusCode": 400,
  "error": "Bad Request",
  "logException": "java.lang.RuntimeException: Something went wrong..."
}
```

## Requirements
- Java 17+
- Spring Boot 3.x

## Lisensi
Distributed under the Apache License, Version 2.0. See `LICENSE` for more information.
