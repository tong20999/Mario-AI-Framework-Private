from py4j.java_gateway import JavaGateway, CallbackServerParameters
from marioAgent import MarioAgent
from marioGame import MarioGame

def train():
    plot_scores = []
    plot_mean_score = []
    total_score = 0
    record = 0

    gateway = JavaGateway(callback_server_parameters=CallbackServerParameters())
    game = gateway.entry_point.getMarioGame()
    level = gateway.entry_point.getLevel()
    javaAgent = gateway.entry_point.getAgent()
    agent = MarioAgent(gateway, javaAgent)
    game.runGame(javaAgent, level, 100, 0, True)
    while True:
        pass
        # get old state
        #state_old = agent.get_state()

        # get move
        # final_move = agent.get_action()
        # agent.set_java_action(final_move)

        # perform move and get new state
        # reward, done, score = game.play_step(final_move)
        # state_new = agent.get_state(game)

        # train short memory
        # agent.train_short_memory(state_old, final_move, reward, state_new, done)

        # remember
        # agent.remember(state_old, final_move, reward, state_new, done)

        # if done:
        #     # train long memory
        #     game.reset()
        #     agent.n_games += 1
        #     agent.train_long_memory()

        #     if score > record:
        #         record = score
        #         agent.model.save()

        #     print('Game', agent.n_games, 'Score', score, 'Record:', record)

        #     plot_scores.append(score)
        #     total_score += score
        #     mean_score = total_score / agent.n_games
        #     plot_mean_score.append(mean_score)
        #     plot(plot_scores, plot_mean_score)

if __name__ == '__main__':
    train()