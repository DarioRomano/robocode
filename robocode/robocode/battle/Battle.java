/*******************************************************************************
 * Copyright (c) 2001, 2008 Mathew A. Nelson and Robocode contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://robocode.sourceforge.net/license/cpl-v10.html
 *
 * Contributors:
 *     Mathew A. Nelson
 *     - Initial API and implementation
 *     Flemming N. Larsen
 *     - Code cleanup
 *     - Replaced the ContestantPeerVector, BulletPeerVector, and RobotPeerVector
 *       with plain Vector
 *     - Integration of new render classes placed under the robocode.gfx package
 *     - BattleView is not given via the constructor anymore, but is retrieved
 *       from the RobocodeManager. In addition, the battleView is now allowed to
 *       be null, e.g. if no GUI is available
 *     - Ported to Java 5.0
 *     - Bugfixed sounds that were cut off after first battle
 *     - Changed initialize() to use loadClass() instead of loadRobotClass() if
 *       security is turned off
 *     - Changed the way the TPS is loaded and updated
 *     - Added updateTitle() in order to manage and update the title on the
 *       RobocodeFrame
 *     - Added replay feature
 *     - Updated to use methods from the Logger, which replaces logger methods
 *       that has been (re)moved from the robocode.util.Utils class
 *     - Changed so robots die faster graphically when the battles are over
 *     - Changed cleanup to only remove the robot in the robot peers, as the
 *       robot peers themselves are used for replay recording
 *     - Added support for playing background music when the battle is ongoing
 *     - Removed unnecessary catches of NullPointerExceptions
 *     - Added support for setting the initial robot positions on the battlefield
 *     - Removed the showResultsDialog field which is replaced by the
 *       getOptionsCommonShowResults() from the properties
 *     - Simplified the code in the run() method when battle is stopped
 *     - Changed so that stop() makes the current round stop immediately
 *     - Added handling keyboard events thru a KeyboardEventDispatcher
 *     - Added mouseMoved(), mouseClicked(), mouseReleased(), mouseEntered(),
 *       mouseExited(), mouseDragged(), mouseWheelMoved()
 *     - Changed to take the new JuniorRobot class into account
 *     - When cleaning up robots their static fields are now being cleaned up
 *     - Bugfix: Changed the runRound() so that the robot are painted after
 *       they have made their turn
 *     - The thread handling for unsafe robot loading has been put in an
 *       independent UnsafeLoadRobotsThread class. In addition, the battle
 *       thread is not sharing it's run() method anymore with the
 *       UnsafeLoadRobotsThread, which has now got its own run() method
 *     - The 'running' and 'aborted' flags are now synchronized towards
 *       'battleMonitor' instead of 'this' object
 *     - Added waitTillRunning() method so another thread can be blocked until
 *       the battle has started running
 *     - Replaced synchronizedList on lists for deathEvent, robots, bullets,
 *       and contestants with a CopyOnWriteArrayList in order to prevent
 *       ConcurrentModificationExceptions when accessing these list via
 *       Iterators using public methods to this class
 *     - The moveBullets() was simplified and moved inside the runRound() method
 *     - The flushOldEvents() method was moved into the runRound() method
 *     - Major bugfix: Two robots running with exactly the same code was getting
 *       different scores. Robots listed before other robots always got a better
 *       score in the end. Hence, the getRobotsAtRandom() method has been added
 *       in order to gain fair play, and this method should be used where robots
 *       are checked and awakened in turn
 *     - Simplified the repainting of the battle
 *     - Bugfix: In wakeupRobots(), only wakeup a robot that is running and alive
 *     - A StatusEvent is now send to all alive robot each turn
 *     - Extended allowed max. length of a robot's full package name from 16 to
 *       32 characters
 *     Luis Crespo
 *     - Added sound features using the playSounds() method
 *     - Added debug step feature
 *     - Added isRunning()
 *     Robert D. Maupin
 *     - Replaced old collection types like Vector and Hashtable with
 *       synchronized List and HashMap
 *     Titus Chen
 *     - Bugfix: Added Battle parameter to the constructor that takes a
 *       BulletRecord as parameter due to a NullPointerException that was raised
 *       as the battleField variable was not intialized
 *     Nathaniel Troutman
 *     - Bugfix: In order to prevent memory leaks, the cleanup() method has now
 *       been extended to cleanup all robots, but also all classes that this
 *       class refers to in order to avoid circular references. In addition,
 *       cleanup has been added to the KeyEventHandler
 *     Julian Kent
 *     - Fix: Method for using only nano second precision when using
 *       RobotPeer.wait(0, nanoSeconds) in order to prevent the millisecond
 *       granularity issue, which is typically were coarse compared to the one
 *       with nano seconds 
 *     Pavel Savara
 *     - Re-work of robot interfaces
 *     - Refactored large methods into several smaller methods
 *******************************************************************************/
