import math
from py4j.java_gateway import JavaGateway, CallbackServerParameters
from py4j.java_collections import ListConverter
import torch
from helper import plot
from marioAgent import MarioAgent


def printResult(result):
    print("****************************************************************")
    print(f"Game Status: {result.getGameStatus().toString()} Percentage Completion: {result.getCompletionPercentage()}")
    remaingTime = math.ceil(result.getRemainingTime() / 1000)
    print(f"Lives: {result.getCurrentLives()} Coins: {result.getCurrentCoins()} Remaining Time: {remaingTime}")
    print(" ")
    print(f"Mario State: {result.getMarioMode()} Mushrooms: {result.getNumCollectedMushrooms()} Fire Flowers: {result.getNumCollectedFireflower()}")
    print(f"Total Kills: {result.getKillsTotal()} (Stomps: {result.getKillsByStomp()} Fire Flowers: {result.getNumCollectedFireflower()} Fireballs: {result.getKillsByFire()} Shells: {result.getKillsByShell()} Falls: {result.getKillsByFall()}")
    print(f"Bricks: {result.getNumDestroyedBricks()} Jumps {result.getNumJumps()} Max X Jump: {result.getMaxXJump()} Max Air Time: {result.getMaxJumpAirTime()}")

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
    game.reset(javaAgent, level, 100, 0, True)
    # result = game.runGame(javaAgent, level, 100, 0, True)
    while True:
        # get old state
        state = agent.get_state()
        # get move
        final_move = agent.get_action(state)
        java_list = ListConverter().convert(final_move, gateway._gateway_client)
        # perform move and get new state
        # reward, done, score = game.playStep(True, java_list)
        playStepResult = game.playStep(True, java_list)
        reward = playStepResult.getReward()
        done = playStepResult.isDone()
        score = playStepResult.getScore()

        state_next = agent.get_state()

        # train short memory
        agent.train_short_memory(state, final_move, reward, state_next, done)

        # remember
        agent.remember(state, final_move, reward, state_next, done)

        if done:
            # train long memory
            game.reset(javaAgent, level, 100, 0, True)
            agent.n_games += 1
            agent.train_long_memory()

            if score > record:
                record = score
                agent.model.save()

            print('Game', agent.n_games, 'Score', score, 'Record:', record)

            plot_scores.append(score)
            total_score += score
            mean_score = total_score / agent.n_games
            plot_mean_score.append(mean_score)
            # plot(plot_scores, plot_mean_score)

if __name__ == '__main__':
    train()