/*
 * Copyright 2018 Fluence Labs Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fluence.node.workers.tendermint

import java.nio.ByteBuffer

import cats.data.EitherT
import cats.effect.concurrent.MVar
import cats.effect.{ConcurrentEffect, Resource, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.apply._
import com.softwaremill.sttp.SttpBackend
import fluence.effects.ipfs.IpfsClient
import fluence.effects.tendermint.block.data.Block
import fluence.effects.tendermint.block.history.{BlockHistory, Receipt}
import fluence.effects.{Backoff, EffectError}
import fluence.log.Log
import fluence.node.MakeResource
import fluence.node.config.storage.RemoteStorageConfig
import fluence.node.workers.{Worker, WorkerServices}
import io.circe.Json

import scala.language.higherKinds

/**
 * Implements continuous uploading process of Tendermint's blocks
 *
 * @param history Description of how to store blocks
 */
class BlockUploading[F[_]: ConcurrentEffect: Timer: Log](history: BlockHistory[F]) {

  /**
   * Subscribe on new blocks from tendermint and upload them one by one to the decentralized storage
   * For each block:
   *   1. retrieve vmHash from state machine
   *   2. Send block manifest receipt to state machine
   *
   * @param worker Blocks are coming from this worker's Tendermint; receipts are sent to this worker
   */
  def start(worker: Worker[F]): Resource[F, Unit] = {
    if (!BlockUploading.Enabled) { // TODO: remove that once BlockUploading is enabled
      return Resource.pure(())
    }

    import worker.services

    for {
      // Storage for a previous manifest
      lastManifestReceipt <- Resource.liftF(MVar.of[F, Option[Receipt]](None))
      // TODO: 1st block could be missed if we're too late to subscribe. Retrieve it manually.
      blocks = services.tendermint.subscribeNewBlock[F]
      _ <- MakeResource.concurrentStream(
        blocks.evalMap(processBlock(_, services, lastManifestReceipt, worker.appId)),
        name = "BlockUploadingStream"
      )
    } yield ()
  }

  private def processBlock(
    blockJson: Json,
    services: WorkerServices[F],
    lastManifestReceipt: MVar[F, Option[Receipt]],
    appId: Long
  ) = {
    // Parse block from JSON
    Block(blockJson) match {
      case Left(e) =>
        // TODO: load block through TendermintRPC (not WRPC) again
        Log[F].error(s"BlockUploading: failed to parse Tendermint block", e)

      case Right(block) =>
        val processF = for {
          lastReceipt <- EitherT.liftF(lastManifestReceipt.take)
          vmHash <- services.control.getVmHash
          receipt <- history.upload(block, vmHash, lastReceipt)
          _ <- services.control.sendBlockReceipt(receipt)
          // TODO: How to avoid specifying [F, NoStackTrace, Unit] in liftF?
          _ <- EitherT.liftF[F, EffectError, Unit](lastManifestReceipt.put(Some(receipt)))
        } yield ()

        // TODO: add health check on this: if error keeps happening, miner should be alerted
        // Retry uploading until forever
        Backoff.default
          .retry(
            processF,
            (e: EffectError) => Log[F].error(s"BlockUploading: error uploading block ${block.header.height}", e)
          ) *>
          Log[F].info(s"BlockUploading: block ${block.header.height} uploaded")

    }
  }
}

object BlockUploading {

  private val Enabled = false

  def make[F[_]: Log: ConcurrentEffect: Timer](
    remoteStorageConfig: RemoteStorageConfig
  )(implicit sttpBackend: SttpBackend[EitherT[F, Throwable, ?], fs2.Stream[F, ByteBuffer]]): BlockUploading[F] = {
    // TODO: should I handle remoteStorageConfig.enabled = false?
    val ipfs = new IpfsClient[F](remoteStorageConfig.ipfs.address)
    val history = new BlockHistory[F](ipfs)
    new BlockUploading[F](history)
  }
}