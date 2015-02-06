package scat

/**
 * Author: @aguestuser
 * Date: 2/5/15
 * License: GPLv2
 */

trait sDecoding
case class sList(l: List[sDecoding]) extends sDecoding
case class sFlag(fl: Array[Byte]) extends sDecoding
case class sHandle(h: Array[Byte]) extends sDecoding
case class sMsg(msg: Array[Byte]) extends sDecoding
