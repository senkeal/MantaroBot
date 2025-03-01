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

package net.kodehawa.mantarobot.commands.custom.legacy;

import net.dv8tion.jda.api.entities.Message;

import static net.kodehawa.mantarobot.utils.StringUtils.splitArgs;

public class CustomMessage {
    private Message message;
    private String prefix;

    public CustomMessage(Message message, String prefix) {
        this.message = message;
        this.prefix = prefix;
    }

    public String getContentRaw() {
        if(prefix.isEmpty()) {
            return splitArgs(message.getContentRaw(), 2)[1];
        }

        return splitArgs(message.getContentRaw().replace(prefix, "").trim(), 2)[1];
    }

    public String getContentDisplay() {
        if(prefix.isEmpty()) {
            return splitArgs(message.getContentDisplay(), 2)[1];
        }

        return splitArgs(message.getContentDisplay().replace(prefix, "").trim(), 2)[1];
    }

    public String getContentStripped() {
        if(prefix.isEmpty()) {
            return splitArgs(message.getContentStripped(), 2)[1];
        }

        return splitArgs(message.getContentStripped().replace(prefix, "").trim(), 2)[1];
    }
}
