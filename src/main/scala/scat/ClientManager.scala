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



trait ClientManager {

  def shakeHands(sock: SC): Future[RmtClient] = {
    read(sock) flatMap { handle =>
      val h = trimByteArray(handle)
      Future.successful(RmtClient(sock, h)) } }

  def addClient(clients: CMap[RmtClient, Boolean], client: RmtClient): Future[RmtClient] =
    Future { clients putIfAbsent(client, true) } flatMap { _ =>
      Future.successful(client) }

  def sum(bs: Array[Byte]) : Int = (0 /: bs)((sum,b) => 256*sum + (b & 0xff))
  def sum(bs: List[Byte]) : Int = (0 /: bs)((sum,b) => 256*sum + (b & 0xff))

}
