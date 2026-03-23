package nl.ticketservice.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.enterprise.context.ApplicationScoped;
import nl.ticketservice.exception.TicketServiceException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.imageio.ImageIO;

@ApplicationScoped
public class QrCodeService {

    private static final int QR_SIZE = 300;
    private static final String HMAC_ALGO = "HmacSHA256";

    @ConfigProperty(name = "ticket.qr.secret")
    String qrSecret;

    public byte[] generateQrCodeImage(String data) {
        try {
            String signedData = data + "|" + generateHmac(data);
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(signedData, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (WriterException | IOException e) {
            throw new TicketServiceException("Fout bij het genereren van QR code", 500);
        }
    }

    public boolean verifyQrCode(String scannedData) {
        int separatorIndex = scannedData.lastIndexOf('|');
        if (separatorIndex < 0) {
            return false;
        }
        String ticketData = scannedData.substring(0, separatorIndex);
        String signature = scannedData.substring(separatorIndex + 1);
        return generateHmac(ticketData).equals(signature);
    }

    public String extractTicketData(String scannedData) {
        int separatorIndex = scannedData.lastIndexOf('|');
        if (separatorIndex < 0) {
            return scannedData;
        }
        return scannedData.substring(0, separatorIndex);
    }

    private String generateHmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec keySpec = new SecretKeySpec(qrSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new TicketServiceException("Fout bij het genereren van QR handtekening", 500);
        }
    }
}
