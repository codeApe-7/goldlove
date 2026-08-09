package com.love.archive.guest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.love.archive.common.web.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PhotoFileValidatorTest {

    private final PhotoFileValidator validator = new PhotoFileValidator();

    @Test
    void validatesJpegPngAndWebpDimensions() throws Exception {
        PhotoFileValidator.ImageInfo jpeg = validator.validate(jpeg(64, 64));
        assertThat(jpeg.contentType()).isEqualTo("image/jpeg");
        assertThat(jpeg.width()).isEqualTo(64);
        assertThat(jpeg.height()).isEqualTo(64);

        PhotoFileValidator.ImageInfo png = validator.validate(png(80, 100));
        assertThat(png.contentType()).isEqualTo("image/png");
        assertThat(png.width()).isEqualTo(80);
        assertThat(png.height()).isEqualTo(100);

        PhotoFileValidator.ImageInfo webp = validator.validate(webpVp8l(64, 64));
        assertThat(webp.contentType()).isEqualTo("image/webp");
        assertThat(webp.width()).isEqualTo(64);
        assertThat(webp.height()).isEqualTo(64);
    }

    @Test
    void rejectsEmptyOversizedAndFakeFiles() {
        assertCode(() -> validator.validate(new byte[0]), "PHOTO_CONTENT_INVALID");
        assertCode(() -> validator.validate(null), "PHOTO_CONTENT_INVALID");
        assertCode(() -> validator.validate(new byte[10 * 1024 * 1024 + 1]),
                "PHOTO_TOO_LARGE");
        assertCode(() -> validator.validate("not-an-image".getBytes()),
                "PHOTO_FORMAT_UNSUPPORTED");
        assertCode(() -> validator.validate(new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5}),
                "PHOTO_FORMAT_UNSUPPORTED");
    }

    @Test
    void rejectsImagesBelowMinimumDimension() throws Exception {
        assertCode(() -> validator.validate(jpeg(32, 32)), "PHOTO_FORMAT_UNSUPPORTED");
        assertCode(() -> validator.validate(png(63, 100)), "PHOTO_FORMAT_UNSUPPORTED");
        assertCode(() -> validator.validate(webpVp8l(1, 1)), "PHOTO_FORMAT_UNSUPPORTED");
    }

    private static byte[] jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] webpVp8l(int width, int height) {
        byte[] data = new byte[30];
        data[0] = 'R'; data[1] = 'I'; data[2] = 'F'; data[3] = 'F';
        data[8] = 'W'; data[9] = 'E'; data[10] = 'B'; data[11] = 'P';
        data[12] = 'V'; data[13] = 'P'; data[14] = '8'; data[15] = 'L';
        data[20] = 0x2F;
        int bits = (width - 1) | ((height - 1) << 14);
        data[21] = (byte) (bits & 0xFF);
        data[22] = (byte) ((bits >>> 8) & 0xFF);
        data[23] = (byte) ((bits >>> 16) & 0xFF);
        data[24] = (byte) ((bits >>> 24) & 0xFF);
        return data;
    }

    private static void assertCode(Operation operation, String expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    @FunctionalInterface
    private interface Operation {
        void run() throws Exception;
    }
}
