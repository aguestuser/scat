package scat

import java.nio.channels._

import scat.Socket._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/**
 * Author: @aguestuser
 * Date: 1/31/15
 * License: GPLv2
 */

object RunServer extends App {
  try {
    val sSock = getServerSock(args(0).toInt)
    println(s"Listening on port ${sSock.getLocalAddress}")
    Server.acceptClients(sSock)
  } catch {
    case e: NumberFormatException => throw new NumberFormatException("Port number for scat must be a valid int")
  }
}

object Server {

  type SSC = AsynchronousServerSocketChannel
  type SC = AsynchronousSocketChannel

  var clients = Set[Client]()

  def acceptClients(sSock: SSC) : Unit = {
    val cSock = accept(sSock)
    cSock flatMap { cs =>
      configClient(cs) flatMap { cl =>
        listen(cl) } }

    Await.result(cSock, Duration.Inf)
    acceptClients(sSock)
  }

  def configClient(cSock: SC) : Future[Client] = {
    getHandle(cSock) map { h =>
      val cl = new Client(cSock,h)
      synchronized { clients = clients + cl}
      println(s"Created new client: \n${cl.info}")
      println(s"${clients.size} total client(s): \n${clients.map{ _.info }.mkString("\n") }")
      cl
    }
  }

  def getHandle(cSock: SC): Future[Array[Byte]] = {
    val prompt = "Welcome to scat! Please choose a handle...\n".getBytes
    write(prompt, cSock) flatMap { _ =>
      read(cSock) map { msg =>
        trimCarriageReturn(msg) } }
  }

  def listen(client: Client): Future[Set[Unit]] =
    read(client.sock) flatMap { msg =>
      relay(msg, client) flatMap { _ =>
        println(s"Relayed message from ${client.humanHandle}")
        listen(client)
      }
    }

  def relay(msg: Array[Byte], sender: Client): Future[Set[Unit]] =
    if (msg.map(_.toChar).mkString.trim == "exit") {
      sender.sock.close()
      synchronized { clients = clients - sender }
      Future.successful(Set(()))
    }
    else Future sequence {
      (clients - sender) map { cl => write(appendHandle(msg, sender), cl.sock) }
    }


  private def appendHandle(msg: Array[Byte], cl: Client) : Array[Byte] = cl.handle ++ ": ".getBytes ++ msg
  private def trimCarriageReturn(msg: Array[Byte]): Array[Byte] = msg.takeWhile( _ != (10:Byte))

}