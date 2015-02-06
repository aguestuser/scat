package scat

import java.nio.channels.{AsynchronousServerSocketChannel => SSC, AsynchronousSocketChannel => SC}
import java.util.Date

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
        log(logs, now, msg)
        Future.successful(()) } }

  def logConnection(logs: CMap[Date,String], sock: SC) : Future[Unit] =
      log(logs, new Date(), s"Client connection received from ${sock.getRemoteAddress}")

  def logNewClient(logs: CMap[Date,String], cls: CMap[Client,Boolean], cl: Client) : Future[Unit] = {
    log(logs, new Date(), s"Created new client: \n${Client.info(cl)}\n" +
      s"${cls.size} total client(s): \n" +
      s"${cls.keySet.map{ Client.info }.mkString("\n") }") }

  def getLogs(logs: CMap[Date,String], num: Int): Vector[String] = {
    val ks = logs.keySet
    ks.toVector
      .sortBy(_.getTime)
      .slice(ks.size - num, ks.size)
      .map{ logs.get(_).get } }


}
