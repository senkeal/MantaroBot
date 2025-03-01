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

import com.github.natanbc.javaeval.CompilationException;
import com.github.natanbc.javaeval.CompilationResult;
import com.github.natanbc.javaeval.JavaEvaluator;
import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.kodehawa.mantarobot.MantaroBot;
import net.kodehawa.mantarobot.commands.currency.item.Item;
import net.kodehawa.mantarobot.commands.currency.item.ItemStack;
import net.kodehawa.mantarobot.commands.currency.item.Items;
import net.kodehawa.mantarobot.commands.currency.profile.Badge;
import net.kodehawa.mantarobot.core.CommandRegistry;
import net.kodehawa.mantarobot.core.listeners.operations.InteractiveOperations;
import net.kodehawa.mantarobot.core.listeners.operations.core.Operation;
import net.kodehawa.mantarobot.core.modules.Module;
import net.kodehawa.mantarobot.core.modules.commands.SimpleCommand;
import net.kodehawa.mantarobot.core.modules.commands.base.Category;
import net.kodehawa.mantarobot.core.modules.commands.base.Command;
import net.kodehawa.mantarobot.core.modules.commands.base.CommandPermission;
import net.kodehawa.mantarobot.core.modules.commands.help.HelpContent;
import net.kodehawa.mantarobot.core.modules.commands.i18n.I18nContext;
import net.kodehawa.mantarobot.data.Config;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.db.entities.DBGuild;
import net.kodehawa.mantarobot.db.entities.MantaroObj;
import net.kodehawa.mantarobot.db.entities.Player;
import net.kodehawa.mantarobot.db.entities.helpers.PlayerData;
import net.kodehawa.mantarobot.utils.Pair;
import net.kodehawa.mantarobot.utils.Utils;
import net.kodehawa.mantarobot.utils.commands.EmoteReference;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.kodehawa.mantarobot.utils.StringUtils.SPLIT_PATTERN;

@Slf4j
@Module
@SuppressWarnings("unused")
public class OwnerCmd {
    private static final String JAVA_EVAL_IMPORTS = "" +
            "import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;\n" +
            "import net.kodehawa.mantarobot.*;\n" +
            "import net.kodehawa.mantarobot.core.listeners.operations.*;\n" +
            "import net.kodehawa.mantarobot.data.*;\n" +
            "import net.kodehawa.mantarobot.db.*;\n" +
            "import net.kodehawa.mantarobot.db.entities.*;\n" +
            "import net.kodehawa.mantarobot.commands.currency.*;\n" +
            "import net.kodehawa.mantarobot.utils.*;\n" +
            "import net.dv8tion.jda.api.entities.*;\n";

