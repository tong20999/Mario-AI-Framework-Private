# logger.py
import logging
import sys

def setup_logging(level=logging.INFO):
    logger = logging.getLogger() 
    logger.setLevel(level)

    if logger.handlers:
        for handler in list(logger.handlers):
            logger.removeHandler(handler)

    stdout_handler = logging.StreamHandler(sys.stdout)
    stdout_handler.setLevel(level)
    stdout_handler.addFilter(lambda record: record.levelno < logging.ERROR)
    
    
    stderr_handler = logging.StreamHandler(sys.stderr)
    stderr_handler.setLevel(logging.ERROR)

    formatter = logging.Formatter(
        fmt='%(asctime)s - %(name)s - %(levelname)s: %(message)s',
        datefmt='%Y-%m-%d %H:%M:%S' # <-- This defines the format for %(asctime)s
    )
    stdout_handler.setFormatter(formatter)
    stderr_handler.setFormatter(formatter)

    logger.addHandler(stdout_handler)
    logger.addHandler(stderr_handler)