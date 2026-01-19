# common-logger

Spring Boot friendly logger library that provides correlation id handling and an AOP-based `@Loggable` annotation for consistent, structured method logging.

## Fitur
- Auto-configuration (`CommonLoggerAutoConfiguration`) terdaftar otomatis lewat Spring Boot 3 `AutoConfiguration.imports`.
- Filter `CorrelationIdFilter` memastikan setiap request HTTP memiliki correlation id yang disimpan di MDC dan dikembalikan di header response.
- `@Loggable` + `LoggingAspect` menghasilkan log JSON terstruktur dengan pola seperti contoh: `{"logLevel":"info","apiId":"SendNotification","httpStatusCode":200,...}`.

## Cara pakai
1. Tambahkan dependency ke project Spring Boot Anda (Maven):
   ```xml
   <dependency>
     <groupId>com.yahya</groupId>
     <artifactId>common-logger</artifactId>
     <version>1.0.0</version>
   </dependency>
   ```

2. Annotasi class atau method dengan `@Loggable` untuk mengaktifkan logging:
   ```java
   import com.yahya.commonlogger.Loggable;

   @Service
   public class ExampleService {
       @Loggable
       public String hello(String name) {
           return "Hello " + name;
       }
   }
   ```

3. Opsi konfigurasi (application.yml):
   ```yaml
   common:
     logger:
       correlation-id-header: X-Correlation-Id            # header yang dibaca/ditulis
       correlation-id-mdc-key: correlationId              # key MDC utama (dipakai juga sebagai default transactionId)
       transaction-id-mdc-key: correlationId              # key MDC untuk transactionId (opsional)
       internal-transaction-id-mdc-key: correlationId     # key MDC untuk internalTransactionId (opsional)
       api-id: SendNotification                           # identifier API/use case
       log-level: INFO                                    # level log untuk eksekusi sukses (@Loggable)
       success-http-status-code: 200                      # status sukses
       error-http-status-code: 500                        # status gagal
   ```
   Jika memakai `application.properties`, ekuivalennya:
   ```properties
   common.logger.correlation-id-header=X-Correlation-Id
   common.logger.correlation-id-mdc-key=correlationId
   common.logger.transaction-id-mdc-key=correlationId
   common.logger.internal-transaction-id-mdc-key=correlationId
   common.logger.api-id=SendNotification
   common.logger.log-level=INFO
   common.logger.success-http-status-code=200
   common.logger.error-http-status-code=500
   ```

   Atau konfigurasi langsung di kode (tanpa file config) dengan mendeklarasikan bean `CommonLoggerProperties`:
   ```java
   import com.yahya.commonlogger.CommonLoggerProperties;
   import org.springframework.context.annotation.Bean;
   import org.springframework.context.annotation.Configuration;

   @Configuration
   public class LoggerConfig {
       @Bean
       CommonLoggerProperties commonLoggerProperties() {
           CommonLoggerProperties props = new CommonLoggerProperties();
           props.setCorrelationIdHeader("X-Trace-Id");
           props.setCorrelationIdMdcKey("traceId");
           props.setApiId("MyApi");
           props.setSuccessHttpStatusCode(200);
           props.setErrorHttpStatusCode(500);
           return props;
       }
   }
   ```

Library ini ditujukan sebagai pondasi; Anda dapat menambahkan formatter, appender, atau sink log lain di atasnya sesuai kebutuhan.

## Langkah cepat (Spring Boot)
1) Tambahkan dependency di `pom.xml`:
```xml
<dependency>
  <groupId>com.yahya</groupId>
  <artifactId>common-logger</artifactId>
  <version>1.0.0</version>
</dependency>
```
2) Konfigurasi `application.properties` hanya untuk menentukan level log sukses yang ingin ditampilkan (log ERROR selalu keluar):
```properties
common.logger.log-level=INFO        # pilih INFO/DEBUG/TRACE; exception selalu di ERROR
# jika diset INFO, log sukses pada level DEBUG tidak tercetak.
# jika diset DEBUG, aktifkan juga logger paketnya:
logging.level.com.yahya.commonlogger=DEBUG
```
3) Tandai method/class yang ingin dilog dengan `@Loggable`:
```java
import com.yahya.commonlogger.Loggable;

@Service
public class ExampleService {
    @Loggable
    public String send(String payload) {
        return "ok";
    }
}
```
4) Jalankan aplikasi. Setiap pemanggilan method `@Loggable` akan mencetak JSON ke stdout/stderr dengan field: `logLevel`, `apiId`, `httpStatusCode`, `transactionId`, `internalTransactionId`, `logMessage`, `logPoint`, `logTimestamp`, `processTime` (error menambah `error` + `logException`).

### Alternatif: set log-level langsung di kode (tanpa file config)
```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.Map;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DemoApplication.class);
        app.setDefaultProperties(Map.of(
            "common.logger.log-level", "INFO"  // atau DEBUG/TRACE
        ));
        app.run(args);
    }
}
```
Setelah itu, cukup anotasi method dengan `@Loggable` seperti biasa.

## Menggunakan log-nya
- Payload dikirim langsung ke stdout/stderr sebagai JSON tanpa prefix logger.
- Correlation id otomatis disiapkan oleh `CorrelationIdFilter` (jika starter web ada). Key `transactionId` dan `internalTransactionId` diambil dari MDC dengan key yang dapat dikonfigurasi (default: `correlationId`). Header request dibaca dari `correlation-id-header`; jika tidak ada, nilai baru di-generate dan dikembalikan di response.
- Anda dapat menambah/override field dinamis per call lewat `StructuredLogCustomizer`.
- Agar log terlihat, pastikan level logger `com.yahya.commonlogger` diaktifkan minimal pada level yang Anda set di `log-level`.
- Build library ini dengan Maven: `mvn clean package` (menghasilkan JAR biasa, bukan fat jar).

## Customizer contoh
```java
import com.yahya.commonlogger.StructuredLogCustomizer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

@Configuration
class LoggingCustomizerConfig {
    @Bean
    StructuredLogCustomizer userLogCustomizer() {
        return (Map<String, Object> payload, ProceedingJoinPoint jp, Object result, long duration, boolean success, Throwable failure) -> {
            payload.put("tenantId", resolveTenant());
            payload.put("durationBucket", duration > 500 ? "SLOW" : "FAST");
        };
    }

    private String resolveTenant() {
        return "default-tenant";
    }
}
```

## Format log terstruktur
`LoggingAspect` mengeluarkan log berbentuk JSON sederhana seperti:
```json
{
  "logLevel": "info",
  "apiId": "SendNotification",
  "httpStatusCode": 200,
  "internalTransactionId": "8edcc9b4-10c1-4dfa-bd69-dab673801039",
  "logMessage": "SendNotification-sendnotification Completed",
  "logPoint": "SendNotification-sendnotification-End",
  "logTimestamp": "2026-01-07T15:03:33.961+07:00",
  "processTime": 16,
  "transactionId": "pb19b977b85f011728001196723166aSae400000000"
}
```
`logLevel` mengikuti konfigurasi; field error otomatis ditambahkan saat terjadi exception termasuk `logException` (stack trace ter-escape). `transactionId`/`internalTransactionId` diambil dari MDC (fallback: `correlationId`). Field tambahan bisa ditambahkan via `StructuredLogCustomizer`.
