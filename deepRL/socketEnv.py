import gymnasium as gym
import socket
import logging
import os, sys

from logger import setup_logging

logger = logging.getLogger('Agent')

payload_size = 1024

class SocketEnv(gym.Env):
    #def __init__(self, host='192.168.1.47', port=4455):
    def __init__(self, host='localhost', port=4455):
        setup_logging(logging.INFO)
        self.host = host
        self.port = port
        self.client_socket = None  # Socket will be created lazily.

    def _connect(self):
        if self.client_socket is not None:
            return  # Already connected

        try:
            pid = os.getpid()
            logger.info(f"Process {pid}: Connecting to {self.host}:{self.port}")
            self.client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.client_socket.connect((self.host, self.port))
        except ConnectionRefusedError:
            pid = os.getpid()
            logger.error(f"Process {pid}: Connection refused. Is the server at {self.host}:{self.port} running?")
            raise

    def _send_operation(self, op_code: str, payload: bytes = b''):
        self._connect()  # Ensure connection exists before sending.
        assert len(op_code) == 2 and payload is not None
        buffer = op_code.encode('utf-8') + payload.ljust(1022, b'\x00')
        assert len(buffer) == 1024
        self.client_socket.sendall(buffer)

    def _receive_fixed(self, size: int = payload_size) -> bytes:
        self._connect() # Ensure connection exists before receiving.
        chunks = []
        bytes_recd = 0
        while bytes_recd < size:
            chunk = self.client_socket.recv(min(size - bytes_recd, payload_size))
            if not chunk:
                raise ConnectionError("Socket connection closed before all data was received.")
            chunks.append(chunk)
            bytes_recd += len(chunk)
        return b''.join(chunks)
    
    def close(self):
        if self.client_socket:
            pid = os.getpid()
            logger.info(f"Process {pid}: Closing socket.")
            self.client_socket.close()
            self.client_socket = None

