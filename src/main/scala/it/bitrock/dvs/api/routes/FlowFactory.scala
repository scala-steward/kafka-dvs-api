package it.bitrock.dvs.api.routes

import akka.NotUsed
import akka.actor.{ActorRef, PoisonPill, Terminated}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.typesafe.scalalogging.LazyLogging
import it.bitrock.dvs.api.core.factory.MessageDispatcherFactory
import it.bitrock.dvs.api.definitions.CoordinatesBox
import it.bitrock.dvs.api.definitions.JsonSupport.coordinatesBoxJsonFormat
import spray.json._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

trait FlowFactory {
  def flow: Flow[Message, Message, NotUsed]
}

object FlowFactory extends LazyLogging {
  private val SourceActorBufferSize       = 1000
  private val SourceActorOverflowStrategy = OverflowStrategy.dropHead

  def flightFlowFactory(
      processorFactory: MessageDispatcherFactory
  )(implicit ec: ExecutionContext, materializer: ActorMaterializer): FlowFactory = new FlowFactory {
    override def flow: Flow[Message, Message, NotUsed] = {
      val (sourceActorRef, publisher) = publisherAndActorRef()
      val processor                   = processorFactory.build(sourceActorRef)
      buildFlow(Sink.ignore, publisher, processor)
    }
  }

  def flightListFlowFactory(
      processorFactory: MessageDispatcherFactory
  )(implicit ec: ExecutionContext, materializer: ActorMaterializer): FlowFactory = new FlowFactory {
    override def flow: Flow[Message, Message, NotUsed] = {
      val (sourceActorRef, publisher) = publisherAndActorRef()
      val processor                   = processorFactory.build(sourceActorRef)

      val sink: Sink[Message, NotUsed] =
        Flow
          .fromFunction(parseMessage)
          .collect {
            case Some(x) => x
          }
          .to(Sink.actorRef(processor, Terminated))

      buildFlow(sink, publisher, processor)
    }
  }

  private def publisherAndActorRef()(implicit materializer: ActorMaterializer): (ActorRef, Source[String, NotUsed]) =
    Source
      .actorRef[String](SourceActorBufferSize, SourceActorOverflowStrategy)
      .toMat(BroadcastHub.sink)(Keep.both)
      .run()

  private def buildFlow(sink: Sink[Message, Any], source: Source[String, NotUsed], processor: ActorRef)(
      implicit ec: ExecutionContext
  ): Flow[Message, Message, NotUsed] = {

    Flow
      .fromSinkAndSourceCoupled(sink, source.map(TextMessage(_)))
      .watchTermination() { (termWatchBefore, termWatchAfter) =>
        termWatchAfter.onComplete {
          case Success(_) =>
            logger.info("Web-socket connection closed normally")
            processor ! PoisonPill
          case Failure(e) =>
            logger.warn("Web-socket connection terminated", e)
            processor ! PoisonPill
        }
        termWatchBefore
      }
  }

  private def parseMessage: Message => Option[CoordinatesBox] = {
    case TextMessage.Strict(txt) =>
      logger.debug(s"Got message: $txt")
      Try(txt.parseJson.convertTo[CoordinatesBox])
        .fold(e => {
          logger.warn(s"Failed to parse JSON message $txt", e)
          None
        }, Option(_))
    case m =>
      logger.trace(s"Got non-TextMessage, ignoring it: $m")
      None
  }
}