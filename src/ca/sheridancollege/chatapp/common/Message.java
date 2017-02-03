package ca.sheridancollege.chatapp.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Base64;

/**
 * Message is a container for messages that are send between the client and the server.
 * 
 * @author danielnewton
 *
 */
public class Message implements Serializable{
	
	private static final long serialVersionUID = 1964553777763815456L;

	public static final String SYSTEM_NAME = "System";
	public static final String GETNAMES_HEADER = "Currently Online:";
	
	public enum MessageContext{
		CONNECTION_OPEN, CONNECTION_CHECK, CONNECTION_CLOSE, CLIENT_NAME, SEND_BROADCAST, SEND_ADDRESSED, GET_CLIENTS_ALL, GET_CLIENTS_OTHER
	}
	
	private MessageContext context;
	private String sender;
	private String addressee;
	private String content;
	private transient PrintWriter out;
	
	public Message(MessageContext context, PrintWriter out) {
		this.context = context;
		this.out = out;
	}

	public MessageContext getContext() {
		return context;
	}

	public String getAddressee() {
		return addressee;
	}

	public String getContent() {
		return content;
	}
	public String getSender(){
		return sender;
	}
	public void setContext(MessageContext context) {
		this.context = context;
	}

	public void setAddressee(String addressee) {
		this.addressee = addressee;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public void setSender(String sender){
		this.sender = sender;
	}
	
	public void setOut(PrintWriter out){
		this.out = out;
	}
	
	/**
	 * Serializes the Message and sends it to the PrintWriter out.
	 */
	public void send(){
		if(out == null) return; //sending would be impossible if out=null
		String serializedMessage = null;
		ByteArrayOutputStream bo = null;
		ObjectOutputStream so = null;
		try { //convert Message to a string and send it via out.
			bo = new ByteArrayOutputStream();
			so = new ObjectOutputStream(bo);
			so.writeObject(this);
			so.flush();
			serializedMessage = new String(Base64.getEncoder().encodeToString(bo.toByteArray()));  
			out.println(serializedMessage);
			out.flush();
		} catch (Exception e) {
			e.printStackTrace();        
		} finally {
			try{ //close resources
				if(bo != null) bo.close();
				if(so != null) so.close();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	/**
	 * Deserializes a Message object from a String.
	 * 
	 * @param serializedMessage Message object that has been serialized to a String
	 * @return The deserialized Message object
	 */
	public static Message createFromString(String serializedMessage){
		if(serializedMessage == null || serializedMessage.isEmpty()) return null; //you can't deserialize nothing
		Message deserializedMessage = null;
		ByteArrayInputStream bi = null;
		ObjectInputStream si = null;
		try {
			byte b[] = Base64.getDecoder().decode(serializedMessage.getBytes()); 
			bi = new ByteArrayInputStream(b);
			si = new ObjectInputStream(bi);
			deserializedMessage = (Message)si.readObject();   
		} 
		catch (Exception e) {
			e.printStackTrace();        
		} finally {
			try{
				if(bi != null) bi.close();
				if(si != null) si.close();
			} catch (Exception e){
				e.printStackTrace();
			}
		}
		return deserializedMessage;
	}
}
