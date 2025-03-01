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

package net.kodehawa.mantarobot.core.shard;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.experimental.Delegate;
import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.utils.SessionController;
import net.dv8tion.jda.api.utils.SessionControllerAdapter;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.kodehawa.mantarobot.MantaroBot;
import net.kodehawa.mantarobot.MantaroInfo;
import net.kodehawa.mantarobot.commands.music.listener.VoiceChannelListener;
import net.kodehawa.mantarobot.commands.utils.birthday.BirthdayTask;
import net.kodehawa.mantarobot.core.MantaroEventManager;
import net.kodehawa.mantarobot.core.listeners.MantaroListener;
import net.kodehawa.mantarobot.core.listeners.command.CommandListener;
import net.kodehawa.mantarobot.core.listeners.entities.CachedMessage;
import net.kodehawa.mantarobot.core.listeners.operations.InteractiveOperations;
import net.kodehawa.mantarobot.core.listeners.operations.ReactionOperations;
import net.kodehawa.mantarobot.core.processor.core.ICommandProcessor;
import net.kodehawa.mantarobot.data.Config;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.utils.Prometheus;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.time.DateUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static net.kodehawa.mantarobot.data.MantaroData.config;
import static net.kodehawa.mantarobot.utils.Utils.httpClient;
import static net.kodehawa.mantarobot.utils.Utils.pretty;

/**
 * Represents a Discord shard.
 * This class and contains all the logic necessary to build, start and configure shards.
 * The logic for configuring sharded instances of the bot is on {@link net.kodehawa.mantarobot.core.MantaroCore}.
 * <p>
 * This also handles posting stats to dbots/dbots.org/carbonitex. Because uh... no other class was fit for it.
 */
public class MantaroShard implements JDA {
    private static SessionController sessionController = new SessionControllerAdapter();

