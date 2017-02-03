package ca.sheridancollege.chatapp.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;

import ca.sheridancollege.chatapp.common.Message;
import ca.sheridancollege.chatapp.common.Message.MessageContext;

/**
 * ChatClient is the entry point into the console client. It maintains the socket connections made to the server.
 * 
 * @author danielnewton
 */
public class ChatClient {

	protected enum ClientState{
		SET_NAME, LOGGEDIN, CLOSE, CHANGE_NAME
	}
	
	private ClientState state = ClientState.SET_NAME;
	
	final int PORT = 60000;
	final String HOST = "localhost";
	final int SERVER_TIMEOUT = 20000;
	
	private BufferedReader in = null;
	private PrintWriter out = null;
	private Socket s = null;
	private long lastConnectionCheck = System.currentTimeMillis();
	/*
	 * I used a BufferedReader here instead of a scanner because when I used a scanner it would
	 * prevent the client from closing until it had received input at scan.nextLine().
	 * This was noticeable when the server terminated and the lastConnectionCheck exceeded SERVER_TIMEOUT.
	 */
	private BufferedReader scan = new BufferedReader(new InputStreamReader(System.in));
	
	//for gui support
	private boolean enableGUISupport = false;
	private String[] getNames = null;
	private ArrayList<String> printedMessages;
	
