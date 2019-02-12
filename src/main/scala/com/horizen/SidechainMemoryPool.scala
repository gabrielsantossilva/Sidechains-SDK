package com.horizen

import java.util.{List => JList}

import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.secret.Secret
import com.horizen.transaction.BoxTransaction
import scorex.util.ModifierId
import scorex.core.transaction.MempoolReader

import scala.collection.concurrent.TrieMap
import scala.util.{Try, Success, Failure}
import scala.collection.JavaConverters._

class SidechainMemoryPool(val unconfirmed: TrieMap[String, SidechainTypes#BT])
  extends scorex.core.transaction.MemoryPool[SidechainTypes#BT, SidechainMemoryPool]
{
  override type NVCT = SidechainMemoryPool
  //type BT = BoxTransaction[ProofOfKnowledgeProposition[Secret], Box[ProofOfKnowledgeProposition[Secret]]]

  def this(memoryPool : SidechainMemoryPool) {
    this(memoryPool.unconfirmed.clone())
  }

  // Getters:
  override def getById(id: ModifierId): Option[SidechainTypes#BT] = {
    unconfirmed.get(id)
  }

  override def modifierById(modifierId: ModifierId): Option[SidechainTypes#BT] = {
    unconfirmed.get(modifierId)
  }

  override def contains(id: ModifierId): Boolean = {
    unconfirmed.contains(id)
  }

  override def getAll(ids: Seq[ModifierId]): Seq[SidechainTypes#BT] = {
    ids.flatMap(getById)
  }

  override def size: Int = {
    unconfirmed.size
  }

  override def take(limit: Int): Iterable[SidechainTypes#BT] = {
    unconfirmed.values.toSeq.sortBy(-_.fee).take(limit)
  }

  override def filter(txs: Seq[SidechainTypes#BT]): SidechainMemoryPool = {
    filter(t => !txs.exists(_.id == t.id))
  }

  override def filter(condition: (SidechainTypes#BT) => Boolean): SidechainMemoryPool = {
    unconfirmed.retain { (k, v) =>
      condition(v)
    }
    this
  }

  override def notIn(ids: Seq[ModifierId]): Seq[ModifierId] = {
    super.notIn(ids)
  }

  override def getReader: MempoolReader[SidechainTypes#BT] = {
    this
  }

  // Setters:
  private def internalPut (tx: SidechainTypes#BT) : Boolean = {
    val txIC = tx.incompatibilityChecker()
    val txs = this.unconfirmed.values.toList.asJava.asInstanceOf[java.util.List[BoxTransaction[Proposition, Box[Proposition]]]]
    txIC.hasIncompatibleTransactions(tx, txs)
  }

  override def put(tx: SidechainTypes#BT): Try[SidechainMemoryPool] = {
    // check if tx is not colliding with unconfirmed using
    // tx.incompatibilityChecker().hasIncompatibleTransactions(tx, unconfirmed)
    val memoryPool = new SidechainMemoryPool(this)
    if (memoryPool.internalPut(tx))
      new Success[SidechainMemoryPool](memoryPool)
    else
      new Failure(new IllegalArgumentException("Transaction is incompatible - " + tx))
  }

  override def put(txs: Iterable[SidechainTypes#BT]): Try[SidechainMemoryPool] = {
    // for each tx in txs call "put"
    // rollback to initial state if "put(tx)" failed
    val memoryPool = new SidechainMemoryPool(this)
    for (tx <- txs)
      if (!memoryPool.internalPut(tx))
        new Failure(new IllegalArgumentException("There is incompatible transaction - " + tx))
    new Success[SidechainMemoryPool](memoryPool)
  }

  // TO DO: check usage in Scorex core
  // Probably, we need to do a Global check inside for both new and existing transactions.
  override def putWithoutCheck(txs: Iterable[SidechainTypes#BT]): SidechainMemoryPool = {
    throw new UnsupportedOperationException()
  }

  override def remove(tx: SidechainTypes#BT): SidechainMemoryPool = {
    val memoryPool = new SidechainMemoryPool(this)
    memoryPool.unconfirmed.remove(tx.id)
    memoryPool
  }
}

