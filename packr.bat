SET JAVA=C:\tools\java\openjdk-18.0.2.1\bin
SET JAR_PATH=out/artifacts/hunspellDemo_jar2/
SET JARS=%JAR_PATH%bridj-0.7.0.jar ^
%JAR_PATH%controlsfx-11.1.0.jar ^
%JAR_PATH%dx-1.7.jar ^
%JAR_PATH%formsfx-core-11.3.2.jar ^
%JAR_PATH%hunspell-bridj-1.0.4.jar ^
%JAR_PATH%hunspellDemo.jar ^
%JAR_PATH%javafx-base-11.0.2-win.jar ^
%JAR_PATH%javafx-base-11.0.2.jar ^
%JAR_PATH%javafx-controls-11.0.2-win.jar ^
%JAR_PATH%javafx-controls-11.0.2.jar ^
%JAR_PATH%javafx-fxml-11.0.2-win.jar ^
%JAR_PATH%javafx-fxml-11.0.2.jar ^
%JAR_PATH%javafx-graphics-11.0.2-win.jar ^
%JAR_PATH%javafx-graphics-11.0.2.jar ^
%JAR_PATH%log4j-1.2.17.jar

: %JAR_PATH%hunspell.jar %JAR_PATH%jna-5.12.1.jar

: clean out dir
rmdir "out-win" /q /s

%JAVA%/java -jar packr-all-4.0.0.jar  ^
     --platform windows64 ^
     --jdk C:\tools\java\openjdk-18.0.2.1\ ^
     --useZgcIfSupportedOs ^
     --executable myapp ^
     --classpath %JARS% ^
     --mainclass com.example.hunspelleditor.HelloApplication ^
     --vmargs Xmx1G ^
     --resources futashoz\hunspell test.cfg ^
     --output out-win