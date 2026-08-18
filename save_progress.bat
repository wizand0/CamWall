@echo off
echo Saving progress to GitHub...

cd /d "D:\Android\AndroidProjects\CamWall"

REM Check git status
git status

REM Add all changes
git add .

REM Commit changes
git commit -m "feat: Complete initial architecture setup and RTSP frame capture implementation

- Set up project architecture with MVVM pattern
- Implemented Camera model and repository
- Created RTSP frame capture functionality using Media3 and TextureView
- Added basic UI components with Jetpack Compose
- Prepared foundation for camera wall feature
- Created development log"

REM Push to GitHub
git push origin main

echo Progress saved successfully!
pause