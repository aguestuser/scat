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
    val server = new Server
    server.acceptClients(getServerSock(args(0).toInt))
    Await.result(Promise[Unit]().future, Duration.Inf)
  } catch {
    case e: NumberFormatException => throw new NumberFormatException("Port number for scat must be a valid int")
  }
}

class Server(
              val clients: CMap[Client,Boolean] = CMap[Client,Boolean](),
              val logs: CMap[Date,String] = CMap[Date,String]()
              ) {

  type SSC = AsynchronousServerSocketChannel
  type SC = AsynchronousSocketChannel

  def acceptClients(sSock: SSC) : Future[Unit] =
    accept(sSock) flatMap { cs =>
      configClient(cs) flatMap { cl => listen(cl)}
      acceptClients(sSock)
    }

  //TODO move to ClientManager trait?

  def configClient(cSock: SC) : Future[Client] = {
    getHandle(cSock) flatMap { h =>
      createClient(cSock, h) flatMap { cl =>
        log(new Date(), s"Created new client: \n${cl.info}\n" +
          s"${clients.size} total client(s): \n" +
          s"${clients.keySet.map{ _.info }.mkString("\n") }") map { _ =>
          cl
        }
      }
    }
  }

  def getHandle(cSock: SC): Future[Array[Byte]] = {
    val prompt = "Welcome to scat! Please choose a handle...\n".getBytes
    write(prompt, cSock) flatMap { _ =>
      read(cSock) map { msg =>
        trimByteArray(msg) }
    }
  }

  def createClient(cSock: SC, handle: Array[Byte]) : Future[Client] =
    Future {
      val cl = new Client(cSock,handle)
      clients putIfAbsent(cl, true)
      cl
    }

  // TODO move to Logger Trait

  def log(now: Date, msg: String) : Future[Unit] = {
    logs putIfAbsent(now,msg) match {
      case None => // if no member of the hash map had that key
        println(msg)
        Future.successful(())
      case Some(str) => // if some member of the hash map had that key
        log(now,msg)
    }
  }

  def getLogs(num: Int): Vector[String] = {
//    res
//    val res = logs.keySet.toVector
//    println(s"YO! ${logs.keySet.toVector} YO???")
//    res
    logs.keySet.toVector.sortBy(_.getTime).slice(0, logs.keys.size) map { logs.get(_).get }
  }


  //TODO keep this here!

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
    clients - sender
    Future.successful(false)
  }

  def relay(msg: Array[Byte], sender: Client): Future[Boolean] =
    Future sequence { ( clients.keys.toSet - sender) map { cl =>
        write(trimByteArray(format(msg, sender)), cl.sock)
      }
    } flatMap { _ =>
      println(s"Relayed message from ${sender.humanHandle}")
      Future.successful(true)
    }

  def format(msg: Array[Byte], cl: Client) : Array[Byte] = cl.handle ++ ": ".getBytes ++ msg ++ "\n".getBytes
}