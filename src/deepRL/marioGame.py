from py4j.java_gateway import JavaGateway

class MarioGame:
        
    def __init__(self, gateway:JavaGateway) -> None:
        self.agent = gateway.entry_point.getAgent()


    def get_agent(self):
        return self.agent