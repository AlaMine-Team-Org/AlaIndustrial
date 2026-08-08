package dev.alaindustrial.visual;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * The only class in this package that touches the filesystem — everything else is pure arithmetic on
 * {@link PixelGrid}, which is what keeps the toolkit unit-testable without a client or a screenshot.
 */
public final class PngIo {

    private PngIo() {
    }

    /** Decodes a PNG into a grid. Fails loudly: an unreadable screenshot is a broken gate, not a warning. */
    public static PixelGrid read(Path file) {
        if (!Files.exists(file)) {
            throw new UncheckedIOException(new IOException("no such image: " + file.toAbsolutePath()));
        }
        BufferedImage image;
        try {
            image = ImageIO.read(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file.toAbsolutePath(), e);
        }
        if (image == null) {
            throw new UncheckedIOException(new IOException(
                    "no image decoder accepted " + file.toAbsolutePath() + " — the file exists but is not a "
                            + "readable PNG (truncated capture?)"));
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
        return new PixelGrid(width, height, argb);
    }

    /** Writes a grid as PNG, creating parent directories. */
    public static void write(PixelGrid grid, Path file) {
        BufferedImage image = new BufferedImage(grid.width(), grid.height(), BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, grid.width(), grid.height(), grid.argb(), 0, grid.width());
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!ImageIO.write(image, "png", file.toFile())) {
                throw new IOException("no PNG writer available");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not write " + file.toAbsolutePath(), e);
        }
    }

    /**
     * Writes {@code <name>_diff.png} next to a failing frame and returns its path.
     *
     * <p>Called from the failure path of a pixel gate, so it must never mask the real assertion: if the
     * diff cannot be written the caller still reports the original failure. That is why this returns
     * {@code null} instead of throwing.
     */
    public static Path writeDiffBeside(Path frame, PixelGrid diff) {
        try {
            String fileName = frame.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String stem = dot < 0 ? fileName : fileName.substring(0, dot);
            Path target = frame.resolveSibling(stem + "_diff.png");
            write(diff, target);
            return target;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