    private final Logger log;
    private static final VoiceChannelListener VOICE_CHANNEL_LISTENER = new VoiceChannelListener();
    private final CommandListener commandListener;
    private final MantaroListener mantaroListener;
    private final int shardId;
    private final int totalShards;
    private BirthdayTask birthdayTask = new BirthdayTask();
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2, new ThreadFactoryBuilder().setNameFormat("Mantaro-ShardExecutor Thread-%d").build());
    private static final Config config = MantaroData.config().get();
    //Message cache of 2500 cached messages per shard. If it reaches 2500 it will delete the first one stored, and continue being 2500.
    @Getter
    private final Cache<String, Optional<CachedMessage>> messageCache = CacheBuilder.newBuilder().concurrencyLevel(5).maximumSize(2500).build();

    //Christmas date
    private static final Calendar christmas = new Calendar.Builder().setDate(Calendar.getInstance().get(Calendar.YEAR), Calendar.DECEMBER, 25).build();
    //New year date
    private static final Calendar newYear = new Calendar.Builder().setDate(Calendar.getInstance().get(Calendar.YEAR), Calendar.JANUARY, 1).build();

    private final String callbackPoolIdentifierString;
    private final String ratelimitPoolIdentifierString;
    @Getter
    public final MantaroEventManager manager;
    @Getter
    private final ExecutorService threadPool;
    @Getter
    private final ExecutorService commandPool;
    @Delegate
    private JDA jda;

    /**
     * Builds a new instance of a MantaroShard.
     *
     * @param shardId          The id of the newly-created shard.
     * @param totalShards      The total quantity of shards that the bot will startup with.
     * @param manager          The event manager.
     * @param commandProcessor The {@link ICommandProcessor} used to process upcoming Commands.
     * @throws RateLimitedException
     * @throws LoginException
     * @throws InterruptedException
     */
    public MantaroShard(int shardId, int totalShards, MantaroEventManager manager, ICommandProcessor commandProcessor) throws RateLimitedException, LoginException, InterruptedException {
        this.callbackPoolIdentifierString = "callback-pool-shard-" + shardId;
        this.ratelimitPoolIdentifierString = "ratelimit-pool-shard-" + shardId;
        this.shardId = shardId;
        this.totalShards = totalShards;
        this.manager = manager;

        ThreadFactory normalTPNamedFactory =
                new ThreadFactoryBuilder()
                        .setNameFormat("MantaroShard-Executor[" + shardId + "/" + totalShards + "] Thread-%d")
                        .build();

        ThreadFactory commandTPNamedFactory =
                new ThreadFactoryBuilder()
                        .setNameFormat("MantaroShard-Command[" + shardId + "/" + totalShards + "] Thread-%d")
                        .build();

        threadPool = Executors.newCachedThreadPool(normalTPNamedFactory);
        commandPool = Executors.newCachedThreadPool(commandTPNamedFactory);

        Prometheus.THREAD_POOL_COLLECTOR.add("mantaro-shard-" + shardId + "-birthday-executor", executorService);
        Prometheus.THREAD_POOL_COLLECTOR.add("mantaro-shard-" + shardId + "-thread-pool", threadPool);
        Prometheus.THREAD_POOL_COLLECTOR.add("mantaro-shard-" + shardId + "-command-pool", commandPool);

        log = LoggerFactory.getLogger("MantaroShard-" + shardId);
        mantaroListener = new MantaroListener(shardId, this);
        commandListener = new CommandListener(shardId, this, commandProcessor);

        start(false);
    }

    /**
     * Starts a new Shard.
     * This method builds a {@link JDA} instance and then attempts to start it up.
     * This locks until the shard finds a status of AWAITING_LOGIN_CONFIRMATION + 5 seconds.
     * <p>
     * The newly-started shard will have auto reconnect enabled, a core pool size of 18 and a new NAS instance. The rest is defined either on global or instance
     * variables.
     *
     * @param force Whether we will call {@link JDA#shutdown()} or {@link JDA#shutdownNow()}
     * @throws LoginException
     * @throws InterruptedException
     */
    public void start(boolean force) throws LoginException, InterruptedException {
        if(jda != null) {
            log.info("Attempting to drop shard {}...", shardId);
            prepareShutdown();

            if(!force)
                jda.shutdown();
            else
                jda.shutdownNow();

            log.info("Dropped shard #{} successfully!", shardId);
            removeListeners();
        }

        ThreadPoolExecutor callbackPool;
        ScheduledThreadPoolExecutor ratelimitPool;
        synchronized(this) {
            callbackPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(15);
            ratelimitPool = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(config.ratelimitPoolSize);
            Prometheus.THREAD_POOL_COLLECTOR.remove(callbackPoolIdentifierString);
            Prometheus.THREAD_POOL_COLLECTOR.add(callbackPoolIdentifierString, callbackPool);
            Prometheus.THREAD_POOL_COLLECTOR.remove(ratelimitPoolIdentifierString);
            Prometheus.THREAD_POOL_COLLECTOR.add(ratelimitPoolIdentifierString, ratelimitPool);
        }

        JDABuilder jdaBuilder = new JDABuilder(AccountType.BOT)
                .setToken(config().get().token)
                .setAutoReconnect(true)
                .setRateLimitPool(ratelimitPool, true)
                .setCallbackPool(callbackPool, true)
                .setEventManager(manager)
                .setSessionController(sessionController)
                .setBulkDeleteSplittingEnabled(false)
                .useSharding(shardId, totalShards)
                .addEventListeners(MantaroBot.getInstance().getLavalink()) //try here then down there ig
                .setVoiceDispatchInterceptor(MantaroBot.getInstance().getLavalink().getVoiceInterceptor())
                .setDisabledCacheFlags(EnumSet.of(CacheFlag.ACTIVITY, CacheFlag.EMOTE))
                .setActivity(Activity.playing("Hold on to your seatbelts!"));

        if(shardId < getTotalShards() - 1) {
            jda = jdaBuilder.build();
        } else {
            //Block until all shards start up properly.
            jda = jdaBuilder.build().awaitReady();
        }

        //Assume everything is alright~
        addListeners();
    }

    private void addListeners() {
        log.debug("Added all listeners for shard {}", shardId);
        //jda.addEventListener(MantaroBot.getInstance().getLavalink());
        jda.addEventListener(mantaroListener, commandListener, VOICE_CHANNEL_LISTENER, InteractiveOperations.listener(), ReactionOperations.listener());
    }

    private void removeListeners() {
        log.debug("Removed all listeners for shard {}", shardId);
        jda.removeEventListener(mantaroListener, commandListener, VOICE_CHANNEL_LISTENER, InteractiveOperations.listener(), ReactionOperations.listener());
    }

    /**
     * Starts the birthday task wait until tomorrow. When 00:00 arrives, this will call {@link BirthdayTask#handle(int)} every 24 hours.
     * Every shard has one birthday task.
     *
     * @param millisecondsUntilTomorrow The amount of milliseconds until 00:00.
     */
    public void startBirthdayTask(long millisecondsUntilTomorrow) {
        log.debug("Started birthday task for shard {}, scheduled to run in {} ms more", shardId, millisecondsUntilTomorrow);

        executorService.scheduleWithFixedDelay(() -> birthdayTask.handle(shardId),
                millisecondsUntilTomorrow, TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS);
    }

    /**
     * Updates Mantaro's "splash".
     * Splashes are random gags like "now seen in theaters!" that show on Mantaro's status.
     * This has been on Mantaro since 2016, so it's part of its "personality" as a bot.
     */
    public void updateStatus() {
        Runnable changeStatus = () -> {
            //insert $CURRENT_YEAR meme here
            if(DateUtils.isSameDay(christmas, Calendar.getInstance())) {
                getJDA().getPresence().setActivity(Activity.playing(String.format("%shelp | %s | [%d]", config().get().prefix[0], "Merry Christmas!", getId())));
                return;
            } else if (DateUtils.isSameDay(newYear, Calendar.getInstance())) {
                getJDA().getPresence().setActivity(Activity.playing(String.format("%shelp | %s | [%d]", config().get().prefix[0], "Happy New Year!", getId())));
                return;
            }

            AtomicInteger users = new AtomicInteger(0), guilds = new AtomicInteger(0);
            if(MantaroBot.getInstance() != null) {
                Arrays.stream(MantaroBot.getInstance().getShardedMantaro().getShards()).filter(Objects::nonNull).filter(mantaroShard -> mantaroShard.getJDA() != null).map(MantaroShard::getJDA).forEach(jda -> {
                    users.addAndGet((int) jda.getUserCache().size());
                    guilds.addAndGet((int) jda.getGuildCache().size());
                });
            }

            JSONObject reply;

            try {
                Request request = new Request.Builder()
                        .url(config.apiTwoUrl + "/mantaroapi/bot/splashes/random")
                        .addHeader("Authorization", config.getApiAuthKey())
                        .addHeader("User-Agent", MantaroInfo.USER_AGENT)
                        .get()
                        .build();

                Response response = httpClient.newCall(request).execute();
                String body = response.body().string();
                response.close();

                reply = new JSONObject(body);
            } catch (Exception e) {
                //I had to, lol.
                reply = new JSONObject().put("splash", "With a missing status!");
            }

            String newStatus = reply.getString("splash")
                    //Replace fest.
                    .replace("%ramgb%", String.valueOf(((long) (Runtime.getRuntime().maxMemory() * 1.2D)) >> 30L))
                    .replace("%usercount%", users.toString())
                    .replace("%guildcount%", guilds.toString())
                    .replace("%shardcount%", String.valueOf(getTotalShards()))
                    .replace("%prettyusercount%", pretty(users.get()))
                    .replace("%prettyguildcount%", pretty(guilds.get()));

            getJDA().getPresence().setActivity(Activity.playing(String.format("%shelp | %s | [%d]", config().get().prefix[0], newStatus, getId())));
            log.debug("Changed status to: " + newStatus);
        };

        changeStatus.run();
        Executors.newSingleThreadScheduledExecutor(r->new Thread(r, "Splash Thread"))
                .scheduleAtFixedRate(changeStatus, 10, 10, TimeUnit.MINUTES);
    }

    /**
     * @return The current {@link MantaroEventManager} for this specific instance.
     */
    public MantaroEventManager getShardEventManager() {
        return manager;
    }

    public int getId() {
        return shardId;
    }

    public JDA getJDA() {
        return jda;
    }

    private int getTotalShards() {
        return totalShards;
    }

    //This used to be bigger...
    public void prepareShutdown() {
        jda.removeEventListener(jda.getRegisteredListeners().toArray());
    }

    @Override
    public String toString() {
        return "MantaroShard [" + (getId()) + " / " + totalShards + "]";
    }
}
