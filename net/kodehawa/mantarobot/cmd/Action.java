package net.kodehawa.mantarobot.cmd;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.kodehawa.mantarobot.cmd.management.Command;
import net.kodehawa.mantarobot.util.StringArrayFile;

public class Action extends Command {

	private CopyOnWriteArrayList<String> pats = new CopyOnWriteArrayList<String>();
	private CopyOnWriteArrayList<String> hugs = new CopyOnWriteArrayList<String>();

	public Action()
	{
		setName("action");
		setDescription("Action commands. Arguments avaliable: pat (mention), hug (mention), bloodsuck (mention), meow, meow2, facedesk, nom");
		setCommandType("user");
		new StringArrayFile("patting", pats, false);
		new StringArrayFile("hugs", hugs, false);
	}
	
	
	@Override
	public void onCommand(String[] message, String beheadedMessage, MessageReceivedEvent evt) {
        guild = evt.getGuild();
        author = evt.getAuthor();
        channel = evt.getChannel();
        receivedMessage = evt.getMessage();
        String noArgs = beheadedMessage.split(" ")[0];
        
		Random rand = new Random();
		switch(noArgs){
		case "pat": 
			List<User> menctions = receivedMessage.getMentionedUsers();
			StringBuilder pString = new StringBuilder();
			int patint = rand.nextInt(pats.size());
		
			for(User s : menctions){
				pString.append(s.getAsMention());
			}
		
			String pat = String.format(":speech_balloon: %s you have been patted by %s \r %s", pString, author.getAsMention(), pats.get(patint));
			channel.sendMessage(pat).queue();
			break;
		case "hug":
			List<User> hugMentions = receivedMessage.getMentionedUsers();
			StringBuilder hString = new StringBuilder();
			int hugint = rand.nextInt(hugs.size());
			
			for(User s : hugMentions){
				hString.append(s.getAsMention());
			}
			
			String hug = String.format(":speech_balloon: %s you have been hugged by %s \r %s", hString, author.getAsMention(), hugs.get(hugint));
			channel.sendMessage(hug).queue();
			break;
		case "bloodsuck":
			if(evt.getMessage().getMentionedUsers().isEmpty()){
				channel.sendMessage("http://puu.sh/qEYYH/e5094405a5.jpg").queue();
			} else{
				StringBuilder listString = new StringBuilder();

				for(User s : evt.getMessage().getMentionedUsers())	{
					listString.append(s.getAsMention());
				}
				
				String bs = String.format(":speech_balloon: http://puu.sh/qEYYH/e5094405a5.jpg \rSucks the blood of %s", listString);
				channel.sendMessage(bs).queue();
			}
			break;
		case "meow2": 
			channel.sendMessage(":speech_balloon: " + "Meeeeow.\r " + "http://puu.sh/rK5K7/034039286e.gif").queue();
			break;
		case "meow":
			if(receivedMessage.getMentionedUsers().isEmpty() != true){
				List<User> mentions = receivedMessage.getMentionedUsers();
	            StringBuilder builder = new StringBuilder();
	            for (User user: mentions) {
	                builder.append(user.getName());
	            }
	            String mention = builder.toString().replace("MantaroBot", "");
				channel.sendMessage(":speech_balloon: " + "*meows at " + mention + ".*\r" + "http://puu.sh/rK5Nf/63d90628c2.gif").queue();;
	        } else{
				channel.sendMessage(":speech_balloon: " + "Who am I gonna meow at, silly?\r\nAnyway, I guess I'll have to meow you.\r\n*meows at " + evt.getAuthor().getAsMention() + " .*").queue();;
			}
			break;
		case "facedesk":
			channel.sendMessage("http://puu.sh/rK6E7/0b745e5544.gif").queue();
			break;
		case "nom":
			channel.sendMessage("http://puu.sh/rK7t2/330182c282.gif").queue();
			break;
		}
	}
}