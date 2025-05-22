from typing import Any, SupportsFloat
import gymnasium as gym
from typing import Optional
import socket
import numpy as np
import asyncio

class SocketEnv(gym.Env):
    def __init__(self):
        self.client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

        try:
            self.client_socket.connect(('localhost', 4455))
        except ConnectionRefusedError:
            print("Connection refused. Ensure the server is running.")

    def _send_operation(self, op_code: str, payload: bytes = b''):
        # op_code 
        # 01 reset
        # 02 get_observation
        # 03 step
        assert len(op_code) == 2 and payload is not None
        buffer = op_code.encode('utf-8') + payload.ljust(1022, b'\x00')  # pad to 1024
        assert len(buffer) == 1024
        self.client_socket.sendall(buffer)

    def _receive_fixed(self, size: int = 1024) -> bytes:
        """Receives exactly `size` bytes from the socket."""
        chunks = []
        bytes_recd = 0
        while bytes_recd < size:
            chunk = self.client_socket.recv(min(size - bytes_recd, 2048))
            if not chunk:
                raise ConnectionError("Socket connection closed before all data was received.")
            chunks.append(chunk)
            bytes_recd += len(chunk)
        return b''.join(chunks)
                                        