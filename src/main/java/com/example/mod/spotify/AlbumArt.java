
package com.example.mod.spotify;

import java.nio.ByteBuffer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(value=EnvType.CLIENT)
public final class AlbumArt {
    private static final int MAX_SOURCE_SIDE = 4096;
    private final int size;
    private final byte[] rgba;

    private AlbumArt(int size, byte[] rgba) {
        this.size = size;
        this.rgba = rgba;
    }


    public static AlbumArt decode(byte[] encoded, int maxSide) {
        if (encoded == null || encoded.length == 0 || encoded.length > 4 * 1024 * 1024 || maxSide < 1) return null;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(encoded));
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1
                    || image.getWidth() > MAX_SOURCE_SIDE || image.getHeight() > MAX_SOURCE_SIDE) return null;
            int side = Math.min(image.getWidth(), image.getHeight());
            int size = Math.min(side, maxSide);
            int left = (image.getWidth() - side) / 2;
            int top = (image.getHeight() - side) / 2;
            byte[] rgba = new byte[size * size * 4];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int color = image.getRGB(left + x * side / size, top + y * side / size);
                    int at = (y * size + x) * 4;
                    rgba[at] = (byte) (color >>> 16);
                    rgba[at + 1] = (byte) (color >>> 8);
                    rgba[at + 2] = (byte) color;
                    rgba[at + 3] = (byte) (color >>> 24);
                }
            }
            return new AlbumArt(size, rgba);
        } catch (IOException failure) {
            return null;
        }
    }

    static AlbumArt crop(ByteBuffer pixels, int width, int height, int maxSide) {
        int side = Math.min(width, height);
        int left = (width - side) / 2;
        int top = (height - side) / 2;
        int size = Math.min(side, maxSide);
        byte[] out = new byte[size * size * 4];
        int o = 0;
        for (int oy = 0; oy < size; ++oy) {
            int y0 = top + (int)((long)oy * (long)side / (long)size);
            int y1 = Math.max(y0 + 1, top + (int)((long)(oy + 1) * (long)side / (long)size));
            for (int ox = 0; ox < size; ++ox) {
                int x0 = left + (int)((long)ox * (long)side / (long)size);
                int x1 = Math.max(x0 + 1, left + (int)((long)(ox + 1) * (long)side / (long)size));
                long r = 0L;
                long g = 0L;
                long b = 0L;
                long a = 0L;
                for (int y = y0; y < y1; ++y) {
                    int row = (y * width + x0) * 4;
                    int x = x0;
                    while (x < x1) {
                        r += (long)(pixels.get(row) & 0xFF);
                        g += (long)(pixels.get(row + 1) & 0xFF);
                        b += (long)(pixels.get(row + 2) & 0xFF);
                        a += (long)(pixels.get(row + 3) & 0xFF);
                        ++x;
                        row += 4;
                    }
                }
                long count = (long)(y1 - y0) * (long)(x1 - x0);
                out[o++] = (byte)((r + count / 2L) / count);
                out[o++] = (byte)((g + count / 2L) / count);
                out[o++] = (byte)((b + count / 2L) / count);
                out[o++] = (byte)((a + count / 2L) / count);
            }
        }
        return new AlbumArt(size, out);
    }

    public int size() {
        return this.size;
    }

    public int byteSize() {
        return this.rgba.length;
    }

    public void copyTo(ByteBuffer target) {
        target.put(this.rgba);
    }
}
