package ru.synesis.media.player;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBoxBuilder;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.StackPaneBuilder;
import javafx.scene.layout.VBox;
import javafx.scene.layout.VBoxBuilder;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

/**
 * Very simple implementation of a player to watch motion jpeg (multipart/x-mixed-replace) stream
 * 
 * This class is a UI implementation.
 * 
 * @see StreamThread, VideoSource
 * 
 * @author Arseny Kovalchuk<br/><a href="http://www.linkedin.com/in/arsenykovalchuk/">LinkedIn&reg; Profile</a>
 */
public class Player extends Application {
    
    private static final String TITLE = "Motion JPEG (multipart/x-mixed-replace) Player - %1$s";
    
    private StreamThread streamThread;
    private String currentUrl;
    private boolean statsEnabled = true;
    
    // UI Controls
    private Label labelUrl;
    private Label labelFrames;
    private Label labelEFrames;
    private Label labelTimeUp;
    private Label labelTotalBytes;
    private Label labelBandwidth;
    private Stage ownerStage;
    private Scene scene;
    private MenuBar menuBar;
    private ImageView imageView;
 
    @Override
    public void start(final Stage stage) throws Exception {
        this.ownerStage = stage;
        stage.setTitle(String.format(TITLE, ""));
        stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent arg0) {
                stopStreamAndWait();
            }
            
        });
        // Build menu
        menuBar = new MenuBar();
        Menu menuFile = new Menu("File");
        MenuItem menuOpenUrl = new MenuItem("Open URL");
        menuFile.getItems().add(menuOpenUrl);
        Menu menuOptions = new Menu("Options");
        CheckMenuItem checkOptionsMenuItem = new CheckMenuItem("Show statistics");
        checkOptionsMenuItem.setSelected(statsEnabled);

        menuOptions.getItems().add(checkOptionsMenuItem);
        Menu menuHelp = new Menu("Help");
        MenuItem menuHelpShow = new MenuItem("Show help");
       
        menuHelp.getItems().add(menuHelpShow);
        menuBar.getMenus().addAll(menuFile, menuOptions, menuHelp);
        
        // Main scene
        imageView = new ImageView();
        imageView.getStyleClass().add("imageView");
        BorderPane borderPaneImView = new BorderPane();
        borderPaneImView.setCenter(imageView);
        
        labelUrl = new Label("URL: ");
        labelFrames = new Label("Frames: ");
        labelEFrames = new Label("Error frames: ");
        labelTimeUp = new Label("Time up: ");
        labelTotalBytes = new Label("Total: ");
        labelBandwidth = new Label("Bandwidth: ");
        
        final VBox infoPane = VBoxBuilder.create().children(labelUrl, labelFrames, labelEFrames, labelTimeUp, labelTotalBytes, labelBandwidth).build();
        infoPane.getStyleClass().add("infoPane");
        infoPane.setVisible(statsEnabled);

        BorderPane borderPaneStatsView = new BorderPane();
        borderPaneStatsView.setTop(infoPane);

        StackPane stack = StackPaneBuilder.create().children(borderPaneImView, borderPaneStatsView).alignment(Pos.TOP_LEFT).build();
        
        VBox mainBox = VBoxBuilder.create().children(menuBar, stack).build();
        mainBox.getStyleClass().add("vbox");
        scene = new Scene(mainBox, 640, 480);
        
        borderPaneImView.setPrefSize(mainBox.getWidth(), mainBox.getHeight());
        borderPaneImView.prefHeightProperty().bind(scene.heightProperty());
        borderPaneImView.prefWidthProperty().bind(scene.widthProperty());
        
        scene.setFill(Color.BLACK);
        scene.getStylesheets().add("style.css");
        ownerStage.setScene(scene);
        
        scene.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent arg0) {
                if (streamThread != null) {
                    streamThread.interrupt();
                    streamThread = null;
                } else {
                    startStream();
                }
            }
        });
        
        checkOptionsMenuItem.selectedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> ov, Boolean oldVal, Boolean newVal) {
                statsEnabled = newVal;
                infoPane.setVisible(statsEnabled);
            }
        });
        
        menuHelpShow.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent arg0) {
                // TODO : display help.html in dialog scene
            }
        });
        
        menuOpenUrl.setOnAction(new EventHandler<ActionEvent>() {

            @Override
            public void handle(ActionEvent arg0) {
                
                final TextField urlTextField = new TextField();
                urlTextField.setMinWidth(300);
                urlTextField.setMinHeight(25);
                Button ok = new Button("Ok");
                ok.setMinWidth(70);
                Button cancel = new Button("Cancel");
                cancel.setMinWidth(70);
                
                
                final Stage dialogStage = new Stage();
                dialogStage.initOwner(ownerStage);
                dialogStage.initStyle(StageStyle.UTILITY);
                dialogStage.initModality(Modality.WINDOW_MODAL);
                dialogStage.setScene(new Scene(HBoxBuilder.create()
                    .children(new Text("Enter URL:"), urlTextField, ok, cancel)
                    .alignment(Pos.CENTER).padding(new Insets(10))
                    .minHeight(40)
                    .prefHeight(50)
                    .spacing(7)
                    .build()));
                
                cancel.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent arg0) {
                        dialogStage.close();
                    }
                });
                ok.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent arg0) {
                        currentUrl = urlTextField.getText();
                        ownerStage.setTitle(String.format(TITLE, currentUrl));
                        labelUrl.setText("URL:\t\t\t" + currentUrl);
                        Image img = new Image(getClass().getResourceAsStream("/loading_2.gif"));
                        imageView.setImage(img);
                        dialogStage.close();
                        Player.this.startStream();
                    }
                });

                dialogStage.show();
            }
            
        });
        
        stage.show();
        
    }
    
    private void stopStreamAndWait() {
        if (streamThread != null) {
            System.out.println("Interrupt Stream Thread");
            streamThread.interrupt();
            while (streamThread.isAlive()) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            streamThread = null;
        }
    }

    private void startStream() {
        
        stopStreamAndWait();
        
        streamThread = new StreamThread(currentUrl, ownerStage, scene, menuBar, imageView);
        streamThread.setOnErrorHandler(new StreamEventHandler<StreamEvent>() {
            @Override
            public void handle(final StreamEvent event) {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        Button ok = new Button("Ok");
                        ok.setMinWidth(70);
                        final Stage dialogStage = new Stage();
                        dialogStage.initOwner(ownerStage);
                        dialogStage.initStyle(StageStyle.UTILITY);
                        dialogStage.initModality(Modality.WINDOW_MODAL);
                        dialogStage.setScene(new Scene(VBoxBuilder.create()
                            .children(new Label(event.getMessage()), ok)
                            .alignment(Pos.CENTER).padding(new Insets(10))
                            .spacing(7)
                            .build()));
                        ok.setOnAction(new EventHandler<ActionEvent>() {
                            @Override
                            public void handle(ActionEvent arg0) {
                                
                                if (streamThread != null && streamThread.isAlive() && !streamThread.isInterrupted()) {
                                    streamThread.interrupt();
                                }
                                dialogStage.close();
                            }
                        });
                        dialogStage.show();
                    }
                });
            }
        });
        streamThread.setOnStatsHandler(new StreamEventHandler<StreamEvent>() {
            @Override
            public void handle(StreamEvent event) {
                final StreamThread t = event.getStreamThread();
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        labelFrames.setText(t.getFrameCount());
                        labelEFrames.setText(t.getErrorFrameCount());
                        labelTimeUp.setText(t.getTimeUp());
                        labelTotalBytes.setText(t.getBytesRead());
                        labelBandwidth.setText(t.getBandwidth());
                    }
                });
            }
        });
        streamThread.start();
    }

    public static void main(String[] args) {
        Application.launch(args);
    }
}
