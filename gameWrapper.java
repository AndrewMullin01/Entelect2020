// Author: Andrew Mullin

import java.util.ArrayList;
import java.util.List;

import java.lang.reflect.*;
import sun.misc.*;

import scala.util.Random;
import za.co.entelect.challenge.config.GameRunnerConfig;
import za.co.entelect.challenge.engine.loader.GameEngineClassLoader;
import za.co.entelect.challenge.engine.runner.GameEngineRunner;
import za.co.entelect.challenge.engine.runner.RunnerReferee;
import za.co.entelect.challenge.config.BotMetaData;
import za.co.entelect.challenge.botrunners.BotRunner;
import za.co.entelect.challenge.botrunners.BotRunnerFactory;
import za.co.entelect.challenge.botrunners.*;
import za.co.entelect.challenge.game.contracts.game.*;
import za.co.entelect.challenge.player.*;
import za.co.entelect.challenge.player.bootstrapper.*;
import za.co.entelect.challenge.player.entity.*;
import za.co.entelect.challenge.game.contracts.player.Player;
import za.co.entelect.challenge.player.entity.BasePlayer;
import za.co.entelect.challenge.renderer.RendererResolver;
import za.co.entelect.challenge.player.ConsolePlayer;
import za.co.entelect.challenge.player.BotPlayer;
import za.co.entelect.challenge.game.contracts.map.*;
import za.co.entelect.challenge.game.contracts.common.RefereeMessage;
import za.co.entelect.challenge.game.contracts.exceptions.InvalidCommandException;
import za.co.entelect.challenge.game.contracts.commands.*;
import za.co.entelect.challenge.game.contracts.commands.CommandFactory;
import za.co.entelect.challenge.game.contracts.command.*;
import za.co.entelect.challenge.game.contracts.command.RawCommand;
import za.co.entelect.challenge.game.contracts.command.Command;

import za.co.entelect.challenge.game.contracts.Config.Config;
import za.co.entelect.challenge.game.contracts.bootstrapper.GameEngineBootstrapper;

class ObjectFactory {

  private static Unsafe sUnsafe;

  static {
    try {
      Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
      unsafeField.setAccessible(true);
      sUnsafe = (Unsafe) unsafeField.get(null);
    } catch (Throwable e) {
      sUnsafe = null;
    }
  }

  public static <T> T newInstance(Class<T> clss) {
    try {
      return (T) sUnsafe.allocateInstance(clss);
    } catch (InstantiationException e) {
      return null;
    }
  }
}

class gameDataOutput {
  // general
  public int round;
  public int MAPSIZE;
  public int[] mapData;
  public int NUMSTATES;
  // player
  public int[] pState;   //one-hot encoded
  public int pSpeed;
  public int pX;
  public int pY;
  public int pPowerups[];
  public int pBoosting;
  public int pBoostCounter;
  // opponent
  public int oX;
  public int oY;
  public int oSpeed;

  public int[] getMapView(CarGameMapFragment mapFrag, Config conny){
    Block[] blocks = mapFrag.getBlocks();
    int sizeDiff = MAPSIZE - blocks.length;
    int bX = blocks[0].getPosition().getBlockNumber() - 1;
    int i = 0;
    for (Block block : blocks) {
      bX = block.getPosition().getBlockNumber();
      if(bX <= 1){
        for(int j = 0; j < sizeDiff/conny.NUMBER_OF_LANES(); j++){
          mapData[i] = 0;
          i++;
        }
        mapData[i] = block.getMapObject();
        i++;
      }
      else if(bX >= conny.TRACK_LENGTH()){
        mapData[i] = 0; //block.getMapObject(); <= finish line == 4
        i++;
        for(int j = 0; j < sizeDiff/conny.NUMBER_OF_LANES(); j++){
          mapData[i] = 0;
          i++;
        }
      }
      else{
        mapData[i] = block.getMapObject();
        i++;
      }
      
    }
    
    return mapData;
  }
  
