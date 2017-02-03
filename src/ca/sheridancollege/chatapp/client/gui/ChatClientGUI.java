package ca.sheridancollege.chatapp.client.gui;

import java.util.Optional;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 * ChatClientGUI is the entry point into the GUI controls for the chat client.
 * 
 * @author danielnewton
 *
 */
public class ChatClientGUI extends Application{
	
	private enum GUIState {
		RUNNING, POPUP
	}
	
	private GUIState state = GUIState.RUNNING;
	
	/**
	 * Performs all configurations required by the GUI to start
	 */
    @Override
    public void start(Stage primaryStage) {
    	
    	ChatClientAPI client = new ChatClientAPI(); //start client
    	
    	/*
    	 * Create GUI components
    	 */
        primaryStage.setTitle("Chat Client");
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(25, 25, 25, 25));
        
        TextArea ta = new TextArea();
        ta.setEditable(false);
        ta.setWrapText(true);
        grid.add(ta, 0, 0, 3, 1);
        
        ComboBox<String> options = new ComboBox<String>();
        options.setPrefWidth(100);
        grid.add(options, 0, 1, 1, 1);
        
        TextField textField = new TextField();
        textField.setPrefWidth(280);
        grid.add(textField, 1, 1, 1, 1);
       
        Button btn = new Button("Send");
        HBox hbBtn = new HBox(10);
        hbBtn.setAlignment(Pos.BOTTOM_RIGHT);
        hbBtn.getChildren().add(btn);
        grid.add(hbBtn, 2, 1, 1, 1);
        
        Scene scene = new Scene(grid, 500, 250);
        primaryStage.setScene(scene);
        primaryStage.show();
        
        /*
         * Create event handlers
         */
        btn.setOnAction(new EventHandler<ActionEvent>() {
        	@Override
            public void handle(ActionEvent e) {
            	send(options, textField, client);
            }
        });
        
        textField.setOnKeyPressed(new EventHandler<KeyEvent>()
        {
            @Override
            public void handle(KeyEvent ke)
            {
                if (ke.getCode().equals(KeyCode.ENTER))
                {
                    send(options, textField, client);
                }
            }
        });

        primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            public void handle(WindowEvent we) {
                client.shutdownClient();
            }
        });        
        
       /*
        * Create task to poll info from the client 
        */
        Task<Integer> updateFromClient = new Task<Integer>() {
            @Override protected Integer call() throws Exception {
            	int iterations = 0;
            	while(true){
            		if (isCancelled()) {
                        updateMessage("Cancelled");
                        break;
                    }

            		Platform.runLater(new Runnable() {
            			@Override public void run() {
            				if(client.isConnected()){
            					//update combo box
            					String value = options.getValue();
            					String[] tempOptions = client.getOptions();
            					if(tempOptions != null && tempOptions.length > 0){
            						boolean resetValue = false; //reset value when more options are available after logging in
            						if(options.getItems().size() == 1 && tempOptions.length > 1){
            							resetValue = true;
            						}
            						options.setItems(FXCollections.observableArrayList(tempOptions));
            						if(resetValue){
            							value = options.getItems().get(0);
            						}
            					}

            					if(options.getItems() != null && !options.getItems().isEmpty() && options.getItems().contains(value)){
            						options.setValue(value);
            					} else if(options.getItems().size() > 0){
            						options.setValue(options.getItems().get(0));
            					}

            					//update text area
            					String[] updates = client.getConsoleUpdates();
            					if(updates != null && updates.length > 0){
            						for(int i = 0; i < updates.length; i++){
            							ta.appendText("\n" + updates[0]);
            						}
            					}
            				} else if(state == GUIState.RUNNING){ //not connected
            					state = GUIState.POPUP;
            					String alertMsg = "The client could not connect to the server."
            							+ "\nSelect OK to attempt to reconnect."
            							+ "\nSelect Cancel to close the application.";
            					Alert alert = new Alert(AlertType.CONFIRMATION, alertMsg);
            					Optional<ButtonType> result = alert.showAndWait();
            					if (result.isPresent() && result.get() == ButtonType.OK) {
            						client.startConnection();
            						state = GUIState.RUNNING;
            					} else {
            						Platform.exit();
            					}
            				}
            			}
                    });
            		
            		try {
                        Thread.sleep(100);
                    } catch (InterruptedException interrupted) {
                        if (isCancelled()) {
                            updateMessage("Cancelled");
                            break;
                        }
                    }
            		iterations++;
            	}
            	return iterations;
            }
        };
        
        Thread updateThread = new Thread(updateFromClient);
        updateThread.setDaemon(true);
        updateThread.start();
        
    }
    
    /**
     * Sends the message with the selected option
     * 
     * @param selection The selected option
     * @param textField The textfield component
     * @param client The ChatClient
     */
    public void send(ComboBox<String> options, TextField textField, ChatClientAPI client){
    	String selection = options.getValue();
    	String text = textField.getText();
    	if(selection == null || selection.isEmpty()) return;
    	if(text == null || text.trim().isEmpty()) return;
    	
        switch(selection){
        case ChatClientAPI.OPTION_BROADCAST:
        	client.broadcast(text);
        	break;
        case ChatClientAPI.OPTION_NAME:
        	client.setName(text);
        	options.setValue(options.getItems().get(0));
        	break;
        default:
        	client.privateMessage(text, selection);
        	break;
        }
        
        textField.clear();
    }
    
    /**
     * Entry point into the GUI chat client.
     * 
     */
    public static void main(String[] args) {
        launch(args);
    }
}
