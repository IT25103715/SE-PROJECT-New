package com.cinemahub.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Singleton pattern: Spring beans are singleton-scoped by default, so the
 * whole application shares exactly one QrCodeService instance - there is no
 * need for a hand-written private-constructor/getInstance() singleton, the
 * container already guarantees it. This bean wraps the ZXing library used
 * for Function 6 (ticket QR codes).
 */
@Service
public class QrCodeService {

    private static final int QR_SIZE = 250;

    /** A short random token that becomes the QR code's payload / the ticket's lookup key. */
    public String generateTicketToken() {
        return UUID.randomUUID().toString();
    }

    /** Renders the given text as a QR code PNG and returns the raw image bytes. */
    public byte[] generateQrCodePng(String data) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(data, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Failed to generate QR code", e);
        }
    }
}
