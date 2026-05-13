@REM SBT launcher script
@REM
@REM Environment:
@REM JAVA_HOME - location of a JDK home dir (mandatory)
@REM SBT_OPTS  - JVM options (optional)
@REM Configuration:
@REM sbtconfig.txt found in the SBT_HOME.

@REM   ZOMG! We need delayed expansion to build up CFG_OPTS later
@setlocal enabledelayedexpansion

@echo off
set SBT_BIN_DIR=%~dp0
if not defined SBT_HOME for %%I in ("!SBT_BIN_DIR!\..") do set "SBT_HOME=%%~fI"

set SBT_ARGS=
set _JAVACMD=
set _SBT_OPTS=
set _JAVA_OPTS=

set init_sbt_version=1.12.11
set sbt_default_mem=1024
set default_sbt_opts=
set default_java_opts=-Dfile.encoding=UTF-8
set sbt_jar=
set build_props_sbt_version=
set run_native_client=
set run_jvm_client=
set shutdownall=

set sbt_args_print_version=
set sbt_args_print_sbt_version=
set sbt_args_print_sbt_script_version=
set sbt_args_verbose=
set sbt_args_debug=
set sbt_args_debug_inc=
set sbt_args_batch=
set sbt_args_color=
set sbt_args_no_colors=
set sbt_args_no_global=
set sbt_args_no_share=
set sbt_args_no_hide_jdk_warnings=
set sbt_args_sbt_jar=
set sbt_args_ivy=
set sbt_args_supershell=
set sbt_args_timings=
set sbt_args_traces=
set sbt_args_sbt_boot=
set sbt_args_sbt_cache=
set sbt_args_allow_empty=
set sbt_args_sbt_dir=
set sbt_args_sbt_version=
set sbt_args_mem=
set sbt_args_client=
set sbt_args_jvm_client=
set sbt_args_no_server=
set is_this_dir_sbt=0

rem users can set SBT_OPTS via .sbtopts
if exist .sbtopts for /F %%A in (.sbtopts) do (
  set _sbtopts_line=%%A
  if not "!_sbtopts_line:~0,1!" == "#" (
    if "!_sbtopts_line:~0,2!" == "-J" (
      set _sbtopts_line=!_sbtopts_line:~2,1000!
    )
    if defined _SBT_OPTS (
      set _SBT_OPTS=!_SBT_OPTS! !_sbtopts_line!
    ) else (
      set _SBT_OPTS=!_sbtopts_line!
    )
  )
)

rem TODO: remove/deprecate sbtconfig.txt and parse the sbtopts files

