# Gdakon 2026 LCD Display remote control
A simple program writen (undecided yet) to control the Gdakon 2026 LCD Display from a Raspberry Pi. Access the screen remotely via phone app.

## Features (to do)
- [x] Display video on the screen
- [x] Clear the screen
- [x] Access the screen remotely via phone app
- [x] Display screen saver/battery pertcentage
- [ ] Display time and date (kinda useless maybe in the future)

## Installation and starting
```
python -m venv venv
venv\Scripts\activate
```
```
pip install -r requirements.txt
python API.py
```


## Possible programs for SSH
- Tailscale (static ip) <- chosen program


## Usage
- **Connection**: connect to the rasbery pi via SSH
- **Run**: run the program
- **Control**: control the screen via the phone app
- **Exit**: exit the program (the screen still works with the program included for the time being)
- **Reboot**: reboot the rasbery pi
- **Debug**: debug the program (for debug only lol)

## Uni
- **PCB board**: two PCB boards (one for making sure it will be 5V and 2nd for the powerbank discharge and charge at the same time)

## Possible program/app on mobile device
- **App**: written in Android studio prob
- **Connection**: Tailscale/bash scripts
- **API**: Api end point for getting the data for the app (is it online the connection status or queue)

## Support
- ☕ & ♥
- 
