package jsettlers.main;

import jsettlers.common.network.IMatch;
import jsettlers.common.network.IMatchSettings;
import jsettlers.graphics.INetworkScreenAdapter;
import jsettlers.graphics.ISettlersGameDisplay;
import jsettlers.graphics.localization.Labels;
import jsettlers.graphics.progress.EProgressState;
import jsettlers.graphics.progress.ProgressConnector;
import jsettlers.graphics.startscreen.IStartScreenConnector.IGameSettings;
import jsettlers.graphics.startscreen.IStartScreenConnector.ILoadableGame;
import jsettlers.graphics.startscreen.IStartScreenConnector.IMapItem;
import jsettlers.logic.map.save.MapLoader;
import jsettlers.main.JSettlersGame.Listener;
import jsettlers.main.network.INetworkConnectTask;
import jsettlers.main.network.INetworkStartScreenEndListener;
import jsettlers.main.network.NetworkMatchJoiner;
import jsettlers.main.network.NetworkMatchOpener;
import jsettlers.main.network.NetworkMatchOpener.INetworkStartListener;
import jsettlers.main.network.NetworkRunningGameConnector;
import jsettlers.main.network.NetworkScreenAdapter;
import jsettlers.network.client.ClientThread;
import jsettlers.network.server.restapi.MatchDescription;
import network.NetworkManager;
import synchronic.timer.NetworkTimer;

/**
 * This is the new main game class
 * 
 * @author michael
 */
public class ManagedJSettlers implements Listener, IGameStarter, INetworkStartListener, INetworkStartScreenEndListener {

	private ISettlersGameDisplay content;
	private JSettlersGame ongoingGame;
	private INetworkConnectTask networkConnectTask;
	private NetworkScreenAdapter networkScreen;

	public synchronized void start(ISettlersGameDisplay content) {
		this.content = content;
		showMainScreen();
	}

	private synchronized void showMainScreen() {
		content.showStartScreen(new StartConnector(this));
	}

	@Override
	public synchronized void startGame(IGameSettings game) {
		stopOldStuff();

		IMapItem map = game.getMap();
		if (map instanceof IGameCreator) {
			// TODO: pass on player count
			IGameCreator creator = (IGameCreator) map;
			ongoingGame = new JSettlersGame(content, creator, 123456L, new NetworkManager(), (byte) 0);
			ongoingGame.setListener(ManagedJSettlers.this);
			ongoingGame.start();
		}
	}

	@Override
	public synchronized void loadGame(ILoadableGame load) {
		stopOldStuff();

		if (load instanceof IGameCreator) {
			IGameCreator creator = (IGameCreator) load;
			ongoingGame = new JSettlersGame(content, creator, 123456L, new NetworkManager(), (byte) 0);
			ongoingGame.setListener(ManagedJSettlers.this);
			ongoingGame.start();
		}
	}

	private synchronized void stopOldStuff() {
		if (ongoingGame != null) {
			ongoingGame.setListener(null);
			ongoingGame.stop();
		}

		// TODO: stop main screen
		INetworkConnectTask oldTask = networkConnectTask;
		if (oldTask != null) {
			networkConnectTask = null;
			oldTask.cancel();
		}

		NetworkScreenAdapter oldScreen = networkScreen;
		if (oldScreen != null) {
			networkScreen = null;
			oldScreen.cancel();
		}
	}

	/**
	 * Game ended from inside the game.
	 */
	@Override
	public void gameEnded() {
		ongoingGame.setListener(null);
		ongoingGame = null;
		showMainScreen();
	}

	/**
	 * Sets the pause status of the ongoing game. Does noting if there is no game.
	 * 
	 * @param b
	 */
	public void setPaused(boolean b) {
		if (ongoingGame != null) {
			ongoingGame.setPaused(b);
		}
	}

	public boolean isPaused() {
		if (ongoingGame != null) {
			return ongoingGame.isPaused();
		}
		return false;
	}

	public String saveAndStopCurrentGame() {
		if (ongoingGame != null) {
			String id = ongoingGame.save();
			ongoingGame.stop();
			return id;
		} else {
			return null;
		}
	}

	@Override
	public synchronized void openNetworkGame(IMatchSettings gameSettings) {
		ProgressConnector connector = content.showProgress();
		connector.setProgressState(EProgressState.STARTING_SERVER, -1);
		NetworkMatchOpener starter = new NetworkMatchOpener(gameSettings, this);
		starter.start();
		networkConnectTask = starter;
	}

	@Override
	public void networkMatchJoinFailed(INetworkConnectTask starter) {
		if (starter == networkConnectTask) {
			networkConnectTask = null;
			// TODO: add stirng to the file.
			showError(Labels.getString("could not start network"));
			showMainScreen();
		}
	}

	private void showError(String string) {
		System.out.println("Error: " + string);
		content.showErrorMessage(string);
	}

	@Override
	public void networkMatchJoined(INetworkConnectTask starter, ClientThread clientThread, MatchDescription description) {
		if (starter == networkConnectTask) {
			networkConnectTask = null;
			System.out.println("now the network game screen should be displayed.");

			networkScreen = new NetworkScreenAdapter(clientThread, description);
			content.showNetworkScreen(networkScreen);
			networkScreen.setEndListener(this);
		}
	}

	@Override
	public void joinNetworkGame(String serverAddress, IMatch match) {
		ProgressConnector connector = content.showProgress();
		connector.setProgressState(EProgressState.JOINING_GAME, -1);
		NetworkMatchJoiner joiner = new NetworkMatchJoiner(serverAddress, match, this);
		joiner.start();
		networkConnectTask = joiner;
	}

	@Override
	public void leftMatch(INetworkScreenAdapter networkScreenAdapter) {
		if (networkScreen == networkScreenAdapter) {
			showMainScreen();
		}
	}

	@Override
	public synchronized void networkMatchStarting(INetworkScreenAdapter networkScreenAdapter, ClientThread client) {
		if (networkScreen == networkScreenAdapter) {
			MatchDescription description = networkScreen.getMatchDescription();

			description.getMapId();
			MapLoader map = networkScreen.getMapLoader();
			NetworkRunningGameConnector runningGameConnector = new NetworkRunningGameConnector(client);
			NetworkManager networkManager = new NetworkManager(runningGameConnector, networkScreen.getMyPlayerNumber(), networkScreen.getPlayerIDs(),
					NetworkTimer.get());

			client.setListener(runningGameConnector);

			// TODO: pass on player count
			IGameCreator creator = map;
			ongoingGame = new JSettlersGame(content, creator, description.getRandomSeed(), networkManager, networkScreen.getMyPlayerNumber());
			ongoingGame.setListener(ManagedJSettlers.this);
			ongoingGame.start();

			networkScreen = null;
		}
	}

	public void stop() {
		stopOldStuff();
    }

}
