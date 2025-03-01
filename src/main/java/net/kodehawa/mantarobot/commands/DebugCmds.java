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

package net.kodehawa.mantarobot.commands;

import com.google.common.eventbus.Subscribe;
import com.sedmelluq.discord.lavaplayer.tools.PlayerLibrary;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.utils.cache.SnowflakeCacheView;
import net.kodehawa.mantarobot.MantaroBot;
import net.kodehawa.mantarobot.MantaroInfo;
import net.kodehawa.mantarobot.core.CommandRegistry;
import net.kodehawa.mantarobot.core.listeners.MantaroListener;
import net.kodehawa.mantarobot.core.listeners.command.CommandListener;
import net.kodehawa.mantarobot.core.listeners.events.PreLoadEvent;
import net.kodehawa.mantarobot.core.modules.Module;
import net.kodehawa.mantarobot.core.modules.commands.SimpleCommand;
import net.kodehawa.mantarobot.core.modules.commands.base.Category;
import net.kodehawa.mantarobot.core.modules.commands.help.HelpContent;
import net.kodehawa.mantarobot.core.modules.commands.i18n.I18nContext;
import net.kodehawa.mantarobot.core.processor.DefaultCommandProcessor;
import net.kodehawa.mantarobot.core.shard.MantaroShard;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.utils.DiscordUtils;
import net.kodehawa.mantarobot.utils.Utils;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;
import net.kodehawa.mantarobot.utils.commands.IncreasingRateLimiter;

import java.lang.management.ManagementFactory;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.kodehawa.mantarobot.commands.info.AsyncInfoMonitor.*;
import static net.kodehawa.mantarobot.utils.Utils.handleDefaultIncreasingRatelimit;

