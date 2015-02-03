package scat

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousSocketChannel
import Socket._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, ExecutionContext}
import ExecutionContext.Implicits.global

/**
 * Author: aguestuser
 * Date: 2/2/15.
 * License: GPLv2
 */

object RunClient extends App {
  val cSock = getClientSock(args(1).toInt)
  val sAddr = new InetSocketAddress(args(0).toInt)
  Client.initialize(cSock,sAddr)
}

class Client(val sock: AsynchronousSocketChannel, val handle: Array[Byte]) {
  val humanHandle = handle.map{ _.toChar }.mkString
  val info = humanHandle + " @ " + sock.getRemoteAddress.toString
}

object Client {

  def initialize(cSock: SC, sAddr: SAddr): Future[Unit] = {
//    val c = connect(cSock, sAddr)
//    Await.result(c, Duration.Inf)
//    listen(cSock)
    val cnxn = connect(cSock, sAddr) map { _ => listen(cSock)}
    Await.result(cnxn, Duration.Inf )
  }

  def listen(cSock: SC): Future[Unit] = {
    val done = read(cSock) map { msg => println(msg.map(_.toChar).mkString) }
    Await.result(done, Duration.Inf )
    done flatMap { _ => listen(cSock)}
  }

  def getUserInput(): Future[Unit]

  def
}