package robocode.battle;


import robocode.BattleEndedEvent;
import robocode.*;
import robocode.battle.events.*;
import robocode.battle.snapshot.TurnSnapshot;
import robocode.common.Command;
import robocode.control.RandomFactory;
import robocode.control.RobotResults;
import robocode.control.RobotSpecification;
import robocode.io.Logger;
import static robocode.io.Logger.logMessage;
import robocode.manager.RobocodeManager;
import robocode.peer.*;
import robocode.peer.robot.RobotClassManager;
import robocode.robotinterfaces.IBasicRobot;
import robocode.security.RobocodeClassLoader;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * The {@code Battle} class is used for controlling a battle.
 *
 * @author Mathew A. Nelson (original)
 * @author Flemming N. Larsen (contributor)
 * @author Luis Crespo (contributor)
 * @author Robert D. Maupin (contributor)
 * @author Titus Chen (contributor)
 * @author Nathaniel Troutman (contributor)
 * @author Julian Kent (contributor)
 * @author Pavel Savara (contributor)
 */
public final class Battle extends BaseBattle {

	// Allowed maximum length for a robot's full package name
	private final static int MAX_FULL_PACKAGE_NAME_LENGTH = 32;
	// Allowed maximum length for a robot's short class name
	private final static int MAX_SHORT_CLASS_NAME_LENGTH = 32;

	// Inactivity related items
	private int inactiveTurnCount;
	private double inactivityEnergy;

	// Turn skip related items
	private boolean parallelOn;
	private double parallelConstant;

	// Objects in the battle
    private List<RobotClassManager> battlingRobotsList;
    private List<RobotPeer> robots = new ArrayList<RobotPeer>();
	private List<ContestantPeer> contestants = new ArrayList<ContestantPeer>();
	private List<BulletPeer> bullets = new CopyOnWriteArrayList<BulletPeer>();
	private int activeRobots;

	// Death events
	private List<RobotPeer> deathRobots = new CopyOnWriteArrayList<RobotPeer>();

	// Flag specifying if debugging is enabled thru the debug command line option
	private boolean isDebugging;

	// Robot loading related items
	private Thread unsafeLoadRobotsThread;
	private final AtomicBoolean isUnsafeLoaderThreadRunning = new AtomicBoolean(false);
	private final AtomicBoolean isRobotsLoaded = new AtomicBoolean(false);

	// Initial robot start positions (if any)
	private double[][] initialRobotPositions;

	public Battle(List<RobotClassManager> battlingRobotsList, BattleProperties battleProperties, RobocodeManager manager, BattleEventDispatcher eventDispatcher, boolean paused) {
		super(manager, eventDispatcher, paused);
        this.battlingRobotsList=battlingRobotsList;
		isDebugging = System.getProperty("debug", "false").equals("true");
        setBattleRules(new BattleRules(battleProperties));
        setInitialPositions(battleProperties.getInitialPositions());
        createPeers();
    }

    private void createPeers(){
        //create teams
        Hashtable<String, Integer> countedNames=new Hashtable<String, Integer>();
        List<String> teams = new ArrayList<String>();

        //count duplicate robots, enumerate teams
        for (RobotClassManager rcm : battlingRobotsList){
            final String name = rcm.getClassNameManager().getFullClassNameWithVersion();
            if(countedNames.containsKey(name)){
                int value = countedNames.get(name);
                countedNames.put(name, value==1 ? 3 : value+1);
            }
            else{
                countedNames.put(name, 1);
            }

            String teamFullName = rcm.getTeamName();
            if (teamFullName!=null){
                if (!teams.contains(teamFullName)){
                    teams.add(teamFullName);
                    String teamName=teamFullName.substring(0,teamFullName.length()-6);
                    if(countedNames.containsKey(teamName)){
                        int value = countedNames.get(teamName);
                        countedNames.put(teamName, value==1 ? 3 : value+1);
                    }
                    else{
                        countedNames.put(teamName, 1);
                    }
                }
            }
        }

        //name teams
        Hashtable<String, TeamPeer> namedTeams=new Hashtable<String, TeamPeer>();
        for (String teamFullName : teams){
            String name=teamFullName.substring(0,teamFullName.length()-6);
            final Integer value = countedNames.get(name);
            String newName=name;
            if (value > 1){
                newName = name + "(" + (value - 1) + ")";
            }
            countedNames.put(name, value - 1);
            namedTeams.put(teamFullName, new TeamPeer(newName));
        }

        //name robots
        for (RobotClassManager rcm : battlingRobotsList){
            final String name = rcm.getClassNameManager().getFullClassNameWithVersion();
            final Integer value = countedNames.get(name);
            int duplicate = -1;
            if (value > 1){
                duplicate = (value - 2);
            }
            countedNames.put(name, value - 1);

            TeamPeer team = null;
            final String teamFullName = rcm.getTeamName();
            if (teamFullName!=null){
                team = namedTeams.get(teamFullName);
            }
            RobotPeer robotPeer = new RobotPeer(this, rcm, duplicate, team);
            robots.add(robotPeer);
        }
    }

