package scat

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousSocketChannel

import scat.Socket._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

/**
 * Author: aguestuser
 * Date: 2/2/15.
 * License: GPLv2
 */

object RunClient extends App {

  val cSock = getClientSock(args(1).toInt)
  val sAddr = new InetSocketAddress(args(0).toInt)
  val client = new Client(cSock)
  client.connectTo(sAddr)
  Await.result(client.kill.future, Duration.Inf)
}

class Client(
              val cSock: AsynchronousSocketChannel,
              val handle: Array[Byte] = Array[Byte](),
              val kill: Promise[Unit] = Promise[Unit]()) {

  val humanHandle = handle match {
    case Array() => "no_name"
    case good => good.map{ _.toChar }.mkString
  }
  val info = humanHandle + " @ " + cSock.getRemoteAddress.toString

  def connectTo(sAddr: SAddr): Future[Unit] =
    connect(cSock, sAddr) flatMap { _ =>
      listenToWire
      listenToUser
      Future.successful(())
    }

  def listenToWire: Future[Unit] =
    read(cSock) flatMap { msg =>
      print(s"${strFromWire(msg)}\n")
      listenToWire
    }

  def listenToUser: Future[Unit] =
    Future { scala.io.StdIn.readLine().getBytes } flatMap { msg =>
      dispatch(msg) flatMap { _ =>
        listenToUser
      }
    }

  def dispatch(msg: Array[Byte]): Future[Unit] =
    if (strFromWire(msg) == "exit") {
      write(msg, cSock) flatMap { _ =>
        cSock.close()
        println(s"Closed connection with scat server")
        kill success { () }
        Future.successful(())
      }
    }
    else write(msg, cSock)

}