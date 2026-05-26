package com.mrpup.emotion_overlays_twitch.client.tvAddon;

import com.mojang.blaze3d.platform.NativeImage;
import com.mrpup.emotion_overlays.client.animate.AnimatedTexture;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GifDecoder {

    public static AnimatedTexture decode(byte[] gifBytes) throws IOException {
        ImageInputStream stream = ImageIO.createImageInputStream(
                new ByteArrayInputStream(gifBytes));

        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) throw new IOException("No GIF reader found");

        ImageReader reader = readers.next();
        reader.setInput(stream);

        int frameCount = reader.getNumImages(true);
        List<AnimatedTexture.Frame> frames = new ArrayList<>();

        BufferedImage firstFrame = reader.read(0);
        int width = firstFrame.getWidth();
        int height = firstFrame.getHeight();

        BufferedImage canvas = new BufferedImage(
                width, height, BufferedImage.TYPE_INT_ARGB);
        BufferedImage previousCanvas = new BufferedImage(
                width, height, BufferedImage.TYPE_INT_ARGB);

        for (int i = 0; i < frameCount; i++) {
            BufferedImage frame = reader.read(i);


            IIOMetadata metadata = reader.getImageMetadata(i);
            IIOMetadataNode root = (IIOMetadataNode)
                    metadata.getAsTree("javax_imageio_gif_image_1.0");


            IIOMetadataNode imgDesc = (IIOMetadataNode)
                    root.getElementsByTagName("ImageDescriptor").item(0);
            int offsetX = Integer.parseInt(imgDesc.getAttribute("imageLeftPosition"));
            int offsetY = Integer.parseInt(imgDesc.getAttribute("imageTopPosition"));


            String disposalMethod = "doNotDispose";
            IIOMetadataNode gce = (IIOMetadataNode)
                    root.getElementsByTagName("GraphicControlExtension").item(0);
            if (gce != null) {
                disposalMethod = gce.getAttribute("disposalMethod");
            }

            if ("restoreToPrevious".equals(disposalMethod)) {
                java.awt.Graphics2D g2 = previousCanvas.createGraphics();
                g2.drawImage(canvas, 0, 0, null);
                g2.dispose();
            }

            java.awt.Graphics2D g = canvas.createGraphics();
            g.drawImage(frame, offsetX, offsetY, null);
            g.dispose();

            NativeImage nativeImage = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = canvas.getRGB(x, y);
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g2 = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    nativeImage.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g2 << 8) | r);
                }
            }

            int delayMs = getFrameDelay(reader, i);
            frames.add(new AnimatedTexture.Frame(nativeImage, delayMs));

            java.awt.Graphics2D gc = canvas.createGraphics();
            switch (disposalMethod) {
                case "restoreToBackgroundColor" -> {
                    gc.setComposite(java.awt.AlphaComposite.Clear);
                    gc.fillRect(offsetX, offsetY, frame.getWidth(), frame.getHeight());
                }
                case "restoreToPrevious" -> {
                    gc.drawImage(previousCanvas, 0, 0, null);
                }
            }
            gc.dispose();
        }

        reader.dispose();
        return new AnimatedTexture(frames);
    }

    private static int getFrameDelay(ImageReader reader, int frameIndex) {
        try {
            IIOMetadata metadata = reader.getImageMetadata(frameIndex);
            IIOMetadataNode root = (IIOMetadataNode)
                    metadata.getAsTree("javax_imageio_gif_image_1.0");

            IIOMetadataNode gce = (IIOMetadataNode)
                    root.getElementsByTagName("GraphicControlExtension").item(0);

            if (gce != null) {
                int delay = Integer.parseInt(
                        gce.getAttribute("delayTime")) * 10;
                return Math.max(delay, 20);
            }
        } catch (Exception ignored) {}
        return 100;
    }
}
