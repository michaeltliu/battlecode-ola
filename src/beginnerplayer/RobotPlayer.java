package beginnerplayer;
import battlecode.common.*;

import java.util.*;

public strictfp class RobotPlayer {
    static RobotController rc;

    static Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST
    };
    static RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};

    static int turnCount;
    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        turnCount = 0;

        System.out.println("I'm a " + rc.getType() + " and I just got created!");
        // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
        try {
            // Here, we've separated the controls into a different method for each RobotType.
            // You can add the missing ones or rewrite this into your own control structure.
            switch (rc.getType()) {
                case HQ:                 runHQ();                break;
                case MINER:              runMiner();             break;
                case REFINERY:           runRefinery();          break;
                case VAPORATOR:          runVaporator();         break;
                case DESIGN_SCHOOL:      runDesignSchool();      break;
                case FULFILLMENT_CENTER: runFulfillmentCenter(); break;
                case LANDSCAPER:         runLandscaper();        break;
                case DELIVERY_DRONE:     runDeliveryDrone();     break;
                case NET_GUN:            runNetGun();            break;
            }

        } catch (Exception e) {
            System.out.println(rc.getType() + " Exception");
            e.printStackTrace();
        }
    }

    static void runHQ() throws GameActionException {
        int minersActive = 0;
        while (true) {
            if (minersActive < 15) {
                if (tryBuild(RobotType.MINER, randomDirection()))
                    minersActive++;
            }
            turnCount ++;
            Clock.yield();
        }
    }

    static void runMiner() throws GameActionException {
        // -----------------------------------------------------------------------------------
        // collects HQ coordinates, so we know the location of at least one refinery
        // one-time call, so outside the while loop
        MapLocation hqLoc = null;
        RobotInfo[] nearby = rc.senseNearbyRobots(2, rc.getTeam());
        for (RobotInfo i : nearby) {
            if (i.type == RobotType.HQ) {
                hqLoc = i.location;
            }
        }

        boolean hasDestination = false;
        int destinationx = -1;
        int destinationy = -1;
        HashSet<MapLocation> emptySoupSquares = new HashSet<>();
        HashMap<MapLocation, Integer> soupySquares = new HashMap<>();

        while (true) {
            System.out.println(turnCount);
            for (Direction dir : directions) {
                if (tryRefine(dir)) {
                    System.out.println("refined");
                }

            }
            for (Direction dir : directions) {
                if (tryMine(dir)) {
                    System.out.println("mined");
                    System.out.println(rc.getSoupCarrying());
                }
            }

            if (rc.getSoupCarrying() == RobotType.MINER.soupLimit &&
                    rc.getLocation().distanceSquaredTo(hqLoc) > 2) {
                tryMove(rc.getLocation().directionTo(hqLoc));
            }

            // miner moves in the direction of his destination
            if (hasDestination) {
                if (rc.canSenseLocation(new MapLocation(destinationx, destinationy))) {
                    int amt = rc.senseSoup(new MapLocation(destinationx, destinationy));
                    if (amt == 0) {
                        hasDestination = false;
                    }
                }
                tryMove(rc.getLocation().directionTo(new MapLocation(destinationx, destinationy)));
            }
            // checks every 30 turns if there are coordinates to an open soup deposit
            else if (turnCount % 5 == 0) {
                int depositLoc = searchBlockchainForDeposit(5);
                if (depositLoc >= 0) {
                    System.out.println("GOT COMM");
                    destinationx = depositLoc / 100;
                    destinationy = depositLoc % 100;
                    hasDestination = true;
                    tryMove(rc.getLocation().directionTo(new MapLocation(destinationx, destinationy)));
                }
            }
                // if miner couldn't find soup deposit coordinates
            else if (turnCount % 3 == 0){
                int maxdelta = (int) Math.sqrt(rc.getCurrentSensorRadiusSquared());
                int x = rc.getLocation().x;
                int y = rc.getLocation().y;
                outer: for (int dx = -maxdelta; dx <= maxdelta; dx ++) {
                    for (int dy = -maxdelta; dy <= maxdelta; dy ++) {
                        MapLocation loc = new MapLocation(x + dx, y + dy);
                        if (!emptySoupSquares.contains(loc) && rc.canSenseLocation(loc)) {
                            int amt = rc.senseSoup(loc);
                            if (amt > 0) {
                                soupySquares.put(loc, amt);
                                destinationx = loc.x;
                                destinationy = loc.y;
                                hasDestination = true;
                                tryMove(rc.getLocation().directionTo(new MapLocation(destinationx, destinationy)));
                                System.out.println("got here 2");
                                if (amt > 0) {
                                    System.out.println("got here 3");
                                    int[] message = new int[7];
                                    message[0] = 3355678;
                                    message[1] = 1;
                                    message[2] = 100 * loc.x + loc.y;
                                    message[3] = amt;
                                    if (rc.canSubmitTransaction(message, 10))
                                        rc.submitTransaction(message, 10);
                                }
                                break outer;
                            }
                            else {
                                emptySoupSquares.add(loc);
                            }
                        }
                    }
                }
            }
            // miner doesn't have destination and isn't reading the blockchain
            else {
                if (tryMove(Direction.NORTHEAST))
                    System.out.println("random walking");
            }

            // tryBuild(randomSpawnedByMiner(), randomDirection());
            turnCount ++;
            Clock.yield();
        }
    }

    /*
    @param x - how many rounds back to search
     */
    static int searchBlockchainForDeposit(int x) throws GameActionException {
        int round = rc.getRoundNum();
        ArrayList<Transaction> prevTransactions = new ArrayList<>();
        for (int i = Math.max(1, round - x); i < round; i ++) {
            prevTransactions.addAll(Arrays.asList(rc.getBlock(i)));
        }
        for (Transaction trans : prevTransactions) {
            int[] message = trans.getMessage();
            if (message[0] == 3355678 && message[1] == 1) {
                return message[2];
            }
        }
        return -1;
    }

    static void runRefinery() throws GameActionException {
        // System.out.println("Pollution: " + rc.sensePollution(rc.getLocation()));
    }

    static void runVaporator() throws GameActionException {

    }

    static void runDesignSchool() throws GameActionException {

    }

    static void runFulfillmentCenter() throws GameActionException {
        while (true) {
            for (Direction dir : directions)
                tryBuild(RobotType.DELIVERY_DRONE, dir);
            turnCount ++;
            Clock.yield();
        }
    }

    static void runLandscaper() throws GameActionException {

    }

    static void runDeliveryDrone() throws GameActionException {
        while (true) {
            Team enemy = rc.getTeam().opponent();
            if (!rc.isCurrentlyHoldingUnit()) {
                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] robots = rc.senseNearbyRobots(GameConstants.DELIVERY_DRONE_PICKUP_RADIUS_SQUARED, enemy);

                if (robots.length > 0) {
                    // Pick up a first robot within range
                    rc.pickUpUnit(robots[0].getID());
                }
                else {
                    // No close robots, so search for robots within sight radius
                    tryMove(randomDirection());
                }
            }
            Clock.yield();
        }
    }

    static void runNetGun() throws GameActionException {

    }

    /**
     * Returns a random Direction.
     *
     * @return a random Direction
     */
    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

    /**
     * Returns a random RobotType spawned by miners.
     *
     * @return a random RobotType
     */
    static RobotType randomSpawnedByMiner() {
        return spawnedByMiner[(int) (Math.random() * spawnedByMiner.length)];
    }

    static boolean tryMove() throws GameActionException {
        for (Direction dir : directions)
            if (tryMove(dir))
                return true;
        return false;
        // MapLocation loc = rc.getLocation();
        // if (loc.x < 10 && loc.x < loc.y)
        //     return tryMove(Direction.EAST);
        // else if (loc.x < 10)
        //     return tryMove(Direction.SOUTH);
        // else if (loc.x > loc.y)
        //     return tryMove(Direction.WEST);
        // else
        //     return tryMove(Direction.NORTH);
    }

    /**
     * Attempts to move in a given direction.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        // System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " + rc.getCooldownTurns() + " " + rc.canMove(dir));
        if (rc.isReady() && rc.canMove(dir)) {
            rc.move(dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to build a given robot in a given direction.
     *
     * @param type The type of the robot to build
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryBuild(RobotType type, Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canBuildRobot(type, dir)) {
            rc.buildRobot(type, dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to mine soup in a given direction.
     *
     * @param dir The intended direction of mining
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMineSoup(dir)) {
            rc.mineSoup(dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to refine soup in a given direction.
     *
     * @param dir The intended direction of refining
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryRefine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDepositSoup(dir)) {
            rc.depositSoup(dir, rc.getSoupCarrying());
            return true;
        } else return false;
    }


    static void tryMinerBlockchain() throws GameActionException {
        int[] message = new int[7];
        message[0] = 3355678;
        message[1] = 1;
        message[2] = 1;
        if (rc.canSubmitTransaction(message, 10))
            rc.submitTransaction(message, 10);
        // System.out.println(rc.getRoundMessages(turnCount-1));
    }
}
