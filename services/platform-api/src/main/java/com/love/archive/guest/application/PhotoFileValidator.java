package com.love.archive.guest.application;

import com.love.archive.common.web.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class PhotoFileValidator {

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MIN_DIMENSION = 64;
    private static final int JPEG = 0;
    private static final int PNG = 1;
    private static final int WEBP = 2;

    public ImageInfo validate(byte[] content) {
        if (content == null || content.length == 0) {
            throw invalid("PHOTO_CONTENT_INVALID", "文件内容不能为空");
        }
        if (content.length > MAX_BYTES) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "PHOTO_TOO_LARGE",
                    "单张照片不能超过 10 MiB");
        }
        int format = sniff(content);
        if (format < 0) {
            throw invalid("PHOTO_FORMAT_UNSUPPORTED", "仅支持 JPEG、PNG、WebP 图片");
        }
        int[] size = decodeSize(format, content);
        if (size == null || size[0] < MIN_DIMENSION || size[1] < MIN_DIMENSION) {
            throw invalid("PHOTO_FORMAT_UNSUPPORTED", "图片无法识别或尺寸过小（至少 64×64）");
        }
        String contentType = switch (format) {
            case JPEG -> "image/jpeg";
            case PNG -> "image/png";
            default -> "image/webp";
        };
        return new ImageInfo(contentType, size[0], size[1]);
    }

    private static int sniff(byte[] content) {
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (content.length >= 8
                && (content[0] & 0xFF) == 0x89
                && content[1] == 'P' && content[2] == 'N' && content[3] == 'G'
                && (content[4] & 0xFF) == 0x0D
                && (content[5] & 0xFF) == 0x0A
                && (content[6] & 0xFF) == 0x1A
                && (content[7] & 0xFF) == 0x0A) {
            return PNG;
        }
        if (content.length >= 12
                && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return WEBP;
        }
        return -1;
    }

    private static int[] decodeSize(int format, byte[] content) {
        if (format == JPEG || format == PNG) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
                return image == null
                        ? null
                        : new int[] {image.getWidth(), image.getHeight()};
            } catch (IOException exception) {
                return null;
            }
        }
        return decodeWebpSize(content);
    }

    private static int[] decodeWebpSize(byte[] content) {
        if (content.length < 30) {
            return null;
        }
        if (content[12] == 'V' && content[13] == 'P' && content[14] == '8'
                && content[15] == 'L' && (content[20] & 0xFF) == 0x2F) {
            int bits = (content[21] & 0xFF)
                    | ((content[22] & 0xFF) << 8)
                    | ((content[23] & 0xFF) << 16)
                    | ((content[24] & 0xFF) << 24);
            return new int[] {(bits & 0x3FFF) + 1, ((bits >>> 14) & 0x3FFF) + 1};
        }
        if (content[12] == 'V' && content[13] == 'P' && content[14] == '8'
                && content[15] == 'X') {
            int width = (content[24] & 0xFF)
                    | ((content[25] & 0xFF) << 8)
                    | ((content[26] & 0xFF) << 16);
            int height = (content[27] & 0xFF)
                    | ((content[28] & 0xFF) << 8)
                    | ((content[29] & 0xFF) << 16);
            return new int[] {width + 1, height + 1};
        }
        if (content[12] == 'V' && content[13] == 'P' && content[14] == '8'
                && content[15] == ' ') {
            if ((content[23] & 0xFF) != 0x9D
                    || (content[24] & 0xFF) != 0x01
                    || (content[25] & 0xFF) != 0x2A) {
                return null;
            }
            int width = (content[26] & 0xFF) | ((content[27] & 0xFF) << 8);
            int height = (content[28] & 0xFF) | ((content[29] & 0xFF) << 8);
            return new int[] {width & 0x3FFF, height & 0x3FFF};
        }
        return null;
    }

    private static ApiException invalid(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public record ImageInfo(String contentType, int width, int height) {
    }
}
