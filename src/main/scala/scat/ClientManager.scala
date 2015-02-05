package scat

import java.util.Date

import scat.Socket._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
* Author: @aguestuser
* Date: 2/4/15
* License: GPLv2
*/

trait ClientManager extends Logger {

  def configClient(server: Server, cSock: SC) : Future[Client] =
    getHandle(cSock) flatMap { h =>
      val cl = new Client(cSock,h)

      Future.successful(cl)
    }

  def addClient(server: Server, client: Client) : Future[Unit] =
    Future{
      server.clients putIfAbsent(client, true)
    } flatMap { _ =>
      log(server, new Date(), s"Created new client: \n${client.info}\n" +
        s"${server.clients.size} total client(s): \n" +
        s"${server.clients.keySet.map{ _.info }.mkString("\n") }")
    }

  def getHandle(cSock: SC): Future[Array[Byte]] =
    write("Welcome to scat! Please choose a handle...\n".getBytes, cSock) flatMap { _ =>
      read(cSock) map { msg =>
        trimByteArray(msg) } }

}
