# common-logger

Spring Boot friendly logger library that provides correlation ID handling and an AOP-based `@Loggable` annotation for consistent, structured method logging.

## Fitur
- **Auto-configuration**: Terdaftar otomatis via Spring Boot `AutoConfiguration.imports`.
- **Correlation ID**: `CorrelationIdFilter` memastikan setiap request HTTP memiliki ID unik yang disimpan di MDC dan dikembalikan di header response.
- **Structured Logging**: `@Loggable` + `LoggingAspect` menghasilkan log JSON terstruktur yang siap dikonsumsi oleh ELK/Splunk/CloudWatch.
- **Customizable**: Tambahkan field dinamis ke log Anda menggunakan `StructuredLogCustomizer`.
- **Sensitive Data Masking**: Redact field sensitif secara otomatis via `SensitiveDataMasker` bean atau konfigurasi `sensitive-fields`.
- **Error Classification**: Field `errorType` otomatis terisi `CLIENT_ERROR` (4xx) atau `SERVER_ERROR` (5xx).

## Instalasi

Tambahkan dependency berikut ke `pom.xml` Anda:

```xml
<dependency>
    <groupId>io.github.yahyahouse</groupId>
    <artifactId>common-logger</artifactId>
    <version>1.0.8</version>
</dependency>
```

Jika Anda menggunakan Gradle:

```kotlin
implementation("io.github.yahyahouse:common-logger:1.0.8")
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
# Identifier API untuk log (Default: nama class)
common.logger.api-id=MyAwesomeAPI

# Log level untuk eksekusi sukses (Default: INFO)
common.logger.log-level=DEBUG

# HTTP status code default (Default: 200 / 500)
common.logger.success-http-status-code=200
common.logger.error-http-status-code=500

# Custom header untuk Correlation ID (Default: X-Correlation-Id)
common.logger.correlation-id-header=X-Trace-Id

# Key MDC untuk Correlation ID (Default: correlationId)
common.logger.correlation-id-mdc-key=traceId

# Key MDC untuk Transaction ID (Default: sama dengan correlation-id-mdc-key)
common.logger.transaction-id-mdc-key=transactionId

# Field sensitif yang akan di-redact otomatis di log output
common.logger.sensitive-fields=password,token,cardNumber,cvv
```

### 3. Kustomisasi Log (StructuredLogCustomizer)
Tambahkan field dinamis ke setiap log `@Loggable` secara otomatis:

```java
import com.yahya.commonlogger.StructuredLogCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyLogConfig {

    @Bean
    public StructuredLogCustomizer customFields() {
        return (payload, joinPoint, result, duration, success, failure) -> {
            payload.put("tenantId", TenantContext.get());
            if (!success) {
                payload.put("alertLevel", "HIGH");
            }
        };
    }
}
```

### 4. Sensitive Data Masking (SensitiveDataMasker)
Ada dua cara untuk menyembunyikan data sensitif dari log output:

**Via properties** (sederhana, berbasis nama field):
```properties
common.logger.sensitive-fields=password,token,cardNumber
```

**Via Spring bean** (fleksibel, untuk logika masking kustom):
```java
import com.yahya.commonlogger.SensitiveDataMasker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MaskingConfig {

    @Bean
    public SensitiveDataMasker myMasker() {
        return payload -> {
            // Mask seluruh objek request jika mengandung field sensitif
            if (payload.get("request") instanceof Map<?, ?> req) {
                req.forEach((k, v) -> {
                    if (k.toString().toLowerCase().contains("secret")) {
                        ((Map<String, Object>) payload.get("request")).put(k.toString(), "***");
                    }
                });
            }
        };
    }
}
```

Gunakan anotasi `@MaskField` untuk menandai field atau parameter sebagai sensitif:

```java
public class LoginRequest {
    private String username;

    @MaskField
    private String password;
}
```

### 5. Logging Manual (StructuredLogger)
Untuk skenario di luar AOP, gunakan bean `StructuredLogger` dengan fluent API yang thread-safe:

```java
import com.yahya.commonlogger.StructuredLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ManualLoggingService {

    @Autowired
    private StructuredLogger logger;

    public void doSomethingManual(String msisdn) {
        long start = System.currentTimeMillis();
        try {
            // Logika bisnis Anda
            logger.newLog()
                    .withTransactionId("TX-123")
                    .withApiId("MY_MANUAL_API")
                    .withRequest(Map.of("msisdn", msisdn))
                    .onSuccess("Success Result", System.currentTimeMillis() - start);

        } catch (Exception e) {
            logger.newLog()
                    .withApiId("MY_MANUAL_API")
                    .withHttpStatusCode(500)
                    .onFailure(e, System.currentTimeMillis() - start);
        }
    }
}
```

### 6. Integrasi Micrometer Tracing (traceId / spanId)
Jika aplikasi Anda menggunakan **Spring Boot 3.x + Micrometer Tracing** (dengan Brave atau OpenTelemetry), `traceId` dan `spanId` sudah otomatis tersedia di MDC. Cukup arahkan `transaction-id-mdc-key` ke key tersebut:

```properties
common.logger.transaction-id-mdc-key=traceId
```

Log output akan otomatis menyertakan `traceId` aktif dari distributed trace tanpa konfigurasi tambahan.

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
  "logLevel": "error",
  "apiId": "MyAwesomeAPI",
  "httpStatusCode": 500,
  "errorType": "SERVER_ERROR",
  "error": "Something went wrong",
  "logException": "java.lang.RuntimeException: Something went wrong\n\tat ..."
}
```

## Requirements
- Java 17+
- Spring Boot 3.x

## Lisensi
Distributed under the Apache License, Version 2.0. See `LICENSE` for more information.
