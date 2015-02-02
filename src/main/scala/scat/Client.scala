package scat

import java.nio.channels.AsynchronousSocketChannel

/**
 * Author: aguestuser
 * Date: 2/2/15.
 * License: GPLv2
 */

class Client(val sock: AsynchronousSocketChannel, val handle: Array[Byte]) {
  val humanHandle = handle.map{ _.toChar }.mkString
  val info = humanHandle + " @ " + sock.getRemoteAddress.toString
}
