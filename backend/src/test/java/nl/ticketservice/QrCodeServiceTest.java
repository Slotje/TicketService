package nl.ticketservice;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import nl.ticketservice.service.QrCodeService;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class QrCodeServiceTest {

    @Inject
    QrCodeService qrCodeService;

    @Test
    void testGenerateQrCodeImage() {
        byte[] image = qrCodeService.generateQrCodeImage("test-data");

        assertNotNull(image);
        assertTrue(image.length > 0);
    }

    @Test
    void testVerifyQrCodeWithValidSignature() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                "default-dev-secret-change-in-production".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        String hmac = HexFormat.of().formatHex(
                mac.doFinal("test-data".getBytes(StandardCharsets.UTF_8)));
        String signedData = "test-data|" + hmac;

        assertTrue(qrCodeService.verifyQrCode(signedData));
    }

    @Test
    void testVerifyQrCodeWithInvalidSignature() {
        assertFalse(qrCodeService.verifyQrCode("data|wrongsignature"));
    }

    @Test
    void testVerifyQrCodeWithNoSeparator() {
        assertFalse(qrCodeService.verifyQrCode("noseparator"));
    }

    @Test
    void testExtractTicketDataWithSeparator() {
        assertEquals("data", qrCodeService.extractTicketData("data|signature"));
    }

    @Test
    void testExtractTicketDataWithNoSeparator() {
        assertEquals("noseparator", qrCodeService.extractTicketData("noseparator"));
    }

    @Test
    void testExtractTicketDataWithValidSignature() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                "default-dev-secret-change-in-production".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        String hmac = HexFormat.of().formatHex(
                mac.doFinal("test-data".getBytes(StandardCharsets.UTF_8)));
        String signedData = "test-data|" + hmac;

        assertEquals("test-data", qrCodeService.extractTicketData(signedData));
    }
}
