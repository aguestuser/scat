package scat

import java.nio.channels.{ AsynchronousSocketChannel => SC, AsynchronousServerSocketChannel => SSC }
import java.util.Date

import scat.Socket._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}
import scala.collection.concurrent.{TrieMap => CMap}

/**
 * Author: @aguestuser
 * Date: 1/31/15
 * License: GPLv2
 */

object RunServer extends App {
  import scat.Server._
  try {
    val kill = Promise[Unit]()
    acceptClients(getServerSock(args(0).toInt)), kill)
    Await.result(kill.future, Duration.Inf)
  } catch {
    case e: NumberFormatException => throw new NumberFormatException("Port number for scat must be a valid int")
  }
}

object Server extends Logger with ClientManager {
  val clients: CMap[Client,Boolean] = CMap[Client,Boolean]()
  val logs: CMap[Date,String] = CMap[Date,String]()
  val kill: Promise[Future[Unit]]  = Promise[Future[Unit]]()

  def acceptClients(sSock: SSC, kill: Promise[Unit]): Future[Unit] =
    accept(sSock) flatMap { sock =>
      logConnection(logs, sock)
      Future { clients putIfAbsent(InitClient(sock), true) }
      acceptClients(sSock,kill)
    }



  def listen(client: Client): Future[Unit] =
    read(client.cSock) flatMap { msg =>
      dispatch(msg, client) flatMap { cont =>
        if (cont) listen(client)
        else Future.successful(())
      }
    }

  def dispatch(msg: Array[Byte], sender: Client): Future[Boolean] =
    if (strFromWire(msg) == "exit") close(sender)
    else relay(msg, sender)

  def close(sender: Client): Future[Boolean] = {
    sender.cSock.close()
    log(this, new Date(), s"Closed connection with ${sender.info}")
    clients - sender
    Future.successful(false)
  }

  def relay(msg: Array[Byte], sender: Client): Future[Boolean] =
    Future sequence { ( clients.keySet - sender) map { cl =>
        write(trimByteArray(appendHandle(msg, sender)), cl.cSock)
      }
    } flatMap { _ =>
      log(this, new Date(), s"Relayed message from ${sender.humanHandle}")
      Future.successful(true)
    }

  def exit: Future[Unit] = {
    clients.keySet map { _.cSock.close()}
    sSock.close()
    kill success {Future.successful(())}
    Future.successful(())
  }


}