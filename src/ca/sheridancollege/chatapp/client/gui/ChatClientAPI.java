package ca.sheridancollege.chatapp.client.gui;

import java.util.ArrayList;

import ca.sheridancollege.chatapp.client.ChatClient;

/**
 * ChatClientAPI extends ChatClient to expose it's protected methods to allow a GUI application to leverage it's methods.
 * 
 * @author danielnewton
 *
 */
public class ChatClientAPI extends ChatClient{

	public final static String OPTION_BROADCAST = "ALL";
	public final static String OPTION_NAME = "NEW NAME";

	/**
	 * If the client fails to connect to the server it will print an error message and die.
	 */
	public ChatClientAPI(){
		startConnection();
	}
	
	public void startConnection(){
		clientStart(true);
	}

	public void setName(String name){
		sendSetNameMessage(name);
	}

	public void broadcast(String message){
		sendBroadcast(message);
	}

	public void privateMessage(String message, String addressee){
		sendPrivateMessage(message, addressee);
	}

	/**
	 * Returns the options for sending a message.
	 * This includes the names of all clients on the server for private message.
	 * This also includes choosing to broadcast to all clients and changing the clients name.
	 * 
	 * The order will always be:
	 * 1. BROADCAST
	 * 2. SET NAME
	 * 3. Client Names
	 * 
	 * @return Array of Strings representing messaging options
	 */
	public String[] getOptions() {
		ArrayList<String> options = new ArrayList<String>();
		if(getState() == ClientState.LOGGEDIN){
			options.add(OPTION_BROADCAST);
			options.add(OPTION_NAME);
			try{
				String[] names = getNames();
				if(names != null && names.length > 0){
					for(int i = 0; i < names.length; i++){
						options.add(names[i]);
					}
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		} else if (getState() == ClientState.SET_NAME) {
			options.add(OPTION_NAME);
		}
		return options.toArray(new String[0]);
	}

	public void shutdownClient(){
		shutdown();
	}

	/**
	 * If the client connected to the server then this method will return an array of message that should be printed to the window.
	 * If the client failed to connect to the server then an error String will be returned once.
	 * 
	 * @return Strings to print to window
	 */
	public String[] getConsoleUpdates(){
		return getMessages();
	}
	
	/**
	 * If the client is not disconnected then true is returned.
	 * 
	 * @return is connected
	 */
	public boolean isConnected(){
		return getState() != ClientState.CLOSE;
	}
}