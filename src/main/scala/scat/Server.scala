package scat

import java.nio.channels._
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
  try {
    val server = new Server(getServerSock(args(0).toInt))
    server.acceptClients
    Await.result(server.kill.future, Duration.Inf)
  } catch {
    case e: NumberFormatException => throw new NumberFormatException("Port number for scat must be a valid int")
  }
}

class Server(
              val sSock: AsynchronousServerSocketChannel,
              val clients: CMap[Client,Boolean] = CMap[Client,Boolean](),
              val logs: CMap[Date,String] = CMap[Date,String](),
              val kill: Promise[Future[Unit]]  = Promise[Future[Unit]]()
              ) extends Logger with ClientManager {

  type SSC = AsynchronousServerSocketChannel
  type SC = AsynchronousSocketChannel

  def acceptClients: Future[Unit] =
    accept(sSock) flatMap { cs =>

      log(this, new Date(), s"Client connection received from ${cs.getRemoteAddress}")

      configClient(this, cs) flatMap { cl =>
        addClient(this, cl) flatMap { _ =>
          listen(cl) } }

      acceptClients
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

  private def appendHandle(msg: Array[Byte], cl: Client) : Array[Byte] = cl.handle ++ ": ".getBytes ++ msg

}