	public void registerDeathRobot(RobotPeer r) {
		deathRobots.add(r);
	}

	public BattleRules getBattleRules() {
		return battleRules;
	}

	public int getRobotsCount() {
		return robots.size();
	}

	public boolean isDebugging() {
		return isDebugging;
	}

	public void removeBullet(BulletPeer bullet) {
		bullets.remove(bullet);
	}

    public void addBullet(BulletPeer bullet) {
        bullets.add(bullet);
    }

	public void resetInactiveTurnCount(double energyLoss) {
		if (energyLoss < 0) {
			return;
		}
		inactivityEnergy += energyLoss;
		while (inactivityEnergy >= 10) {
			inactivityEnergy -= 10;
			inactiveTurnCount = 0;
		}
	}

	/**
	 * Gets the activeRobots.
	 *
	 * @return Returns a int
	 */
	public synchronized int getActiveRobots() {
		return activeRobots;
	}

	@Override
	public synchronized void cleanup() {

		if (contestants != null) {
			contestants.clear();
			contestants = null;
		}

		if (robots != null) {
			for (RobotPeer r : robots) {
				r.cleanup();
			}
			robots.clear();
			robots = null;
		}

		super.cleanup();

		battleManager = null;

		Logger.setLogListener(null);

		// Request garbage collecting
		for (int i = 4; i >= 0; i--) { // Make sure it is run
			System.gc();
		}
	}

	@Override
	protected void initializeBattle() {
		super.initializeBattle();

		// Starting loader thread
		ThreadGroup unsafeThreadGroup = new ThreadGroup("Robot Loader Group");
		unsafeThreadGroup.setDaemon(true);
		unsafeThreadGroup.setMaxPriority(Thread.NORM_PRIORITY);
		unsafeLoadRobotsThread = new UnsafeLoadRobotsThread(unsafeThreadGroup);
		manager.getThreadManager().setRobotLoaderThread(unsafeLoadRobotsThread);
		unsafeLoadRobotsThread.start();

        initBattleRobots();

        parallelOn = System.getProperty("PARALLEL", "false").equals("true");
        if (parallelOn) {
            // how could robots share CPUs ?
            parallelConstant = robots.size() / Runtime.getRuntime().availableProcessors();
            // four CPUs can't run two single threaded robot faster than two CPUs
            if (parallelConstant < 1) {
                parallelConstant = 1;
            }
        }

		final TurnSnapshot snapshot = new TurnSnapshot(this, robots, bullets);

		eventDispatcher.onBattleStarted(new BattleStartedEvent(snapshot, battleRules, false));
		if (isPaused()) {
			eventDispatcher.onBattlePaused(new BattlePausedEvent());
		}
	}

	@Override
	protected void shutdownBattle() {
		try {
            synchronized (isUnsafeLoaderThreadRunning){
                isUnsafeLoaderThreadRunning.notify();
                isUnsafeLoaderThreadRunning.wait(10);
            }
            unsafeLoadRobotsThread.interrupt();
            unsafeLoadRobotsThread.join(5000);
		} catch (InterruptedException e) {
			Logger.logError(e);
		}

		for (RobotPeer r : robots) {
			r.getRobotThreadManager().cleanup();
		}

		super.shutdownBattle();

		// At this point the 'running' state should be false = not running, before the scores are calculated

		eventDispatcher.onBattleEnded(new robocode.battle.events.BattleEndedEvent(isAborted()));

		if (!isAborted()) {
			eventDispatcher.onBattleCompleted(new BattleCompletedEvent(battleRules, computeResults()));
		}
	}

