cd "C:\Users\AndrewDesktop\Desktop\GameWrapper"
javac -cp ".;./lib/ge.jar;./lib/gr.jar" -d "./lib/" gameWrapper.java
cd "lib"
copy "*.class" "C:\Users\AndrewDesktop\Desktop\sanicEnv\lib\"
cmd /k