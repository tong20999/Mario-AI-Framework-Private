from py4j.java_gateway import JavaGateway
gateway = JavaGateway()

game = gateway.entry_point.getMarioGame()
agent = gateway.entry_point.getAgent()
level = gateway.entry_point.getLevel()
result = game.runGame(agent, level, 3, 0, True)
print(result.getGameStatus().toString())