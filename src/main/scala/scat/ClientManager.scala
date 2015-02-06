package scat

import java.nio.channels.{AsynchronousSocketChannel => SC}

import scat.Socket._

import scala.collection.concurrent.{TrieMap => CMap}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
* Author: @aguestuser
* Date: 2/4/15
* License: GPLv2
*/



trait ClientManager extends Logger {

  def shakeHands(sock: SC): Future[Unit] = {
    read(sock) flatMap { msg =>

    }
  }

  def parseHandShake(bs: Array[Byte]) = {
    val (flag, rest) = bs.splitAt(4) match { case (h,t) => (sum(h),t) }


  }

  def parseOne(bs: Array[Byte], num: Int)



  def addClient(client: Client) : Future[Unit] =
    Future { clients putIfAbsent(client, true)}

  def getHandle(cSock: SC): Future[Array[Byte]] =
    write("Welcome to scat! Please choose a handle...\n".getBytes, cSock) flatMap { _ =>
      read(cSock) map { msg =>
        trimByteArray(msg) } }


  def sum(bs: Array[Byte]) : Int = (0 /: bs)((sum,b) => 256*sum + (b & 0xff))
  def sum(bs: List[Byte]) : Int = (0 /: bs)((sum,b) => 256*sum + (b & 0xff))

}
