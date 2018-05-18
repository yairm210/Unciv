@ECHO OFF
ECHO ##############################################################
ECHO # Crush all PNG from Images/* and store them in Optimized/*  #
ECHO ##############################################################
ECHO Files to crush:

for /D  %%d in (.\*) do ( for /F "tokens=*" %%x IN ('DIR %%d\*.png /B') DO ECHO - %%x )

PAUSE

for /D  %%d in (.\*) do (
	for /F "tokens=*" %%x IN ('DIR %%d\*.png /B') DO (
	ECHO Crushing: %%d\%%x
	pngcrush -brute "%%d\%%x" temp.png
	if not exist "Optimized\%%d" mkdir "Optimized\%%d"
	MOVE /Y temp.png "Optimized\%%d\%%x"
	)
)

PAUSE