@Module
@SuppressWarnings("unused")
public class DebugCmds {
    @Subscribe
    public void info(CommandRegistry cr) {
        cr.register("info", new SimpleCommand(Category.INFO) {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content, String[] args) {
                MantaroBot mantaroBot = MantaroBot.getInstance();

                SnowflakeCacheView<Guild> guilds = mantaroBot.getGuildCache();
                SnowflakeCacheView<User> users = mantaroBot.getUserCache();

                event.getChannel().sendMessage("```prolog\n"
                        + " --------- Technical Information --------- \n\n"
                        + "Commands: " + DefaultCommandProcessor.REGISTRY.commands().values().stream().filter(command -> command.category() != null).count() + "\n"
                        + "Bot Version: " + MantaroInfo.VERSION + "\n"
                        + "JDA Version: " + JDAInfo.VERSION + "\n"
                        + "Lavaplayer Version: " + PlayerLibrary.VERSION + "\n"
                        + "API Responses: " + String.format("%,d", mantaroBot.getResponseTotal()) + "\n"
                        + "CPU Usage: " + String.format("%.2f", getInstanceCPUUsage()) + "%" + "\n"
                        + "CPU Cores: " + getAvailableProcessors() + "\n"
                        + "Shard Info: " + event.getJDA().getShardInfo()
                        + "\n\n --------- Mantaro Information --------- \n\n"
                        + "Guilds: " + String.format("%,d", guilds.size()) + "\n"
                        + "Users: " + String.format("%,d", users.size()) + "\n"
                        + "Shards: " + mantaroBot.getShardedMantaro().getTotalShards() + " (Current: " + (mantaroBot.getShardForGuild(event.getGuild().getId()).getId()) + ")" + "\n"
                        + "Threads: " + String.format("%,d", Thread.activeCount()) + "\n"
                        + "Executed Commands: " + String.format("%,d", CommandListener.getCommandTotalInt()) + "\n"
                        + "Logs: " + String.format("%,d", MantaroListener.getLogTotalInt()) + "\n"
                        + "Memory: " + String.format("%,dMB/%,dMB", (int)(getTotalMemory() - getFreeMemory()), (int)getMaxMemory()) + "\n"
                        + "Queue Size: " + String.format("%,d", mantaroBot.getAudioManager().getTotalQueueSize())
                        + "```").queue();
            }
            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Gets the bot technical information. Nothing all that interesting, but shows cute stats.")
                        .build();
            }
        });
    }

    @Subscribe
    public void shard(CommandRegistry cr) {
        cr.register("shard", new SimpleCommand(Category.INFO) {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content, String[] args) {
                event.getChannel().sendMessageFormat(languageContext.get("commands.shard.info"), event.getJDA().getShardInfo() == null ? 0 : event.getJDA().getShardInfo().getShardId()).queue();
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Returns in what shard I am.")
                        .build();
            }
        });
    }

    @Subscribe
    public void ping(CommandRegistry cr) {
        final IncreasingRateLimiter rateLimiter = new IncreasingRateLimiter.Builder()
                .limit(1)
                .spamTolerance(2)
                .cooldown(10, TimeUnit.SECONDS)
                .maxCooldown(10, TimeUnit.SECONDS)
                .randomIncrement(true)
                .pool(MantaroData.getDefaultJedisPool())
                .prefix("ping")
                .build();

        cr.register("ping", new SimpleCommand(Category.INFO) {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content, String[] args) {
                if(!handleDefaultIncreasingRatelimit(rateLimiter, event.getAuthor(), event, languageContext, false))
                    return;

                long start = System.currentTimeMillis();
                event.getChannel().sendTyping().queue(v -> {
                    long ping = System.currentTimeMillis() - start;
                    //display: show a random quote, translated.
                    event.getChannel().sendMessageFormat(languageContext.get("commands.ping.text"), EmoteReference.MEGA, languageContext.get("commands.ping.display"), ping, ratePing(ping, languageContext), event.getJDA().getGatewayPing()).queue();
                });
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Plays Ping-Pong with Discord and prints out the result.")
                        .build();
            }
        });
    }

    @Subscribe
    public void shardinfo(CommandRegistry cr) {
        cr.register("shardinfo", new SimpleCommand(Category.INFO) {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content, String[] args) {
                StringBuilder builder = new StringBuilder();
                int connecting = 0;
                for(MantaroShard shard : MantaroBot.getInstance().getShardList()) {
                    if(shard == null) {
                        connecting++;
                        continue;
                    }

                    JDA jda = shard.getJDA();
                    builder.append(String.format(
                            "%-17s | %-9s | U: %-6d | G: %-4d | EV: %-8s | P: %-6s",
                            jda.getShardInfo(),
                            jda.getStatus(),
                            jda.getUserCache().size(),
                            jda.getGuildCache().size(),
                            shard.getShardEventManager().getLastJDAEventTimeDiff() + " ms",
                            jda.getGatewayPing()
                    ));

                    if(shard.getJDA().getShardInfo().equals(event.getJDA().getShardInfo())) {
                        builder.append(" <- CURRENT");
                    }

                    builder.append("\n");
                }

                if(connecting > 0)
                    builder.append("\nWARNING: Number of shards still booting up: ").append(connecting);

                List<String> m = DiscordUtils.divideString(builder);
                List<String> messages = new LinkedList<>();

                for(String s1 : m) {
                    messages.add(String.format("%s\n```prolog\n%s```", "**Mantaro's Shard Information. Use &p >> and &p << to move pages, &cancel to exit.**", s1));
                }

                DiscordUtils.listText(event, 45, false, messages);
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Returns information about shards.")
                        .build();
            }
        });
    }

    @Subscribe
    public void debug(CommandRegistry cr) {
        cr.register("status", new SimpleCommand(Category.INFO) {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content, String[] args) {
                MantaroBot bot = MantaroBot.getInstance();
                long ping = bot.getGatewayPing();
                List<MantaroShard> shards = bot.getShardList();
                StringBuilder stringBuilder = new StringBuilder();
                int dead = 0;
                int reconnecting = 0;
                int connecting = 0;
                int high = 0;

                for(MantaroShard shard : shards) {
                    if(shard == null) {
                        connecting++;
                        continue;
                    }

                    boolean reconnect = shard.getStatus().equals(JDA.Status.RECONNECT_QUEUED);
                    if(shard.getShardEventManager().getLastJDAEventTimeDiff() > 50000 && !reconnect)
                        dead++;
                    if(reconnect)
                        reconnecting++;
                    if(shard.getShardEventManager().getLastJDAEventTimeDiff() > 1650 && !reconnect)
                        high++;
                }

                String status = (dead == 0 && (high == 0 || reconnecting > 10)) ? "Status: Okay :)\n\n" : "Status: Warning :(\n\n";
                stringBuilder.append(status);

                if(reconnecting > 10)
                    stringBuilder.append("WARNING: A large number of shards are reconnecting right now!" +
                            " Bot might be unavailable on several thousands guilds for some minutes! (").append(reconnecting).append(" shards reconnecting now)\n");
                if(high > 20)
                    stringBuilder.append("WARNING: A very large number of shards has a high last event time! A restart might be needed if this doesn't fix itself on some minutes!\n");
                if(dead > 5)
                    stringBuilder.append("WARNING: Several shards (").append(dead).append(") ")
                            .append("appear to be dead! If this doesn't get fixed in 30 minutes please report this!\n");

                stringBuilder.append(String.format(
                        "Uptime: %s.\n\n" +
                                "Bot Version: %s\n" +
                                "JDA Version: %s\n" +
                                "LP Version: %s\n\n" +
                                "* Average Ping: %dms.\n" +
                                "* (High) Ping Breakdown: %s\n" +
                                "* Dead Shards: %s shards.\n" +
                                "* Shards Reconnecting: %s shards.\n" +
                                "* Shards Connecting: %s shards\n" +
                                "* High Last Event Time: %s shards.\n\n" +
                                "--- Guilds: %-4s | Users: %-8s | Shards: %-3s"
                        ,
                        Utils.getHumanizedTime(ManagementFactory.getRuntimeMXBean().getUptime()), MantaroInfo.VERSION, JDAInfo.VERSION, PlayerLibrary.VERSION, ping,
                        bot.getShardList().stream().filter(Objects::nonNull)
                                //"high" ping shards
                                .filter(shard -> shard.getGatewayPing() > 350)
                                .map(shard -> shard.getId() + ": " + shard.getGatewayPing() + "ms")
                                .collect(Collectors.joining(", ")),
                        dead, reconnecting, connecting, high, String.format("%,d", bot.getGuildCache().size()),
                        String.format("%,d", bot.getUserCache().size()), bot.getShardList().size()));

                event.getChannel().sendMessage(new MessageBuilder()
                        .append(EmoteReference.OK)
                        .append("**Mantaro's Status**")
                        .append("\n")
                        .appendCodeBlock(stringBuilder.toString(), "prolog")
                        .build()).queue();
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Is the bot doing fine? Oh who am I kidding, it's probably fire. Or an earthquake, who knows (not really, it's probably doing ok)")
                        .build();
            }
        });
    }

    private String ratePing(long ping, I18nContext languageContext) {
        if(ping == 69)
            return languageContext.get("commands.ping.quotes.69");
        if(ping <= 1)
            return languageContext.get("commands.ping.quotes.1"); //just in case...
        if(ping <= 10)
            return languageContext.get("commands.ping.quotes.10");
        if(ping <= 100)
            return languageContext.get("commands.ping.quotes.100");
        if(ping <= 200)
            return languageContext.get("commands.ping.quotes.200");
        if(ping <= 300)
            return languageContext.get("commands.ping.quotes.300");
        if(ping <= 400)
            return languageContext.get("commands.ping.quotes.400");
        if(ping <= 500)
            return languageContext.get("commands.ping.quotes.500");
        if(ping <= 600)
            return languageContext.get("commands.ping.quotes.600");
        if(ping <= 700)
            return languageContext.get("commands.ping.quotes.700");
        if(ping <= 800)
            return languageContext.get("commands.ping.quotes.800");
        if(ping <= 900)
            return languageContext.get("commands.ping.quotes.900");
        if(ping <= 1600)
            return languageContext.get("commands.ping.quotes.1600");
        if(ping <= 10000)
            return languageContext.get("commands.ping.quotes.10000");

        return languageContext.get("commands.ping.quotes.default");
    }

    @Subscribe
    public void onPreLoad(PreLoadEvent e) {
        start();
    }
}
