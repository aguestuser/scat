package scat

/**
 * Author: @aguestuser
 * Date: 2/5/15
 * License: GPLv2
 */

trait SDecoding
case class SList(l: List[SDecoding]) extends SDecoding
case class SHandle(h: Array[Byte]) extends SDecoding
case class SMsg(msg: Array[Byte]) extends SDecoding