  private static int[] getEncodedPlayerState(String state, Config conny) {
    if (state.equals(conny.ACCELERATING_PLAYER_STATE())){         return new int[]{1,0,0,0,0,0,0,0};}
    else if(state.equals(Config.TURNING_LEFT_PLAYER_STATE())){    return new int[]{0,1,0,0,0,0,0,0};}
    else if(state.equals(Config.TURNING_RIGHT_PLAYER_STATE())){   return new int[]{0,0,1,0,0,0,0,0};}
    else if(state.equals(conny.HIT_MUD_PLAYER_STATE())){          return new int[]{0,0,0,1,0,0,0,0};}
    else if(state.equals("HIT_OIL")){                             return new int[]{0,0,0,0,1,0,0,0};} // N.B! typo => conny.HIT_OIL_PLAYER_STAE()
    else if(state.equals(conny.NOTHING_PLAYER_STATE())){          return new int[]{0,0,0,0,0,1,0,0};}
    else if(state.equals(conny.DECELERATING_PLAYER_STATE())){     return new int[]{0,0,0,0,0,0,1,0};}
    else if(state.equals(conny.USED_POWERUP_OIL_PLAYER_STATE())){ return new int[]{0,0,0,0,0,0,0,1};}
    else{                                                         return new int[]{1,0,0,0,0,0,0,0};}
  }

  public gameDataOutput(Config conny, CarGameMap mappy, int playerID) {
    CarGameMapFragment mapFrag;
    if(playerID == 1){
      mapFrag = mappy.getMapFragment(mappy.getCarGamePlayers()[0]);
    }
    else{
      mapFrag = mappy.getMapFragment(mappy.getCarGamePlayers()[1]);
    }    
    
    // general
    round = mappy.getCurrentRound();
    MAPSIZE = (conny.BACKWARD_VISIBILITY() + 1 + conny.FORWARD_VISIBILITY()) * conny.NUMBER_OF_LANES(); // L x W
    mapData = new int[MAPSIZE];
    mapData = getMapView(mapFrag, conny);
    
    NUMSTATES = 8;
    // player
    MapFragmentPlayer p = mapFrag.getPlayer();
    pState = getEncodedPlayerState(p.getState(), conny);
    pSpeed = p.getSpeed();
    pX = p.getPosition().getBlockNumber();
    pY = p.getPosition().getLane();
    pPowerups = new int[]{0, 0};
    for(String PU : p.getPowerups()){
      if(PU.equals("BOOST")) pPowerups[0]++;
      else if(PU.equals("OIL")) pPowerups[1]++;
      //else others++;
    }
    // pBoosting = p.isBoosting() ? 1 : 0; Redundent
    pBoostCounter = p.getBoostCounter();
    // opponent
    MapFragmentPlayer o = mapFrag.getOpponent();
    oX = o.getPosition().getBlockNumber();
    oY = o.getPosition().getLane();
    oSpeed = o.getSpeed();
  }

  public void printMapView(Config conny){
    int viewSize = conny.BACKWARD_VISIBILITY() + 1 + conny.FORWARD_VISIBILITY();    
    System.out.print("\nPrinting game data map view:\n");
    for(int i = 0; i < mapData.length; i++){
      System.out.print(mapData[i] + "  ");
      if((i+1)%viewSize == 0){
        System.out.print("\n");
      }
    }
    System.out.print("\n");
  }
}

public class gameWrapper {
  private int seedy;
  public Config conny;
  private scala.util.Random randy;
  private CommandFactory mandy;
  public int p1ID;
  public int p2ID;
  private List<Player> players;
  private Block[] blockies;
  public CarGameMap mappy;
  public int gameStatus;
  public int posDifference;
  public int[] gameData;

  public gameWrapper(String configPath) throws Exception {
    this.conny = ObjectFactory.newInstance(Config.class);
    if (configPath.equals("")) conny.loadDefault();
    else conny.load(configPath);
    this.mandy = new CommandFactory();
    this.p1ID = 1;
    this.p2ID = 2;
  }
    
  public static void print(Object s) {
    System.out.print(""+s);
  }

  public static void printBlockPosition(BlockPosition bp) {
    System.out.print("[" + bp.getLane() + ", " + bp.getBlockNumber() + "]\n");
  }

  public void printRoundData() {
    if(mappy.getCurrentRound() == 1){
      print("\nTrack Length: " + conny.TRACK_LENGTH());
      print("\nRound:\t\tdX:\t\tV1\t\tV2");
    }
    print("\n" + mappy.getCurrentRound() + "\t\t" + posDifference + "\t\t" + String.format("%.2f bps\t%.2f bps", (getGameData(1).pX*1.0)/mappy.getCurrentRound(), (getGameData(2).pX*1.0)/mappy.getCurrentRound()));
  }

