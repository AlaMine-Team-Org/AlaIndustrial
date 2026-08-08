package dev.alaindustrial.visual;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * L1 unit tests — reading and writing frames.
 *
 * <p>The round trip matters because every gate compares a freshly captured frame against a stored one:
 * if encoding lost a channel, two identical renders would read as different and the suite would flake.
 *
 * @implements R-VIS-01 frame I/O (MOD-362)
 * @covers R-VIS-01
 */
class PngIoTest {

    @Test
    void roundTripPreservesEveryPixelExactly(@TempDir Path dir) throws IOException {
        PixelGrid original = PixelGrid.filled(9, 7, 0xFF102030)
                .withRegionPainted(new Region(2, 2, 3, 3), 0xFFF800F8)
                .withRegionPainted(new Region(0, 6, 9, 1), 0xFFFFFFFF);

        Path file = dir.resolve("frame.png");
        PngIo.write(original, file);

        assertTrue(Files.size(file) > 0, "a written frame must not be empty");
        PixelGrid reloaded = PngIo.read(file);

        assertEquals(original.width(), reloaded.width());
        assertEquals(original.height(), reloaded.height());
        assertArrayEquals(original.argb(), reloaded.argb(),
                "a lossy round trip would make identical renders compare as different");
    }

    @Test
    void readingAMissingFileFailsLoudly(@TempDir Path dir) {
        // A gate that silently treats "no screenshot" as "no difference" is the exact failure mode
        // MOD-362 exists to remove.
        assertThrows(UncheckedIOException.class, () -> PngIo.read(dir.resolve("never-written.png")));
    }

    @Test
    void readingSomethingThatIsNotAnImageFailsLoudly(@TempDir Path dir) throws IOException {
        Path bogus = dir.resolve("truncated.png");
        Files.writeString(bogus, "this is not a PNG");

        assertThrows(UncheckedIOException.class, () -> PngIo.read(bogus));
    }

    @Test
    void writeCreatesMissingParentDirectories(@TempDir Path dir) {
        Path nested = dir.resolve("run").resolve("diffs").resolve("frame.png");
        PngIo.write(PixelGrid.filled(2, 2, 0xFF000000), nested);

        assertTrue(Files.exists(nested), "the diff writer must not fail just because its folder is new");
    }

    @Test
    void diffIsWrittenNextToTheFrameItExplains(@TempDir Path dir) {
        Path frame = dir.resolve("0042_gui_macerator_empty.png");
        PngIo.write(PixelGrid.filled(4, 4, 0xFF000000), frame);

        Path diff = PngIo.writeDiffBeside(frame, PixelGrid.filled(4, 4, 0xFFFF2020));

        assertNotNull(diff, "the diff path is what the failure message points the reviewer at");
        assertEquals("0042_gui_macerator_empty_diff.png", diff.getFileName().toString());
        assertEquals(frame.getParent(), diff.getParent(), "the diff must sit beside its frame");
        assertTrue(Files.exists(diff));
    }
}
