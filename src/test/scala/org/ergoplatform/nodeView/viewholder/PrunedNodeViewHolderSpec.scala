package org.ergoplatform.nodeView.viewholder

import org.ergoplatform.mining.DefaultFakePowScheme
import org.ergoplatform.modifiers.ErgoFullBlock
import org.ergoplatform.modifiers.history.Header
import org.ergoplatform.nodeView.state.wrapped.WrappedUtxoState
import org.ergoplatform.nodeView.state.StateType
import org.ergoplatform.settings.{ErgoSettings, VotingSettings}
import org.ergoplatform.utils.fixtures.NodeViewFixture
import org.ergoplatform.utils.{ErgoPropertyTest, NodeViewTestOps}
import scorex.core.NodeViewHolder.ReceivableMessages.LocallyGeneratedModifier
import scorex.testkit.utils.NoShrink

import scala.concurrent.duration._

/**
  * Test how pruning is working
  */
class PrunedNodeViewHolderSpec extends ErgoPropertyTest with NodeViewTestOps with NoShrink {

  private val BlocksToKeep = 3
  private val BlockInterval = 2.minutes

  def prunedSettings: ErgoSettings = {
    val defaultSettings = ErgoSettings.read(None)
    defaultSettings.copy(
      chainSettings = defaultSettings.chainSettings.copy(
        powScheme = new DefaultFakePowScheme(defaultSettings.chainSettings.powScheme.k, defaultSettings.chainSettings.powScheme.n),
        voting = VotingSettings(10, 10, 10),
        blockInterval = BlockInterval
      ),
      walletSettings = defaultSettings.walletSettings.copy(scanningInterval = 15.millis),
      nodeSettings = defaultSettings.nodeSettings.copy(
        stateType = StateType.Digest,
        verifyTransactions = true,
        PoPoWBootstrap = false,
        blocksToKeep = BlocksToKeep
      )
    )
  }

  def genFullChain(genesisState: WrappedUtxoState, howMany: Int): Seq[ErgoFullBlock] = {
    (1 to howMany).foldLeft((Seq[ErgoFullBlock](), genesisState, None: Option[Header])) { case ((chain, wus, parentOpt), h) =>
      val time = System.currentTimeMillis() - (howMany - h) * BlockInterval.toMillis
      val block = validFullBlock(parentOpt, wus, time)
      val newState = wus.applyModifier(block).get
      (chain :+ block, newState, Some(block.header))
    }._1
  }

  property(s"pruned") {
    new NodeViewFixture(prunedSettings).apply({ fixture =>
      import fixture._

      val (us, bh) = createUtxoState(stateConstants)
      val wus = WrappedUtxoState(us, bh, stateConstants)

      val fullChain = genFullChain(wus, 20)

      fullChain.take(10).foreach { block =>
        applyHeader(block.header).isSuccess shouldBe true
      }

      fullChain.drop(10).foreach { block =>
        applyHeader(block.header).isSuccess shouldBe true
      }

      fullChain.takeRight(11).foreach { block =>
        block.blockSections.foreach { section =>
          nodeViewHolderRef ! LocallyGeneratedModifier(section)
          Thread.sleep(500)
        }
      }
    })
  }

}
