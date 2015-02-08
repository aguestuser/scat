package scat

import java.nio.channels.{AsynchronousServerSocketChannel => SSC, AsynchronousSocketChannel => SC}
import java.util.Date

import scat.Client._
import scat.Socket._

import scala.collection.concurrent.{TrieMap => CMap}
import scala.concurrent.Future


/**
 * Author: @aguestuser
 * Date: 2/5/15
 * License: GPLv2
 */

trait Logger  {

  def log(logs: CMap[Date,String], now: Date, msg: String) : Future[Unit] = {
    logs putIfAbsent(now,msg) match {
      case None => // if no member of the hash map had that key, continue
        println(msg)
        Future.successful(())
      case Some(str) => // if some member of the hash map had that key, try again
        log(logs, new Date(), msg)
        Future.successful(()) } }

  def logConnection(logs: CMap[Date,String], sock: SC) : Future[Unit] =
      log(logs, new Date(), s"Client connection received from ${sock.getRemoteAddress}")

  def logNewClient(logs: CMap[Date,String], cls: CMap[RmtClient,Boolean], cl: RmtClient) :Future[Unit] =
    log(logs, new Date(), s"Created new client: \n${info(cl)}\n" +
      s"${cls.size} total client(s): \n" +
      s"${cls.keySet.map(info).toVector.sorted.mkString("\n")}")

  def logClose(logs: CMap[Date,String], cl: RmtClient): Future[Unit] =
    log(logs, new Date(), s"Closed connection with ${info(cl)}")

  def logRelay(logs: CMap[Date,String], cl: RmtClient, msg: Array[Byte]): Future[Unit] =
    log(logs, new Date(), s"Relayed message from ${info(cl)}\n${strFromWire(msg)}")

  def getLogs(logs: CMap[Date,String], num: Int): Vector[String] = {
    val ks = logs.keySet
    ks.toVector
      .sortBy(_.getTime)
      .slice(ks.size - num, ks.size)
      .map{ logs.get(_).get } }


}
