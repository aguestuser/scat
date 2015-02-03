package scat

import java.nio.channels._

import scat.Socket._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Promise, Await, Future}

/**
 * Author: @aguestuser
 * Date: 1/31/15
 * License: GPLv2
 */

object RunServer extends App {
  try {
    Server.acceptClients(getServerSock(args(0).toInt))
    Await.result(Promise[Unit]().future, Duration.Inf)
  } catch {
    case e: NumberFormatException => throw new NumberFormatException("Port number for scat must be a valid int")
  }
}

object Server {

  type SSC = AsynchronousServerSocketChannel
  type SC = AsynchronousSocketChannel

  var clients = Set[Client]()

  def acceptClients(sSock: SSC) : Future[Unit] =
    accept(sSock) flatMap { cs =>
      configClient(cs) flatMap { cl => listen(cl)}
      acceptClients(sSock)
    }

  //TODO move configClient and getHandle to Client object?

  def configClient(cSock: SC) : Future[Client] = {
    getHandle(cSock) map { h =>
      val cl = new Client(cSock,h)
      synchronized { clients = clients + cl }
      println(s"Created new client: \n${cl.info}")
      println(s"${clients.size} total client(s): \n${clients.map{ _.info }.mkString("\n") }")
      cl
    }
  }

  def getHandle(cSock: SC): Future[Array[Byte]] = {
    val prompt = "Welcome to scat! Please choose a handle...\n".getBytes
    write(prompt, cSock) flatMap { _ =>
      read(cSock) map { msg =>
        trimByteArray(msg) }
    }
  }

  def listen(client: Client): Future[Unit] =
    read(client.sock) flatMap { msg =>
      dispatch(msg, client) flatMap { cont =>
        if (cont) listen(client)
        else Future.successful(())
      }
    }

  def dispatch(msg: Array[Byte], sender: Client): Future[Boolean] =
    if (strFromWire(msg) == "exit") close(sender)
    else relay(msg, sender)

  def close(sender: Client): Future[Boolean] = {
    sender.sock.close()
    println(s"Closed connection with ${sender.info}")
    synchronized { clients = clients - sender }
    Future.successful(false)
  }

  def relay(msg: Array[Byte], sender: Client): Future[Boolean] =
    Future sequence { (clients - sender) map { cl =>
        write(trimByteArray(appendHandle(msg, sender)), cl.sock)
      }
    } flatMap { _ =>
      println(s"Relayed message from ${sender.humanHandle}")
      Future.successful(true)
    }

  private def appendHandle(msg: Array[Byte], cl: Client) : Array[Byte] = cl.handle ++ ": ".getBytes ++ msg
}