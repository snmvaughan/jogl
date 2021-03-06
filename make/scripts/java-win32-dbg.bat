
set BLD_SUB=build-win32
set J2RE_HOME=c:\jre1.6.0_21_x32
set JAVA_HOME=c:\jdk1.6.0_21_x32
set ANT_PATH=C:\apache-ant-1.8.0

set PATH=%JAVA_HOME%\bin;%ANT_PATH%\bin;c:\mingw\bin;%PATH%

set BLD_DIR=..\%BLD_SUB%
set LIB_DIR=..\..\gluegen\%BLD_SUB%\obj;%BLD_DIR%\nativewindow\obj;%BLD_DIR%\jogl\obj;%BLD_DIR%\newt\obj

set CP_ALL=.;%BLD_DIR%\jogl\jogl.all.jar;%BLD_DIR%\nativewindow\nativewindow.all.jar;%BLD_DIR%\newt\newt.all.jar;%BLD_DIR%\jogl\jogl.test.jar;..\..\gluegen\%BLD_SUB%\gluegen-rt.jar;..\..\gluegen\make\lib\junit.jar;%ANT_PATH%\lib\ant.jar;%ANT_PATH%\lib\ant-junit.jar

echo CP_ALL %CP_ALL%

REM set D_ARGS="-Djogamp.debug.JNILibLoader=true" "-Djogamp.debug.NativeLibrary=true" "-Djogamp.debug.NativeLibrary.Lookup=true" "-Djogl.debug.GLProfile=true"
REM set D_ARGS="-Djogl.debug=all" "-Dnewt.debug=all" "-Dnativewindow.debug=all"
REM set D_ARGS="-Dnewt.debug.Window" "-Dnativewindow.debug.TraceLock"
REM set D_ARGS="-Dnativewindow.debug.TraceLock"
set D_ARGS="-Dnewt.debug.Window" "-Dnewt.debug.Display"
REM set D_ARGS="-Dnewt.debug.Window" "-Dnewt.debug.Display" "-Dnewt.test.Window.reparent.incompatible=true"

set X_ARGS="-Dsun.java2d.noddraw=true" "-Dsun.awt.noerasebackground=true"

%J2RE_HOME%\bin\java -classpath %CP_ALL% "-Djava.library.path=%LIB_DIR%" %D_ARGS% %X_ARGS% %1 %2 %3 %4 %5 %6 %7 %8 %9 > java-win32-dbg.log 2>&1

