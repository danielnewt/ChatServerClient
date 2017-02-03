package ca.sheridancollege.chatapp.server;

import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import ca.sheridancollege.chatapp.common.Message;
import ca.sheridancollege.chatapp.common.Message.MessageContext;

/**
 * ChatServer is the entrypoint to start the server.
 * The server will wait for connections until somebody connects to it.
 * After having that initial connection established the server will close itself the next time it times out waiting for a connection.
 * 
 * @author danielnewton
 *
 */
public class ChatServer {

	private enum ServerState{
		RUNNING_PENDING, RUNNING, CLOSE
	}
	
	private static ServerState state = ServerState.RUNNING_PENDING;
	
	private final static DateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
	private final int PORT = 60000;
	private ServerSocket serverSocket = null;
	private static ArrayList<String> clientNames = new ArrayList<String>();
	private static ArrayList<PrintWriter> clientOutputs = new ArrayList<PrintWriter>();
	private final int CONN_TIMEOUT = 10000;
	private String lastStatusUpdate = "";
	
	/**
	 * Starts the server.
	 * The server will wait for connections until it is set to CLOSE.
	 */
	private void serverStart() {
		try {
			serverSocket = new ServerSocket(PORT);
			serverSocket.setSoTimeout(CONN_TIMEOUT);

			log("Server started");
			while (true) {
				try {
					if(state == ServerState.CLOSE) return;
					Socket clientSocket = serverSocket.accept();
					ConnectionThread clientConnection = new ConnectionThread(clientSocket);
					clientConnection.start();
				} catch (SocketTimeoutException ste) {
					if(state == ServerState.RUNNING && getNumConnections() == 0){
						state = ServerState.CLOSE;
					}
					String status = "STATUS UPDATE: " + state.name() + " --- " + getNumConnections() + " connections";
					if(!status.equals(lastStatusUpdate)){ //only log status update on change
						lastStatusUpdate = status;
						log("STATUS UPDATE: " + state.name() + " --- " + getNumConnections() + " connections");
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			close();
		}
	}
	
	/**
	 * Close the server socket resource.
	 * Attempt to force close all running threads.
	 */
	private synchronized void close(){
		try{
			serverSocket.close();
		} catch (Exception e){
			e.printStackTrace();
		}
		log("Server Closed");
	}

	/**
	 * Entry point into the server.
	 */
	public static void main(String[] args) {
		new ChatServer().serverStart();
	}

	private synchronized static int getNumConnections(){
		return clientNames.size();
	}
	
	public synchronized static String[] getClientNames(){
		return clientNames.toArray(new String[0]);
	}
	
	/**
	 * Adds connection to the collection of clients with name as the key.
	 * @param name The client name. Must be unique.
	 * @param connection The ConnectionThread managing the connection to the client
	 * @return Success
	 */
	public synchronized static boolean registerClient(String name, PrintWriter out){
		try{
			state = ServerState.RUNNING;
			if(clientNames.contains(name)){ //name is not unique
				return false; 
			}
			clientNames.add(name);
			clientOutputs.add(out);
			log("Established connection with: " + name);
			return true;
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
	}
	
	public synchronized static void closeConnection(String name){
		if(clientNames.contains(name)){
			int i = clientNames.indexOf(name);
			clientNames.remove(i);
			clientOutputs.remove(i);
			
			Message outgoing = new Message(MessageContext.SEND_BROADCAST, null);
			outgoing.setSender(Message.SYSTEM_NAME);
			outgoing.setContent(name + " has disconnected!");
			ChatServer.sendMessage(outgoing);
			
			log(name + " has disconnected!");
		}
	}
	
	/**
	 * Reassigns the name of a client.
	 * @param oldn Old name
	 * @param newn New name
	 * @return success
	 */
	public synchronized static boolean changeClientName(String oldn, String newn){
		if(clientNames.contains(newn) || !clientNames.contains(oldn)){ //new name is not unique or old name does not exist
			return false;
		}
		int i = clientNames.indexOf(oldn);
		clientNames.set(i, newn);
		log("Client " + oldn + " has changed their name to: " + newn);
		return true;
	}
	
	/**
	 * Sends a message to all clients if Message is a broadcast.
	 * If the message is addressed then it is sent only to the addressee.
	 * @param message The Message to be sent
	 */
	public synchronized static void sendMessage(Message message){
		try{
			//HashMap<String, ConnectionThread> clients = INSTANCE.getClients();
			if(message.getContext() == MessageContext.SEND_BROADCAST){
				for(PrintWriter o : clientOutputs){
					message.setOut(o);
					message.send();
				}
			}
			if(message.getContext() == MessageContext.SEND_ADDRESSED){
				String clientName = message.getAddressee();
				if(clientNames.contains(clientName)){
					int i = clientNames.indexOf(clientName);
					message.setOut(clientOutputs.get(i));
					message.send();
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	/*
	protected HashMap<String, ConnectionThread> getClients(){
		return clients;
	}*/
	
	/**
	 * This method is used so that the console messages will have timestamps.
	 * @param message String to print to console
	 */
	public static  void log(String message){
		Date now = Calendar.getInstance().getTime();        
		String timestamp = df.format(now);
		System.out.println("[" + timestamp + "] " + message);
	}
}