  public void printPlayerStateData(boolean first) {

    if(first){
      print("Round   ");
      print("[Y, X]");
      print("\t    V");
      print("   Pts");
      print("    B?");
      print("  B++");
      print("  State");
      print("\t\tPowerUps");
      print("\n");
    }

    print(mappy.getCurrentRound() + ":");// + mappy.getPlayerBlockPosition(1) + "\t\t" + mappy.getPlayerBlockPosition(2) + "\n");

    CarGameMapFragment[] frags = {mappy.getMapFragment(mappy.getCarGamePlayers()[0]), mappy.getMapFragment(mappy.getCarGamePlayers()[1])};
    for (CarGameMapFragment frag : frags){
      print(String.format("\t%-12s", "[" + frag.getPlayer().getPosition().getLane() + ", " + frag.getPlayer().getPosition().getBlockNumber() + "]"));
      print(String.format("%-4d", frag.getPlayer().getSpeed()));
      print(String.format("%-7d", frag.getPlayer().getScore()));
      print(String.format("%-4s", frag.getPlayer().isBoosting() ? "T" : "F"));
      print(String.format("%-4d", frag.getPlayer().getBoostCounter()));
      print(String.format("\t%-16s", frag.getPlayer().getState()));
      int boosts=0, oils=0, others=0;
      for(String PU : frag.getPlayer().getPowerups()){
        if(PU.equals("BOOST")) boosts++;
        else if(PU.equals("OIL")) oils++;
        else others++;
      }
      print(String.format("%d BOOSTS, %d OILS, %d OTHERS", boosts, oils, others));
      print("\n");
    }
  }

  public void printMap(CarGameMap map){
    print("\nPrinting map:\n");

    int i = 0;
    for (Block block : map.getBlocks()){
      print(block.getMapObject() + " ");
      i++;
      if(i % conny.TRACK_LENGTH() == 0){
        print("\n");
      }
    }
    print("\n");

  }

  public void printMiniMap(int focus, int size){

    Block[] blocks = mappy.blocks();
    int lanes = conny.NUMBER_OF_LANES();
    int trackLength = conny.TRACK_LENGTH();
    int pos = focus-size;

    print("\nPrinting mini map:\n");

    print(String.format("=>%-6d", pos-pos % 100));
    for (int j = focus-size + 1; j < focus+size+1; j++){
      if(j >= 0){
        print(String.format("%-3d", j%100));
      }
      else{
        print("   ");
      }
    }
    print("\n");  

    String tile = "";
    for (int i = 0; i < lanes; i++){
      print(String.format("%-8d", i+1));
      for (int j = focus-size; j < focus+size; j++){
        pos = j + i*trackLength;
        if(j < 0 || j > trackLength-1){
          tile = ". ";
        }
        else if (blocks[pos].getOccupiedByPlayerWithId() > 0){
          tile = "P" + blocks[pos].getOccupiedByPlayerWithId();
        }
        else{
          tile = blocks[pos].getMapObject()+" ";
        }
        

        print(tile + " ");
      }
      print("\n");
    }

  }

  public List<Player> myLoadPlayers() throws Exception {
    List<Player> players = new ArrayList<>();

    /*/ Create players as botRunner players
    GameRunnerConfig gameRunnerConfig = GameRunnerConfig.load("./game-runner-config.json");    
    BotMetaData botConfig1 = BotMetaData.load(gameRunnerConfig.playerAConfig);
    BotRunner botRunner1 = BotRunnerFactory.createBotRunner(botConfig1, 10000000);
    BasePlayer player1 = new BotPlayer(String.format("%s - %s", p1ID, p1ID), botRunner1);
    BotMetaData botConfig2 = BotMetaData.load(gameRunnerConfig.playerBConfig);
    BotRunner botRunner2 = BotRunnerFactory.createBotRunner(botConfig2, 10000000);
    BasePlayer player2 = new BotPlayer(String.format("%s - %s", p1ID, p1ID), botRunner2);
    /*/
    BasePlayer player1 = new ConsolePlayer(p1ID+"");
    BasePlayer player2 = new ConsolePlayer(p2ID+"");

    GamePlayer gP1 = new CarGamePlayer(100, 0, p1ID, conny.INITIAL_SPEED());
    player1.setGamePlayer(gP1);
    player1.setName(p1ID+"");
    player1.setPlayerId(p1ID+""); 
    player1.setNumber(p1ID);
    player1.gameStarted();

    GamePlayer gP2 = new CarGamePlayer(100, 0, p2ID, conny.INITIAL_SPEED());    
    player2.setGamePlayer(gP2);
    player2.setName(p2ID+"");
    player2.setPlayerId(p2ID+""); 
    player2.setNumber(p2ID);
    player2.gameStarted();
    
    players.add(player1);
    players.add(player2);

    
    //for (Player p : players){print(p.getName() + ", " + p.getNumber() + ", " + p.getTimeoutCounts() + ", " + p.getGamePlayer().getHealth() + "\n");}

    return players;
  }

