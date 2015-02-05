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
  val kill = Promise[Unit]()
  Client.initialize(cSock,sAddr, kill)
  Await.result(kill.future, Duration.Inf)
}

class Client(val sock: AsynchronousSocketChannel, val handle: Array[Byte]) {
  val humanHandle = handle.map{ _.toChar }.mkString
  val info = humanHandle + " @ " + sock.getRemoteAddress.toString
}

object Client {

  def initialize(cSock: SC, sAddr: SAddr, kill: Promise[Unit]): Future[Unit] =
    connect(cSock, sAddr) flatMap { _ => 
      listenToWire(cSock)
      listenToUser(cSock, kill)
    }

  def listenToWire(cSock: SC): Future[Unit] =
    read(cSock) flatMap { msg =>
      print(s"${strFromWire(msg)}\n")
      listenToWire(cSock)
    }

  def listenToUser(cSock: SC, kill: Promise[Unit]): Future[Unit] =
    Future { scala.io.StdIn.readLine().getBytes } flatMap { msg =>
      dispatch(msg, cSock, kill) flatMap { _ =>
        listenToUser(cSock, kill)
      }
    }

  def dispatch(msg: Array[Byte], cSock: SC, kill: Promise[Unit]): Future[Unit] =
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