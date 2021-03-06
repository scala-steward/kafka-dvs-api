package it.bitrock.dvs.api.core.dispatcher

import akka.actor.{Actor, ActorRef}
import com.typesafe.scalalogging.LazyLogging
import it.bitrock.dvs.api.config.WebSocketConfig

trait MessageDispatcher extends Actor with LazyLogging {

  val sourceActorRef: ActorRef

  val webSocketConfig: WebSocketConfig

  def forwardMessage(event: String): Unit =
    sourceActorRef ! event

  override def preStart(): Unit = {
    super.preStart()

    // This actor lifecycle is bound to the sourceActor
    context watch sourceActorRef
    logger.debug("Starting message processor")
  }

  override def postStop(): Unit = {
    logger.debug("Stopping message processor")
    super.postStop()
  }
}