	@Override
	protected void finalizeBattle() {
		super.finalizeBattle();
		for (RobotPeer r : robots) {
			r.getRobotStatistics().resetScores();
		}
	}

	@Override
	protected void preloadRound() {
		super.preloadRound();
		loadRoundRobots();

		computeActiveRobots();

		manager.getThreadManager().reset();

		// Turning on robots
		startRobots();
	}

	@Override
	protected void initializeRound() {
		super.initializeRound();
		inactiveTurnCount = 0;

		eventDispatcher.onRoundStarted(new RoundStartedEvent(getRoundNum()));
	}

	@Override
	protected void finalizeRound() {
		super.finalizeRound();

		eventDispatcher.onRoundEnded(new RoundEndedEvent(getRoundNum(), getTime()));
	}

	@Override
	protected void cleanupRound() {
		super.cleanupRound();

		bullets.clear();
		for (RobotPeer r : robots) {
			r.getRobotStatistics().generateTotals();
			r.getRobotThreadManager().waitForStop();
		}
	}

	@Override
	protected void initializeTurn() {
		super.initializeTurn();

		eventDispatcher.onTurnStarted(new TurnStartedEvent());
	}

	@Override
	protected void runTurn() {

		super.runTurn();

		updateBullets();

		updateRobots();

		handleDeathRobots();

		if (isAborted() || oneTeamRemaining()) {
			shutdownTurn();
		}

		inactiveTurnCount++;

		computeActiveRobots();

		publishStatuses();

		// Robot time!
		wakeupRobots();
	}

	@Override
	protected void shutdownTurn() {
		if (getEndTimer() == 0) {
			if (isAborted()) {
				for (RobotPeer r : getRobotsAtRandom()) {
					if (!r.isDead()) {
						r.println("SYSTEM: game aborted.");
					}
				}
			} else if (oneTeamRemaining()) {
				boolean leaderFirsts = false;
				TeamPeer winningTeam = null;

				for (RobotPeer r : getRobotsAtRandom()) {
					if (!r.isDead()) {
						if (!r.isWinner()) {
							r.getRobotStatistics().scoreLastSurvivor();
							r.setWinner(true);
							r.println("SYSTEM: " + r.getName() + " wins the round.");
							r.addEvent(new WinEvent());
							if (r.getTeamPeer() != null) {
								if (r.isTeamLeader()) {
									leaderFirsts = true;
								} else {
									winningTeam = r.getTeamPeer();
								}
							}
						}
					}
				}
				if (!leaderFirsts && winningTeam != null) {
					winningTeam.getTeamLeader().getRobotStatistics().scoreFirsts();
				}
			}
		}

		if (getEndTimer() == 1 && (isAborted() || isLastRound())) {

			List<RobotPeer> orderedRobots = new ArrayList<RobotPeer>(robots);

			Collections.sort(orderedRobots);
			Collections.reverse(orderedRobots);

			for (int rank = 0; rank < robots.size(); rank++) {
				RobotPeer r = orderedRobots.get(rank);
				BattleResults resultsForRobots = r.getStatistics().getFinalResults(rank + 1);

				r.addEvent(new BattleEndedEvent(isAborted(), resultsForRobots));
			}
		}

		if (getEndTimer() > 4 * 30) {
			for (RobotPeer r : robots) {
				r.setHalt(true);
			}
		}

		super.shutdownTurn();
	}

	@Override
	protected void finalizeTurn() {
		eventDispatcher.onTurnEnded(new TurnEndedEvent(new TurnSnapshot(this, robots, bullets)));

		super.finalizeTurn();
	}

