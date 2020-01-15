package it.bitrock.dvs.api.config

import scala.concurrent.duration.FiniteDuration

final case class ServerConfig(
    host: String,
    port: Int,
    websocket: WebsocketConfig
)

final case class WebsocketConfig(
    throttleDuration: FiniteDuration,
    pathPrefix: String,
    flightListPath: String,
    topElementsPath: String,
    totalElementsPath: String
)