  public Block[] myGenerateBlocks() {
    int lanes = conny.NUMBER_OF_LANES();
    int trackLength = conny.TRACK_LENGTH();
    BlockPosition p1Pos = new BlockPosition(conny.PLAYER_ONE_START_LANE(), conny.PLAYER_ONE_START_BLOCK());
    BlockPosition p2Pos = new BlockPosition(conny.PLAYER_TWO_START_LANE(), conny.PLAYER_TWO_START_BLOCK());

    Block[] blocks = new Block[lanes * trackLength];
    BlockObjectCreator blockernator = new BlockObjectCreator();

    for (int i = 0; i < blocks.length; i++) {
      int lane = (int)(i/trackLength) + 1;
      int blockNumber = i % trackLength + 1;
      BlockPosition position = new BlockPosition(lane, blockNumber);

      int generatedMapObject = conny.EMPTY_MAP_OBJECT();
      int blockHoldsPlayer = conny.EMPTY_PLAYER();
      int finalBlockForGeneratedMapObject = trackLength;
      if (blockNumber >= conny.STARTING_BLOCK_FOR_GENERATED_MAP_OBJECTS() && blockNumber < conny.TRACK_LENGTH()) {
        generatedMapObject = blockernator.generateMapObject(randy);
      } else if (blockNumber == finalBlockForGeneratedMapObject) {
        generatedMapObject = conny.FINISH_LINE_MAP_OBJECT();
      }

      if(lane == p1Pos.getLane() && blockNumber == p1Pos.getBlockNumber()){
        blockHoldsPlayer = p1ID;
      }
      else if(lane == p2Pos.getLane() && blockNumber == p2Pos.getBlockNumber()){
        blockHoldsPlayer = p2ID;
      }

      blocks[i] = new Block(position, generatedMapObject, blockHoldsPlayer);
    }

    // val playerOneId =
    // players.get(0).getGamePlayer().asInstanceOf[CarGamePlayer].getGamePlayerId();
    // setPlayerOneStartPosition(blocks, 1000);

    // val playerTwoId =
    // players.get(1).getGamePlayer().asInstanceOf[CarGamePlayer].getGamePlayerId();
    // setPlayerTwoStartPosition(blocks, 2000);

    return blocks;
  }

  public void tickGame(String P1Cstr, String P2Cstr) throws InvalidCommandException {
    mappy.setCurrentRound(mappy.getCurrentRound() + 1);

    GamePlayer[] gamePlayers = mappy.getCarGamePlayers();

    RawCommand p1C = mandy.makeCommand(P1Cstr);
    RawCommand p2C = mandy.makeCommand(P2Cstr);
    //p1C.setCommand(P1Cstr);
    //p2C.setCommand(P2Cstr);
    p1C.performCommand(mappy, gamePlayers[0]);
    p2C.performCommand(mappy, gamePlayers[1]);

    mappy.resolvePlayerCollisions();
    mappy.commitStagedPositions();
    
    posDifference = mappy.getPlayerBlockPosition(1).getBlockNumber() - mappy.getPlayerBlockPosition(2).getBlockNumber();
    gameStatus = isGameWon();
  }

  public int isGameWon(){
    String p1State = mappy.getCarGamePlayers()[0].getState();
    String p2State = mappy.getCarGamePlayers()[1].getState();
    if (p1State.equals(conny.FINISHED_PLAYER_STATE())) return 1;
    else if (p2State.equals(conny.FINISHED_PLAYER_STATE())) return 2;
    else return 0;
  }

