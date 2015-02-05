package scat

import java.util.Date

import scala.collection.concurrent.{TrieMap => CMap}
import scala.concurrent.Future


/**
 * Author: @aguestuser
 * Date: 2/5/15
 * License: GPLv2
 */

trait Logger  {

  def log(server: Server, now: Date, msg: String) : Future[Unit] = {
    server.logs putIfAbsent(now,msg) match {
      case None => // if no member of the hash map had that key
        println(msg)
        Future.successful(())
      case Some(str) => // if some member of the hash map had that key
        log(server, now, msg)
    }
  }

  def getLogs(server: Server, num: Int): Vector[String] = {
    val ks = server.logs.keySet
    ks.toVector
      .sortBy(_.getTime)
      .slice(ks.size - num, ks.size)
      .map{ server.logs.get(_).get }
  }


}
