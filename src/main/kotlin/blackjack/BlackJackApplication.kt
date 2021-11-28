package blackjack

import blackjack.domain.card.Deck
import blackjack.domain.player.BetAmount
import blackjack.domain.player.Dealer
import blackjack.domain.player.Player
import blackjack.domain.player.Players
import blackjack.strategy.draw.DrawStrategy
import blackjack.strategy.draw.HitDrawStrategy
import blackjack.strategy.draw.ReadyDrawStrategy
import blackjack.strategy.shuffle.CardsRandomShuffleStrategy
import blackjack.strategy.split.SingleCommaSplitStrategy
import blackjack.strategy.ui.input.ConsoleInputStrategy
import blackjack.strategy.ui.output.ConsoleOutputStrategy
import blackjack.ui.Command
import blackjack.ui.ErrorView
import blackjack.ui.InputView
import blackjack.ui.ResultView

class BlackJackApplication(
    private val inputView: InputView,
    private val resultView: ResultView,
    private val errorView: ErrorView,
) {
    fun run() {
        val readyDrawStrategy = ReadyDrawStrategy
        val hitDrawStrategy = HitDrawStrategy

        val deck = Deck.initialize(CardsRandomShuffleStrategy)
        val players = players()
        val dealer = Dealer()
        val betBoard = bettingBoard(players)

        val readiedPlayers = readiedPlayers(players, deck, readyDrawStrategy)
        val readiedDealer = readiedDealer(dealer, deck, readyDrawStrategy)
        resultView.showReadiedPlayers(dealer, readiedPlayers)

        val endedGamePlayers = startGameOnGamePlayer(readiedPlayers, deck, HitDrawStrategy)
        val endedDealer = startGameOneDealer(readiedDealer, deck, HitDrawStrategy)
        resultView.showEndedPlayers(endedDealer, endedGamePlayers)

        // val result = endedGamePlayers.players
        //     .associateWith { it.match(endedDealer) }
        //     .map { betBoard[it.key].winBet(it.value.rate)  }

        val dealerProfit = endedGamePlayers.players
            .associateWith { endedDealer.match(it) }
            .map { betBoard.getValue(it.key) }
            .reduce(BetAmount::plus)
    }

    private fun players(): Players =
        try {
            Players.of(inputView.askPlayersInformation(), SingleCommaSplitStrategy)
        } catch (e: Exception) {
            errorView.showErrorMessage(e.message.toString())
            players()
        }

    private fun bettingBoard(players: Players): Map<Player, BetAmount> {
        return players.players
            .associateWith { BetAmount(betAmountPerPlayer(it)) }
    }

    private fun betAmountPerPlayer(it: Player): Int =
        try {
            inputView.askPlayerBetAmount(it.name.name)
        } catch (e: Exception) {
            errorView.showErrorMessage(e.message.toString())
            betAmountPerPlayer(it)
        }

    private fun readiedDealer(dealer: Dealer, deck: Deck, drawStrategy: ReadyDrawStrategy): Player =
        dealer.draw(deck, drawStrategy)

    private fun readiedPlayers(players: Players, deck: Deck, drawStrategy: ReadyDrawStrategy): Players =
        players.players
            .map { it.draw(deck, drawStrategy) }
            .let { Players.from(it) }

    private fun startGameOnGamePlayer(gamePlayer: Players, deck: Deck, drawStrategy: DrawStrategy): Players =
        gamePlayer.players
            .map { drawOrStay(it, deck, drawStrategy) }
            .let { Players.from(it) }

    private fun drawOrStay(player: Player, deck: Deck, drawStrategy: DrawStrategy): Player =
        when {
            player.isFinished() -> player
            !askDrawable(player) -> player.stay()
            else -> draw(player, deck, drawStrategy)
        }

    private fun askDrawable(player: Player): Boolean =
        try {
            val command = Command.values(inputView.askDrawable(player.name.name))
            command.isDrawable
        } catch (e: Exception) {
            errorView.showErrorMessage(e.message.toString())
            askDrawable(player)
        }

    private fun draw(player: Player, deck: Deck, drawStrategy: DrawStrategy): Player {
        val nowPlayer = player.draw(deck, drawStrategy)
        resultView.showPlayerHands(nowPlayer)
        return drawOrStay(nowPlayer, deck, drawStrategy)
    }

    private fun startGameOneDealer(dealer: Player, deck: Deck, hitDrawStrategy: HitDrawStrategy): Player {
        var nowDealer = dealer
        while (!nowDealer.isFinished()) {
            resultView.noticeDealerDraw()
            nowDealer = dealer.draw(deck, hitDrawStrategy)
        }
        return nowDealer
    }
}

fun main() {
    val inputView = InputView(ConsoleInputStrategy, ConsoleOutputStrategy)
    val resultView = ResultView(ConsoleOutputStrategy)
    val errorView = ErrorView(ConsoleOutputStrategy)
    BlackJackApplication(inputView, resultView, errorView).run()
}