  public String decideMove(int PID) {
    
    String command = conny.ACCELERATE_COMMAND();
    CarGamePlayer p = mappy.getCarGamePlayers()[PID-1];
    int fwdVis = conny.FORWARD_VISIBILITY(), bckVis = conny.BACKWARD_VISIBILITY();
    BlockPosition endBlock;
	  BlockPosition pBlock = mappy.getPlayerBlockPosition(PID);
    int pos = pBlock.getBlockNumber();
    int lane = pBlock.getLane() - 1;
    int boosts=0, oils=0, others=0;
    for(String PU : p.getPowerups()){
      if(PU.equals("BOOST")) boosts++;
      else if(PU.equals("OIL")) oils++;
      else others++;
    }
    Boolean isBoosting = p.isBoosting();
    Boolean infront = mappy.getPlayerBlockPosition(1).getBlockNumber() > mappy.getPlayerBlockPosition(2).getBlockNumber();

    int[] boostCount = new int[conny.NUMBER_OF_LANES()];
    int[] oilItemCount = new int[conny.NUMBER_OF_LANES()];
    int[] oilSpillCount = new int[conny.NUMBER_OF_LANES()];
    int[] mudCount = new int[conny.NUMBER_OF_LANES()];
    for (int i = 0; i < boostCount.length; i++) {
      endBlock = new BlockPosition(i+1, pos+fwdVis);
      boostCount[i] = mappy.boostCountInPath(pBlock, endBlock);
      oilItemCount[i] = mappy.oilItemCountInPath(pBlock, endBlock);
      oilSpillCount[i] = mappy.oilSpillCountInPath(pBlock, endBlock);
      mudCount[i] = mappy.mudCountInPath(pBlock, endBlock);
    }
    int obstacleCountAbove, obstacleCountBelow;
	  int obstacleCount = oilSpillCount[lane] + mudCount[lane];
    if(lane >= 1) obstacleCountAbove = oilSpillCount[lane-1] + mudCount[lane-1];
    else obstacleCountAbove = 9999;
    if(lane <= 2) obstacleCountBelow = oilSpillCount[lane+1] + mudCount[lane+1];
    else obstacleCountBelow = 9999;

    if((obstacleCount > 3) || (obstacleCount > 1 && isBoosting)){
      if (obstacleCount < obstacleCountAbove && obstacleCount < obstacleCountBelow){
        command = conny.ACCELERATE_COMMAND();
      }
      else if (obstacleCountAbove < obstacleCount && obstacleCountAbove < obstacleCountBelow){
        command = conny.TURN_LEFT_COMMAND();
        return command;
      }
      else{
        command = conny.TURN_RIGHT_COMMAND();
        return command;
      }
    }
    if (boosts >= 1 && !isBoosting){
      command = conny.USE_BOOST_COMMAND();
      return command;
    }
    else if (oils >= 1 && p.getSpeed() >= conny.MAXIMUM_SPEED() && infront){
      command = conny.USE_OIL_COMMAND();
      return command;
    }

    return command;
  }

  public void loadConfigFile(String configPath){
    this.conny.load(configPath);
  }

  public gameDataOutput getGameData(int player) {
    return new gameDataOutput(conny, mappy, player);
  }

  public int getPlayerLead(int player) {
    if(player == 1){
      return mappy.getPlayerBlockPosition(1).getBlockNumber() - mappy.getPlayerBlockPosition(2).getBlockNumber();
    }
    else{
      return mappy.getPlayerBlockPosition(2).getBlockNumber() - mappy.getPlayerBlockPosition(1).getBlockNumber();
    }
  }

  public void newGame(int seed) throws Exception {
    this.gameStatus = 0;
    this.posDifference = 0;
    //this.gameData = new int[];

    this.randy = new Random();
    this.seedy = seed;
    randy.setSeed(seedy);

    this.players = myLoadPlayers();
    this.blockies = myGenerateBlocks();
    this.mappy = new CarGameMap(players, seedy, conny.NUMBER_OF_LANES(), conny.TRACK_LENGTH(), blockies, 0);
  
    mappy.setCurrentRound(mappy.getCurrentRound() + 1);

  }

  public CarGameMap getMappy(){
    return this.mappy;
  }

  public static void main(String[] args) throws Exception {

    gameWrapper gw = new gameWrapper("");

    
    double sumAvgSpeeds = 0;
    for (int i = 0; i < 1000; i++) {
      int r = (int)(Math.random()*10000);
      gw.newGame(r);
      while (gw.gameStatus == 0){
        gw.tickGame(gw.decideMove(1), gw.conny.ACCELERATE_COMMAND());
        //if (gw.getMappy().getCurrentRound()%50==0){
        //  gw.printRoundData();
        //}
      }
      sumAvgSpeeds += 1.0*gw.getGameData(2).pX/gw.getGameData(2).round;
      gw.print(".");
    }
    gw.print("\nAverage speed: " + sumAvgSpeeds/1000.0);
    
    //printPlayerStateData(gw.mappy, true);
    //gw.printMiniMap(gw.mappy.getPlayerBlockPosition(1).getBlockNumber(), 20);
    //gw.getGameData(1).printMapView(gw.conny);
    
    //gw.printRoundData();

  }


}
