/*
 * Copyright (C) 2016-2019 David Alejandro Rubio Escares / Kodehawa
 *
 * Mantaro is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Mantaro is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mantaro.  If not, see http://www.gnu.org/licenses/
 *
 */

package net.kodehawa.mantarobot.commands.utils.birthday;

import io.prometheus.client.Counter;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.utils.cache.SnowflakeCacheView;
import net.kodehawa.mantarobot.MantaroBot;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.db.ManagedDatabase;
import net.kodehawa.mantarobot.db.entities.helpers.GuildData;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;
import org.apache.commons.lang3.time.FastDateFormat;

import java.util.Calendar;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class BirthdayTask {
    private static FastDateFormat dateFormat = FastDateFormat.getInstance("dd-MM-yyyy");
    private ManagedDatabase db = MantaroData.db();
    private static final Counter birthdayCounter = Counter.build()
            .name("birthdays_logged").help("Logged birthdays (guild id)")
            .register();

    public void handle(int shardId) {
        try {
            BirthdayCacher cache = MantaroBot.getInstance().getBirthdayCacher();
            if(cache == null)
                return;
            if(!cache.isDone)
                return;

            int i = 0;
            int r = 0;

            JDA jda = MantaroBot.getInstance().getShard(shardId);

            log.info("Checking birthdays in shard {} to assign roles...", jda.getShardInfo() == null ? 0 : jda.getShardInfo().getShardId());
            long start = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance();
            String now = dateFormat.format(cal.getTime()).substring(0, 5);
            Map<String, BirthdayCacher.BirthdayData> cached = cache.cachedBirthdays;
            SnowflakeCacheView<Guild> guilds = jda.getGuildCache();

            for(Guild guild : guilds) {
                GuildData tempGuildData = db.getGuild(guild).getData();
                if(tempGuildData.getBirthdayChannel() != null && tempGuildData.getBirthdayRole() != null) {
                    Role birthdayRole = guild.getRoleById(tempGuildData.getBirthdayRole());
                    TextChannel channel = guild.getTextChannelById(tempGuildData.getBirthdayChannel());

                    if(channel != null && birthdayRole != null) {
                        if(!guild.getSelfMember().canInteract(birthdayRole))
                            continue; //Go to next guild...
                        if(!channel.canTalk())
                            continue; //cannot talk here...
                        if(tempGuildData.getGuildAutoRole() != null && birthdayRole.getId().equals(tempGuildData.getGuildAutoRole()))
                            continue;
                        if(birthdayRole.isPublicRole())
                            continue;
                        if(birthdayRole.isManaged())
                            continue;

                        Map<String, BirthdayCacher.BirthdayData> guildMap = cached.entrySet().stream().filter(map -> guild.getMemberById(map.getKey()) != null)
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                        for(Map.Entry<String, BirthdayCacher.BirthdayData> data : guildMap.entrySet()) {
                            Member member = guild.getMemberById(data.getKey());
                            String birthday = data.getValue().birthday;

                            if(birthday == null) {
                                log.debug("Birthday is null? Removing role if present and continuing to next iteration...");
                                if(member.getRoles().contains(birthdayRole)) {
                                    guild.removeRoleFromMember(member, birthdayRole)
                                            .reason("Birthday assigner. If you see this happening for every member of your server, or in unintended ways, please do ~>opts birthday disable")
                                            .queue();
                                }
                                continue; //shouldn't happen
                            }
                            //else start the assigning

                            //tada!
                            if(birthday.substring(0, 5).equals(now)) {
                                log.debug("Assigning birthday role on guild {} (M: {})", guild.getId(), member.getEffectiveName());
                                String tempBirthdayMessage = String.format(EmoteReference.POPPER + "**%s is a year older now! Wish them a happy birthday.** :tada:",
                                        member.getEffectiveName());

                                if(tempGuildData.getBirthdayMessage() != null) {
                                    tempBirthdayMessage = tempGuildData.getBirthdayMessage().replace("$(user)", member.getEffectiveName());
                                }

                                //Variable used in lambda expression should be final or effectively final...
                                final String birthdayMessage = tempBirthdayMessage;

                                if(!member.getRoles().contains(birthdayRole)) {
                                    try {
                                        guild.addRoleToMember(member, birthdayRole)
                                                .reason("Birthday assigner. If you see this happening for every member of your server, or in unintended ways, please do ~>opts birthday disable")
                                                .queue(s -> {
                                                    channel.sendMessage(birthdayMessage).queue();
                                                    birthdayCounter.inc();
                                                }
                                        );
                                        log.debug("Assigned birthday role on guild {} (M: {})", guild.getId(), member.getEffectiveName());
                                        i++;
                                        //Something went boom, ignore and continue
                                    } catch(Exception e) {
                                        log.debug("Something went boom while assigning a birthday role?...");
                                    }
                                }
                            } else {
                                //day passed | member can return null? well, ill follow the ide advice here.
                                if(member != null) {
                                    if(member.getRoles().contains(birthdayRole)) {
                                        try {
                                            log.debug("Removing birthday role on guild {} (M: {})", guild.getId(), member.getEffectiveName());
                                            guild.removeRoleFromMember(member, birthdayRole)
                                                    .reason("Birthday assigner. If you see this happening for every member of your server, or in unintended ways, please do ~>opts birthday disable")
                                                    .queue();
                                            r++;
                                            //Something went boom, ignore and continue
                                        } catch(Exception e) {
                                            log.debug("Something went boom while removing a birthday role?...");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            long end = System.currentTimeMillis();

            String toSend = String.format("Finished checking birthdays for shard %s, people assigned: %d, people divested: %d, took %dms",
                    jda.getShardInfo() == null ? 0 : jda.getShardInfo().getShardId(), i, r, (end - start));

            log.info(toSend);
        } catch(Exception e) {
            e.printStackTrace();
            Sentry.capture(e);
        }
    }
}