	/**
	 * Starts the client communications with the server. 
	 * throws ConnectException when contact to server fails.
	 */
	protected void clientStart(boolean enableGUISupport) {
		state = ClientState.SET_NAME;
		printedMessages = new ArrayList<String>();
		this.enableGUISupport = enableGUISupport;
		try {
			s = new Socket(HOST, PORT);
			s.setSoTimeout(SERVER_TIMEOUT);
			out = new PrintWriter(s.getOutputStream());
			in = new BufferedReader(new InputStreamReader(s.getInputStream()));
			
			//receive
			new Thread(new Runnable(){
				@Override
				public void run() {
					try{
						while(true){
								try{
									if(state == ClientState.CLOSE ) return;
									
									String incomingStr = in.readLine();
									Message incoming = Message.createFromString(incomingStr);
									
									if(incoming == null) {
										updateConnectionTimeout();
										if(state != ClientState.CLOSE){
											Thread.sleep(1000);
										}
										continue;
									}
									Message outgoing = null;
									
									switch(incoming.getContext()){
									case CONNECTION_CHECK:
										lastConnectionCheck = System.currentTimeMillis();
										outgoing = new Message(MessageContext.CONNECTION_CHECK, out);
										outgoing.send();
										break;
									case CLIENT_NAME:
										state = ClientState.LOGGEDIN;
										break;
									case SEND_BROADCAST:
										printMessage(incoming);
										break;
									case GET_CLIENTS_ALL:
									case GET_CLIENTS_OTHER:
										if(enableGUISupport){
											updateGetNames(incoming);
										}else {
											printMessage(incoming, true);
										}
										break;
									case SEND_ADDRESSED:
										printMessage(incoming, true);
										break;
									default:
										//ignore
									}
								} catch(SocketTimeoutException e){
									updateConnectionTimeout();
								}
							}
					}catch(Exception e){
						e.printStackTrace();
					} finally {
						close(); //close socket and streams
					}
				}
			}).start();
			
			//console input handler
			new Thread(new Runnable(){
				@Override
				public void run() {
					try{
						new Message(MessageContext.CONNECTION_OPEN, out).send();
						while(true){
							if(state == ClientState.CLOSE ) return;
							if(enableGUISupport) return; //this code is unnecessary if we're using the gui
						
							if(!scan.ready()){ //don't read from console unless something has been typed
								Thread.sleep(100);
								continue;
							}
							
							String input = scan.readLine();
							input = input.trim();
							if(input.isEmpty()) continue; //ignore empty strings

							Message outgoing = null;

							if(input.equals("QUIT")){
								state = ClientState.CLOSE;
								outgoing = new Message(MessageContext.CONNECTION_CLOSE, out);
								outgoing.send();
								continue;
							}
							if(input.equals("CHANGENAME")){ //request to change name
								System.out.println("Enter new name:");
								state = ClientState.CHANGE_NAME;
								continue;
							}
							if(input.equals("GETNAMES")){ //get all names of users
								sendGetNamesMessage(true);
								continue;
							}

							switch(state){
							case CHANGE_NAME:
								state = ClientState.LOGGEDIN;
							case SET_NAME:
								sendSetNameMessage(input);
								break;
							case LOGGEDIN:
								
								String[] chunks = input.split(":");
								if(chunks[0].equals("TO") && chunks.length > 2){ //Private Message
									StringBuilder content = new StringBuilder();
									for(int i = 2; i < chunks.length; i++){
										content.append(chunks[i]);
									}
									outgoing = sendPrivateMessage(content.toString(), chunks[1]);
								}else if(chunks[0].equals("ALL")){ //broadcast
									input = input.substring("ALL:".length(), input.length()).trim(); //remote "ALL:" from message
									sendBroadcast(input);
								}else{
									sendBroadcast(input); //default behavior is broadcast
								}
								
								break;
							default:
									//ignore input
							}
						}
					}catch(Exception e){
						e.printStackTrace();
					}
				}
			}).start();
			
		} catch (ConnectException ce){
			printMessage("Failed to connect to server!");
			state = ClientState.CLOSE;
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Main method. Program will terminate on ConnectException.
	 */
	public static void main(String[] args){
		new ChatClient().clientStart(false);
	}
	
	/**
	 * Close fields that need to be closed.
	 * Ensure state is CLOSE.
	 */
	private void close(){
		try{
			state = ClientState.CLOSE;
			if(scan != null) scan.close();
			scan = null;
			if(out != null) out.close();
			out = null;
			if(in != null) in.close();
			in = null;
			if(s != null) s.close();
			s = null;
		}catch(IOException io){
			io.printStackTrace();
		}
	}
	
	/**
	 * prints string as a broadcast message
	 */
	protected void printMessage(String content){
		Message message = new Message(null, null);
		message.setSender(Message.SYSTEM_NAME);
		message.setContent(content);
		printMessage(message);
	}
	
	/**
	 * print message as a broadcast. ie: "name: message"
	 */
	private void printMessage(Message message){
		printMessage(message, false);
	}
	
	/**
	 * print message to console as broadcast, or private message if priv=true.
	 * If enableGUISupport=true then the messages are queued to be returned by getMessages()
	 */
	private synchronized void printMessage(Message message, boolean priv){
		String author = message.getSender();
		String content = message.getContent();
		if(priv){
			author += " (PRIVATE)";
		}
		String output = author + ": " + content;
		if(enableGUISupport){
			printedMessages.add(output);
		}else{
			System.out.println(author + ": " + content);
		}
	}
	
	/**
	 * Sends a message to the server to return the names of all connected clients.
	 * If all=true then the client will also receive their own name in the list.
	 */
	private void sendGetNamesMessage(boolean all){
		Message outgoing = null;
		if(all){
			outgoing = new Message(MessageContext.GET_CLIENTS_ALL, out);
		} else{
			outgoing = new Message(MessageContext.GET_CLIENTS_OTHER, out);
		}
		outgoing.send();
	}
	
	/**
	 * Takes the incoming message on a GET_CLIENTS message and extracts the individual names and stores them in the getNames array.
	 */
	private boolean updateGetNames(Message incoming){
		boolean updated = false;
		String[] tempGetNames = incoming.getContent().split("\n");
		if(tempGetNames.length > 2 && tempGetNames[1].equals(Message.GETNAMES_HEADER)){
			updated = true;
			getNames = new String[tempGetNames.length - 2];
			for(int i = 2; i < tempGetNames.length; i++){
				getNames[i-2] = tempGetNames[i];
			}
		} else {
			getNames = new String[0];
		}

		return updated;
	}
	
	/**
	 * Sends an addressed (private) message which requires a message and the destination client name.
	 */
	public Message sendPrivateMessage(String message, String addressee){
		Message outgoing = new Message(MessageContext.SEND_ADDRESSED, out);
		outgoing.setAddressee(addressee.trim());
		outgoing.setContent(message.toString().trim());
		outgoing.send();
		outgoing.setSender("TO: " + addressee); //add TO: tag and print to screen so that the sender can see both sides of the conversation
		printMessage(outgoing, true);
		return outgoing;
	}
	
	/**
	 * Sends a broadcast message to all clients
	 */
	protected Message sendBroadcast(String message){
		Message outgoing = new Message(MessageContext.SEND_BROADCAST, out);
		outgoing.setContent(message.trim());
		outgoing.send();
		return outgoing;
	}
	
	/**
	 * Sends a request to set the clients name.
	 */
	protected void sendSetNameMessage(String name){
		Message outgoing = new Message(MessageContext.CLIENT_NAME, out);
		outgoing.setContent(name);
		outgoing.send();
	}
	
	/**
	 * getNames returns a copy of the getNames array which contains all the client names.
	 * This method will do nothing if enableGUISupport is not enabled.
	 */
	protected synchronized String[] getNames() throws Exception{
		String[] names = null;
		if(enableGUISupport){
			if(getNames != null && getNames.length > 0){
				names = Arrays.copyOf(getNames, getNames.length);
			}
		} else {
			throw new Exception("enableGUISupport boolean property must be enabled to use the getNames method");
		}
		sendGetNamesMessage(false);
		return names;
	}
	
	/**
	 * Sends disconnect message to the server and sets state to CLOSE
	 */
	protected void shutdown(){
		Message outgoing = new Message(MessageContext.CONNECTION_CLOSE, out);
		outgoing.send();
		state = ClientState.CLOSE;
	}
	
	/**
	 * Returns a copy of all the messages that are to be printed in the window
	 * This method will do nothing if enableGUISupport is not enabled.
	 */
	protected synchronized String[] getMessages(){
		String[] messages = null;
		messages = printedMessages.toArray(new String[0]);
		printedMessages.clear();
		return messages;
	}
	
	/**
	 * Marks the connection to be closed if it has exceeded the timeout.
	 */
	private void updateConnectionTimeout(){
		long timeOut = System.currentTimeMillis() - lastConnectionCheck;
		if(timeOut > SERVER_TIMEOUT){
			state = ClientState.CLOSE;
			String msg = "Connection to the server has timed out and will be disconnected. Goodbye.";
			printMessage(msg);
		} else if (timeOut > SERVER_TIMEOUT * 0.5){
			String msg = "Lost connection to Server! Connection will time out in: " + Math.round(((SERVER_TIMEOUT - timeOut) * 0.001)) + " seconds...";
			printMessage(msg);
		}
	}
	
	protected ClientState getState(){
		return this.state;
	}
	protected void setState(ClientState state){
		this.state = state;
	}
}
