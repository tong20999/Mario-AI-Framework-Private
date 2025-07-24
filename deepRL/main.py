import time
import signal

shutdown_requested = False

def handle_shutdown(signum, frame):
    global shutdown_requested
    shutdown_requested = True
    print("\nShutdown signal received.")

if __name__ == '__main__':
    signal.signal(signal.SIGINT, handle_shutdown)
    signal.signal(signal.SIGTERM, handle_shutdown)
    print('hello')
    try:
        while not shutdown_requested:
            print('working')
            user_response = input("Please enter your name: ")
    except (Exception, KeyboardInterrupt) as e:
            print("!An error occurred or Ctrl+C was detected! Saving progress before exiting...")
            print(f"Error Type: {type(e).__name__}")
            print(f"Error Message: {e}")
            print("\n--- Full Traceback ---")
    finally:
         print('save')