	private void initBattleRobots() {
		for (RobotPeer r : robots) {
			String pkgn = r.getRobotClassManager().getClassNameManager().getFullPackage();

			if (pkgn != null && pkgn.length() > MAX_FULL_PACKAGE_NAME_LENGTH) {
				final String message = "SYSTEM: Your package name is too long.  " + MAX_FULL_PACKAGE_NAME_LENGTH
						+ " characters maximum please.";

				r.println(message);
				logMessage(message);
				r.println("SYSTEM: Robot disabled.");
				r.setEnergy(0);
			}

			String clsn = r.getRobotClassManager().getClassNameManager().getShortClassName();

			if (clsn != null && clsn.length() > MAX_SHORT_CLASS_NAME_LENGTH) {
				final String message = "SYSTEM: Your classname is too long.  " + MAX_SHORT_CLASS_NAME_LENGTH
						+ " characters maximum please.";

				r.println(message);
				logMessage(message);
				r.println("SYSTEM: Robot disabled.");
				r.setEnergy(0);
			}
			try {
				Class<?> c;

				RobotClassManager classManager = r.getRobotClassManager();
				String className = classManager.getFullClassName();

				RobocodeClassLoader classLoader = classManager.getRobotClassLoader();

                // Pre-load robot classes without security...
                // loadClass WILL NOT LINK the class, so static "cheats" will not work.
                // in the safe robot loader the class is linked.
				if (RobotClassManager.isSecutityOn()) {
					c = classLoader.loadRobotClass(className, true);
				} else {
					c = classLoader.loadClass(className);
				}

				classManager.setRobotClass(c);

				// create proxy
				r.createRobotProxy(manager.getHostManager());
			} catch (Throwable e) {
				r.println("SYSTEM: Could not load " + r.getName() + " : " + e);
				r.println(e.getStackTrace().toString());
				r.setEnergy(0);
			}
		}
	}

	private void startRobots() {
		for (RobotPeer r : getRobotsAtRandom()) {
			manager.getThreadManager().addThreadGroup(r.getRobotThreadManager().getThreadGroup(), r);
			long waitTime = Math.min(300 * manager.getCpuManager().getCpuConstant(), 10000000000L);

			synchronized (r) {
				try {
					Logger.logMessage(".", false);

					// Add StatusEvent for the first turn
					r.publishStatus(true, getTime());

					// Start the robot thread
					r.getRobotThreadManager().start();

					if (!isDebugging) {
						// Wait for the robot to go to sleep (take action)
						r.wait(waitTime / 1000000, (int) (waitTime % 1000000));
					}
				} catch (InterruptedException e) {
					logMessage("Wait for " + r + " interrupted.");

					// Immediately reasserts the exception by interrupting the caller thread itself
					Thread.currentThread().interrupt();
				}
			}
			if (!(r.isSleeping() || isDebugging)) {
				logMessage(
						"\n" + r.getName() + " still has not started after " + (waitTime / 100000) + " ms... giving up.");
			}
		}
	}

	private RobotResults[] computeResults() {
		List<ContestantPeer> orderedPeers = new ArrayList<ContestantPeer>(contestants);

		Collections.sort(orderedPeers);
		Collections.reverse(orderedPeers);

		RobotResults results[] = new RobotResults[orderedPeers.size()];

		for (int i = 0; i < results.length; i++) {
			ContestantPeer cp = orderedPeers.get(i);
			RobotSpecification robotSpec = null;

			if (cp instanceof RobotPeer) {
				robotSpec = ((RobotPeer) cp).getRobotClassManager().getControlRobotSpecification();
			} else if (cp instanceof TeamPeer) {
				robotSpec = ((TeamPeer) cp).getTeamLeader().getRobotClassManager().getControlRobotSpecification();
			}
			BattleResults battleResults = cp.getStatistics().getFinalResults(i + 1);

			results[i] = new RobotResults(robotSpec, battleResults);
		}
		return results;
	}

	/**
	 * Returns a list of all robots in random order. This method is used to gain fair play in Robocode,
	 * so that a robot placed before another robot in the list will not gain any benefit when the game
	 * checks if a robot has won, is dead, etc.
	 * This method was introduced as two equal robots like sample.RamFire got different scores even
	 * though the code was exactly the same.
	 *
	 * @return a list of robots
	 */
	private List<RobotPeer> getRobotsAtRandom() {
		List<RobotPeer> shuffledList = new ArrayList<RobotPeer>(robots);

		Collections.shuffle(shuffledList, RandomFactory.getRandom());
		return shuffledList;
	}

	private void updateBullets() {
		for (BulletPeer b : bullets) {
			b.update(robots, bullets);
            if (b.getState() == BulletState.INACTIVE) {
                bullets.remove(b);
            }
		}
    }

	private void updateRobots() {
		boolean zap = (inactiveTurnCount > battleRules.getInactivityTime());

		// Move all bots
		for (RobotPeer r : getRobotsAtRandom()) {

			final RobotCommands currentCommands = r.loadCommands(robots, bullets);

			// update robots
			final double zapEnergy = isAborted() ? 5 : zap ? .1 : 0;

			r.update(currentCommands, robots, zapEnergy);

			// publish deaths to live robots
			if (!r.isDead()) {
				for (RobotPeer de : deathRobots) {
					r.addEvent(new RobotDeathEvent(de.getName()));
					if (r.getTeamPeer() == null || r.getTeamPeer() != de.getTeamPeer()) {
						r.getRobotStatistics().scoreSurvival();
					}
				}
			}
		}
	}

