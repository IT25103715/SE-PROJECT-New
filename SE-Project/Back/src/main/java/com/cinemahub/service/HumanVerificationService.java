package com.cinemahub.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Image CAPTCHA shown right before a payment is submitted (see BookingController#create
 * and fragments/human-check.html). Self-contained - the image is drawn on the server,
 * so no external CAPTCHA service or API key is involved.
 *
 * Flow, all state kept in the server-side HttpSession:
 * <ol>
 *   <li>{@link #newChallenge} - every render of a payment screen picks a fresh random
 *       code. Only the image of it goes to the browser, never the text.</li>
 *   <li>{@link #renderImage} - GET /bookings/captcha draws the current code as a
 *       distorted PNG (rotated letters, noise lines and dots). "New code" asks for a
 *       fresh challenge and image.</li>
 *   <li>{@link #verify} - the payment POST must carry the typed code (field "humanToken").
 *       Case-insensitive. The code is removed from the session whether it matched or not,
 *       so one solved CAPTCHA can't be replayed for many payments and a wrong guess always
 *       needs a new image. Codes also expire after {@link #CODE_TTL_MS}.</li>
 * </ol>
 */
@Service
public class HumanVerificationService {

    static final String SESSION_CODE_KEY = "CHECKOUT_CAPTCHA_CODE";
    static final String SESSION_ISSUED_AT_KEY = "CHECKOUT_CAPTCHA_ISSUED_AT";

    /** Number of characters the customer has to type. */
    public static final int CODE_LENGTH = 5;

    /** A code is only valid for this long after it was issued. */
    static final long CODE_TTL_MS = 10 * 60 * 1000;

    /** No 0/O, 1/I/L - characters people commonly mix up are left out. */
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private static final int WIDTH = 180;
    private static final int HEIGHT = 60;

    private final SecureRandom random = new SecureRandom();

    /**
     * Starts a new CAPTCHA for a freshly rendered payment screen - replaces any earlier code.
     */
    public void newChallenge(HttpSession session) {
        char[] code = new char[CODE_LENGTH];
        for (int i = 0; i < code.length; i++) {
            code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        session.setAttribute(SESSION_CODE_KEY, new String(code));
        session.setAttribute(SESSION_ISSUED_AT_KEY, System.currentTimeMillis());
    }

    /**
     * PNG image of the session's current code. If there is no code yet (or it has
     * expired), a new one is issued first so the image is never blank.
     */
    public byte[] renderImage(HttpSession session) {
        if (currentCode(session) == null) {
            newChallenge(session);
        }
        String code = (String) session.getAttribute(SESSION_CODE_KEY);
        return drawPng(code);
    }

    /**
     * True only if the typed answer matches the session's current, unexpired code.
     * Single-use: the code is cleared either way.
     */
    public boolean verify(HttpSession session, String answer) {
        String expected = currentCode(session);
        session.removeAttribute(SESSION_CODE_KEY);
        session.removeAttribute(SESSION_ISSUED_AT_KEY);
        if (expected == null || answer == null) {
            return false;
        }
        String typed = answer.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                typed.getBytes(StandardCharsets.UTF_8));
    }

    private String currentCode(HttpSession session) {
        Object code = session.getAttribute(SESSION_CODE_KEY);
        Object issuedAt = session.getAttribute(SESSION_ISSUED_AT_KEY);
        if (!(code instanceof String c) || !(issuedAt instanceof Long t)
                || System.currentTimeMillis() - t > CODE_TTL_MS) {
            return null;
        }
        return c;
    }

    private byte[] drawPng(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Light, slightly uneven background.
            g.setPaint(new GradientPaint(0, 0, new Color(236, 238, 242),
                    WIDTH, HEIGHT, new Color(214, 219, 228)));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            // Background noise lines.
            for (int i = 0; i < 6; i++) {
                g.setColor(randomColor(120, 190));
                g.setStroke(new BasicStroke(1f + random.nextFloat()));
                g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                        random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }

            // The characters - each with its own font, size, tilt and vertical offset.
            String[] fonts = {Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED};
            int slot = (WIDTH - 20) / code.length();
            for (int i = 0; i < code.length(); i++) {
                Font font = new Font(fonts[random.nextInt(fonts.length)], Font.BOLD, 30 + random.nextInt(8));
                g.setFont(font);
                g.setColor(randomColor(20, 90));
                double x = 12 + i * slot + random.nextInt(6);
                double y = HEIGHT / 2.0 + 11 + random.nextInt(9) - 4;
                AffineTransform original = g.getTransform();
                g.rotate(Math.toRadians(random.nextInt(51) - 25), x + 10, y - 10);
                g.drawString(String.valueOf(code.charAt(i)), (float) x, (float) y);
                g.setTransform(original);
            }

            // Two curves through the text so it can't be read by simple OCR.
            for (int i = 0; i < 2; i++) {
                g.setColor(randomColor(40, 110));
                g.setStroke(new BasicStroke(1.8f));
                g.draw(new CubicCurve2D.Float(
                        0, random.nextInt(HEIGHT),
                        WIDTH / 3f, random.nextInt(HEIGHT),
                        2 * WIDTH / 3f, random.nextInt(HEIGHT),
                        WIDTH, random.nextInt(HEIGHT)));
            }

            // Speckle noise.
            for (int i = 0; i < 220; i++) {
                image.setRGB(random.nextInt(WIDTH), random.nextInt(HEIGHT), randomColor(60, 200).getRGB());
            }
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not render CAPTCHA image", ex);
        }
    }

    private Color randomColor(int min, int max) {
        int range = max - min;
        return new Color(min + random.nextInt(range), min + random.nextInt(range), min + random.nextInt(range));
    }
}
