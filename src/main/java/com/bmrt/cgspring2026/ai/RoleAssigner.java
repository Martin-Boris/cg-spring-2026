package com.bmrt.cgspring2026.ai;

import com.bmrt.cgspring2026.model.Troll;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RoleAssigner {

    private RoleAssigner() {
    }

    public static Map<Integer, Role> assign(List<Troll> myTrolls) {
        Map<Integer, Role> result = new HashMap<>();
        if (myTrolls.isEmpty()) {
            return result;
        }
        if (myTrolls.size() == 1) {
            result.put(myTrolls.get(0).id, Role.LOCAL);
            return result;
        }
        Troll leader = myTrolls.get(0);
        int leaderScore = score(leader);
        for (int i = 1; i < myTrolls.size(); i++) {
            Troll t = myTrolls.get(i);
            int s = score(t);
            if (s > leaderScore || (s == leaderScore && t.id < leader.id)) {
                leader = t;
                leaderScore = s;
            }
        }
        for (Troll t : myTrolls) {
            result.put(t.id, t == leader ? Role.LEADER : Role.LOCAL);
        }
        return result;
    }

    private static int score(Troll t) {
        return t.movementSpeed + t.carryCapacity + t.chopPower;
    }
}
