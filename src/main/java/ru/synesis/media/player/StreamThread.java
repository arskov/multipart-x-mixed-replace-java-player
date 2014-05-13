package ru.synesis.media.player;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.MenuBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import javax.imageio.ImageIO;

/**
 * <p>Thread for the Player which gets Images from motion jpeg (multipart/x-mixed-replace) stream through VideoSource 
 * and updates UI with those images.</p>
 * <p>Also contains methods to get statistical information about a stream like: frames count, bytes count, bandwidth, etc.
 * 
 * @author Arseny Kovalchuk<br/><a href="http://www.linkedin.com/in/arsenykovalchuk/">LinkedIn&reg; Profile</a>
 *
 */
public class StreamThread extends Thread {

    private Stage stage;
    private Scene scene;
    private ImageView imageView;
    private MenuBar menuBar;
    private String urlString;
    private String username;
    private String password;
    private StreamEventHandler<StreamEvent> onErrorEventHandler;
    private StreamEventHandler<StreamEvent> onStatsEventHandler;

    private int errorFrameCount;
    private int frameCount;
    private Calendar startDate;
    private long bytesRead;

    public StreamThread(String urlString, Stage stage, Scene scene, MenuBar menuBar, ImageView imageView) {
        super(urlString);
        this.urlString = urlString;
        this.stage = stage;
        this.scene = scene;
        this.menuBar = menuBar;
        this.imageView = imageView;
    }

    @Override
    public void run() {
        VideoSource src = null;
        try {
            parseURL();
            // if parseURL has detected user:password in the URL, we initiate a VideoSource with
            // appropriate username and password
            if (username != null) {
                src = new VideoSource(urlString, username, password);
            } else {
                src = new VideoSource(urlString);
            }
            // initiate real connection
            src.connect();
            synchronized (this) {
                startDate = Calendar.getInstance();
            }
            // get first image to calculate dimensions for stage
            // for some reason, jfx Image didn'r return real image height for me ^)
            // so, I decided to use BufferedImage to get image dimensions
            if (src.iterator().hasNext()) {
                byte[] imgBytes = src.iterator().next();
                try (InputStream is = new ByteArrayInputStream(imgBytes)) {
                    final BufferedImage image = ImageIO.read(is);
                    Platform.runLater(new Runnable() {
                        public void run() {
                            double h = menuBar.getHeight() + image.getHeight();
                            double w = image.getWidth();
                            stage.setWidth(w);
                            stage.setHeight(h);
                        }
                    });
                }
            }
            // main loop, which updates ImageView with JPEG frames
            try {

                for (byte[] img : src) {
                    if (isInterrupted())
                        break;
                    try (InputStream is = new ByteArrayInputStream(img)) {
                        final Image image = new Image(is);
                        synchronized (this) {
                            frameCount++;
                            bytesRead += img.length;
                        }
                        if (!image.isError()) {
                            Platform.runLater(new Runnable() {
                                public void run() {
                                    imageView.setImage(image);
                                }
                            });
                        } else {
                            synchronized (this) {
                                errorFrameCount++;
                            }
                        }

                    }
                    if (onStatsEventHandler != null /*&& frameCount % 15 == 0*/) {
                        onStatsEventHandler.handle(new StreamEvent() {
                            @Override
                            public String getMessage() {
                                return "";
                            }
                            @Override
                            public StreamThread getStreamThread() {
                                return StreamThread.this;
                            }

                        });
                    }
                }
            } catch (Exception e) {
                if (e.getCause() instanceof InterruptedException) {
                    // do nothing, just stop execution.
                } else {
                    throw e;
                }
            }

            //src.disconnect(); do it in finally

        } catch (Exception e) {
            e.printStackTrace();
            String stackTrace = null;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); PrintStream ps = new PrintStream(baos)) {
                e.printStackTrace(ps);
                stackTrace = baos.toString();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            final String message = stackTrace;
            onErrorEventHandler.handle(new StreamEvent() {
                @Override
                public String getMessage() {
                    return message;
                }
                @Override
                public StreamThread getStreamThread() {
                    return StreamThread.this;
                }

            });
        } finally {
            if (src != null)
                src.disconnect();
        }
    }

    public void parseURL() throws MalformedURLException {
        URL url = new URL(urlString);
        String[] parts = url.getAuthority().split("@");
        if (parts.length > 1) {
            String[] up = parts[0].split(":");
            if (up.length > 0) {
                username = up[0];
                password = up[1];
            }
        }
    }

    public void setOnErrorHandler(StreamEventHandler<StreamEvent> eventHandler) {
        this.onErrorEventHandler = eventHandler;
    }

    public void setOnStatsHandler(StreamEventHandler<StreamEvent> eventHandler) {
        this.onStatsEventHandler = eventHandler;
    }

    public String getTimeUp() {
        synchronized (this) {
            Calendar now = Calendar.getInstance();

            int days = now.get(Calendar.DAY_OF_YEAR) - startDate.get(Calendar.DAY_OF_YEAR);
            int hours = now.get(Calendar.HOUR_OF_DAY) - startDate.get(Calendar.HOUR_OF_DAY);
            int minutes = now.get(Calendar.MINUTE) - startDate.get(Calendar.MINUTE);
            int seconds = now.get(Calendar.SECOND)/* - startDate.get(Calendar.SECOND)*/;

            if (days <= 0) {
                return String.format("Time up:\t\t%1$d h %2$d m %3$d s", hours, minutes, seconds);
            } else {
                return String.format("Time up:\t\t%1$d d %2$d h %3$d m %4$d s", days, hours, minutes, seconds);
            }

        }
    }

    /**
     * Returns a number of images that couldn't get parsed by Java FX Image class
     * 
     * Note! Integer overflow
     * @return
     */
    public String getErrorFrameCount() {
        synchronized (this) {
            return String.format("Error frames:\t%d", errorFrameCount);
        }
    }

    /**
     * Returns total number of images got from camera
     * 
     * Note! Integer overflow
     * @return
     */
    public String getFrameCount() {
        synchronized (this) {
            return String.format("Frames:\t\t%d", frameCount);
        }
    }

    /**
     * Returns total bytes of images (only images, for count convenience)
     * 
     * Note! It's basic approximation which doesn't include protocol header's length. Only bytes of jpegs.
     * @return
     */
    public String getBytesRead() {
        synchronized (this) {
            if (bytesRead > 1024 * 1024 * 1024) {
                return String.format("Bytes read:\t%.2f Gb", ((double) bytesRead) / (1024 * 1024 * 1024));
            } else if (bytesRead > 1024 * 1024) {
                return String.format("Bytes read:\t%.2f Mb", ((double) bytesRead) / (1024 * 1024));
            } else {
                return String.format("Bytes read:\t%.2f Kb", ((double) bytesRead) / 1024);
            }
        }
    }

    /**
     * Returns average bandwidth.
     * 
     * Note! It's basic approximation which doesn't include protocol header's length. Only bytes of jpegs.
     * @return
     */
    public String getBandwidth() {
        synchronized (this) {
            Calendar now = Calendar.getInstance();
            long diff = (now.getTimeInMillis() - startDate.getTimeInMillis()) / 1000; /* seconds */
            return String.format("Bandwidth:\t%d Kbps", (bytesRead * 8 /* bits */) / 1000 / (diff == 0 ? 1 : diff));
        }
    }

}