	private void handleDeathRobots() {

		// Compute scores for dead robots
		for (RobotPeer r : deathRobots) {
			if (r.getTeamPeer() == null) {
				r.getRobotStatistics().scoreRobotDeath(getActiveContestantCount(r));
			} else {
				boolean teammatesalive = false;

				for (RobotPeer tm : robots) {
					if (tm.getTeamPeer() == r.getTeamPeer() && (!tm.isDead())) {
						teammatesalive = true;
						break;
					}
				}
				if (!teammatesalive) {
					r.getRobotStatistics().scoreRobotDeath(getActiveContestantCount(r));
				}
			}
		}

		deathRobots.clear();
	}

	private void publishStatuses() {
		for (RobotPeer r : robots) {
			r.publishStatus(false, getTime());
		}
	}

	private synchronized void computeActiveRobots() {
		int ar = 0;

		// Compute active robots
		for (RobotPeer r : robots) {
			if (!r.isDead()) {
				ar++;
			}
		}
		this.activeRobots = ar;
	}

	private void wakeupRobots() {
		// Wake up all robot threads
        final List<RobotPeer> robotsAtRandom = getRobotsAtRandom();

        if (parallelOn) {
            wakeupParallel(robotsAtRandom);
        } else {
            wakeupSerial(robotsAtRandom);
        }
	}

	private void wakeupSerial(List<RobotPeer> robotsAtRandom) {
		final long waitTime = manager.getCpuManager().getCpuConstant();
		int millisWait = (int) (waitTime / 1000000);

		for (RobotPeer r : robotsAtRandom) {
			if (r.isRunning()) {
				// This call blocks until the
				// robot's thread actually wakes up.
				r.waitWakeup();

				if (r.isAlive()) {
					r.waitSleeping(waitTime, millisWait);
					r.setSkippedTurns();
				}
			}
		}
	}

	private void wakeupParallel(List<RobotPeer> robotsAtRandom) {
		final long waitTime = (long) (manager.getCpuManager().getCpuConstant() * parallelConstant);
		int millisWait = (int) (waitTime / 1000000);

		for (RobotPeer r : robotsAtRandom) {
			if (r.isRunning()) {
				r.waitWakeup();
			}
		}
		for (RobotPeer r : robotsAtRandom) {
			if (r.isRunning() && r.isAlive()) {
				r.waitSleeping(waitTime, millisWait);
			}
		}
		for (RobotPeer r : robotsAtRandom) {
			if (r.isRunning() && r.isAlive()) {
				r.setSkippedTurns();
			}
		}
	}

	private int getActiveContestantCount(RobotPeer peer) {
		int count = 0;

		for (ContestantPeer c : contestants) {
			if (c instanceof RobotPeer && !((RobotPeer) c).isDead()) {
				count++;
			} else if (c instanceof TeamPeer && c != peer.getTeamPeer()) {
				for (RobotPeer r : (TeamPeer) c) {
					if (!r.isDead()) {
						count++;
						break;
					}
				}
			}
		}
		return count;
	}

