import jpype as jp
import jpype.imports
from jpype.types import *


#jp.addClassPath('gr.jar')
#jp.addClassPath('ge.jar')
#jp.addClassPath('acm.jar')
classpath=".;./lib/ge.jar;./lib/gr.jar"
jp.startJVM(convertStrings=False, classpath=classpath)
import java.lang
import java.util

java.lang.System.out.println("\nJava Class Path:")
print(java.lang.System.getProperty("java.class.path") + "\n")

wrapperClass = jp.JClass("gameWrapper")
gamedataClass = jp.JClass("gameDataOutput")

gw = wrapperClass("")

gw.print_("The eagle has landed!\n\n")

gw.newGame(123)
gw.printMiniMap(10, 15)
gw.getGameData(1).printMapView(gw.conny)

while (gw.gameStatus == 0):
    gw.tickGame(gw.decideMove(1), gw.conny.ACCELERATE_COMMAND())
    if (gw.getMappy().getCurrentRound() == 100):
        gw.printMiniMap(gw.mappy.getPlayerBlockPosition(1).getBlockNumber(), 20)
        gw.getGameData(1).printMapView(gw.conny)
    

gw.printMiniMap(gw.mappy.getPlayerBlockPosition(1).getBlockNumber(), 20)
gw.getGameData(1).printMapView(gw.conny)
gw.print_("\nPosition difference: " + str(gw.posDifference))


jp.shutdownJVM()