    @Subscribe
    public void blacklist(CommandRegistry cr) {
        cr.register("blacklist", new SimpleCommand(Category.OWNER, CommandPermission.OWNER) {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content, String[] args) {
                MantaroObj obj = MantaroData.db().getMantaroData();
                if(args[0].equals("guild")) {
                    if(args[1].equals("add")) {
                        if(MantaroBot.getInstance().getGuildById(args[2]) == null) return;
                        obj.getBlackListedGuilds().add(args[2]);
                        event.getChannel().sendMessage(EmoteReference.CORRECT + "Blacklisted Guild: " + MantaroBot.getInstance().getGuildById(args[2])).queue();
                        obj.saveAsync();
                        return;
                    } else if(args[1].equals("remove")) {
                        if(!obj.getBlackListedGuilds().contains(args[2])) return;
                        obj.getBlackListedGuilds().remove(args[2]);
                        event.getChannel().sendMessage(EmoteReference.CORRECT + "Unblacklisted Guild: " + args[2]).queue();
                        obj.saveAsync();
                        return;
                    }

                    event.getChannel().sendMessage("Invalid guild scope. (Valid: add, remove)").queue();
                    return;
                }

                if(args[0].equals("user")) {
                    if(args[1].equals("add")) {
                        if(MantaroBot.getInstance().getUserById(args[2]) == null) {
                            event.getChannel().sendMessage("Can't find user.").queue();
                            return;
                        }

                        obj.getBlackListedUsers().add(args[2]);
                        event.getChannel().sendMessage(EmoteReference.CORRECT + "Blacklisted User: " + MantaroBot.getInstance().getUserById(args[2])).queue();

                        obj.saveAsync();
                        return;
                    } else if(args[1].equals("remove")) {
                        if(!obj.getBlackListedUsers().contains(args[2])) {
                            event.getChannel().sendMessage("User not in blacklist.").queue();
                            return;
                        }

                        obj.getBlackListedUsers().remove(args[2]);
                        event.getChannel().sendMessage(EmoteReference.CORRECT + "Unblacklisted User: " + MantaroBot.getInstance().getUserById(args[2])).queue();

                        obj.saveAsync();
                        return;
                    }

                    event.getChannel().sendMessage("Invalid user scope. (Valid: add, remove)").queue();
                    return;
                }

                event.getChannel().sendMessage("Invalid scope. (Valid: user, guild)").queue();
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Blacklists a user (user argument) or a guild (guild argument) by id.\n" +
                                "Examples: ~>blacklist user add/remove 293884638101897216, ~>blacklist guild add/remove 305408763915927552")
                        .build();
            }
        });
    }

    //This is for testing lol
    @Subscribe
    public void giveItem(CommandRegistry cr) {
        cr.register("giveitem", new SimpleCommand(Category.OWNER, CommandPermission.OWNER) {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content, String[] args) {
                if(content.isEmpty()) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "You need to tell me which item to give you.").queue();
                    return;
                }

                Item i = Items.fromAnyNoId(content).orElse(null);

                if(i == null) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "I didn't find that item.").queue();
                    return;
                }

                Player p = MantaroData.db().getPlayer(event.getAuthor());
                if(p.getInventory().getAmount(i) < 5000) {
                    p.getInventory().process(new ItemStack(i, 1));
                } else {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "Too many of this item already.").queue();
                }

                p.saveAsync();
                event.getChannel().sendMessage("Gave you " + i).queue();
            }
        });
    }

    @Subscribe
    public void transferPlayer(CommandRegistry cr) {
        cr.register("transferplayer", new SimpleCommand(Category.OWNER, CommandPermission.OWNER) {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content, String[] args) {
                if(content.isEmpty() || args.length < 2) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "You need to tell me the 2 players ids to transfer!").queue();
                    return;
                }

                event.getChannel().sendMessage(EmoteReference.WARNING + "You're about to transfer all the player information from " + args[0] + " to " + args[1] + " are you sure you want to continue?").queue();
                InteractiveOperations.create(event.getChannel(), event.getAuthor().getIdLong(), 30, e -> {
                    if(e.getAuthor().getIdLong() != event.getAuthor().getIdLong()) {
                        return Operation.IGNORED;
                    }

                    if(e.getMessage().getContentRaw().equalsIgnoreCase("yes")) {
                        Player transferred = MantaroData.db().getPlayer(args[0]);
                        Player transferTo = MantaroData.db().getPlayer(args[1]);

                        transferTo.setMoney(transferred.getMoney());
                        transferTo.setLevel(transferred.getLevel());
                        transferTo.setReputation(transferred.getReputation());
                        transferTo.getInventory().merge(transferred.getInventory().asList());

                        PlayerData transferredData = transferred.getData();
                        PlayerData transferToData = transferTo.getData();

                        transferToData.setExperience(transferredData.getExperience());
                        transferToData.setBadges(transferredData.getBadges());
                        transferToData.setShowBadge(transferredData.isShowBadge());
                        transferToData.setMarketUsed(transferredData.getMarketUsed());
                        transferToData.setMainBadge(transferredData.getMainBadge());
                        transferToData.setGamesWon(transferredData.getGamesWon());


                        transferTo.save();
                        Player reset = Player.of(args[0]);
                        reset.save();

                        e.getChannel().sendMessage(EmoteReference.CORRECT + "Transfer from " + args[0] + " to " + args[1] + " completed.").queue();

                        return Operation.COMPLETED;
                    }

                    if(e.getMessage().getContentRaw().equalsIgnoreCase("no")) {
                        e.getChannel().sendMessage(EmoteReference.CORRECT + "Cancelled.").queue();
                        return Operation.COMPLETED;
                    }

                    return Operation.IGNORED;
                });
            }
        });
    }

    @Subscribe
    public void badge(CommandRegistry cr) {
        cr.register("addbadge", new SimpleCommand(Category.OWNER, CommandPermission.OWNER) {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content, String[] args) {
                if(event.getMessage().getMentionedUsers().isEmpty()) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "You need to give me a user to apply the badge to!").queue();
                    return;
                }

                if(args.length != 2) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "Wrong args length").queue();
                    return;
                }

                String b = args[1];
                List<User> users = event.getMessage().getMentionedUsers();
                Badge badge = Badge.lookupFromString(b);
                if(badge == null) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "No badge with that enum name! Valid badges: " +
                            Arrays.stream(Badge.values()).map(b1 -> "`" + b1.name() + "`").collect(Collectors.joining(" ,"))).queue();
                    return;
                }

                for(User u : users) {
                    Player p = MantaroData.db().getPlayer(u);
                    p.getData().addBadgeIfAbsent(badge);
                    p.saveAsync();
                }

                event.getChannel().sendMessage(
                        EmoteReference.CORRECT + "Added badge " + badge + " to " + users.stream().map(User::getName).collect(Collectors.joining(" ,"))
                ).queue();
            }
        });

        cr.register("removebadge", new SimpleCommand(Category.OWNER, CommandPermission.OWNER) {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content, String[] args) {
                if(event.getMessage().getMentionedUsers().isEmpty()) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "You need to give me a user to remove the badge from!").queue();
                    return;
                }

                if(args.length != 2) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "Wrong args length").queue();
                    return;
                }

                String b = args[1];
                List<User> users = event.getMessage().getMentionedUsers();
                Badge badge = Badge.lookupFromString(b);
                if(badge == null) {
                    event.getChannel().sendMessage(EmoteReference.ERROR + "No badge with that enum name! Valid badges: " +
                            Arrays.stream(Badge.values()).map(b1 -> "`" + b1.name() + "`").collect(Collectors.joining(" ,"))).queue();
                    return;
                }

                for(User u : users) {
                    Player p = MantaroData.db().getPlayer(u);
                    p.getData().removeBadge(badge);
                    p.saveAsync();
                }

                event.getChannel().sendMessage(
                        String.format("%sRemoved badge %s from %s", EmoteReference.CORRECT, badge, users.stream().map(User::getName).collect(Collectors.joining(" ,")))
                ).queue();
            }
        });
    }

    @Subscribe
    public void eval(CommandRegistry cr) {
        //has no state
        JavaEvaluator javaEvaluator = new JavaEvaluator();

        Map<String, Evaluator> evals = new HashMap<>();
        evals.put("js", (event, code) -> {
            ScriptEngine script = new ScriptEngineManager().getEngineByName("nashorn");
            script.put("mantaro", MantaroBot.getInstance());
            script.put("db", MantaroData.db());
            script.put("jda", event.getJDA());
            script.put("event", event);
            script.put("guild", event.getGuild());
            script.put("channel", event.getChannel());

            try {
                return script.eval(String.join(
                        "\n",
                        "load(\"nashorn:mozilla_compat.js\");",
                        "imports = new JavaImporter(java.util, java.io, java.net);",
                        "(function() {",
                        "with(imports) {",
                        code,
                        "}",
                        "})()"
                ));
            } catch(Exception e) {
                return e;
            }
        });

        evals.put("java", (event, code) -> {
            try {
                CompilationResult r = javaEvaluator.compile()
                        .addCompilerOptions("-Xlint:unchecked")
                        .source("Eval", JAVA_EVAL_IMPORTS + "\n\n" +
                                "public class Eval {\n" +
                                "   public static Object run(GuildMessageReceivedEvent event) throws Throwable {\n" +
                                "       try {\n" +
                                "           return null;\n" +
                                "       } finally {\n" +
                                "           " + (code + ";").replaceAll(";{2,}", ";") + "\n" +
                                "       }\n" +
                                "   }\n" +
                                "}"
                        )
                        .execute();
                EvalClassLoader ecl = new EvalClassLoader();
                r.getClasses().forEach((name, bytes) -> ecl.define(bytes));

                return ecl.loadClass("Eval").getMethod("run", GuildMessageReceivedEvent.class).invoke(null, event);
            } catch(CompilationException e) {
                StringBuilder sb = new StringBuilder("\n");

                if(e.getCompilerOutput() != null)
                    sb.append(e.getCompilerOutput());

                if(!e.getDiagnostics().isEmpty()) {
                    if(sb.length() > 0) sb.append("\n\n");
                    e.getDiagnostics().forEach(d -> sb.append(d).append('\n'));
                }
                return new Error(sb.toString()) {
                    @Override
                    public String toString() {
                        return getMessage();
                    }
                };
            } catch(Exception e) {
                return e;
            }
        });

        cr.register("eval", new SimpleCommand(Category.OWNER, CommandPermission.OWNER) {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content, String[] args) {
                Evaluator evaluator = evals.get(args[0]);
                if(evaluator == null) {
                    event.getChannel().sendMessage("That's not a valid evaluator, silly.").queue();
                    return;
                }

                String[] values = SPLIT_PATTERN.split(content, 2);
                if(values.length < 2) {
                    event.getChannel().sendMessage("Not enough arguments.").queue();
                    return;
                }

                String v = values[1];

                Object result = evaluator.eval(event, v);
                boolean errored = result instanceof Throwable;

                event.getChannel().sendMessage(new EmbedBuilder()
                        .setAuthor(
                                "Evaluated " + (errored ? "and errored" : "with success"), null,
                                event.getAuthor().getAvatarUrl()
                        )
                        .setColor(errored ? Color.RED : Color.GREEN)
                        .setDescription(
                                result == null ? "Executed successfully with no objects returned" : ("Executed " + (errored ? "and errored: " : "successfully and returned: ") + result
                                        .toString()))
                        .setFooter("Asked by: " + event.getAuthor().getName(), null)
                        .build()
                ).queue();
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Evaluates stuff (A: js/bsh).")
                        .build();
            }
        });
    }

    @Subscribe
    public void link(CommandRegistry cr) {
        cr.register("link", new SimpleCommand(Category.OWNER, CommandPermission.OWNER) {
            @Override
            protected void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content, String[] args) {
                final Config config = MantaroData.config().get();

                if(!config.isPremiumBot()) {
                    event.getChannel().sendMessage("This command can only be ran in MP, as it'll link a guild to an MP holder.").queue();
                    return;
                }

                if(args.length < 2) {
                    event.getChannel().sendMessage("You need to enter both the user and the guild id (example: 132584525296435200 493297606311542784).").queue();
                    return;
                }

                String userString = args[0];
                String guildString = args[1];
                Guild guild = MantaroBot.getInstance().getGuildById(guildString);
                User user = MantaroBot.getInstance().getUserById(userString);
                if(guild == null || user == null) {
                    event.getChannel().sendMessage("User or guild not found.").queue();
                    return;
                }

                final DBGuild dbGuild = MantaroData.db().getGuild(guildString);
                Map<String, String> t = getArguments(args);
                if(t.containsKey("u")) {
                    dbGuild.getData().setMpLinkedTo(null);
                    dbGuild.save();

                    event.getChannel().sendMessageFormat("Un-linked MP for guild %s (%s).", guild.getName(), guild.getId()).queue();
                    return;
                }

                Pair<Boolean, String> pledgeInfo = Utils.getPledgeInformation(user.getId());
                //guaranteed to be an integer
                if(pledgeInfo == null || !pledgeInfo.getLeft() || Double.parseDouble(pledgeInfo.getRight()) < 4) {
                    event.getChannel().sendMessage("Pledge not found, pledge amount not enough or pledge was cancelled.").queue();
                    return;
                }

                //Guild assignment.
                dbGuild.getData().setMpLinkedTo(userString); //Patreon check will run from this user.
                dbGuild.save();

                event.getChannel().sendMessageFormat("Linked MP for guild %s (%s) to user %s (%s). Including this guild in pledge check (id -> user -> pledge).", guild.getName(), guild.getId(), user.getName(), user.getId()).queue();
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("Links a guild to a patreon owner (user id). Use -u to unlink.")
                        .setUsage("`~>link <user id> <guild id>`")
                        .build();
            }
        });
    }

    @Subscribe
    public void owner(CommandRegistry cr) {
        cr.register("owner", new SimpleCommand(Category.OWNER) {
            @Override
            public CommandPermission permission() {
                return CommandPermission.OWNER;
            }

            @Override
            public HelpContent help() {
                return new HelpContent.Builder()
                        .setDescription("`~>owner premium guild <id> <days>` - Adds premium to the specified guild for x days.")
                        .build();
            }

            @Override
            public void call(GuildMessageReceivedEvent event, I18nContext languageContext, String content, String[] args) {
                if(args.length < 1) {
                    event.getChannel().sendMessage("Not enough arguments.").queue();
                    return;
                }

                String option = args[0];

                if(option.equals("premium")) {
                    String sub = args[1].substring(0, args[1].indexOf(' '));
                    if(sub.equals("guild")) {
                        try {
                            String[] values = SPLIT_PATTERN.split(args[1], 3);
                            DBGuild db = MantaroData.db().getGuild(values[1]);
                            db.incrementPremium(TimeUnit.DAYS.toMillis(Long.parseLong(values[2])));
                            db.saveAsync();
                            event.getChannel().sendMessage(EmoteReference.CORRECT +
                                    "The premium feature for guild " + db.getId() + " now is until " +
                                    new Date(db.getPremiumUntil())).queue();
                            return;
                        } catch(IndexOutOfBoundsException e) {
                            event.getChannel().sendMessage(EmoteReference.ERROR + "You need to specify id and number of days").queue();
                            e.printStackTrace();
                            return;
                        }
                    }
                }

                event.getChannel().sendMessage("You're not meant to use this incorrectly, silly.").queue();
            }

            @Override
            public String[] splitArgs(String content) {
                return SPLIT_PATTERN.split(content, 2);
            }
        });
    }

    private interface Evaluator {
        Object eval(GuildMessageReceivedEvent event, String code);
    }

    private static class EvalClassLoader extends ClassLoader {
        public Class<?> define(byte[] bytes) {
            return super.defineClass(null, bytes, 0, bytes.length);
        }
    }
}
