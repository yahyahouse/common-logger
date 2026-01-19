# Dokumentasi common-logger

Library Spring Boot untuk logging terstruktur dengan dukungan correlation id, anotasi AOP `@Loggable`, dan hook customizer.

## Komponen utama
- **CommonLoggerAutoConfiguration**: mendaftarkan bean `CommonLoggerProperties`, `CorrelationIdFilter`, dan `LoggingAspect` ketika kelas yang dibutuhkan tersedia.
- **CorrelationIdFilter**: memastikan setiap request HTTP memiliki correlation id (header dan MDC).
- **@Loggable + LoggingAspect**: membungkus method/class ber-anotasi dan mengeluarkan log JSON terstruktur ke stdout/stderr.
- **StructuredLogCustomizer**: hook fungsional untuk menambahkan/override field payload log per panggilan.

## Cara pakai singkat
1) Tambahkan dependency di `pom.xml`:
   ```xml
   <dependency>
     <groupId>com.yahya</groupId>
     <artifactId>common-logger</artifactId>
     <version>1.0.0</version>
   </dependency>
   ```
2) (Opsional) Set properti di `application.yml` / `application.properties` untuk menyesuaikan header, MDC key, dan level log.
3) Anotasi method/class dengan `@Loggable`.
4) Pastikan level logger `com.yahya.commonlogger` diaktifkan minimal sesuai `common.logger.log-level`.

## Referensi konfigurasi
| Properti                                   | Default             | Keterangan                                                                    |
|--------------------------------------------|---------------------|-------------------------------------------------------------------------------|
| `common.logger.correlation-id-header`      | `X-Correlation-Id`  | Header HTTP yang dibaca/ditulis filter.                                       |
| `common.logger.correlation-id-mdc-key`     | `correlationId`     | Key MDC utama untuk correlation id.                                           |
| `common.logger.transaction-id-mdc-key`     | `correlationId`     | Key MDC untuk `transactionId` (fallback ke correlation id).                   |
| `common.logger.internal-transaction-id-mdc-key` | `correlationId` | Key MDC untuk `internalTransactionId` (fallback ke correlation id).           |
| `common.logger.api-id`                     | kosong              | Identifier API/use case; jika kosong, pakai nama class target.                |
| `common.logger.log-level`                  | `INFO`              | Level log untuk eksekusi sukses (`INFO`, `DEBUG`, `TRACE`, dll).               |
| `common.logger.success-http-status-code`   | `200`               | Kode status yang dicetak untuk eksekusi sukses.                               |
| `common.logger.error-http-status-code`     | `500`               | Kode status yang dicetak untuk eksekusi gagal/exception.                      |

## Alur kerja
- Saat ada request HTTP:
  - `CorrelationIdFilter` membaca header `correlation-id-header`. Jika kosong, generate UUID baru.
  - Nilai disimpan di MDC dengan key `correlation-id-mdc-key` (juga jadi default `transactionId`/`internalTransactionId`).
  - Header yang sama dikembalikan di response.
- Saat method `@Loggable` dieksekusi:
  - `LoggingAspect` mencatat waktu mulai, mengeksekusi method, lalu membangun payload log JSON.
  - Level log sukses mengikuti konfigurasi `log-level`; jika ada exception, level otomatis `ERROR`.
  - Payload dikirim ke stdout (sukses) atau stderr (error) tanpa prefix logger.
  - `StructuredLogCustomizer` (jika ada) dipanggil untuk menambah/override field sebelum dicetak.

## Format payload log
Field default yang dicetak:
- `logLevel` (lowercase), `apiId`, `httpStatusCode`, `transactionId`, `internalTransactionId`
- `logMessage` (`<apiId>-<method> Completed/Failed`)
- `logPoint` (`<apiId>-<method>-End/Error`)
- `logTimestamp` (ISO offset), `processTime` (ms)
- Jika error: `error` (message) + `logException` (stack trace)

Contoh sukses:
```json
{
  "logLevel": "info",
  "apiId": "SendNotification",
  "httpStatusCode": 200,
  "transactionId": "corr-123",
  "internalTransactionId": "corr-123",
  "logMessage": "SendNotification-send Completed",
  "logPoint": "SendNotification-send-End",
  "logTimestamp": "2026-01-19T11:04:30.000+07:00",
  "processTime": 12
}
```

Contoh error:
```json
{
  "logLevel": "error",
  "apiId": "SendNotification",
  "httpStatusCode": 500,
  "transactionId": "corr-123",
  "internalTransactionId": "corr-123",
  "logMessage": "SendNotification-send Failed",
  "logPoint": "SendNotification-send-Error",
  "logTimestamp": "2026-01-19T11:04:30.000+07:00",
  "processTime": 5,
  "error": "boom",
  "logException": "java.lang.IllegalStateException: boom\n..."
}
```

## Customizer contoh
Tambahkan bean `StructuredLogCustomizer` untuk memasukkan field dinamis:
```java
@Configuration
class LoggingCustomizerConfig {
    @Bean
    StructuredLogCustomizer addTenant() {
        return (payload, jp, result, duration, success, failure) -> {
            payload.put("tenantId", resolveTenant());
            payload.put("durationBucket", duration > 500 ? "SLOW" : "FAST");
        };
    }

    private String resolveTenant() {
        return "default-tenant";
    }
}
```

## Tips penggunaan
- Untuk melihat log sukses di level `DEBUG/TRACE`, set `common.logger.log-level` dan aktifkan logger paket `com.yahya.commonlogger` pada level yang sama.
- Jika aplikasi tidak memakai starter web, Anda bisa mendeklarasikan `CommonLoggerProperties` saja (tanpa filter) untuk penggunaan non-HTTP.
- `transactionId` dan `internalTransactionId` diisi dari MDC; set key khusus jika Anda sudah menaruh ID sendiri di MDC sebelum memanggil `@Loggable`.
- Build/test: `mvn clean test` atau `mvn clean package` (menghasilkan JAR biasa, bukan fat JAR).
