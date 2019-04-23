package org.ergoplatform.nodeView.wallet

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.ergoplatform.modifiers.mempool.ErgoTransaction
import org.ergoplatform.nodeView.wallet.ErgoWalletActor._
import org.ergoplatform.nodeView.wallet.requests.TransactionRequest
import org.ergoplatform.wallet.boxes.ChainStatus
import org.ergoplatform.wallet.boxes.ChainStatus.{Fork, MainChain}
import org.ergoplatform.{ErgoAddress, ErgoBox, P2PKAddress}
import sigmastate.basics.DLogProtocol.DLogProverInput
import scorex.core.transaction.wallet.VaultReader

import scala.concurrent.Future
import scala.util.Try

trait ErgoWalletReader extends VaultReader {

  val walletActor: ActorRef

  private implicit val timeout: Timeout = Timeout(60, TimeUnit.SECONDS)

  def initWallet(pass: String): Future[Try[String]] = {
    (walletActor ? InitWallet(pass)).mapTo[Try[String]]
  }

  def restoreWallet(encryptionPass: String, mnemonic: String,
                    mnemonicPassOpt: Option[String] = None): Unit = {
    walletActor ! RestoreWallet(mnemonic, mnemonicPassOpt, encryptionPass)
  }

  def unlockWallet(pass: String): Future[Try[Unit]] = {
    (walletActor ? UnlockWallet(pass)).mapTo[Try[Unit]]
  }

  def lockWallet(): Unit = {
    walletActor ! LockWallet
  }

  def balances(chainStatus: ChainStatus): Future[BalancesSnapshot] = {
    (walletActor ? ReadBalances(chainStatus)).mapTo[BalancesSnapshot]
  }

  def confirmedBalances(): Future[BalancesSnapshot] = balances(MainChain)

  def balancesWithUnconfirmed(): Future[BalancesSnapshot] = balances(Fork)

  def publicKeys(from: Int, to: Int): Future[Seq[P2PKAddress]] = {
    (walletActor ? ReadPublicKeys(from, to)).mapTo[Seq[P2PKAddress]]
  }

  def firstSecret(): Future[DLogProverInput] = {
    (walletActor ? GetFirstSecret).mapTo[DLogProverInput]
  }

  def unspendBoxes(): Future[Iterator[ErgoBox]] = {
    (walletActor ? GetBoxes).mapTo[Iterator[ErgoBox]]
  }

  def randomPublicKey(): Future[P2PKAddress] = {
    (walletActor ? ReadRandomPublicKey).mapTo[P2PKAddress]
  }

  def trackedAddresses(): Future[Seq[ErgoAddress]] = {
    (walletActor ? ReadTrackedAddresses).mapTo[Seq[ErgoAddress]]
  }

  def generateTransaction(requests: Seq[TransactionRequest]): Future[Try[ErgoTransaction]] = {
    (walletActor ? GenerateTransaction(requests)).mapTo[Try[ErgoTransaction]]
  }

}
