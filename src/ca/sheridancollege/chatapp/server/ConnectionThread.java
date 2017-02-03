package ca.sheridancollege.chatapp.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;

import ca.sheridancollege.chatapp.common.Message;
import ca.sheridancollege.chatapp.common.Message.MessageContext;

/**
 * ConnectionThread manages the connection to the client from the server.
 * 
 * @author danielnewton
 *
 */
public class ConnectionThread extends Thread {

	private enum ConnState{
		SET_NAME, LISTENING, DISCONNECT
	}

	private ConnState state = ConnState.SET_NAME;

	private Socket socket = null;
	private BufferedReader in = null;
	private PrintWriter out = null;
	private String clientName = null;
	private long lastConnectionCheck = System.currentTimeMillis();
	private final int CLIENT_TIMEOUT = 30000;
	private final int CONN_CHECK_WAIT_TIME = 5000;
	

	public ConnectionThread(Socket socket) {
		super("ConnectionThread");
		this.socket = socket;
	}

	/**
	 * Starts the threads to receive messages from the client and ensure the connection with the client is alive.
	 */
	public void run() {
		try {
			socket.setSoTimeout(CLIENT_TIMEOUT);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(socket.getOutputStream());

			//recieve messages
			new Thread(new Runnable(){
				@Override
				public void run() {
					try{
						while (true) {
							try{
								if(state == ConnState.DISCONNECT) return;
								String incomingStr = in.readLine();
								Message incoming = Message.createFromString(incomingStr);

								if(incoming == null) {
									updateConnectionTimeout();
									if(state != ConnState.DISCONNECT){
										Thread.sleep(CONN_CHECK_WAIT_TIME);
									}
									continue;
								}
								Message outgoing = null;
								
								switch(incoming.getContext()){
								case CONNECTION_CHECK: //connection is alive
									lastConnectionCheck = System.currentTimeMillis();
									break;
								case CONNECTION_OPEN: //client has just connected
									outgoing = new Message(MessageContext.SEND_ADDRESSED, out);
									outgoing.setSender(Message.SYSTEM_NAME);
									outgoing.setContent("Please enter a user name:");
									outgoing.send();
									break;
								case CONNECTION_CLOSE: //client has announced that they have disconnected
									state = ConnState.DISCONNECT;
									break;
								case GET_CLIENTS_ALL: //get all client names and send GETNAMES String
								case GET_CLIENTS_OTHER: //ignores this clients name
									String[] clientNames = ChatServer.getClientNames();
									StringBuilder content = new StringBuilder("\n" + Message.GETNAMES_HEADER);
									for(String c : clientNames){
										if(incoming.getContext() == MessageContext.GET_CLIENTS_OTHER && c.equals(clientName)) continue;
										content.append("\n");
										content.append(c);
									}
									outgoing = new Message(incoming.getContext(), out);
									outgoing.setSender(Message.SYSTEM_NAME);
									outgoing.setContent(content.toString());
									outgoing.send();
									break;
								case CLIENT_NAME: //attempt to set the client name
									String name = incoming.getContent();
									
									if(clientName == null){ //this is an initial name
										if(validateName(name) && ChatServer.registerClient(name, out)){ //name is valid
											clientName = name;
											//let client know that they now have a name
											outgoing = new Message(MessageContext.CLIENT_NAME, out);
											outgoing.send();
											//send welcome message
											outgoing = new Message(MessageContext.SEND_BROADCAST, null);
											outgoing.setSender(Message.SYSTEM_NAME);
											outgoing.setContent("Welcome " + clientName + "!");
											ChatServer.sendMessage(outgoing);
											state = ConnState.LISTENING;
										} else { //name is invalid. request new name.
											//send error message
											outgoing = new Message(MessageContext.SEND_ADDRESSED, out);
											outgoing.setSender(Message.SYSTEM_NAME);
											outgoing.setContent("The requested name is invalid or taken, try again");
											outgoing.send();
											//prompt for name entry
											outgoing.setContent("Please Enter a name:");
											outgoing.send();
										}
									} else { //this is  name change, mostly the same as above but doesnt require new name.
										if(validateName(name) && ChatServer.changeClientName(clientName, name)){
											//send success message
											outgoing = new Message(MessageContext.SEND_BROADCAST, null);
											outgoing.setSender(Message.SYSTEM_NAME);
											outgoing.setContent(clientName + " has changed their name to: " + name);
											clientName = name;
											ChatServer.sendMessage(outgoing);
										} else {
											//send error messaqge
											outgoing = new Message(MessageContext.SEND_ADDRESSED, out);
											outgoing.setSender(Message.SYSTEM_NAME);
											outgoing.setContent("The requested name is invalid or taken. Your name is still: " + clientName);
											outgoing.send();
										}
									}
									break;
								case SEND_BROADCAST: //sends message to all clients
									outgoing = new Message(MessageContext.SEND_BROADCAST, null);
									outgoing.setSender(clientName);
									outgoing.setContent(incoming.getContent());
									ChatServer.sendMessage(outgoing);
									break;
								case SEND_ADDRESSED: //sends message to the addressee
									outgoing = new Message(MessageContext.SEND_ADDRESSED, null);
									outgoing.setSender(clientName);
									outgoing.setAddressee(incoming.getAddressee());
									outgoing.setContent(incoming.getContent());
									ChatServer.sendMessage(outgoing);
									break;
								default:
									//ignore unhandled message
								}
							} catch (SocketTimeoutException stoe){
								updateConnectionTimeout();
							} 
						}
					}catch(Exception e){
						e.printStackTrace();
					} finally {
						close(); //close resources
					}
				}
			}).start();

			//send message to client to ensure the connection is still alive, limited by CONN_CHECK_WAIT_TIME
			new Thread(new Runnable(){
				@Override
				public void run() {
					try{
						lastConnectionCheck = System.currentTimeMillis();
						while (true) {
							if(state == ConnState.DISCONNECT) return;
							Message outgoing = new Message(MessageContext.CONNECTION_CHECK, out);
							outgoing.send();
							Thread.sleep(CONN_CHECK_WAIT_TIME);
						}
					}catch(Exception e){
						e.printStackTrace();
					} 
				}
			}).start();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}	

	/**
	 * Ensures name is not invalid
	 * @param name Candidate clientName
	 * @return Valid
	 */
	private boolean validateName(String name){
		if(name == null) return false;
		if(name.length() == 0) return false;
		return true;
	}

	/**
	 * Closes resources and ensures state=DISCONNECT
	 */
	public void close(){
		try{
			state = ConnState.DISCONNECT;
			if(in != null) in.close();
			in = null;
			if(out != null) out.close();
			out = null;
			if(out != null) socket.close();
			socket = null;
			ChatServer.closeConnection(clientName);
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	/**
	 * Marks the connection to be closed if it has exceeded the timeout.
	 */
	private void updateConnectionTimeout(){
		if(System.currentTimeMillis() - lastConnectionCheck > CLIENT_TIMEOUT){
			ChatServer.log("Client: " + clientName + " has timedout. They will be disconnected");
			state = ConnState.DISCONNECT;
		}
	}

}
