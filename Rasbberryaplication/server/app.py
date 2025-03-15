from flask import Flask, jsonify, request, send_file
import os
import sys
import subprocess
import platform
import logging
import uuid
from werkzeug.utils import secure_filename

app = Flask(__name__)
logging.basicConfig(level=logging.DEBUG)

# Store the current video process and screensaver process
current_video_process = None
current_screensaver_process = None
is_looping = False

# Configure directories
VIDEO_DIRECTORY = os.path.join(os.path.dirname(os.path.abspath(__file__)), "videos")
IMAGES_DIRECTORY = os.path.join(os.path.dirname(os.path.abspath(__file__)), "images")

# Create directories if they don't exist
for directory in [VIDEO_DIRECTORY, IMAGES_DIRECTORY]:
    if not os.path.exists(directory):
        os.makedirs(directory)

def kill_all_processes():
    global current_video_process, current_screensaver_process
    
    # On Windows, use taskkill to forcefully close VLC
    if platform.system() == "Windows":
        try:
            subprocess.run(['taskkill', '/IM', 'vlc.exe', '/F'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            app.logger.info("VLC process killed via taskkill")
        except Exception as e:
            app.logger.error(f"Error killing VLC via taskkill: {str(e)}")
    
    # Reset process trackers
    current_video_process = None
    current_screensaver_process = None
    return True

def play_slideshow(image_paths, duration=5):
    global current_screensaver_process
    
    # Check common VLC installation paths on Windows
    vlc_paths = [
        r"C:\Program Files\VideoLAN\VLC\vlc.exe",
        r"C:\Program Files (x86)\VideoLAN\VLC\vlc.exe",
        os.path.expandvars(r"%PROGRAMFILES%\VideoLAN\VLC\vlc.exe"),
        os.path.expandvars(r"%PROGRAMFILES(X86)%\VideoLAN\VLC\vlc.exe")
    ]
    
    vlc_path = None
    if platform.system() == "Windows":
        for path in vlc_paths:
            if os.path.exists(path):
                vlc_path = path
                break
        if vlc_path is None:
            raise FileNotFoundError("VLC not found. Please install VLC or verify its installation path")
    else:
        vlc_path = "vlc"
    
    # Create a playlist file
    playlist_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "slideshow.m3u")
    with open(playlist_path, "w") as f:
        for image_path in image_paths:
            f.write(f"{image_path}\n")
    
    # Start VLC with slideshow settings
    command = [
        vlc_path,
        "--fullscreen",
        "--image-duration", str(duration),
        "--loop",
        "--no-video-title-show",
        playlist_path
    ]
    
    try:
        current_screensaver_process = subprocess.Popen(command)
        app.logger.info("Screensaver slideshow started successfully")
    except Exception as e:
        app.logger.error(f"Failed to start screensaver: {str(e)}")
        raise

def play_video_vlc(video_path, loop=False):
    global current_video_process
    # Kill any existing processes before starting new video
    kill_all_processes()
    
    # Check common VLC installation paths on Windows
    vlc_paths = [
        r"C:\Program Files\VideoLAN\VLC\vlc.exe",
        r"C:\Program Files (x86)\VideoLAN\VLC\vlc.exe",
        os.path.expandvars(r"%PROGRAMFILES%\VideoLAN\VLC\vlc.exe"),
        os.path.expandvars(r"%PROGRAMFILES(X86)%\VideoLAN\VLC\vlc.exe")
    ]
    
    vlc_path = None
    if platform.system() == "Windows":
        for path in vlc_paths:
            if os.path.exists(path):
                vlc_path = path
                break
        if vlc_path is None:
            app.logger.error("VLC not found in common installation paths")
            raise FileNotFoundError("VLC not found. Please install VLC or verify its installation path")
    else:
        vlc_path = "vlc"  # Default for Linux/Mac
    
    if not os.path.exists(video_path):
        app.logger.error(f"Video file not found at path: {video_path}")
        raise FileNotFoundError(f"Video file not found: {video_path}")
    
    app.logger.info(f"Using VLC at: {vlc_path}")
    app.logger.info(f"Playing video: {video_path} with loop={loop}")
    
    command = [
        vlc_path,
        "--fullscreen",
        "--no-video-title-show",
        "--quiet",
        video_path
    ]
    
    if loop:
        command.append("--loop")
    
    try:
        current_video_process = subprocess.Popen(command)
        app.logger.info("VLC process started successfully")
    except Exception as e:
        app.logger.error(f"Failed to start VLC: {str(e)}")
        raise

@app.route('/api/videos', methods=['GET'])
def list_videos():
    videos = []
    app.logger.info(f"Scanning directory: {VIDEO_DIRECTORY}")
    try:
        for file in os.listdir(VIDEO_DIRECTORY):
            if file.lower().endswith(('.mp4', '.avi', '.mkv', '.mov')):
                full_path = os.path.join(VIDEO_DIRECTORY, file)
                app.logger.info(f"Found video: {file} at {full_path}")
                videos.append({
                    'name': file,
                    'path': full_path
                })
        app.logger.info(f"Found {len(videos)} videos")
        return jsonify(videos)
    except Exception as e:
        app.logger.error(f"Error listing videos: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/videos/upload', methods=['POST'])
def upload_video():
    if 'video' not in request.files:
        app.logger.error("No video file in request")
        return jsonify({'error': 'No video file provided'}), 400
    
    video_file = request.files['video']
    if video_file.filename == '':
        app.logger.error("Empty filename")
        return jsonify({'error': 'No selected file'}), 400
        
    if video_file:
        try:
            # Get original filename and ensure it's secure
            original_filename = secure_filename(video_file.filename)
            
            # Split filename into base and extension
            base_name, file_ext = os.path.splitext(original_filename)
            
            # If no extension, default to .mp4
            if not file_ext:
                file_ext = '.mp4'
            
            # Create a unique filename while preserving the original name
            new_filename = original_filename
            counter = 1
            while os.path.exists(os.path.join(VIDEO_DIRECTORY, new_filename)):
                new_filename = f"{base_name}_{counter}{file_ext}"
                counter += 1
            
            # Save the file
            file_path = os.path.join(VIDEO_DIRECTORY, new_filename)
            app.logger.info(f"Saving video as: {new_filename}")
            video_file.save(file_path)
            
            # Verify file exists and is readable
            if not os.path.exists(file_path):
                raise FileNotFoundError(f"Failed to save file at {file_path}")
            
            app.logger.info(f"Video saved successfully at: {file_path}")
            return jsonify({
                'status': 'success',
                'message': 'Video uploaded successfully',
                'path': file_path,
                'name': new_filename
            })
            
        except Exception as e:
            app.logger.error(f"Failed to save video: {str(e)}")
            return jsonify({'error': f'Failed to save video: {str(e)}'}), 500

@app.route('/api/video/play', methods=['POST'])
def play_video():
    global is_looping
    try:
        data = request.json
        if not data:
            return jsonify({'error': 'No data provided'}), 400
            
        video_path = data.get('path')
        is_looping = data.get('loop', False)
        
        if not video_path:
            return jsonify({'error': 'No video path provided'}), 400
        
        # Ensure the video path is within the VIDEO_DIRECTORY
        video_path = os.path.abspath(video_path)
        app.logger.info(f"Received video path: {video_path}")
        app.logger.info(f"VIDEO_DIRECTORY: {os.path.abspath(VIDEO_DIRECTORY)}")
        
        if not video_path.startswith(os.path.abspath(VIDEO_DIRECTORY)):
            app.logger.error(f"Invalid video path: {video_path}")
            return jsonify({'error': 'Invalid video path'}), 400
        
        # Verify file exists and has video extension
        if not os.path.exists(video_path):
            app.logger.error(f"Video file not found: {video_path}")
            return jsonify({'error': 'Video file not found'}), 404
            
        file_ext = os.path.splitext(video_path)[1].lower()
        if file_ext not in ['.mp4', '.avi', '.mkv', '.mov']:
            app.logger.error(f"Invalid video extension: {file_ext}")
            return jsonify({'error': 'Invalid video file type'}), 400
        
        app.logger.info(f"Attempting to play video: {video_path}")
        play_video_vlc(video_path, is_looping)
        return jsonify({'status': 'success', 'message': 'Video playback started'})
    except Exception as e:
        app.logger.error(f"Error playing video: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/screen/clear', methods=['POST'])
def clear_screen():
    global current_video_process, current_screensaver_process
    try:
        kill_all_processes()
        app.logger.info("All processes terminated and screen cleared successfully")
        return jsonify({
            'status': 'success',
            'message': 'All processes terminated and screen cleared successfully'
        })
    except Exception as e:
        app.logger.error(f"Failed to clear screen: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/images/upload', methods=['POST'])
def upload_image():
    if 'image' not in request.files:
        app.logger.error("No image file in request")
        return jsonify({'error': 'No image file provided'}), 400
    
    image_file = request.files['image']
    if image_file.filename == '':
        app.logger.error("Empty filename")
        return jsonify({'error': 'No selected file'}), 400
        
    if image_file:
        try:
            original_filename = secure_filename(image_file.filename)
            base_name, file_ext = os.path.splitext(original_filename)
            
            if file_ext.lower() not in ['.jpg', '.jpeg', '.png', '.gif']:
                return jsonify({'error': 'Invalid image format'}), 400
            
            new_filename = original_filename
            counter = 1
            while os.path.exists(os.path.join(IMAGES_DIRECTORY, new_filename)):
                new_filename = f"{base_name}_{counter}{file_ext}"
                counter += 1
            
            file_path = os.path.join(IMAGES_DIRECTORY, new_filename)
            image_file.save(file_path)
            
            return jsonify({
                'status': 'success',
                'message': 'Image uploaded successfully',
                'path': file_path,
                'name': new_filename
            })
            
        except Exception as e:
            app.logger.error(f"Failed to save image: {str(e)}")
            return jsonify({'error': f'Failed to save image: {str(e)}'}), 500

@app.route('/api/images/<path:filename>')
def serve_image(filename):
    try:
        return send_file(
            os.path.join(IMAGES_DIRECTORY, filename),
            mimetype='image/*'
        )
    except Exception as e:
        app.logger.error(f"Error serving image {filename}: {str(e)}")
        return jsonify({'error': str(e)}), 404

@app.route('/api/images', methods=['GET'])
def list_images():
    images = []
    app.logger.info(f"Scanning directory: {IMAGES_DIRECTORY}")
    try:
        base_url = request.host_url.rstrip('/')
        for file in os.listdir(IMAGES_DIRECTORY):
            if file.lower().endswith(('.jpg', '.jpeg', '.png', '.gif')):
                full_path = os.path.join(IMAGES_DIRECTORY, file)
                images.append({
                    'name': file,
                    'path': full_path,
                    'url': f"{base_url}/api/images/{file}"
                })
        return jsonify(images)
    except Exception as e:
        app.logger.error(f"Error listing images: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/screensaver', methods=['POST'])
def activate_screensaver():
    try:
        # Kill any existing processes
        kill_all_processes()
        
        # Get list of images
        image_files = []
        for file in os.listdir(IMAGES_DIRECTORY):
            if file.lower().endswith(('.jpg', '.jpeg', '.png', '.gif')):
                image_files.append(os.path.join(IMAGES_DIRECTORY, file))
        
        if not image_files:
            return jsonify({
                'status': 'error',
                'message': 'No images found for screensaver'
            }), 400
        
        # Start slideshow
        play_slideshow(image_files)
        
        return jsonify({
            'status': 'success',
            'message': 'Screensaver activated with image slideshow'
        })
    except Exception as e:
        app.logger.error(f"Failed to activate screensaver: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/status', methods=['GET'])
def get_status():
    return jsonify({
        'status': 'running',
        'video_playing': current_video_process is not None,
        'screensaver_active': current_screensaver_process is not None,
        'looping': is_looping
    })

@app.route('/api/execute', methods=['POST'])
def execute_command():
    try:
        data = request.json
        if not data:
            return jsonify({'error': 'No data provided'}), 400
            
        command = data.get('command')
        if not command:
            return jsonify({'error': 'No command provided'}), 400
            
        # For security, only allow specific commands
        allowed_commands = {
            'kill_vlc': {
                'windows': ['taskkill', '/IM', 'vlc.exe', '/F'],
                'linux': ['killall', '-s', '9', 'vlc'],
                'darwin': ['killall', '-9', 'VLC']
            }
        }
        
        if command not in allowed_commands:
            return jsonify({'error': 'Command not allowed'}), 403
            
        # Get the appropriate command for the current OS
        os_name = platform.system().lower()
        if os_name not in allowed_commands[command]:
            return jsonify({'error': f'Command not supported on {os_name}'}), 400
            
        cmd_array = allowed_commands[command][os_name]
        app.logger.info(f"Executing command: {' '.join(cmd_array)}")
        
        try:
            subprocess.run(cmd_array, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return jsonify({
                'status': 'success',
                'message': f'Command {command} executed successfully'
            })
        except subprocess.CalledProcessError as e:
            app.logger.error(f"Command execution failed: {str(e)}")
            return jsonify({'error': f'Command execution failed: {str(e)}'}), 500
            
    except Exception as e:
        app.logger.error(f"Error executing command: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/image/play', methods=['POST'])
def play_image():
    try:
        data = request.json
        if not data:
            return jsonify({'error': 'No data provided'}), 400
            
        image_path = data.get('path')
        if not image_path:
            return jsonify({'error': 'No image path provided'}), 400
        
        # Ensure the image path is within the IMAGES_DIRECTORY
        image_path = os.path.abspath(image_path)
        app.logger.info(f"Received image path: {image_path}")
        app.logger.info(f"IMAGES_DIRECTORY: {os.path.abspath(IMAGES_DIRECTORY)}")
        
        if not image_path.startswith(os.path.abspath(IMAGES_DIRECTORY)):
            app.logger.error(f"Invalid image path: {image_path}")
            return jsonify({'error': 'Invalid image path'}), 400
        
        # Verify file exists and has image extension
        if not os.path.exists(image_path):
            app.logger.error(f"Image file not found: {image_path}")
            return jsonify({'error': 'Image file not found'}), 404
            
        file_ext = os.path.splitext(image_path)[1].lower()
        if file_ext not in ['.jpg', '.jpeg', '.png', '.gif']:
            app.logger.error(f"Invalid image extension: {file_ext}")
            return jsonify({'error': 'Invalid image file type'}), 400
        
        # Check common VLC installation paths on Windows
        vlc_paths = [
            r"C:\Program Files\VideoLAN\VLC\vlc.exe",
            r"C:\Program Files (x86)\VideoLAN\VLC\vlc.exe",
            os.path.expandvars(r"%PROGRAMFILES%\VideoLAN\VLC\vlc.exe"),
            os.path.expandvars(r"%PROGRAMFILES(X86)%\VideoLAN\VLC\vlc.exe")
        ]
        
        vlc_path = None
        if platform.system() == "Windows":
            for path in vlc_paths:
                if os.path.exists(path):
                    vlc_path = path
                    break
            if vlc_path is None:
                app.logger.error("VLC not found in common installation paths")
                raise FileNotFoundError("VLC not found. Please install VLC or verify its installation path")
        else:
            vlc_path = "vlc"  # Default for Linux/Mac
        
        app.logger.info(f"Using VLC at: {vlc_path}")
        app.logger.info(f"Displaying image: {image_path}")
        
        # Kill any existing processes
        kill_all_processes()
        
        # Start VLC with image settings
        command = [
            vlc_path,
            "--fullscreen",
            "--no-video-title-show",
            "--image-duration", "-1",  # -1 means show indefinitely
            "--no-repeat",  # Don't loop
            "--quiet",
            image_path
        ]
        
        try:
            global current_video_process
            current_video_process = subprocess.Popen(command)
            app.logger.info("VLC process started successfully for image display")
            return jsonify({'status': 'success', 'message': 'Image display started'})
        except Exception as e:
            app.logger.error(f"Failed to start VLC: {str(e)}")
            return jsonify({'error': f'Failed to display image: {str(e)}'}), 500
            
    except Exception as e:
        app.logger.error(f"Error displaying image: {str(e)}")
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True) 
