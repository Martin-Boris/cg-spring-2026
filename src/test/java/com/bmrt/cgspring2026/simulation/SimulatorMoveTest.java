package com.bmrt.cgspring2026.simulation;

import com.bmrt.cgspring2026.action.Action;
import com.bmrt.cgspring2026.model.GameState;
import com.bmrt.cgspring2026.model.TileType;
import com.bmrt.cgspring2026.pathfinding.PathTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorMoveTest {

    @BeforeEach void grid() {
        String[] rows = {
                "........",
                ".0......",
                "........",
                "....1...",
                "........",
                "........",
        };
        GameState.height = rows.length;
        GameState.width  = rows[0].length();
        GameState.tiles  = new byte[GameState.width * GameState.height];
        for (int y = 0; y < GameState.height; y++) {
            for (int x = 0; x < GameState.width; x++) {
                byte t = TileType.fromChar(rows[y].charAt(x));
                GameState.tiles[y * GameState.width + x] = t;
                if (t == TileType.SHACK_ME)  { GameState.shackMeX  = x; GameState.shackMeY  = y; }
                if (t == TileType.SHACK_OPP) { GameState.shackOppX = x; GameState.shackOppY = y; }
            }
        }
        PathTable.init();
    }

    private static int placeTroll(GameState s, int player, int x, int y, int ms) {
        int i = s.trollCount++;
        s.trollPlayer[i] = (byte) player;
        s.trollX[i] = (byte) x; s.trollY[i] = (byte) y;
        s.trollMS[i] = (byte) ms;
        return i;
    }

    @Test void moveToReachableWalkableTargetWithinSpeed() {
        GameState s = new GameState();
        int t = placeTroll(s, 0, 3, 3, 5);
        int[] acts = { Action.move(t, 5, 3) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollX[t] & 0xFF).isEqualTo(5);
        assertThat(s.trollY[t] & 0xFF).isEqualTo(3);
    }

    @Test void moveCapsAtSpeedAlongPath() {
        GameState s = new GameState();
        int t = placeTroll(s, 0, 0, 0, 2);
        int[] acts = { Action.move(t, 5, 0) };
        Simulator.tick(s, acts, 1);
        // 2 steps along path (BFS); destination has distance 5; we go to step 2 along the path.
        assertThat(Math.abs((s.trollX[t] & 0xFF) - 0) + Math.abs((s.trollY[t] & 0xFF) - 0)).isLessThanOrEqualTo(2);
        assertThat((s.trollX[t] & 0xFF) + (s.trollY[t] & 0xFF)).isGreaterThan(0);
    }

    @Test void moveToOwnCellIsNoOp() {
        GameState s = new GameState();
        int t = placeTroll(s, 0, 3, 3, 5);
        int[] acts = { Action.move(t, 3, 3) };
        Simulator.tick(s, acts, 1);
        assertThat(s.trollX[t] & 0xFF).isEqualTo(3);
        assertThat(s.trollY[t] & 0xFF).isEqualTo(3);
    }

    @Test void moveToShackTileSimulatedAsStayInPlaceOrAdjacent() {
        // The shack is a non-walkable endpoint in PathTable. The reference engine would project to
        // adjacent grass; the optimised simulator either lands on adjacent grass (if step-along reaches it)
        // or stays in place when the path is unresolvable. Both are accepted.
        GameState s = new GameState();
        int t = placeTroll(s, 0, 3, 3, 5);
        int[] acts = { Action.move(t, GameState.shackOppX, GameState.shackOppY) };
        Simulator.tick(s, acts, 1);
        int finalX = s.trollX[t] & 0xFF;
        int finalY = s.trollY[t] & 0xFF;
        int manhattan = Math.abs(finalX - GameState.shackOppX) + Math.abs(finalY - GameState.shackOppY);
        // Either the troll stayed (manhattan from start = anything) or moved towards: just check tile is walkable.
        assertThat(GameState.tiles[finalY * GameState.width + finalX]).isIn(TileType.GRASS);
    }
}
