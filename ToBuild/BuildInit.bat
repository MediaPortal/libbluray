
REM detect if BUILD_TYPE should be release or debug
if not %1!==Debug! goto RELEASE
:DEBUG
set BUILD_TYPE=Debug
goto START
:RELEASE
set BUILD_TYPE=Release
goto START


:START
REM Select program path based on current machine environment
set progpath=%ProgramFiles%
if not "%ProgramFiles(x86)%".=="". set progpath=%ProgramFiles(x86)%

REM Select Visual Studio version

REM set other libbluray related paths
set GIT_ROOT=..
set LibblurayJAR="%GIT_ROOT%\src\libbluray\bdj\build.xml"
set NugetPackages=%GIT_ROOT%\Packages

rem Select MSbuild version
if not defined MB set MB="%ProgramFiles(x86)%\Microsoft Visual Studio\2019\Community\MSBuild\Current\Bin\MSBuild.exe"

if not exist %MB% set MB="%ProgramFiles(x86)%\Microsoft Visual Studio\2017\Community\MSBuild\15.0\Bin\MSBUILD.exe"

if not exist %MB% set MB="%ProgramFiles(x86)%\MSBuild\14.0\Bin\MSBUILD.exe"

REM set log file
set log=%project%_%BUILD_TYPE%.log

REM copy BuildReport resources
xcopy /I /Y .\BuildReport\_BuildReport_Files .\_BuildReport_Files

REM Download NuGet packages
@%MB% RestorePackages.targets