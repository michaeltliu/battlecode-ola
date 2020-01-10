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
        MapLocation hqLoc = rc.getLocation();
        int[] message = new int[] {3355678, 5, 100 * hqLoc.x + hqLoc.y, 0, 0, 0, 0};
        boolean messageIsPending = true;
        while (messageIsPending) {
            if (trySubmitTransaction(message, 51)) {
                System.out.println("Location submitted");
                messageIsPending = false;
            }
        }

        int minersActive = 0;
        while (true) {
            System.out.println(turnCount + " " + rc.getCooldownTurns());
            RobotInfo[] enemyBots = rc.senseNearbyRobots(RobotType.HQ.sensorRadiusSquared,
                    rc.getTeam().opponent());
            int closestDistance = Integer.MAX_VALUE;
            RobotInfo closestBot = null;
            for (RobotInfo robot : enemyBots) {
                if (robot.type == RobotType.DELIVERY_DRONE) {
                    int dist = rc.getLocation().distanceSquaredTo(robot.location);
                    if (dist < closestDistance) {
                        closestBot = robot;
                        closestDistance = dist;
                    }
                }
            }

            if (closestBot != null && rc.canShootUnit(closestBot.getID()))
                rc.shootUnit(closestBot.getID());


            if (minersActive < 15) {
                for (Direction dir : directions) {
                    if (tryBuild(RobotType.MINER, dir)) {
                        minersActive++;
                    }
                }
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

        boolean hasDepositDestination = false;
        MapLocation deposit = null;
        HashSet<MapLocation> emptySoupSquares = new HashSet<>();
        HashSet<MapLocation> soupySquares = new HashSet<>();

        int refineryCount = 0;
        HashSet<int[]> refineryLoc = new HashSet<>();

        int designSchoolCount = 0;
        HashSet<int[]> designSchoolLoc = new HashSet<>();

        int fulfillmentCenterCount = 0;
        HashSet<int[]> fulfillmentCenterLoc = new HashSet<>();

        boolean messageIsPending = false;
        int[] pendingMessage = new int[0];
        int pendingMessageCost = 0;

        while (true) {
            System.out.println(turnCount + " " + rc.getCooldownTurns() + " " + hasDepositDestination);
            System.out.println(soupySquares);
            if (messageIsPending) {
                if (trySubmitTransaction(pendingMessage, pendingMessageCost)) {
                    System.out.println("Pending message broadcasted");
                    messageIsPending = false;
                }
            }

            if (designSchoolCount < 2) {
                for (Direction dir : directions) {
                    if (tryBuild(RobotType.DESIGN_SCHOOL, dir)) {
                        System.out.println("Built design school");
                        int[] message = new int[7];
                        message[0] = 3355678;
                        message[1] = 3;
                        MapLocation schoolLoc = rc.getLocation().add(dir);
                        message[2] = 100*schoolLoc.x + schoolLoc.y;
                        if (trySubmitTransaction(message, 20))
                            System.out.println("Broadcast design school");
                        else {
                            messageIsPending = true;
                            pendingMessage = message;
                            pendingMessageCost = 20;
                        }
                        break;
                    }
                }
            }

            if (fulfillmentCenterCount < 2) {
                for (Direction dir : directions) {
                    if (tryBuild(RobotType.FULFILLMENT_CENTER, dir)) {
                        System.out.println("Built fulfillment center");
                        int[] message = new int[7];
                        message[0] = 3355678;
                        message[1] = 4;
                        MapLocation centerLoc = rc.getLocation().add(dir);
                        message[2] = 100*centerLoc.x + centerLoc.y;
                        if (trySubmitTransaction(message, 20)) {
                            System.out.println("Broadcast fulfillment center");
                        }
                        else {
                            messageIsPending = true;
                            pendingMessage = message;
                            pendingMessageCost = 20;
                        }
                        break;
                    }
                }
            }

            if (rc.getSoupCarrying() == RobotType.MINER.soupLimit) {
                System.out.print("Reached max soup carry capacity ");
                if (rc.getLocation().distanceSquaredTo(hqLoc) > 2) {
                    if (tryMove(rc.getLocation().directionTo(hqLoc)))
                        System.out.println("and headed to HQ");
                }
                else {
                    if (tryRefine(rc.getLocation().directionTo(hqLoc)))
                        System.out.println("and refined!");
                }
            }

            // checks the blockchain every 6 turns
            if (turnCount % 6 == 0) {
                System.out.println("Checking the blockchain");
                // Gets the past 10 transactions
                int round = rc.getRoundNum();
                HashSet<Transaction> prevTransactions = new HashSet<>();
                for (int i = Math.max(1, round - 10); i < round; i ++) {
                    prevTransactions.addAll(Arrays.asList(rc.getBlock(i)));
                }

                // Analyzes the received transactions
                for (Transaction trans : prevTransactions) {
                    int[] message = trans.getMessage();
                    if (message[0] == 3355678) {
                        if (message[1] == 1) {
                            int destinationx = message[2] / 100;
                            int destinationy = message[2] % 100;
                            MapLocation dep = new MapLocation(destinationx, destinationy);
                            soupySquares.add(dep);

                            if (!hasDepositDestination) {
                                System.out.println("New deposit destination acquired from blockchain");
                                deposit = closestLocation(soupySquares);
                                hasDepositDestination = true;
                                if (rc.getLocation().distanceSquaredTo(deposit) > 2)
                                    tryMove(rc.getLocation().directionTo(deposit));
                                else
                                    tryMine(rc.getLocation().directionTo(deposit));
                            }
                        }
                        else if (message[1] == 2) {
                            refineryCount ++;
                            System.out.println("Notified of new refinery");
                            refineryLoc.add(new int[] {message[2] / 100, message[2] % 100});
                        }
                        else if (message[1] == 3) {
                            designSchoolCount ++;
                            System.out.println("Notified of new design school");
                            designSchoolLoc.add(new int[] {message[2] / 100, message[2] % 100});
                        }
                        else if (message[1] == 4) {
                            fulfillmentCenterCount ++;
                            System.out.println("Notified of new fulfillment center");
                            fulfillmentCenterLoc.add(new int[] {message[2] / 100, message[2] % 100});
                        }
                    }
                }
            }

            // miner moves in the direction of its destination
            if (hasDepositDestination) {
                System.out.println("Have deposit destination");
                if (rc.canSenseLocation(deposit)) {
                    int amt = rc.senseSoup(deposit);
                    if (amt == 0) {
                        soupySquares.remove(deposit);
                        emptySoupSquares.add(deposit);
                        hasDepositDestination = false;
                    }
                }
                Direction toDeposit = rc.getLocation().directionTo(deposit);
                if (rc.getLocation().distanceSquaredTo(deposit) <= 2) {
                    if (tryMine(toDeposit)) {
                        System.out.println("Mined deposit!");
                    }
                }
                else {
                    if (tryMove(toDeposit)) {
                        System.out.println("Moved towards deposit!");
                    }
                }
            }
            else if (!soupySquares.isEmpty()) {
                MapLocation closestLoc = closestLocation(soupySquares);
                hasDepositDestination = true;
                if (tryMove(rc.getLocation().directionTo(closestLoc)))
                    System.out.println("Moved towards closest deposit!");
                else if (tryMine(rc.getLocation().directionTo(closestLoc)))
                    System.out.println("Mined closest deposit!");
            }
            // random walking
            else {
                System.out.println("trying walking");
                if (tryMove(randomDirection()))
                    System.out.println("random walking");
            }


            // sense soup nearby
            if (turnCount % 4 == 0){
                System.out.println("sensing soup nearby");

                MapLocation selfLoc = rc.getLocation();
                int x = selfLoc.x;
                int y = selfLoc.y;

                int maxdelta = (int) Math.sqrt(rc.getCurrentSensorRadiusSquared());
                for (int dx = -maxdelta; dx <= maxdelta; dx ++) {
                    for (int dy = -maxdelta; dy <= maxdelta; dy ++) {
                        MapLocation loc = new MapLocation(x + dx, y + dy);
                        if (!emptySoupSquares.contains(loc) && !soupySquares.contains(loc) &&
                                rc.canSenseLocation(loc)) {
                            int amt = rc.senseSoup(loc);
                            if (amt > 0) {
                                System.out.println("Soup deposit sensed at" + loc.toString());
                                soupySquares.add(loc);
                                hasDepositDestination = true;

                                if (amt > 50) {
                                    int[] message = new int[7];
                                    message[0] = 3355678;
                                    message[1] = 1;
                                    message[2] = 100 * loc.x + loc.y;
                                    message[3] = amt;
                                    if (trySubmitTransaction(message, 0))
                                        System.out.println("Soup deposit broadcasted");
                                    else {
                                        messageIsPending = true;
                                        pendingMessage = message;
                                        pendingMessageCost = 10;
                                    }
                                }
                            }
                            else {
                                emptySoupSquares.add(loc);
                            }
                        }
                    }
                }
                deposit = closestLocation(soupySquares);
                if (!tryMove(selfLoc.directionTo(deposit)))
                    tryMine(selfLoc.directionTo(deposit));
            }

            // tryBuild(randomSpawnedByMiner(), randomDirection());
            turnCount ++;
            Clock.yield();
        }
    }

    static MapLocation closestLocation(Collection<MapLocation> c) {
        MapLocation self = rc.getLocation();
        int closestDist = Integer.MAX_VALUE;
        MapLocation closestLoc = null;
        for (MapLocation loc : c) {
            int dist = self.distanceSquaredTo(loc);
            if (dist < closestDist) {
                closestDist = dist;
                closestLoc = loc;
            }
        }
        return closestLoc;
    }

    static void runRefinery() throws GameActionException {
        // System.out.println("Pollution: " + rc.sensePollution(rc.getLocation()));
    }

    static void runVaporator() throws GameActionException {

    }

    static void runDesignSchool() throws GameActionException {
        int landscaperCount = 0;
        while (true) {
            System.out.println("Entered loop");
            for (Direction dir : directions) {
                if (tryBuild(RobotType.LANDSCAPER, dir)) {
                    System.out.println("Built landscaper");
                    landscaperCount ++;
                }
            }
            turnCount ++;
            Clock.yield();
        }
    }

    static void runFulfillmentCenter() throws GameActionException {
        int droneCount = 0;
        while (true) {
            System.out.println("Entered loop");
            for (Direction dir : directions) {
                if (tryBuild(RobotType.DELIVERY_DRONE, dir)) {
                    System.out.println("Built drone");
                    droneCount ++;
                }
            }
            turnCount ++;
            Clock.yield();
        }
    }

    static MapLocation getHQLocation() throws GameActionException {
        int checkRound = 1;
        boolean foundHQLoc = false;
        while (!foundHQLoc) {
            Transaction[] transactions = rc.getBlock(checkRound);
            for (Transaction trans : transactions) {
                int[] message = trans.getMessage();
                if (message[0] == 3355678 && message[1] == 5) {
                    return new MapLocation(message[2] / 100, message[2] % 100);
                }
            }
            checkRound ++;
        }
        return null;
    }

    static void runLandscaper() throws GameActionException {
        MapLocation hqLoc = getHQLocation();

        while (true) {
            if (rc.getDirtCarrying() < RobotType.LANDSCAPER.dirtLimit) {
                MapLocation selfLoc = rc.getLocation();
                for (Direction dir : directions) {
                    if (rc.senseElevation(selfLoc) - rc.senseElevation(selfLoc.add(dir)) < 2 &&
                            rc.canDigDirt(dir)) {
                        rc.digDirt(dir);
                        System.out.println("Dug dirt");
                    }
                }
            }
            else {
                System.out.println("Returning to HQ to build wall");
            }
            tryMove(directions[4]);
            Clock.yield();
        }
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
            turnCount ++;
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
        if (dir == null) return false;
        if (rc.isReady() && rc.canMove(dir)) {
            rc.move(dir);
            return true;
        } return false;
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
        if (dir == null) return false;
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


    static boolean trySubmitTransaction(int[] message, int cost) throws GameActionException {
        if (rc.canSubmitTransaction(message, cost)) {
            rc.submitTransaction(message, cost);
            return true;
        }
        return false;
    }
}