	private void loadRoundRobots() {
		// Flag that robots are not loaded
		synchronized (isRobotsLoaded) {
			isRobotsLoaded.set(false);
			isRobotsLoaded.notifyAll();
		}

		// Wait for the unsafe loader thread to start running
		synchronized (isUnsafeLoaderThreadRunning) {
			while (!isUnsafeLoaderThreadRunning.get()) {
				try {
					isUnsafeLoaderThreadRunning.wait();
				} catch (InterruptedException e) {
					// Immediately reasserts the exception by interrupting the caller thread itself
					Thread.currentThread().interrupt();
				}
			}
		}
		// At this point the unsafe loader thread will now set itself to wait for a notify

		for (RobotPeer r : robots) {
			r.println("=========================");
			r.println("Round " + (getRoundNum() + 1) + " of " + getNumRounds());
			r.println("=========================");
		}

		// At this point the unsafe loader thread is still waiting for a signal.
		// So, notify it to continue the loading.
		synchronized (isUnsafeLoaderThreadRunning) {
			isUnsafeLoaderThreadRunning.notifyAll();
		}

		// Wait for the robots to become loaded
		synchronized (isRobotsLoaded) {
			while (!isRobotsLoaded.get()) {
				try {
					isRobotsLoaded.wait();
				} catch (InterruptedException e) {
					// Immediately reasserts the exception by interrupting the caller thread itself
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	private void setInitialPositions(String initialPositions) {
		initialRobotPositions = null;

		if (initialPositions == null || initialPositions.trim().length() == 0) {
			return;
		}

		List<String> positions = new ArrayList<String>();

		Pattern pattern = Pattern.compile("([^,(]*[(][^)]*[)])?[^,]*,?");
		Matcher matcher = pattern.matcher(initialPositions);

		while (matcher.find()) {
			String pos = matcher.group();

			if (pos.length() > 0) {
				positions.add(pos);
			}
		}

		if (positions.size() == 0) {
			return;
		}

		initialRobotPositions = new double[positions.size()][3];

		String[] coords;
		double x, y, heading;

		for (int i = 0; i < positions.size(); i++) {
			coords = positions.get(i).split(",");

			final Random random = RandomFactory.getRandom();

			x = RobotPeer.WIDTH + random.nextDouble() * (battleRules.getBattlefieldWidth() - 2 * RobotPeer.WIDTH);
			y = RobotPeer.HEIGHT + random.nextDouble() * (battleRules.getBattlefieldHeight() - 2 * RobotPeer.HEIGHT);
			heading = 2 * Math.PI * random.nextDouble();

			int len = coords.length;

			if (len >= 1) {
				// noinspection EmptyCatchBlock
				try {
					x = Double.parseDouble(coords[0].replaceAll("[\\D]", ""));
				} catch (NumberFormatException e) {}

				if (len >= 2) {
					// noinspection EmptyCatchBlock
					try {
						y = Double.parseDouble(coords[1].replaceAll("[\\D]", ""));
					} catch (NumberFormatException e) {}

					if (len >= 3) {
						// noinspection EmptyCatchBlock
						try {
							heading = Math.toRadians(Double.parseDouble(coords[2].replaceAll("[\\D]", "")));
						} catch (NumberFormatException e) {}
					}
				}
			}
			initialRobotPositions[i][0] = x;
			initialRobotPositions[i][1] = y;
			initialRobotPositions[i][2] = heading;
		}
	}

	private void initializeRobotPosition(RobotPeer robot) {
		if (initialRobotPositions != null) {
			int index = robots.indexOf(robot);

			if (index >= 0 && index < initialRobotPositions.length) {
				double[] pos = initialRobotPositions[index];

				robot.initialize(pos[0], pos[1], pos[2]);
				if (validSpot(robot)) {
					return;
				}
			}
		}

		double x, y, heading;

		final Random random = RandomFactory.getRandom();

		for (int j = 0; j < 1000; j++) {
			x = RobotPeer.WIDTH + random.nextDouble() * (battleRules.getBattlefieldWidth() - 2 * RobotPeer.WIDTH);
			y = RobotPeer.HEIGHT + random.nextDouble() * (battleRules.getBattlefieldHeight() - 2 * RobotPeer.HEIGHT);
			heading = 2 * Math.PI * random.nextDouble();

			robot.initialize(x, y, heading);

			if (validSpot(robot)) {
				break;
			}
		}
	}

	private boolean validSpot(RobotPeer robot) {
		robot.updateBoundingBox();
		for (RobotPeer r : robots) {
			if (r != null && r != robot) {
				if (robot.getBoundingBox().intersects(r.getBoundingBox())) {
					return false;
				}
			}
		}
		return true;
	}

	private boolean oneTeamRemaining() {
		if (getActiveRobots() <= 1) {
			return true;
		}

		boolean found = false;
		TeamPeer currentTeam = null;

		for (RobotPeer currentRobot : robots) {
			if (!currentRobot.isDead()) {
				if (!found) {
					found = true;
					currentTeam = currentRobot.getTeamPeer();
				} else {
					if (currentTeam == null && currentRobot.getTeamPeer() == null) {
						return false;
					}
					if (currentTeam != currentRobot.getTeamPeer()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private boolean unsafeLoadRobots() {
        synchronized (isUnsafeLoaderThreadRunning) {
            try {
                // Notify that the unsafe loader thread is now running
                isUnsafeLoaderThreadRunning.set(true);
                isUnsafeLoaderThreadRunning.notifyAll();

                // Wait for a notify in order to continue
                isUnsafeLoaderThreadRunning.wait();
            } catch (InterruptedException e) {
                // Immediately reasserts the exception by interrupting the caller thread itself
                Thread.currentThread().interrupt();
            }
        }
        // Loader awake
        if (getRoundNum() >= getNumRounds() || isAborted()) {
            // Robot loader thread terminating
            return false;
        }
        // Loading robots
        for (RobotPeer robotPeer : robots) {
            robotPeer.setRobot(null);
            robotPeer.setState(RobotState.DEAD);
            initializeRobotPosition(robotPeer);
            Class<?> robotClass;

            try {
                manager.getThreadManager().setLoadingRobot(robotPeer);
                robotClass = robotPeer.getRobotClassManager().getRobotClass();
                if (robotClass == null) {
                    robotPeer.println("SYSTEM: Skipping robot: " + robotPeer.getName());
                    robotPeer.setEnergy(0);
                    continue;
                }
                IBasicRobot bot = (IBasicRobot) robotClass.newInstance();

                robotPeer.setRobot(bot);

                bot.setOut(robotPeer.getRobotProxy().getOut());
                bot.setPeer(robotPeer.getRobotProxy());
            } catch (IllegalAccessException e) {
                robotPeer.println("SYSTEM: Unable to instantiate this robot: " + e);
                robotPeer.println("SYSTEM: Is your constructor marked public?");
                robotPeer.setEnergy(0);
                robotPeer.setRobot(null);
                logMessage(e);
            } catch (Throwable e) {
                robotPeer.println(
                        "SYSTEM: An error occurred during initialization of " + robotPeer.getRobotClassManager());
                robotPeer.println("SYSTEM: " + e);
                robotPeer.println(e.getStackTrace().toString());
                robotPeer.setRobot(null);
                robotPeer.setEnergy(0);
                logMessage(e);
            }
        } // for

        manager.getThreadManager().setLoadingRobot(null);

        // Notify that the robots has been loaded
        synchronized (isRobotsLoaded) {
            isRobotsLoaded.set(true);
            isRobotsLoaded.notifyAll();
        }

        return true;
    }

	private class UnsafeLoadRobotsThread extends Thread {

		public UnsafeLoadRobotsThread(ThreadGroup tg) {
			super(tg, "Robot Loader");
			setDaemon(true);
		}

		@Override
		public void run() {
			// Load robots
            while (unsafeLoadRobots()) {
            }
        }
	}

	// --------------------------------------------------------------------------
	// Processing and maintaining robot and battle controls
	// --------------------------------------------------------------------------

	public void killRobot(int robotIndex) {
		sendCommand(new KillRobotCommand(robotIndex));
	}

	public void setPaintEnabled(int robotIndex, boolean enable) {
		sendCommand(new EnableRobotPaintCommand(robotIndex, enable));
	}

	public void setSGPaintEnabled(int robotIndex, boolean enable) {
		sendCommand(new EnableRobotSGPaintCommand(robotIndex, enable));
	}

	public void sendInteractiveEvent(Event e) {
		sendCommand(new SendInteractiveEventCommand(e));
	}

	private class RobotCommand extends Command {
		final int robotIndex;

		RobotCommand(int robotIndex) {
			this.robotIndex = robotIndex;
		}
	}


	private class KillRobotCommand extends RobotCommand {
		KillRobotCommand(int robotIndex) {
			super(robotIndex);
		}

		public void execute() {
			robots.get(robotIndex).kill();
		}
	}


	private class EnableRobotPaintCommand extends RobotCommand {
		final boolean enablePaint;

		EnableRobotPaintCommand(int robotIndex, boolean enablePaint) {
			super(robotIndex);
			this.enablePaint = enablePaint;
		}

		public void execute() {
			robots.get(robotIndex).setPaintEnabled(enablePaint);
		}
	}


	private class EnableRobotSGPaintCommand extends RobotCommand {
		final boolean enableSGPaint;

		EnableRobotSGPaintCommand(int robotIndex, boolean enableSGPaint) {
			super(robotIndex);
			this.enableSGPaint = enableSGPaint;
		}

		public void execute() {
			robots.get(robotIndex).setSGPaintEnabled(enableSGPaint);
		}
	}


	private class SendInteractiveEventCommand extends Command {
		public final Event event;

		SendInteractiveEventCommand(Event event) {
			this.event = event;
		}

		public void execute() {
			for (RobotPeer robot : robots) {
				robot.addEvent(event);
			}
		}
	}
}