rem FIRST we load the config file of extra options.
set SBT_CONFIG=!SBT_HOME!\conf\sbtconfig.txt
set SBT_CFG_OPTS=
if exist "!SBT_CONFIG!" (
  for /F "tokens=* eol=# usebackq delims=" %%i in ("!SBT_CONFIG!") do (
    set DO_NOT_REUSE_ME=%%i
    rem ZOMG (Part #2) WE use !! here to delay the expansion of
    rem SBT_CFG_OPTS, otherwise it remains "" for this loop.
    set SBT_CFG_OPTS=!SBT_CFG_OPTS! !DO_NOT_REUSE_ME!
  )
)

rem poor man's jenv (which is not available on Windows)
if defined JAVA_HOMES (
  if exist .java-version for /F %%A in (.java-version) do (
    set JAVA_HOME=%JAVA_HOMES%\%%A
    set JDK_HOME=%JAVA_HOMES%\%%A
  )
)

if exist "project\build.properties" (
  for /F "eol=# delims== tokens=1*" %%a in (project\build.properties) do (
    if "%%a" == "sbt.version" if not "%%b" == "" (
      set build_props_sbt_version=%%b
    )
  )
)

rem must set PATH or wrong javac is used for java projects
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

rem We use the value of the JAVACMD environment variable if defined
if defined JAVACMD set "_JAVACMD=%JAVACMD%"

rem remove quotes
if defined _JAVACMD set _JAVACMD=!_JAVACMD:"=!

if not defined _JAVACMD (
  if not "%JAVA_HOME%" == "" (
    if exist "%JAVA_HOME%\bin\java.exe" set "_JAVACMD=%JAVA_HOME%\bin\java.exe"
  )
)

if not defined _JAVACMD set _JAVACMD=java

rem We use the value of the JAVA_OPTS environment variable if defined, rather than the config. 
if not defined _JAVA_OPTS if defined JAVA_OPTS set _JAVA_OPTS=%JAVA_OPTS% 

rem users can set JAVA_OPTS via .jvmopts (sbt-extras style) 
if exist .jvmopts for /F %%A in (.jvmopts) do ( 
  set _jvmopts_line=%%A 
  if not "!_jvmopts_line:~0,1!" == "#" ( 
    if defined _JAVA_OPTS ( 
      set _JAVA_OPTS=!_JAVA_OPTS! %%A 
    ) else ( 
      set _JAVA_OPTS=%%A 
    ) 
  ) 
) 
 
rem If nothing is defined, use the defaults. 
if not defined _JAVA_OPTS if defined default_java_opts set _JAVA_OPTS=!default_java_opts!

rem We use the value of the SBT_OPTS environment variable if defined, rather than the config.
if not defined _SBT_OPTS if defined SBT_OPTS set _SBT_OPTS=%SBT_OPTS%
if not defined _SBT_OPTS if defined SBT_CFG_OPTS set _SBT_OPTS=!SBT_CFG_OPTS!
if not defined _SBT_OPTS if defined default_sbt_opts set _SBT_OPTS=!default_sbt_opts!

if defined SBT_NATIVE_CLIENT (
  if "%SBT_NATIVE_CLIENT%" == "true" (
    set sbt_args_client=1
  )
)

:args_loop
shift

if "%~0" == "" goto args_end
set g=%~0

rem make sure the sbt_args_debug gets set first incase any argument parsing uses :dlog
if "%~0" == "-d" set _debug_arg=true
if "%~0" == "--debug" set _debug_arg=true

if defined _debug_arg (
  set _debug_arg=
  set sbt_args_debug=1
  set SBT_ARGS=-debug !SBT_ARGS!
  goto args_loop
)

if "%~0" == "-h" goto usage
if "%~0" == "-help" goto usage
if "%~0" == "--help" goto usage

if "%~0" == "-v" set _verbose_arg=true
if "%~0" == "-verbose" set _verbose_arg=true
if "%~0" == "--verbose" set _verbose_arg=true

if defined _verbose_arg (
  set _verbose_arg=
  set sbt_args_verbose=1
  goto args_loop
)

if "%~0" == "-V" set _version_arg=true
if "%~0" == "-version" set _version_arg=true
if "%~0" == "--version" set _version_arg=true

if defined _version_arg (
  set _version_arg=
  set sbt_args_print_version=1
  goto args_loop
)

if "%~0" == "--client" set _client_arg=true

if defined _client_arg (
  set _client_arg=
  set sbt_args_client=1
  goto args_loop
)

if "%~0" == "--jvm-client" set _jvm_client_arg=true

if defined _jvm_client_arg (
  set _jvm_client_arg=
  set sbt_args_jvm_client=1
  set SBT_ARGS=--client !SBT_ARGS!
  goto args_loop
)

if "%~0" == "-batch" set _batch_arg=true
if "%~0" == "--batch" set _batch_arg=true

if defined _batch_arg (
  set _batch_arg=
  set sbt_args_batch=1
  goto args_loop
)

if "%~0" == "-no-colors" set _no_colors_arg=true
if "%~0" == "--no-colors" set _no_colors_arg=true

if defined _no_colors_arg (
  set _no_colors_arg=
  set sbt_args_no_colors=1
  goto args_loop
)

if "%~0" == "-no-server" set _no_server_arg=true
if "%~0" == "--no-server" set _no_server_arg=true

if defined _no_server_arg (
  set _no_server_arg=
  set sbt_args_no_server=1
  goto args_loop
)

if "%~0" == "--no-hide-jdk-warnings" set _no_hide_jdk_warnings=true

if defined _no_hide_jdk_warnings (
  set _no_hide_jdk_warnings=
  set sbt_args_no_hide_jdk_warnings=1
  goto args_loop
)

if "%~0" == "-no-global" set _no_global_arg=true
if "%~0" == "--no-global" set _no_global_arg=true

if defined _no_global_arg (
  set _no_global_arg=
  set sbt_args_no_global=1
  goto args_loop
)

if "%~0" == "-traces" set _traces_arg=true
if "%~0" == "--traces" set _traces_arg=true

if defined _traces_arg (
  set _traces_arg=
  set sbt_args_traces=1
  goto args_loop
)

if "%~0" == "-sbt-create" set _allow_empty_arg=true
if "%~0" == "--sbt-create" set _allow_empty_arg=true
if "%~0" == "-allow-empty" set _allow_empty_arg=true
if "%~0" == "--allow-empty" set _allow_empty_arg=true

if defined _allow_empty_arg (
  set _allow_empty_arg=
  set sbt_args_allow_empty=1
  goto args_loop
)

if "%~0" == "-sbt-dir" set _sbt_dir_arg=true
if "%~0" == "--sbt-dir" set _sbt_dir_arg=true

if defined _sbt_dir_arg (
 set _sbt_dir_arg=
 if not "%~1" == "" (
   set sbt_args_sbt_dir=%1
   shift
   goto args_loop
 ) else (
   echo "%~0" is missing a value
   goto error
 )
)

if "%~0" == "-sbt-boot" set _sbt_boot_arg=true
if "%~0" == "--sbt-boot" set _sbt_boot_arg=true

if defined _sbt_boot_arg (
 set _sbt_boot_arg=
 if not "%~1" == "" (
   set sbt_args_sbt_boot=%1
   shift
   goto args_loop
 ) else (
   echo "%~0" is missing a value
   goto error
 )
)

if "%~0" == "-sbt-cache" set _sbt_cache_arg=true
if "%~0" == "--sbt-cache" set _sbt_cache_arg=true

if defined _sbt_cache_arg (
 set _sbt_cache_arg=
 if not "%~1" == "" (
   set sbt_args_sbt_cache=%1
   shift
   goto args_loop
 ) else (
   echo "%~0" is missing a value
   goto error
 )
)

if "%~0" == "-sbt-jar" set _sbt_jar=true
if "%~0" == "--sbt-jar" set _sbt_jar=true

if defined _sbt_jar (
 set _sbt_jar=
 if not "%~1" == "" (
   if exist "%~1" (
     set sbt_args_sbt_jar=%1
     shift
     goto args_loop
   ) else (
      echo %~1 does not exist
      goto error
   )
 ) else (
   echo "%~0" is missing a value
   goto error
 )
)

if "%~0" == "-ivy" set _sbt_ivy_arg=true
if "%~0" == "--ivy" set _sbt_ivy_arg=true

if defined _sbt_ivy_arg (
 set _sbt_ivy_arg=
 if not "%~1" == "" (
   set sbt_args_ivy=%1
   shift
   goto args_loop
 ) else (
   echo "%~0" is missing a value
   goto error
 )
)

if "%~0" == "-debug-inc" set _debug_inc_arg=true
if "%~0" == "--debug-inc" set _debug_inc_arg=true

if defined _debug_inc_arg (
  set _debug_inc_arg=
  set sbt_args_debug_inc=1
  goto args_loop
)

if "%~0" == "--sbt-version" set _sbt_version_arg=true
if "%~0" == "-sbt-version" set _sbt_version_arg=true

if defined _sbt_version_arg (
 set _sbt_version_arg=
 if not "%~1" == "" (
   set sbt_args_sbt_version=%~1
   shift
   goto args_loop
 ) else (
   echo "%~0" is missing a value
   goto error
 )
)

if "%~0" == "--mem" set _sbt_mem_arg=true
if "%~0" == "-mem" set _sbt_mem_arg=true

if defined _sbt_mem_arg (
 set _sbt_mem_arg=
 if not "%~1" == "" (
   set sbt_args_mem=%~1
   shift
   goto args_loop
 ) else (
   echo "%~0" is missing a value
   goto error
 )
)

if "%~0" == "--supershell" set _supershell_arg=true
if "%~0" == "-supershell" set _supershell_arg=true

if defined _supershell_arg (
 set _supershell_arg=
 if not "%~1" == "" (
   set sbt_args_supershell=%~1
   shift
   goto args_loop
 ) else (
   echo "%~0" is missing a value
   goto error
 )
)

if "%~0" == "--color" set _color_arg=true
if "%~0" == "-color" set _color_arg=true

if defined _color_arg (
  set _color_arg=
  if not "%~1" == "" (
   set sbt_args_color=%~1
   shift
   goto args_loop
  ) else (
   echo "%~0" is missing a value
   goto error
  )
  goto args_loop
)

if "%~0" == "--no-share" set _no_share_arg=true
if "%~0" == "-no-share" set _no_share_arg=true

if defined _no_share_arg (
  set _no_share_arg=
  set sbt_args_no_share=1
  goto args_loop
)

if "%~0" == "--timings" set _timings_arg=true
if "%~0" == "-timings" set _timings_arg=true

if defined _timings_arg (
  set _timings_arg=
  set sbt_args_timings=1
  goto args_loop
)

if "%~0" == "shutdownall" (
  set shutdownall=1
  goto args_loop
)

if "%~0" == "--script-version" (
  set sbt_args_print_sbt_script_version=1
  goto args_loop
)

if "%~0" == "--numeric-version" (
  set sbt_args_print_sbt_version=1
  goto args_loop
)

if "%~0" == "-jvm-debug" set _jvm_debug_arg=true
if "%~0" == "--jvm-debug" set _jvm_debug_arg=true

if defined _jvm_debug_arg (
  set _jvm_debug_arg=
  if not "%~1" == "" (
    set /a JVM_DEBUG_PORT=%~1 2>nul >nul
    if !JVM_DEBUG_PORT! EQU 0 (
      rem next argument wasn't a port, set a default and process next arg
      set /A JVM_DEBUG_PORT=5005
      goto args_loop
    ) else (
      shift
      goto args_loop
    )
  )
)

if "%~0" == "-java-home" set _java_home_arg=true
if "%~0" == "--java-home" set _java_home_arg=true

if defined _java_home_arg (
  set _java_home_arg=
  if not "%~1" == "" (
    if exist "%~1\bin\java.exe" (
      set "_JAVACMD=%~1\bin\java.exe"
      set "JAVA_HOME=%~1"
      set "JDK_HOME=%~1"
      shift
      goto args_loop
    ) else (
      echo Directory "%~1" for JAVA_HOME is not valid
      goto error
    )
  ) else (
    echo Second argument for --java-home missing
    goto error
  )
)

if "%~0" == "new" (
  if not defined SBT_ARGS (
    set sbt_new=true
  )
)
if "%~0" == "init" (
  if not defined SBT_ARGS (
    set sbt_new=true
  )
)

if "%g:~0,2%" == "-D" (
  rem special handling for -D since '=' gets parsed away
  for /F "tokens=1 delims==" %%a in ("%g%") do (
    rem make sure it doesn't have the '=' already
    if "%g%" == "%%a" (
      if not "%~1" == "" (
        call :dlog [args_loop] -D argument %~0=%~1
        set "SBT_ARGS=!SBT_ARGS! %~0=%~1"
        shift
        goto args_loop
      ) else (
        echo %g% is missing a value
        goto error
      )
    ) else (
      call :dlog [args_loop] -D argument %~0
      set "SBT_ARGS=!SBT_ARGS! %~0"
      goto args_loop
    )
  )
)

if not "%g:~0,5%" == "-XX:+" if not "%g:~0,5%" == "-XX:-" if "%g:~0,3%" == "-XX" (
  rem special handling for -XX since '=' gets parsed away
  for /F "tokens=1 delims==" %%a in ("%g%") do (
    rem make sure it doesn't have the '=' already
    if "%g%" == "%%a" (
      if not "%~1" == "" (
        call :dlog [args_loop] -XX argument %~0=%~1
        set "SBT_ARGS=!SBT_ARGS! %~0=%~1"
        shift
        goto args_loop
      ) else (
        echo %g% is missing a value
        goto error
      )
    ) else (
      call :dlog [args_loop] -XX argument %~0
      set "SBT_ARGS=!SBT_ARGS! %~0"
      goto args_loop
    )
  )
)

rem handle -X JVM options (e.g., -Xmx1G, -Xms512M, -Xss4M) - fixes #5742
if "%g:~0,2%" == "-X" (
  call :dlog [args_loop] -X JVM argument %~0
  call :addJava %~0
  goto args_loop
)

if defined sbt_new if "%g:~0,2%" == "--" (
  rem special handling for -- template arguments since '=' gets parsed away on Windows
  for /F "tokens=1 delims==" %%a in ("%g%") do (
    rem make sure it doesn't have the '=' already
    if "%g%" == "%%a" (
      if not "%~1" == "" (
        call :dlog [args_loop] -- argument %~0=%~1
        set "SBT_ARGS=!SBT_ARGS! %~0=%~1"
        shift
        goto args_loop
      )
    )
  )
)

rem the %0 (instead of %~0) preserves original argument quoting
set SBT_ARGS=!SBT_ARGS! %0

goto args_loop
:args_end

if exist build.sbt (
  set is_this_dir_sbt=1
)
if exist project\build.properties (
  set is_this_dir_sbt=1
)

rem Store current directory in a variable for delayed expansion to handle paths with parentheses
set "CURRENT_DIR=%CD%"

rem Confirm a user's intent if the current directory does not look like an sbt
rem top-level directory and the "new" command was not given.

if not defined sbt_args_allow_empty if not defined sbt_args_print_version if not defined sbt_args_print_sbt_version if not defined sbt_args_print_sbt_script_version if not defined shutdownall (
  if not !is_this_dir_sbt! equ 1 (
    if not defined sbt_new (
      >&2 echo [error] Neither build.sbt nor a 'project' directory in the current directory: "!CURRENT_DIR!"
      >&2 echo [error] run 'sbt new', touch build.sbt, or run 'sbt --allow-empty'.
      goto error
    )
  )
)

call :process

rem avoid bootstrapping/java version check for script version

if !shutdownall! equ 1 (
  set count=0
  for /f "tokens=1" %%i in ('jps -lv ^| findstr "xsbt.boot.Boot"') do (
    taskkill /F /PID %%i
    set /a count=!count!+1
  )
  >&2 echo shutdown !count! sbt processes
  goto :eof
)

if !sbt_args_print_sbt_script_version! equ 1 (
  echo !init_sbt_version!
  goto :eof
)

call :checkjava

if !run_native_client! equ 1 if not defined sbt_args_print_version (
  goto :runnative !SBT_ARGS!
  goto :eof
)

if defined sbt_args_sbt_jar (
  set "sbt_jar=!sbt_args_sbt_jar!"
) else (
  set "sbt_jar=!SBT_HOME!\bin\sbt-launch.jar"
)

set sbt_jar=!sbt_jar:"=!

call :copyrt

if defined JVM_DEBUG_PORT (
  set _JAVA_OPTS=!_JAVA_OPTS! -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=!JVM_DEBUG_PORT!
)

call :sync_preloaded

call :run !SBT_ARGS!

if ERRORLEVEL 1 goto error
goto end

:run

rem set arguments

if defined sbt_args_debug_inc (
  set _SBT_OPTS=-Dxsbt.inc.debug=true !_SBT_OPTS!
)

if defined sbt_args_no_colors (
  set _SBT_OPTS=-Dsbt.log.noformat=true !_SBT_OPTS!
)

if defined sbt_args_no_global (
  set _SBT_OPTS=-Dsbt.global.base=project/.sbtboot !_SBT_OPTS!
)

if defined sbt_args_no_share (
  set _SBT_OPTS=-Dsbt.global.base=project/.sbtboot -Dsbt.boot.directory=project/.boot -Dsbt.ivy.home=project/.ivy !_SBT_OPTS!
)

if defined sbt_args_supershell (
  set _SBT_OPTS=-Dsbt.supershell=!sbt_args_supershell! !_SBT_OPTS!
)

if defined sbt_args_sbt_version (
  set _SBT_OPTS=-Dsbt.version=!sbt_args_sbt_version! !_SBT_OPTS!
)

if defined sbt_args_sbt_dir (
  set _SBT_OPTS=-Dsbt.global.base=!sbt_args_sbt_dir! !_SBT_OPTS!
)

if defined sbt_args_sbt_boot (
  set _SBT_OPTS=-Dsbt.boot.directory=!sbt_args_sbt_boot! !_SBT_OPTS!
)

if defined sbt_args_sbt_cache (
  set _SBT_OPTS=-Dsbt.global.localcache=!sbt_args_sbt_cache! !_SBT_OPTS!
)

if defined sbt_args_ivy (
  set _SBT_OPTS=-Dsbt.ivy.home=!sbt_args_ivy! !_SBT_OPTS!
)

if defined sbt_args_color (
  set _SBT_OPTS=-Dsbt.color=!sbt_args_color! !_SBT_OPTS!
)

if defined sbt_args_mem (
  call :addMemory !sbt_args_mem!
) else (
  call :addDefaultMemory
)

if defined sbt_args_timings (
  set _SBT_OPTS=-Dsbt.task.timings=true -Dsbt.task.timings.on.shutdown=true !_SBT_OPTS!
)

if defined sbt_args_traces (
  set _SBT_OPTS=-Dsbt.traces=true !_SBT_OPTS!
)

if defined sbt_args_no_server (
  set _SBT_OPTS=-Dsbt.io.virtual=false -Dsbt.server.autostart=false !_SBT_OPTS!
)

if not defined sbt_args_no_hide_jdk_warnings (
  if /I !JAVA_VERSION! EQU 25 (
    set _SBT_OPTS=--sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED !_SBT_OPTS!
  )
)

rem TODO: _SBT_OPTS needs to be processed as args and diffed against SBT_ARGS

if !sbt_args_print_sbt_version! equ 1 (
  call :set_sbt_version
  echo !sbt_version!
  goto :eof
)

if !sbt_args_print_version! equ 1 (
  if !is_this_dir_sbt! equ 1 (
    call :set_sbt_version
    echo sbt version in this project: !sbt_version!
  )
  echo sbt runner version: !init_sbt_version!
  >&2 echo.
  >&2 echo [info] sbt runner ^(sbt-the-batch-script^) is a runner to run any declared version of sbt.
  >&2 echo [info] Actual version of the sbt is declared using project\build.properties for each build.
  goto :eof
)

if defined sbt_args_verbose (
  echo # Executing command line:
  echo "!_JAVACMD!"
  if defined _JAVA_OPTS ( call :echolist !_JAVA_OPTS! )
  if defined _SBT_OPTS ( call :echolist !_SBT_OPTS! )
  if defined JAVA_TOOL_OPTIONS ( call :echolist %JAVA_TOOL_OPTIONS% )
  if defined JDK_JAVA_OPTIONS ( call :echolist %JDK_JAVA_OPTIONS% )
  echo -cp
  echo "!sbt_jar!"
  echo xsbt.boot.Boot
  if not "%~1" == "" ( call :echolist %* )
  echo.
)

"!_JAVACMD!" !_JAVA_OPTS! !_SBT_OPTS! %JAVA_TOOL_OPTIONS% %JDK_JAVA_OPTIONS% -cp "!sbt_jar!" xsbt.boot.Boot %*

goto :eof

:runnative

set "_SBTNCMD=!SBT_BIN_DIR!sbtn-x86_64-pc-win32.exe"

if defined sbt_args_verbose (
  echo # running native client
  if not "%~1" == "" ( call :echolist %* )
  set "SBT_ARGS=-v !SBT_ARGS!"
)

set "SBT_SCRIPT=!SBT_BIN_DIR: =%%20!sbt.bat"
set "SBT_ARGS=--sbt-script=!SBT_SCRIPT! %SBT_ARGS%"

rem Microsoft Visual C++ 2010 SP1 Redistributable Package (x64) is required
rem https://www.microsoft.com/en-us/download/details.aspx?id=13523
"!_SBTNCMD!" %SBT_ARGS%

goto :eof

rem for expression tries to interpret files, so simply loop over %* instead
rem fixes dealing with quotes after = args: -Dscala.ext.dirs="C:\Users\First Last\.sbt\0.13\java9-rt-ext-adoptopenjdk_11_0_3"
:echolist
rem call method is in first call of %0
shift

if "%~0" == "" goto echolist_end
set "p=%~0"

if "%p:~0,2%" == "-D" (
  rem special handling for -D since '=' gets parsed away
  for /F "tokens=1 delims==" %%a in ("%p%") do (
    rem make sure it doesn't have the '=' already
    if "%p%" == "%%a" if not "%~1" == "" (
      echo %0=%1
      shift
      goto echolist
    )
  )
)

if not "%p:~0,5%" == "-XX:+" if not "%p:~0,5%" == "-XX:-" if "%p:~0,3%" == "-XX" (
  rem special handling for -XX since '=' gets parsed away
  for /F "tokens=1 delims==" %%a in ("%p%") do (
    rem make sure it doesn't have the '=' already
    if "%p%" == "%%a" if not "%~1" == "" (
      echo %0=%1
      shift
      goto echolist
    )
  )
)

if defined sbt_new if "%p:~0,2%" == "--" (
  rem special handling for -- template arguments since '=' gets parsed away on Windows
  for /F "tokens=1 delims==" %%a in ("%p%") do (
    rem make sure it doesn't have the '=' already
    if "%p%" == "%%a" if not "%~1" == "" (
      echo %0=%1
      shift
      goto echolist
    )
  )
)

if "%p:~0,14%" == "-agentlib:jdwp" (
  rem special handling for --jvm-debug since '=' and ',' gets parsed away
  for /F "tokens=1 delims==" %%a in ("%p%") do (
    rem make sure it doesn't have the '=' already
    if "%p%" == "%%a" if not "%~1" == "" if not "%~2" == "" if not "%~3" == "" if not "%~4" == "" if not "%~5" == "" if not "%~6" == "" if not "%~7" == "" if not "%~8" == "" (
      echo %0=%1=%2,%3=%4,%5=%6,%7=%8
      shift & shift & shift & shift & shift & shift & shift & shift
      goto echolist
    )
  )
)

echo %0
goto echolist

:echolist_end

exit /B 0

:addJava
  call :dlog [addJava] arg = '%*'
  set "_JAVA_OPTS=!_JAVA_OPTS! %*"
exit /B 0

:addMemory
  call :dlog [addMemory] arg = '%*'

  rem evict memory related options
  set _new_java_opts=
  set _old_java_opts=!_JAVA_OPTS!
:next_java_opt
  if "!_old_java_opts!" == "" goto :done_java_opt
  for /F "tokens=1,*" %%g in ("!_old_java_opts!") do (
    set "p=%%g"
    if not "!p:~0,4!" == "-Xmx" if not "!p:~0,4!" == "-Xms" if not "!p:~0,4!" == "-Xss" if not "!p:~0,15!" == "-XX:MaxPermSize" if not "!p:~0,20!" == "-XX:MaxMetaspaceSize" if not "!p:~0,25!" == "-XX:ReservedCodeCacheSize" (
      set _new_java_opts=!_new_java_opts! %%g
    )
    set "_old_java_opts=%%h"
  )
  goto :next_java_opt
:done_java_opt
  set _JAVA_OPTS=!_new_java_opts!

  set _new_sbt_opts=
  set _old_sbt_opts=!_SBT_OPTS!
:next_sbt_opt
  if "!_old_sbt_opts!" == "" goto :done_sbt_opt
  for /F "tokens=1,*" %%g in ("!_old_sbt_opts!") do (
    set "p=%%g"
    if not "!p:~0,4!" == "-Xmx" if not "!p:~0,4!" == "-Xms" if not "!p:~0,4!" == "-Xss" if not "!p:~0,15!" == "-XX:MaxPermSize" if not "!p:~0,20!" == "-XX:MaxMetaspaceSize" if not "!p:~0,25!" == "-XX:ReservedCodeCacheSize" (
      set _new_sbt_opts=!_new_sbt_opts! %%g
    )
    set "_old_sbt_opts=%%h"
  )
  goto :next_sbt_opt
:done_sbt_opt
  set _SBT_OPTS=!_new_sbt_opts!

  rem a ham-fisted attempt to move some memory settings in concert
  set mem=%1
  set /a codecache=!mem! / 8
  if !codecache! GEQ 512 set /a codecache=512
  if !codecache! LEQ 128 set /a codecache=128

  set /a class_metadata_size=!codecache! * 2

  call :addJava -Xms!mem!m
  call :addJava -Xmx!mem!m
  call :addJava -Xss4M
  call :addJava -XX:ReservedCodeCacheSize=!codecache!m

  if /I !JAVA_VERSION! LSS 8 (
    call :addJava -XX:MaxPermSize=!class_metadata_size!m
  )

exit /B 0

:addDefaultMemory
  rem if we detect any of these settings in ${JAVA_OPTS} or ${JAVA_TOOL_OPTIONS} or ${JDK_JAVA_OPTIONS} we need to NOT output our settings.
  rem The reason is the Xms/Xmx, if they don't line up, cause errors.

  set _has_memory_args=

  if defined _JAVA_OPTS for %%g in (%_JAVA_OPTS%) do (
    set "p=%%g"
    if "!p:~0,4!" == "-Xmx" set _has_memory_args=1
    if "!p:~0,4!" == "-Xms" set _has_memory_args=1
    if "!p:~0,4!" == "-Xss" set _has_memory_args=1
  )

  if defined JAVA_TOOL_OPTIONS for %%g in (%JAVA_TOOL_OPTIONS%) do (
    set "p=%%g"
    if "!p:~0,4!" == "-Xmx" set _has_memory_args=1
    if "!p:~0,4!" == "-Xms" set _has_memory_args=1
    if "!p:~0,4!" == "-Xss" set _has_memory_args=1
  )

  if defined JDK_JAVA_OPTIONS for %%g in (%JDK_JAVA_OPTIONS%) do (
    set "p=%%g"
    if "!p:~0,4!" == "-Xmx" set _has_memory_args=1
    if "!p:~0,4!" == "-Xms" set _has_memory_args=1
    if "!p:~0,4!" == "-Xss" set _has_memory_args=1
  )

  if defined _SBT_OPTS for %%g in (%_SBT_OPTS%) do (
    set "p=%%g"
    if "!p:~0,4!" == "-Xmx" set _has_memory_args=1
    if "!p:~0,4!" == "-Xms" set _has_memory_args=1
    if "!p:~0,4!" == "-Xss" set _has_memory_args=1
  )

  if not defined _has_memory_args (
    call :addMemory !sbt_default_mem!
  )
exit /B 0

:dlog
  if defined sbt_args_debug (
    echo %* 1>&2
  )
exit /B 0

:process
rem Parses x out of 1.x; for example 8 out of java version 1.8.0_xx
rem Otherwise, parses the major version; 9 out of java version 9-ea
set JAVA_VERSION=0

for /f "tokens=3 usebackq" %%g in (`CALL "!_JAVACMD!" -Xms32M -Xmx32M -version 2^>^&1 ^| findstr /i version`) do (
  set JAVA_VERSION=%%g
)

rem removes all quotes from JAVA_VERSION
set JAVA_VERSION=!JAVA_VERSION:"=!

for /f "delims=.-_ tokens=1-2" %%v in ("!JAVA_VERSION!") do (
  if /I "%%v" EQU "1" (
    set JAVA_VERSION=%%w
  ) else (
    set JAVA_VERSION=%%v
  )
)

rem parse the first two segments of sbt.version and set run_native_client to
rem 1 if the user has also indicated they want to use native client.
rem sbt new/init should not use native client as it needs to run outside a project
if defined sbt_new (
  set run_native_client=
  exit /B 0
)
set sbtV=!build_props_sbt_version!
set sbtBinaryV_1=
set sbtBinaryV_2=
for /F "delims=.-_ tokens=1-2" %%v in ("!sbtV!") do (
  set sbtBinaryV_1=%%v
  set sbtBinaryV_2=%%w
)
rem default to run_native_client=1 for sbt 2.x
if !sbtBinaryV_1! geq 2 (
  if !sbt_args_jvm_client! equ 1 (
    set run_native_client=
    set run_jvm_client=1
  ) else (
    if !sbt_args_client! equ 0 (
      set run_native_client=
    ) else (
      set run_native_client=1
    )
  )
) else (
  if !sbtBinaryV_1! geq 1 (
    if !sbtBinaryV_2! geq 4 (
      if !sbt_args_jvm_client! equ 1 (
        set run_native_client=
        set run_jvm_client=1
      ) else (
        if !sbt_args_client! equ 1 (
          set run_native_client=1
        )
      )
    )
  )
)

exit /B 0

:checkjava
set /a required_version=8
rem sbt 2.x requires JDK 17+
set "_sbt_check_ver=!build_props_sbt_version!"
if not defined _sbt_check_ver set "_sbt_check_ver=!init_sbt_version!"
if defined _sbt_check_ver (
  for /F "delims=.-_ tokens=1" %%m in ("!_sbt_check_ver!") do (
    if %%m GEQ 2 set /a required_version=17
  )
)
set "_sbt_check_ver="
if /I !JAVA_VERSION! GEQ !required_version! (
  exit /B 0
)
if !required_version! GEQ 17 (
  >&2 echo.
  >&2 echo [error] sbt 2.x requires JDK 17 or above, but you have JDK !JAVA_VERSION!
  >&2 echo.
  exit /B 1
)
>&2 echo.
>&2 echo The Java Development Kit ^(JDK^) installation you have is not up to date.
>&2 echo sbt requires at least version !required_version!+, you have
>&2 echo version "!JAVA_VERSION!"
>&2 echo.
>&2 echo Go to https://adoptium.net/ etc and download
>&2 echo a valid JDK and install before running sbt.
>&2 echo.
exit /B 1

:copyrt
if /I !JAVA_VERSION! GEQ 9 (
  "!_JAVACMD!" !_JAVA_OPTS! !_SBT_OPTS! -jar "!sbt_jar!" --rt-ext-dir > "%TEMP%.\rtext.txt"
  set /p java9_ext= < "%TEMP%.\rtext.txt"
  set "java9_rt=!java9_ext!\rt.jar"

  if not exist "!java9_rt!" (
    mkdir "!java9_ext!"
    "!_JAVACMD!" !_JAVA_OPTS! !_SBT_OPTS! -jar "!sbt_jar!" --export-rt "!java9_rt!"
  )
  set _JAVA_OPTS=!_JAVA_OPTS! -Dscala.ext.dirs="!java9_ext!"
)
exit /B 0

:sync_preloaded
if not defined init_sbt_version (
  rem FIXME: better !init_sbt_version! detection
  FOR /F "tokens=* usebackq" %%F IN (`dir /b "!SBT_HOME!\lib\local-preloaded\org\scala-sbt\sbt" /B`) DO (
    SET init_sbt_version=%%F
  )
)

set PRELOAD_SBT_JAR="%UserProfile%\.sbt\preloaded\org\scala-sbt\sbt\!init_sbt_version!\"
if /I !JAVA_VERSION! GEQ 8 (
  where robocopy >nul 2>nul
  if %ERRORLEVEL% EQU 0 (
    if not exist !PRELOAD_SBT_JAR! (
      if exist "!SBT_HOME!\lib\local-preloaded\" (
        robocopy "!SBT_HOME!\lib\local-preloaded" "%UserProfile%\.sbt\preloaded" /E >nul 2>nul
      )
    )
  )
)
exit /B 0

