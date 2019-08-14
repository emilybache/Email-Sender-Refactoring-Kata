package codingdojo;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Date;
import java.util.Properties;
import java.util.Vector;

import javax.mail.*;
import javax.mail.internet.*;


public class Server {
	private static final String INBOX = "INBOX", POP_MAIL = "pop3",
			SMTP_MAIL = "smtp";
	private boolean debugOn = false;
	private String _smtpHost = null, _pop3Host = null, _user = null,
			_password = null, _listFile = null, _fromName = null;
	private InternetAddress[] toList = null;

	/**
	 * main() is used to start an instance of the Server
	 */
	public static void main(String args[]) throws Exception {
		// check usage
		//
		if (args.length < 6) {
			System.err.println("Usage: java Server SMTPHost POP3Host user password EmailListFile CheckPeriodFromName");
			System.exit(1);
		}

		// Assign command line arguments to meaningful variable names
		//
		String smtpHost = args[0], pop3Host = args[1], user = args[2], password = args[3], emailListFile = args[4], fromName = null;

		int checkPeriod = Integer.parseInt(args[5]);

		if (args.length > 6)
			fromName = args[6];

		// Process every "checkPeriod" minutes
		//
		Server ls = new Server();
		ls.debugOn = false;

		while (true) {
			if (ls.debugOn)
				System.out.println(new Date() + "> " + "SESSION START");
			ls._smtpHost = smtpHost;
			ls._pop3Host = pop3Host;
			ls._user = user;
			ls._password = password;
			ls._listFile = emailListFile;
			
			if (fromName != null)
				ls._fromName = fromName;
			
			// Read in email list file into java.util.Vector
			//
			Vector vList = new Vector(10);
			BufferedReader listFile = new BufferedReader(new FileReader(
					emailListFile));
			String line = null;
			while ((line = listFile.readLine()) != null) {
				vList.addElement(new InternetAddress(line));
			}
			listFile.close();
			if (ls.debugOn)
				System.out.println(new Date() + "> " + "Found " + vList.size() + " email ids in list");
			
			ls.toList = new InternetAddress[vList.size()];
			vList.copyInto(ls.toList);
			vList = null;
			
			//
			// Get individual emails and broadcast them to all email ids
			//
			
			// Get a Session object
			//
			Properties sysProperties = System.getProperties();
			Session session = Session.getDefaultInstance(sysProperties, null);
			session.setDebug(ls.debugOn);
			
			// Connect to host
			//
			Store store = session.getStore(Server.POP_MAIL);
			store.connect(pop3Host, -1, ls._user, ls._password);
			
			// Open the default folder
			//
			Folder folder = store.getDefaultFolder();
			if (folder == null)
				throw new NullPointerException("No default mail folder");
			
			folder = folder.getFolder(Server.INBOX);
			if (folder == null)
				throw new NullPointerException("Unable to get folder: " + folder);
			
			boolean done = false;
			// Get message count
			//
			folder.open(Folder.READ_WRITE);
			int totalMessages = folder.getMessageCount();
			if (totalMessages == 0) {
				if (ls.debugOn)
					System.out.println(new Date() + "> " + folder + " is empty");
				folder.close(false);
				store.close();
				done = true;
			}
			
			if (!done) {
				// Get attributes & flags for all messages
				//
				Message[] messages = folder.getMessages();
				FetchProfile fp = new FetchProfile();
				fp.add(FetchProfile.Item.ENVELOPE);
				fp.add(FetchProfile.Item.FLAGS);
				fp.add("X-Mailer");
				folder.fetch(messages, fp);
			
				// Process each message
				//
				for (int i = 0; i < messages.length; i++) {
					if (!messages[i].isSet(Flags.Flag.SEEN)) {
						Message message = messages[i];
						String replyTo = ls._user, subject, xMailer, messageText;
						Date sentDate;
						int size;
						Address[] a = null;
						
						// Get Headers (from, to, subject, date, etc.)
						//
						if ((a = message.getFrom()) != null)
							replyTo = a[0].toString();
						
						subject = message.getSubject();
						sentDate = message.getSentDate();
						size = message.getSize();
						String[] hdrs = message.getHeader("X-Mailer");
						if (hdrs != null)
							xMailer = hdrs[0];
						String from = ls._user;
						
						// Send message
						//
						// create some properties and get the default Session
						//
						Properties props = new Properties();
						props.put("mail.smtp.host", ls._smtpHost);
						Session session1 = Session.getDefaultInstance(props, null);
						
						// create a message
						//
						Address replyToList[] = { new InternetAddress(replyTo) };
						Message newMessage = new MimeMessage(session1);
						if (ls._fromName != null)
							newMessage.setFrom(new InternetAddress(from, ls._fromName
									+ " on behalf of " + replyTo));
						else
							newMessage.setFrom(new InternetAddress(from));
						newMessage.setReplyTo(replyToList);
						newMessage.setRecipients(Message.RecipientType.BCC, ls.toList);
						newMessage.setSubject(subject);
						newMessage.setSentDate(sentDate);
						
						// Set message contents
						//
						Object content = message.getContent();
						String debugText = "Subject: " + subject + ", Sent date: " + sentDate;
						if (content instanceof Multipart) {
							if (ls.debugOn)
								System.out.println(new Date() + "> " + "Sending Multipart message (" + debugText + ")");
							newMessage.setContent((Multipart) message.getContent());
						} else {
							if (ls.debugOn)
								System.out.println(new Date() + "> " + "Sending Text message (" + debugText + ")");
							newMessage.setText((String) content);
						}
						
					
						// Send newMessage
						//
						Transport transport = session1.getTransport(Server.SMTP_MAIL);
						transport.connect(ls._smtpHost, ls._user, ls._password);
						transport.sendMessage(newMessage, ls.toList);
					}
					messages[i].setFlag(Flags.Flag.DELETED, true);
				}
			
				folder.close(true);
				store.close();
			}
			if (ls.debugOn)
			System.out.println(new Date() + "> " + "SESSION END (Going to sleep for " + checkPeriod
								+ " minutes)");
			Thread.sleep(checkPeriod * 1000 * 60);
		}
	}
	

}
