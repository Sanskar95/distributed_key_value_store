/*
 * The MIT License
 *
 * Copyright 2017 Lars Kroll <lkroll@kth.se>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package se.kth.id2203.kvstore

import com.larskroll.common.repl._
import com.typesafe.scalalogging.StrictLogging;
import org.apache.log4j.Layout
import util.log4j.ColoredPatternLayout;
import fastparse._, NoWhitespace._
import concurrent.Await
import concurrent.duration._

object ClientConsole {
  def lowercase[_: P] = P(CharIn("a-z"))
  def uppercase[_: P] = P(CharIn("A-Z"))
  def digit[_: P] = P(CharIn("0-9"))
  def simpleStr[_: P] = P(lowercase | uppercase | digit).rep!
  val colouredLayout = new ColoredPatternLayout("%d{[HH:mm:ss,SSS]} %-5p {%c{1}} %m%n");
}

class ClientConsole(val service: ClientService) extends CommandConsole with ParsedCommands with StrictLogging {
  import ClientConsole._;

  override def layout: Layout = colouredLayout;
  override def onInterrupt(): Unit = exit();

  val getParser = new ParsingObject[String] {
    override def parseOperation[_: P]: P[String] = P("GET" ~ " " ~ simpleStr.!);
  }
  val getCommand = parsed(getParser, usage = "GET <key>", descr = "Gets a value for a <key>") { key =>
    println(s"GET with $key");
    val response = executeOperation(Get(key, service.self))
    if (response != null) {
      println(s"Response received. Status: ${response.status} Value: ${response.value}")
    }
  }

  val putParser = new ParsingObject[(String, String)] {
    override def parseOperation[_: P]: P[(String, String)] = P("PUT" ~ " " ~ simpleStr.! ~ " " ~ simpleStr.!);
  }
  val putCommand = parsed(putParser, usage = "PUT <key> <value>", descr = "Puts a <value> at a <key>") { parse =>
    val (key, value) = parse
    println(s"PUT value $value to key $key");
    val response = executeOperation(Put(key, value, service.self))
    if (response != null) {
      println(s"Response received. Status: ${response.status}")
    }
  }

  val casParser = new ParsingObject[(String, String, String)] {
    override def parseOperation[_: P]: P[(String, String, String)] = P("CAS" ~ " " ~ simpleStr.! ~ " " ~ simpleStr.! ~ " " ~ simpleStr.!);
  }
  val casCommand = parsed(casParser, usage = "CAS <key> <referenceValue> <value>", descr = "Puts a <value> at a <key> if current value is <referenceValue>") { parse =>
    val (key, value, referenceValue) = parse
    println(s"CAS value $value to key $key if current value is $referenceValue");
    val response = executeOperation(Cas(key, referenceValue, value, service.self))
    if (response != null) {
      println(s"Response received. Status: ${response.status}")
    }
  }

  def executeOperation(operation: Op): OpResponse = {
    val fr = service.operation(operation)
    println("Operation sent to server group for execution , waiting for response");
    try {
      Await.result(fr, 5.seconds);
    } catch {
      case e: Throwable => logger.error("Something went wrong while performing the operation!!!!!!", e)
      null
    }
  }
}