:usage

for /f "tokens=3 usebackq" %%g in (`CALL "!_JAVACMD!" -Xms32M -Xmx32M -version 2^>^&1 ^| findstr /i version`) do (
  set FULL_JAVA_VERSION=%%g
)

echo.
echo Usage: %~n0 [options]
echo.
echo   -h ^| --help         print this message
echo   -v ^| --verbose      this runner is chattier
echo   -V ^| --version      print sbt version information
echo   --numeric-version   print the numeric sbt version (sbt sbtVersion)
echo   --script-version    print the version of sbt script
echo   -d ^| --debug        set sbt log level to debug
echo   -debug-inc ^| --debug-inc
echo                       enable extra debugging for the incremental debugger
echo   --no-colors         disable ANSI color codes
echo   --color=auto^|always^|true^|false^|never
echo                       enable or disable ANSI color codes      ^(sbt 1.3 and above^)
echo   --supershell=auto^|always^|true^|false^|never
echo                       enable or disable supershell            ^(sbt 1.3 and above^)
echo   --traces            generate Trace Event report on shutdown ^(sbt 1.3 and above^)
echo   --timings           display task timings report on shutdown
echo   --sbt-create        start sbt even if current directory contains no sbt project
echo   --sbt-dir   ^<path^>  path to global settings/plugins directory ^(default: ~/.sbt^)
echo   --sbt-boot  ^<path^>  path to shared boot directory ^(default: ~/.sbt/boot in 0.11 series^)
echo   --sbt-cache ^<path^>  path to global cache directory ^(default: operating system specific^)
echo   --ivy       ^<path^>  path to local Ivy repository ^(default: ~/.ivy2^)
echo   --mem    ^<integer^>  set memory options ^(default: %sbt_default_mem%^)
echo   --no-share          use all local caches; no sharing
echo   --no-global         uses global caches, but does not use global ~/.sbt directory.
echo   --jvm-debug ^<port^>  enable on JVM debugging, open at the given port.
rem echo   --batch             disable interactive mode
echo.
echo   # sbt version ^(default: from project/build.properties if present, else latest release^)
echo   --sbt-version  ^<version^>   use the specified version of sbt
echo   --sbt-jar      ^<path^>      use the specified jar as the sbt launcher
echo.
echo   # java version ^(default: java from PATH, currently !FULL_JAVA_VERSION!^)
echo   --java-home ^<path^>         alternate JAVA_HOME
echo.
echo   # jvm options and output control
echo   JAVA_OPTS           environment variable, if unset uses "!default_java_opts!"
echo   .jvmopts            if this file exists in the current directory, its contents
echo                       are appended to JAVA_OPTS
echo   SBT_OPTS            environment variable, if unset uses "!default_sbt_opts!"
echo   .sbtopts            if this file exists in the current directory, its contents
echo                       are prepended to the runner args
echo   !SBT_CONFIG!
echo                       if this file exists, it is prepended to the runner args
echo   -Dkey=val           pass -Dkey=val directly to the java runtime
echo   -X^<flag^>            pass -X^<flag^> directly to the java runtime
echo                       ^(e.g., -Xmx1G, -Xms512M, -Xss4M^)
rem echo   -J-X                pass option -X directly to the java runtime
rem echo                       ^(-J is stripped^)
rem echo   -S-X                add -X to sbt's scalacOptions ^(-S is stripped^)
echo.
echo In the case of duplicated or conflicting options, the order above
echo shows precedence: JAVA_OPTS lowest, command line options highest.
echo.

@endlocal
exit /B 1

:set_sbt_version
set "sbt_version="
for /F "usebackq tokens=1,2 delims= " %%a in (`CALL "!_JAVACMD!" -jar "!sbt_jar!" "sbtVersion" 2^>^&1`) do (
  if "%%a" == "[info]" (
    set "_version_candidate=%%b"
  ) else (
    set "_version_candidate=%%a"
  )
  echo !_version_candidate!| findstr /R "^[0-9][0-9.]*[-+0-9A-Za-z._]*$" >nul && set "sbt_version=!_version_candidate!"
)
if not defined sbt_version if defined build_props_sbt_version set "sbt_version=!build_props_sbt_version!"
exit /B 0

:error
@endlocal
exit /B 1

:end
@endlocal
exit /B 0
