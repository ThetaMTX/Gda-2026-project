﻿# Gdakon 2026 LCD Display remote control
A simple program writen in python and Kotlin to control the Gdakon 2026 LCD Display from a Raspberry Pi.
Displaying media on screen (Raspberry) remotely via phone application.

## Features (to do)
- [x] Display video on the screen
- [x] Clear the screen
- [x] Access the screen remotely via phone app
- [x] Display screen saver/battery pertcentage
- [x] Display time and date (kinda useless maybe in the future)
- [ ] Preplan the 3D printable case for the Raspberry + LCD screen + 2 modules (probably blender)
- [ ] Test test test
- [x] Wifi test
- [ ] Pre test in the hottel
## Installation and starting
```
python -m venv venv
venv\Scripts\activate
```
```
pip install -r requirements.txt
python API.py
```


## Possible programs for static IP's 
- Tailscale app (static ip for both windows and linux devices)[hopefully no need for changing the IP righhhht ?]
  (so far testing proves it and qutie neat VPN)


## Usage
- **Connection**: connect to the rasbery pi to Tailscale app to get static IP
- **Run**: run the program (API.py)
- **Control**: control the screen via the phone app
- **Exit**: exit the program (the screen still works with the program included for the time being)
- **Reboot**: reboot the rasbery pi
- **Debug**: debug the program (for debug only lol)

## Uni
- **PCB board**: two PCB boards (one for making sure it will be 5V and 2nd for the powerbank discharge and charge at the same time)

## 3D printing
- [ ] **Folder**: add specific folder for 3D printing model (pref blender)
- [ ] **Image**: add image representing how it would look like

## 3D model
- [ ] **Model**: needs to be specifically developed for the door frame

## Possible program/app on mobile device
- [x] **App**: Written in Android studio Kotlin
- [x] **Connection**: Tailscale
- [x] **API**: Api end point for getting the data for the app 

## Support
- ☕ & ♥
