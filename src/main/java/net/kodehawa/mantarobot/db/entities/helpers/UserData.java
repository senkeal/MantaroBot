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

package net.kodehawa.mantarobot.db.entities.helpers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import net.kodehawa.mantarobot.commands.currency.item.PlayerEquipment;
import net.kodehawa.mantarobot.data.MantaroData;
import net.kodehawa.mantarobot.db.entities.Marriage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class UserData {
    private String birthday;
    private boolean hasReceivedFirstKey; //Placeholder here for rethonk plz
    private String premiumKey;
    private int reminderN;
    private String timezone;
    private String lang;
    private int dustLevel; //percentage
    private int equippedPick; //item id, 0 = nothing (even tho in theory 0 its headphones...)
    private int equippedRod; //item id, 0 = nothing
    private PlayerEquipment equippedItems = new PlayerEquipment(new HashMap<>(), new HashMap<>()); //hashmap is type -> itemId

    private boolean receivedExpirationWarning; //premium key about to expire!
    private Map<String, String> keysClaimed = new HashMap<>(); //Map of user -> key. Will be used to account for keys the user can create themselves.

    //NEW MARRIAGE SYSTEM
    private String marriageId;
    //user id, value bought for.
    private Map<String, Long> waifus = new HashMap<>();
    private int waifuSlots = 3;
    private int timesClaimed;

    //Persistent reminders. UUID is saved here.
    private List<String> reminders = new ArrayList<>();

    //TODO
    //Persistent reminders, so it works on bot reboot.
    //private Map<String, ReminderObj> reminderMap = new HashMap<>();

    @JsonIgnore
    public Marriage getMarriage() {
        //we're going full round trip here
        return MantaroData.db().getMarriage(marriageId);
    }

    @JsonIgnore
    public int increaseDustLevel(int by) {
        int increased = dustLevel + Math.min(1, by);
        if(increased >= 100)
            return dustLevel; //same as before, cap at 100.

        this.setDustLevel(increased);
        return this.dustLevel;
    }

}
